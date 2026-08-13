package org.jenkinsci.plugins.p4.heal.provider;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Item;
import hudson.security.ACL;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import kong.unirest.core.HttpResponse;
import kong.unirest.core.JsonNode;
import kong.unirest.core.Unirest;
import kong.unirest.core.json.JSONArray;
import kong.unirest.core.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import java.io.IOException;
import java.io.Serial;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Talks to Anthropic's Messages API.
 *
 * <p>Uses the {@code kong.unirest} client the plugin already ships and already
 * uses for Swarm, rather than adding a vendor SDK. A Jenkins plugin inherits much
 * of its classpath from Jenkins core — Jackson and Spring among it — so pulling
 * in an SDK with its own copies risks a {@code NoSuchMethodError} at runtime on a
 * user's controller rather than a failure at build time.
 *
 * <p>The tool-calling loop is implemented here because it is vendor-specific.
 * What tools exist and what they are allowed to do stays outside, so another
 * provider can be added without touching the healing logic.
 */
public class ClaudeProviderImpl extends AiProvider {

	@Serial
	private static final long serialVersionUID = 1L;

	private static final String DEFAULT_ENDPOINT = "https://api.anthropic.com/v1/messages";
	private static final String DEFAULT_MODEL = "claude-opus-5";
	private static final int DEFAULT_MAX_TOKENS = 16000;
	private static final String ANTHROPIC_VERSION = "2023-06-01";

	private final String credentialId;

	private String endpoint = DEFAULT_ENDPOINT;
	private int maxToolIterations = 25;
	private int requestTimeoutSeconds = 600;

	@DataBoundConstructor
	public ClaudeProviderImpl(String credentialId) {
		this.credentialId = credentialId;
	}

	public String getCredentialId() {
		return credentialId;
	}

	public String getEndpoint() {
		return endpoint;
	}

	@DataBoundSetter
	public void setEndpoint(String endpoint) {
		this.endpoint = endpoint == null || endpoint.isBlank() ? DEFAULT_ENDPOINT : endpoint;
	}

	public int getMaxToolIterations() {
		return maxToolIterations;
	}

	@DataBoundSetter
	public void setMaxToolIterations(int maxToolIterations) {
		this.maxToolIterations = maxToolIterations;
	}

	public int getRequestTimeoutSeconds() {
		return requestTimeoutSeconds;
	}

	@DataBoundSetter
	public void setRequestTimeoutSeconds(int requestTimeoutSeconds) {
		this.requestTimeoutSeconds = requestTimeoutSeconds;
	}

	@Override
	public AiResult converse(AiRequest request, List<AiTool> tools) throws IOException {
		// Unirest finds its JSON engine through ServiceLoader, which reads the thread
		// context class loader. A Jenkins build thread does not carry this plugin's
		// loader, so without this the JSON classes fail to initialise and the whole
		// conversation dies with "No Json Parsing Implementation Provided".
		Thread current = Thread.currentThread();
		ClassLoader caller = current.getContextClassLoader();
		current.setContextClassLoader(getClass().getClassLoader());
		try {
			return talk(request, tools);
		} finally {
			current.setContextClassLoader(caller);
		}
	}

	private AiResult talk(AiRequest request, List<AiTool> tools) throws IOException {
		AiRequest effective = withDefaults(request);

		JSONArray messages = new JSONArray();
		messages.put(new JSONObject().put("role", "user").put("content", effective.user()));

		long inputTokens = 0;
		long outputTokens = 0;

		for (int iteration = 0; iteration < maxToolIterations; iteration++) {
			JSONObject response;
			try {
				response = send(ClaudeWire.requestBody(effective, tools, messages));
			} catch (IOException e) {
				// Not every model accepts output_config.effort — the cheap critic
				// model in particular rejects it — and the rest of the request is
				// valid, so it is retried without it rather than failing the heal.
				// The effort is dropped for the remaining turns too, so the round
				// trip is paid once per conversation rather than once per turn.
				if (effective.effort() == null || !rejectsEffort(e)) {
					throw e;
				}
				effective = withoutEffort(effective);
				response = send(ClaudeWire.requestBody(effective, tools, messages));
			}

			ClaudeTurn turn = ClaudeWire.parseTurn(response);

			inputTokens += turn.inputTokens();
			outputTokens += turn.outputTokens();

			// A refusal arrives as HTTP 200 with no usable content, so it has to be
			// checked before the text is read.
			if (turn.refused()) {
				return AiResult.refusal(turn.stopReason(), inputTokens, outputTokens);
			}
			if (!turn.wantsTools()) {
				return AiResult.of(turn.text(), inputTokens, outputTokens);
			}

			messages.put(ClaudeWire.assistantMessage(turn.rawContent()));
			messages.put(ClaudeWire.toolResultMessage(runTools(turn.toolUses(), tools)));
		}

		throw new IOException("The model was still requesting tools after " + maxToolIterations
				+ " iterations; abandoning this conversation.");
	}

	/**
	 * Check that a credential can legally be sent as a header value.
	 *
	 * <p>The JDK rejects illegal header values by throwing with the offending value
	 * in the exception message, which would copy the API key into the build log
	 * where anyone with Read permission can see it. So the check happens here, and
	 * what it reports never includes the value.
	 *
	 * <p>An API key is printable ASCII with no spaces. Anything else — a stray
	 * newline, or a whole block of text pasted into the field by mistake — is
	 * rejected before a request is built.
	 *
	 * @param key the configured credential
	 * @return the key, when it is usable
	 * @throws IOException if it is empty or cannot be sent as a header
	 */
	static String usableKey(String key) throws IOException {
		if (key == null || key.isBlank()) {
			throw new IOException("The API key credential is empty.");
		}

		for (int i = 0; i < key.length(); i++) {
			char c = key.charAt(i);
			if (c <= ' ' || c > '~') {
				throw new IOException("The API key credential cannot be sent as a request header:"
						+ " it holds whitespace or a control character at position " + i + " of "
						+ key.length() + ". Check that the credential contains the key and"
						+ " nothing else.");
			}
		}
		return key;
	}

	/**
	 * Fill in anything the caller left unset.
	 *
	 * <p>The model and response ceiling are the heal step's to choose — it varies
	 * them per agent role — so they are not provider settings. These defaults only
	 * cover a request that names neither.
	 */
	private static AiRequest withDefaults(AiRequest request) {
		String requestedModel = StringUtils.defaultIfBlank(request.model(), DEFAULT_MODEL);
		int requestedMaxTokens = request.maxTokens() > 0 ? request.maxTokens() : DEFAULT_MAX_TOKENS;
		return new AiRequest(request.system(), request.user(),
				requestedModel, requestedMaxTokens, request.effort());
	}

	/**
	 * @param e the failure the API reported
	 * @return true when the request was rejected for carrying an effort
	 */
	private static boolean rejectsEffort(IOException e) {
		String message = e.getMessage();
		return message != null && message.contains("effort");
	}

	/**
	 * @param request the request to strip
	 * @return the same request without its effort
	 */
	private static AiRequest withoutEffort(AiRequest request) {
		return new AiRequest(request.system(), request.user(),
				request.model(), request.maxTokens(), null);
	}

	/**
	 * Run every tool the model asked for this turn.
	 *
	 * <p>A tool that fails, or one the model invented, comes back as an error
	 * result rather than aborting the conversation, so the model can correct
	 * itself on the next turn.
	 */
	private List<ToolOutcome> runTools(List<ToolUse> requested, List<AiTool> available) {
		Map<String, AiTool> byName = new HashMap<>();
		for (AiTool tool : available) {
			byName.put(tool.getName(), tool);
		}

		List<ToolOutcome> outcomes = new ArrayList<>();

		for (ToolUse use : requested) {
			AiTool tool = byName.get(use.name());

			if (tool == null) {
				outcomes.add(new ToolOutcome(use.id(),
						"There is no tool called '" + use.name() + "'.", true));
				continue;
			}

			try {
				outcomes.add(new ToolOutcome(use.id(), tool.execute(use.input()), false));
			} catch (IOException e) {
				outcomes.add(new ToolOutcome(use.id(),
						"Tool '" + use.name() + "' failed: " + e.getMessage(), true));
			}
		}
		return outcomes;
	}

	/**
	 * Post one request. Overridable so the tool loop can be tested without a
	 * network.
	 *
	 * @param body the request body
	 * @return the parsed response body
	 * @throws IOException on a transport failure or a non-200 response
	 */
	protected JSONObject send(JSONObject body) throws IOException {
		HttpResponse<JsonNode> response = Unirest.post(endpoint)
				.header("x-api-key", usableKey(apiKey()))
				.header("anthropic-version", ANTHROPIC_VERSION)
				.header("content-type", "application/json")
				.requestTimeout(requestTimeoutSeconds * 1000)
				.body(body)
				.asJson();

		if (response.getStatus() != 200) {
			throw new IOException("Anthropic API returned " + response.getStatus() + ": "
					+ describeError(response));
		}
		return response.getBody().getObject();
	}

	private static String describeError(HttpResponse<JsonNode> response) {
		if (response.getBody() == null) {
			return response.getStatusText();
		}
		JSONObject error = response.getBody().getObject().optJSONObject("error");
		return error == null ? response.getStatusText() : error.optString("message", "");
	}

	/**
	 * Resolve the API key from Jenkins credentials.
	 *
	 * @return the secret text
	 * @throws IOException if the credential is missing
	 */
	protected String apiKey() throws IOException {
		StringCredentials found = CredentialsMatchers.firstOrNull(
				CredentialsProvider.lookupCredentialsInItemGroup(
						StringCredentials.class, Jenkins.get(), ACL.SYSTEM2,
						Collections.emptyList()),
				CredentialsMatchers.withId(credentialId));

		if (found == null) {
			throw new IOException("No secret text credential found with id '" + credentialId + "'.");
		}
		return found.getSecret().getPlainText();
	}

	@Extension
	@Symbol("claude")
	public static final class DescriptorImpl extends AiProviderDescriptor {

		@NonNull
		@Override
		public String getDisplayName() {
			return "Claude (Anthropic)";
		}

		/**
		 * @param project      the job being configured
		 * @param credentialId the currently selected credential
		 * @return secret text credentials the user may pick from
		 */
		public ListBoxModel doFillCredentialIdItems(@AncestorInPath Item project,
		                                            @QueryParameter String credentialId) {
			if (project == null && !Jenkins.get().hasPermission(Jenkins.ADMINISTER)
					|| project != null && !project.hasPermission(Item.EXTENDED_READ)) {
				return new StandardListBoxModel().includeCurrentValue(credentialId);
			}
			// includeCurrentValue keeps an id that does not resolve here — a
			// credential scoped to a folder, or one not yet created — instead of
			// silently blanking the field the next time the job is saved.
			return new StandardListBoxModel()
					.includeEmptyValue()
					.includeMatchingAs(ACL.SYSTEM2, project, StringCredentials.class,
							Collections.emptyList(),
							CredentialsMatchers.instanceOf(StringCredentials.class))
					.includeCurrentValue(credentialId);
		}
	}
}

package org.jenkinsci.plugins.p4.heal.provider;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.io.Serial;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A scriptable {@link AiProvider} for tests, so nothing in the suite ever calls a
 * real model.
 *
 * <p>State is per-instance rather than static: tests construct one and hand it to
 * the code under test. Shared static fixtures leak between test classes in this
 * repository's single-threaded suite and produce failures that look real.
 */
public class FakeAiProvider extends AiProvider {

	@Serial
	private static final long serialVersionUID = 1L;

	private final Deque<AiResult> scripted = new ArrayDeque<>();
	private final List<AiRequest> requests = new ArrayList<>();
	private final List<String> toolInvocations = new ArrayList<>();
	private final Map<String, String> toolsToCall = new LinkedHashMap<>();

	private IOException failure;

	@DataBoundConstructor
	public FakeAiProvider() {
	}

	/**
	 * Queue the answers this provider will give, in order.
	 *
	 * @param results answers to return from successive calls
	 * @return this, for chaining
	 */
	public FakeAiProvider willAnswer(AiResult... results) {
		for (AiResult result : results) {
			scripted.add(result);
		}
		return this;
	}

	/**
	 * Queue plain text answers.
	 *
	 * @param texts answers to return from successive calls
	 * @return this, for chaining
	 */
	public FakeAiProvider willSay(String... texts) {
		for (String text : texts) {
			scripted.add(AiResult.of(text, 100, 50));
		}
		return this;
	}

	/**
	 * Make the next conversation invoke a tool before answering, so tests can
	 * prove the tool wiring works.
	 *
	 * @param toolName  the tool to call
	 * @param jsonInput arguments to pass it
	 * @return this, for chaining
	 */
	public FakeAiProvider willCallTool(String toolName, String jsonInput) {
		toolsToCall.put(toolName, jsonInput);
		return this;
	}

	/**
	 * Make the next conversation fail at the transport level.
	 *
	 * @param error the failure to throw
	 * @return this, for chaining
	 */
	public FakeAiProvider willFail(IOException error) {
		this.failure = error;
		return this;
	}

	/**
	 * @return every request this provider was given, in order
	 */
	public List<AiRequest> getRequests() {
		return List.copyOf(requests);
	}

	/**
	 * @return what each invoked tool returned, in order
	 */
	public List<String> getToolInvocations() {
		return List.copyOf(toolInvocations);
	}

	@Override
	public AiResult converse(AiRequest request, List<AiTool> tools) throws IOException {
		requests.add(request);

		if (failure != null) {
			IOException toThrow = failure;
			failure = null;
			throw toThrow;
		}

		for (Map.Entry<String, String> call : toolsToCall.entrySet()) {
			for (AiTool tool : tools) {
				if (tool.getName().equals(call.getKey())) {
					toolInvocations.add(tool.execute(call.getValue()));
				}
			}
		}
		toolsToCall.clear();

		if (scripted.isEmpty()) {
			throw new IOException("FakeAiProvider was asked for an answer it was not given: "
					+ request.user());
		}
		return scripted.removeFirst();
	}

	@Extension
	@Symbol("fakeAi")
	public static final class DescriptorImpl extends AiProviderDescriptor {

		@NonNull
		@Override
		public String getDisplayName() {
			return "Fake AI provider (testing only)";
		}
	}
}

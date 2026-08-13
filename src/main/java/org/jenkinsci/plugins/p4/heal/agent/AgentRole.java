package org.jenkinsci.plugins.p4.heal.agent;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * The jobs agents are given in a heal run, and the prompt each one uses.
 *
 * <p>Diagnosis runs twice with different lenses and patch generation three times
 * with different framings, because the first plausible answer to a build failure
 * is often not the right one; giving the critics a choice beats iterating on a
 * single candidate.
 *
 * <p>Prompts live as resources rather than string constants — they are the part
 * of this feature most likely to need tuning, and tuning them should not mean
 * editing Java.
 */
public enum AgentRole {

	/**
	 * Root-cause a compilation failure.
	 */
	DIAGNOSE_COMPILE("diagnose-compile", Kind.DIAGNOSE),

	/**
	 * Root-cause a test failure.
	 */
	DIAGNOSE_TEST("diagnose-test", Kind.DIAGNOSE),

	/**
	 * The smallest change that fixes the failure.
	 */
	FIX_MINIMAL("fix-minimal", Kind.FIX),

	/**
	 * A change that addresses the underlying cause rather than the symptom.
	 */
	FIX_ROOT_CAUSE("fix-root-cause", Kind.FIX),

	/**
	 * A change that also guards against the same class of failure recurring.
	 */
	FIX_DEFENSIVE("fix-defensive", Kind.FIX),

	/**
	 * Does the patch fix the real cause, or hide it?
	 */
	CRITIC_CORRECTNESS("critic-correctness", Kind.CRITIC),

	/**
	 * What else does the patch touch, and what might it break?
	 */
	CRITIC_REGRESSION("critic-regression", Kind.CRITIC),

	/**
	 * Does the patch follow this repository's own conventions?
	 */
	CRITIC_GUIDELINES("critic-guidelines", Kind.CRITIC),

	/**
	 * Does the patch weaken the tests instead of satisfying them?
	 */
	CRITIC_TEST_INTEGRITY("critic-test-integrity", Kind.CRITIC);

	private enum Kind {
		DIAGNOSE, FIX, CRITIC
	}

	private final String resource;
	private final Kind kind;

	/**
	 * The prompt, read from the plugin jar on first use. Enum constants are
	 * singletons, so this reads each resource once per controller rather than once
	 * per model call — a heal run makes tens of those.
	 */
	private volatile String cachedPrompt;

	AgentRole(String resource, Kind kind) {
		this.resource = resource;
		this.kind = kind;
	}

	/**
	 * @return true if this role produces a candidate patch
	 */
	public boolean isFix() {
		return kind == Kind.FIX;
	}

	/**
	 * @return true if this role judges a candidate patch
	 */
	public boolean isCritic() {
		return kind == Kind.CRITIC;
	}

	/**
	 * @return the roles that root-cause a failure, in declaration order
	 */
	public static List<AgentRole> diagnosers() {
		return of(Kind.DIAGNOSE);
	}

	/**
	 * @return the roles that produce a candidate patch, in declaration order
	 */
	public static List<AgentRole> fixers() {
		return of(Kind.FIX);
	}

	/**
	 * @return the roles that judge a candidate patch, in declaration order
	 */
	public static List<AgentRole> critics() {
		return of(Kind.CRITIC);
	}

	/**
	 * Ordering follows the enum's declaration order, which is stable between runs;
	 * the prompt cache depends on the request prefix not moving.
	 */
	private static List<AgentRole> of(Kind kind) {
		List<AgentRole> roles = new ArrayList<>();
		for (AgentRole role : values()) {
			if (role.kind == kind) {
				roles.add(role);
			}
		}
		return List.copyOf(roles);
	}

	/**
	 * The role's instruction text.
	 *
	 * @return the prompt
	 * @throws UncheckedIOException if the prompt resource is missing from the
	 *                              plugin, which is a packaging fault rather than
	 *                              anything a build can recover from
	 */
	public String prompt() {
		String prompt = cachedPrompt;
		if (prompt == null) {
			prompt = read();
			cachedPrompt = prompt;
		}
		return prompt;
	}

	private String read() {
		String path = "prompts/" + resource + ".md";
		try (InputStream stream = AgentRole.class.getResourceAsStream(path)) {
			if (stream == null) {
				throw new IOException("Prompt resource is missing from the plugin: " + path);
			}
			return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}

package org.jenkinsci.plugins.p4.heal.provider;

import java.io.Serial;
import java.io.Serializable;

/**
 * One request to a model.
 *
 * <p>{@code system} carries the stable content — repository guidelines and the
 * failure context — and providers that support prompt caching should mark a
 * cache breakpoint at its end. Everything that varies between agents belongs in
 * {@code user}, after that breakpoint, so a heal run reuses one cached prefix
 * across all of its diagnose, fix and critique calls.
 *
 * @param system    stable system prompt, shared across a heal run
 * @param user      the role-specific instruction for this call
 * @param model     provider-specific model id
 * @param maxTokens ceiling on the response
 * @param effort    reasoning effort hint; providers without the concept ignore it
 */
public record AiRequest(String system, String user, String model, int maxTokens, String effort)
		implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;
}

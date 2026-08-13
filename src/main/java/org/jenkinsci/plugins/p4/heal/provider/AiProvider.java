package org.jenkinsci.plugins.p4.heal.provider;

import hudson.DescriptorExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Describable;
import jenkins.model.Jenkins;

import java.io.IOException;
import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * The model behind self-healing builds.
 *
 * <p>Only Claude ships today, but this is an extension point so another provider
 * can be added without touching the healing loop — the same way this plugin
 * already models {@code Workspace}, {@code Populate} and {@code Publish}.
 *
 * <p>The contract is one method on purpose. Providers differ most in how they
 * express tool calling, so each implementation owns its own request/response loop
 * and hands back only the final answer. What stays on this side of the boundary
 * is everything that must not vary by vendor: which tools exist, what they are
 * allowed to do, and every check applied to the result.
 */
public abstract class AiProvider implements ExtensionPoint, Describable<AiProvider>, Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	/**
	 * Run one conversation to completion.
	 *
	 * <p>Implementations drive their own tool-calling loop: send the request, and
	 * while the model asks for a tool, find it in {@code tools} by name, call
	 * {@link AiTool#execute(String)}, feed the result back, and repeat until the
	 * model answers. Implementations must bound that loop themselves so a model
	 * that keeps calling tools cannot spin forever.
	 *
	 * @param request what to ask
	 * @param tools   read-only tools the model may call; may be empty
	 * @return the final answer, or a refusal
	 * @throws IOException if the provider cannot be reached or replies unusably
	 */
	public abstract AiResult converse(AiRequest request, List<AiTool> tools) throws IOException;

	@Override
	public AiProviderDescriptor getDescriptor() {
		return (AiProviderDescriptor) Jenkins.get().getDescriptor(getClass());
	}

	/**
	 * @return every registered provider implementation
	 */
	public static DescriptorExtensionList<AiProvider, AiProviderDescriptor> all() {
		return Jenkins.get().getDescriptorList(AiProvider.class);
	}
}

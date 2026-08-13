package org.jenkinsci.plugins.p4.heal.provider;

import hudson.model.Descriptor;

/**
 * Descriptor base for {@link AiProvider} implementations, which is what
 * {@code dropdownDescriptorSelector} binds the provider field to.
 */
public abstract class AiProviderDescriptor extends Descriptor<AiProvider> {
}

package org.jenkinsci.plugins.p4.publish;

import hudson.model.Descriptor;

public abstract class PublishDescriptor extends Descriptor<Publish> {
	public PublishDescriptor(Class<? extends Publish> clazz) {
		super(clazz);
	}

	protected PublishDescriptor() {
	}
}

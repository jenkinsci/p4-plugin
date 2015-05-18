package org.jenkinsci.plugins.p4.console;

import hudson.Extension;
import hudson.MarkupText;
import hudson.console.ConsoleAnnotationDescriptor;
import hudson.console.ConsoleAnnotator;
import hudson.console.ConsoleNote;

import org.kohsuke.stapler.DataBoundConstructor;

public class P4ConsoleNote extends ConsoleNote<Object> {

	private static final long serialVersionUID = 1L;

	@DataBoundConstructor
	public P4ConsoleNote() {

	}

	@Override
	public ConsoleAnnotator<?> annotate(Object context, MarkupText text,
			int charPos) {
		return null;
	}

	@Extension
	public static final class DescriptorImpl extends
			ConsoleAnnotationDescriptor {
		public String getDisplayName() {
			return "P4 Logging";
		}
	}
}

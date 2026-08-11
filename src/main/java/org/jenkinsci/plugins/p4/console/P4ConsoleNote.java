package org.jenkinsci.plugins.p4.console;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.MarkupText;
import hudson.console.ConsoleAnnotationDescriptor;
import hudson.console.ConsoleAnnotator;
import hudson.console.ConsoleNote;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.Serial;

public class P4ConsoleNote extends ConsoleNote<Object> {

	@Serial
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
	@Symbol("note")
	public static final class DescriptorImpl extends
			ConsoleAnnotationDescriptor {
		@NonNull
		public String getDisplayName() {
			return "P4 Logging";
		}
	}
}

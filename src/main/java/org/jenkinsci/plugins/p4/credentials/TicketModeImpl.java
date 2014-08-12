package org.jenkinsci.plugins.p4.credentials;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;

import java.io.Serializable;

import org.kohsuke.stapler.DataBoundConstructor;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;

public class TicketModeImpl extends AbstractDescribableImpl<TicketModeImpl>
		implements Serializable {

	private static final long serialVersionUID = 1L;

	@NonNull
	private final String value;

	@NonNull
	private final String ticketValue;

	@NonNull
	private final String ticketPath;

	@DataBoundConstructor
	public TicketModeImpl(@CheckForNull String value,
			@CheckForNull String ticketValue, @CheckForNull String ticketPath) {
		this.value = value;
		this.ticketValue = ticketValue;
		this.ticketPath = ticketPath;
	}

	@NonNull
	public String getValue() {
		return value;
	}

	@NonNull
	public String getTicketValue() {
		return ticketValue;
	}

	@NonNull
	public String getTicketPath() {
		return ticketPath;
	}

	public boolean isTicketValueSet() {
		return "ticketValueSet".equals(value);
	}

	public boolean isTicketPathSet() {
		return "ticketPathSet".equals(value);
	}

	@Extension
	public static class DescriptorImpl extends Descriptor<TicketModeImpl> {
		@Override
		public String getDisplayName() {
			return "TicketMode";
		}
	}
}

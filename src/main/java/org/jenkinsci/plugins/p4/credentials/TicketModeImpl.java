package org.jenkinsci.plugins.p4.credentials;

import java.io.Serializable;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;

public class TicketModeImpl extends AbstractDescribableImpl<TicketModeImpl> implements Serializable {

	private static final long serialVersionUID = 1L;

	@NonNull private final String value;

	@NonNull private final String ticketValue;

	@NonNull private final String ticketPath;

	@DataBoundConstructor
	public TicketModeImpl(String value, String ticketValue, String ticketPath) {
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
		return !StringUtils.isEmpty(getTicketValue());
	}

	public boolean isTicketPathSet() {
		return !StringUtils.isEmpty(getTicketPath());
	}

	@Extension
	public static class DescriptorImpl extends Descriptor<TicketModeImpl> {
		@Override
		public String getDisplayName() {
			return "TicketMode";
		}
	}
}

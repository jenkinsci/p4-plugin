package org.jenkinsci.plugins.p4.credentials;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.util.Secret;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.Serializable;

public class TicketModeImpl extends AbstractDescribableImpl<TicketModeImpl> implements Serializable {

	private static final long serialVersionUID = 1L;

	@NonNull
	private final String value;

	@NonNull
	private final Secret ticketSecret;

	@Deprecated
	private final String ticketValue;

	@NonNull
	private final String ticketPath;

	@DataBoundConstructor
	public TicketModeImpl(String value, String ticketValue, String ticketPath) {
		this.value = value;
		this.ticketValue = "";
		this.ticketSecret = Secret.fromString(ticketValue);
		this.ticketPath = (ticketPath != null) ? ticketPath : "";
	}

	public String getValue() {
		return value;
	}

	public String getTicketValue() {
		if (ticketSecret != null && !getTicketSecret().isEmpty()) {
			return getTicketSecret();
		}
		return ticketValue;
	}

	public String getTicketSecret() {
		return ticketSecret.getPlainText();
	}

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
	@Symbol("ticketMode")
	public static class DescriptorImpl extends Descriptor<TicketModeImpl> {
		@Override
		public String getDisplayName() {
			return "TicketMode";
		}
	}
}

package org.jenkinsci.plugins.p4.credentials;

public interface P4Ticket extends P4Credentials {

	/**
	 * @return The ticket value
	 */
	public String getTicketValue();

	/**
	 * @return true if TicketValue is selected
	 */
	public boolean isTicketValueSet();

	/**
	 * @return Location of the users .p4tickets files
	 */
	public String getTicketPath();

	/**
	 * @return true if TicketPath is selected
	 */
	public boolean isTicketPathSet();
}

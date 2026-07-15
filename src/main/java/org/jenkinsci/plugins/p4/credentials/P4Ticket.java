package org.jenkinsci.plugins.p4.credentials;

public interface P4Ticket extends P4Credentials {

	/**
	 * @return The ticket value
	 */
	String getTicketValue();

	/**
	 * @return true if TicketValue is selected
	 */
	boolean isTicketValueSet();

	/**
	 * @return Location of the users .p4tickets files
	 */
	String getTicketPath();

	/**
	 * @return true if TicketPath is selected
	 */
	boolean isTicketPathSet();
}

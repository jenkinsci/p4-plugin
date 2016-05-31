package org.jenkinsci.plugins.p4.tasks;

public enum CheckoutStatus {
	HEAD,
	SHELVED,
	COMMITTED,
	SUBMITTED;

	/**
	 * Parse a string (case insensitive) for matching enum. Will return HEAD if
	 * no matches.
	 * 
	 * @param status
	 */
	public static CheckoutStatus parse(String status) {
		for (CheckoutStatus s : CheckoutStatus.values()) {
			if (s.name().equalsIgnoreCase(status)) {
				return s;
			}
		}
		return HEAD;
	}
}

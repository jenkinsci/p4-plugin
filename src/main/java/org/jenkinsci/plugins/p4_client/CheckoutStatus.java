package org.jenkinsci.plugins.p4_client;

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
	 * @return
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

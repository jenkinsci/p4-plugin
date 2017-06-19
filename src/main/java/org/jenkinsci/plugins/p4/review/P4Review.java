package org.jenkinsci.plugins.p4.review;

import org.jenkinsci.plugins.p4.tasks.CheckoutStatus;

public class P4Review {

	private String id;
	private CheckoutStatus status;

	public P4Review(String id, CheckoutStatus status) {
		this.id = id;
		this.status = status;
	}

	public String getId() {
		return id;
	}

	public CheckoutStatus getStatus() {
		return status;
	}
}

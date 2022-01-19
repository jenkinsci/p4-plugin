package org.jenkinsci.plugins.p4.client;

public class SessionEntry {

	private final String user;
	private final String ticket;
	private final long expire;

	public SessionEntry(String user, String ticket, long expire) {
		this.user = user;
		this.ticket = ticket;
		this.expire = expire;
	}

	public String getUser() {
		return user;
	}

	public String getTicket() {
		return ticket;
	}

	public long getExpire() {
		return expire;
	}

	@Override
	public String toString() {
		return user + ":" + expire;
	}
}

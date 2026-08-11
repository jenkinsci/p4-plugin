package org.jenkinsci.plugins.p4.publish;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PurgeLimitTest {

	@Test
	void testPurge() {
		String desc = "Configuration change";
		boolean success = false;
		boolean delete = true;
		boolean modtime = false;
		boolean reopen = false;

		SubmitImpl publish = new SubmitImpl(desc, success, delete, modtime, reopen, "11");
		assertEquals(16, publish.getPurgeValue());

		publish = new SubmitImpl(desc, success, delete, modtime, reopen, "300");
		assertEquals(512, publish.getPurgeValue());

		publish = new SubmitImpl(desc, success, delete, modtime, reopen, "10");
		assertEquals(10, publish.getPurgeValue());

		publish = new SubmitImpl(desc, success, delete, modtime, reopen, "600");
		assertEquals(512, publish.getPurgeValue());

		publish = new SubmitImpl(desc, success, delete, modtime, reopen, "foo");
		assertEquals(0, publish.getPurgeValue());
	}
}

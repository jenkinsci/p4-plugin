package org.jenkinsci.plugins.p4.publish;

import org.junit.Assert;
import org.junit.Test;

public class PurgeLimitTest {

	@Test
	public void testPurge() {
		String desc = "Configuration change";
		boolean success = false;
		boolean delete = true;
		boolean modtime = false;
		boolean reopen = false;

		SubmitImpl publish = new SubmitImpl(desc, success, delete, modtime, reopen, "11");
		Assert.assertEquals(16, publish.getPurgeValue());

		publish = new SubmitImpl(desc, success, delete, modtime, reopen, "300");
		Assert.assertEquals(512, publish.getPurgeValue());

		publish = new SubmitImpl(desc, success, delete, modtime, reopen, "10");
		Assert.assertEquals(10, publish.getPurgeValue());

		publish = new SubmitImpl(desc, success, delete, modtime, reopen, "600");
		Assert.assertEquals(512, publish.getPurgeValue());

		publish = new SubmitImpl(desc, success, delete, modtime, reopen, "foo");
		Assert.assertEquals(0, publish.getPurgeValue());
	}
}

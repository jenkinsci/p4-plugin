package org.jenkinsci.plugins.p4.publish;

import org.junit.Assert;
import org.junit.Test;

/**
 * Created by pallen on 31/10/2016.
 */
public class PurgeLimitTest {

	@Test
	public void testPurge() {
		String desc = "Configuration change";
		boolean success = false;
		boolean delete = true;
		boolean reopen = false;

		SubmitImpl publish = new SubmitImpl(desc, success, delete, reopen, "11");
		Assert.assertEquals(16, publish.getPurgeValue());

		publish = new SubmitImpl(desc, success, delete, reopen, "300");
		Assert.assertEquals(512, publish.getPurgeValue());

		publish = new SubmitImpl(desc, success, delete, reopen, "10");
		Assert.assertEquals(10, publish.getPurgeValue());

		publish = new SubmitImpl(desc, success, delete, reopen, "600");
		Assert.assertEquals(512, publish.getPurgeValue());

		publish = new SubmitImpl(desc, success, delete, reopen, "foo");
		Assert.assertEquals(0, publish.getPurgeValue());
	}
}

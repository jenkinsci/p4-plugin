package org.jenkinsci.plugins.p4.client;

import org.jenkinsci.plugins.p4.DefaultEnvironment;
import org.junit.jupiter.api.Test;

import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ViewMapHelperTest extends DefaultEnvironment {

    private static final Logger LOGGER = Logger.getLogger(ViewMapHelperTest.class.getName());

    @Test
	void testSplitDepotPath_singlepath_ok(){
        String[] res = ViewMapHelper.splitDepotPath("//depot/Projects/my-super-project");
        String[] exp = new String[]{"depot", "Projects", "my-super-project" };

        assertArrayEquals(exp, res, "Depot path is equal");
    }

	@Test
	void testGetClientView_multipath_ok(){
        String multi_path = 
            "\n//depot/foo/...\n//depot/bar/...";
        String res = ViewMapHelper.getClientView(multi_path, "p4client", false);
        // String[] exp = new String[]{"depot", "Projects", "my-super-project" };

        String expected = "//depot/foo/... //p4client/depot/foo/...\n//depot/bar/... //p4client/depot/bar/...";

        assertEquals(expected, res);
    }
}

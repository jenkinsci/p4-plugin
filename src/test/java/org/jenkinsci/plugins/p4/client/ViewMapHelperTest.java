package org.jenkinsci.plugins.p4.client;

import org.jenkinsci.plugins.p4.DefaultEnvironment;
import org.junit.Test;

import java.util.logging.Logger;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class ViewMapHelperTest extends DefaultEnvironment {
    private static Logger logger = Logger.getLogger(ViewMapHelperTest.class.getName());
    
    @Test
    public void testSplitDepotPath_singlepath_ok(){
        String[] res = ViewMapHelper.splitDepotPath("//depot/Projects/my-super-project");
        String[] exp = new String[]{"depot", "Projects", "my-super-project" };

        assertArrayEquals("Depot path is equal", exp, res);
    }

    @Test
    public void testgetClientView_multipath_ok(){
        String multi_path = 
            "\n//depot/foo/...\n//depot/bar/...";
        String res = ViewMapHelper.getClientView(multi_path, "p4client", false);
        // String[] exp = new String[]{"depot", "Projects", "my-super-project" };

        String expected = "//depot/foo/... //p4client/depot/foo/...\n//depot/bar/... //p4client/depot/bar/...";

        assertEquals(expected, res);
    }
}

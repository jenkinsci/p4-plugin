package org.jenkinsci.plugins.p4.client;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.util.logging.Logger;

import org.jenkinsci.plugins.p4.DefaultEnvironment;
import org.junit.Test;

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
            "\n//depot/Projects/Foo/Bar/Baz/Tick/Tock/...\n//depot/Projects/Foo/Bar/Baz/Tick/Tack/...\n";
        String res = ViewMapHelper.getClientView(multi_path, "p4client", false);
        // String[] exp = new String[]{"depot", "Projects", "my-super-project" };

        assertEquals("Depot path is equal", "Something", res);
    }
}

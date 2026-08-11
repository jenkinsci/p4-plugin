package org.jenkinsci.plugins.p4;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.io.File;

public class SampleServerExtension extends SimpleTestServer implements BeforeEachCallback, AfterEachCallback {

	public SampleServerExtension(String root, String version) {
		super(root, version);
	}

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        clean();
        extract(new File(getResources() + "/depot.tar.gz"));
        restore(new File(getResources() + "/checkpoint.gz"));
        upgrade();
    }

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        destroy();
    }
}
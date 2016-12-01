package org.jenkinsci.plugins.p4;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.io.File;

public class SampleServerRule extends SimpleTestServer implements TestRule {

	public SampleServerRule(String root, String version) {
		super(root, version);
	}

	@Override
	public Statement apply(Statement statement, Description description) {
		return new ServerStatement(statement);
	}

	public class ServerStatement extends Statement {

		private final Statement statement;

		public ServerStatement(Statement statement) {
			this.statement = statement;
		}

		@Override
		public void evaluate() throws Throwable {
			clean();
			extract(new File(getResources() + "/depot.tar.gz"));
			restore(new File(getResources() + "/checkpoint.gz"));
			upgrade();

			statement.evaluate();

			destroy();
		}
	}
}
package org.jenkinsci.plugins.p4;

import org.jvnet.hudson.test.JenkinsRule;

public class ExtendedJenkinsRule extends JenkinsRule {

	public ExtendedJenkinsRule(int timeout) {
		super();
		this.timeout = timeout;
	}
}

package org.jenkinsci.plugins.p4.groovy;

import hudson.FilePath.FileCallable;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import jenkins.security.Roles;
import org.jenkinsci.plugins.p4.client.ClientHelper;
import org.jenkinsci.plugins.p4.credentials.P4BaseCredentials;
import org.jenkinsci.plugins.p4.tasks.AbstractTask;
import org.jenkinsci.remoting.RoleChecker;

import java.io.File;
import java.io.IOException;
import java.io.Serial;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Map;
import java.util.logging.Logger;

public class P4GroovyTask extends AbstractTask implements FileCallable<Map<String, Object>[]>, Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	private static Logger logger = Logger.getLogger(P4GroovyTask.class.getName());

	private final String cmd;
	private final String[] args;
	private final Map<String, Object> spec;

	protected P4GroovyTask(P4BaseCredentials credential, TaskListener listener, String cmd, String[] args, Map<String, Object> spec) {
		super(credential, listener);
		this.cmd = cmd;
		this.args = Arrays.copyOf(args, args.length);
		this.spec = spec;
	}

	protected P4GroovyTask(P4BaseCredentials credential, TaskListener listener, String cmd, String... args) {
		this(credential, listener, cmd, args, null);
	}

	@Override
	public Map<String, Object>[] invoke(File workspace, VirtualChannel channel) throws IOException {
		return (Map<String, Object>[]) tryTask();
	}

	@Override
	public Object task(ClientHelper p4) throws Exception {
		try {
			if (!checkConnection(p4)) {
				return null;
			}

			return p4.getConnection().execMapCmd(cmd, args, spec);
		} catch (Exception e) {
			StringBuilder sb = new StringBuilder();
			sb.append("P4: Unable to execute p4 groovy task: ");
			sb.append(cmd == null ? "[null]" : cmd).append(" ");
			sb.append(args == null ? "[null]" : Arrays.toString(args)).append(": ");
			sb.append(e);

			String err = sb.toString();
			logger.severe(err);
			p4.log(err);
			throw e;
		}
	}

	@Override
	public void checkRoles(RoleChecker checker) throws SecurityException {
		checker.check(this, Roles.SLAVE);
	}
}

package org.jenkinsci.plugins.p4.groovy;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Map;
import java.util.logging.Logger;

import org.jenkinsci.plugins.p4.client.ClientHelper;
import org.jenkinsci.plugins.p4.tasks.AbstractTask;
import org.jenkinsci.remoting.RoleChecker;
import org.jenkinsci.remoting.RoleSensitive;

import hudson.FilePath.FileCallable;
import hudson.remoting.VirtualChannel;
import jenkins.security.Roles;

public class P4GroovyTask extends AbstractTask implements FileCallable<Map<String,Object>[]>, Serializable {
    
    	private static final long serialVersionUID = 1L;
    
    	private static Logger logger = Logger.getLogger(P4GroovyTask.class.getName());

	private final String cmd;
	private final String[] args;
	private final Map<String,Object> spec;

	public P4GroovyTask(String cmd, String[] args, Map<String,Object> spec) {
		this.cmd = cmd;
		this.args = Arrays.copyOf(args, args.length);
		this.spec = spec;
	}

	public P4GroovyTask(String cmd, String... args) {
		this(cmd, args, null);
	}

        @Override
	public Map<String,Object>[] invoke(File workspace, VirtualChannel channel) throws IOException {
		return (Map<String,Object>[]) tryTask();
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
                        sb.append(cmd==null? "[null]": cmd).append(" ");
                        sb.append(args==null? "[null]": Arrays.toString(args)).append(": ");
                        sb.append(e.toString());
                        
                        String err = sb.toString();
			logger.severe(err);
			p4.log(err);
			throw e;
		} finally {
			p4.disconnect();
		}
	}

        @Override
	public void checkRoles(RoleChecker checker) throws SecurityException {
		checker.check((RoleSensitive) this, Roles.SLAVE);
	}

}

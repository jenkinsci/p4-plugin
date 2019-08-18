package org.jenkinsci.plugins.p4.groovy;

import com.perforce.p4java.exception.P4JavaException;
import com.perforce.p4java.server.IOptionsServer;
import hudson.FilePath;
import hudson.model.TaskListener;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.p4.client.ClientHelper;
import org.jenkinsci.plugins.p4.workspace.Workspace;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.jenkinsci.plugins.p4.credentials.P4BaseCredentials;

public class P4Groovy implements Serializable {

	private static final long serialVersionUID = 1L;

	private final String credentialName;
	private final Workspace workspace;
	private final FilePath buildWorkspace;
	private final P4BaseCredentials p4Credential;

	private transient TaskListener listener = null;

	public P4Groovy(String credentialName, P4BaseCredentials p4Credential, TaskListener listener, Workspace workspace, FilePath buildWorkspace) {
		this.credentialName = credentialName;
		this.p4Credential = p4Credential;
		this.workspace = workspace;
		this.listener = listener;
		this.buildWorkspace = buildWorkspace;
	}

	public String getClientName() {
		return workspace.getFullName();
	}

	public String getUserName() throws Exception {
		IOptionsServer p4 = getConnection();
		String user = p4.getUserName();
		p4.disconnect();
		return user;
	}

	@Deprecated
	public Map<String, Object>[] runString(String cmd, String args) throws P4JavaException, InterruptedException, IOException {
		List<String> argList = new ArrayList<String>();
		for (String arg : args.split(",")) {
			arg = arg.trim();
			argList.add(arg);
		}

		String[] array = argList.toArray(new String[0]);
		return run(cmd, array);
	}

	public Map<String, Object>[] run(String cmd, String... args) throws P4JavaException, InterruptedException, IOException {
		P4GroovyTask task = new P4GroovyTask(p4Credential, credentialName, listener, cmd, args);
		task.setWorkspace(workspace);

		return buildWorkspace.act(task);
	}

	public Map<String, Object>[] run(String cmd, List<String> args) throws P4JavaException, InterruptedException, IOException {
		String[] array = args.toArray(new String[0]);
		return run(cmd, array);
	}

	public Map<String, Object>[] save(String type, Map<String, Object> spec) throws P4JavaException, InterruptedException, IOException {
		return save(type, spec, new ArrayList());
	}

	public Map<String, Object>[] save(String type, Map<String, Object> spec, List<String> list) throws P4JavaException, InterruptedException, IOException {
		// add '-i' to user provided args list
		if (!list.contains("-i")) {
			list.add("-i");
		}
		String[] args = list.toArray(new String[0]);

		P4GroovyTask task = new P4GroovyTask(p4Credential, credentialName, listener, type, args, spec);
		task.setWorkspace(workspace);

		return buildWorkspace.act(task);
	}

	public Map<String, Object>[] save(String type, Map<String, Object> spec, String... args) throws P4JavaException, InterruptedException, IOException {
		ArrayList<String> list = new ArrayList<>(Arrays.asList(args));
		return save(type, spec, list);
	}

	public Map<String, Object> fetch(String type, String id) throws P4JavaException, InterruptedException, IOException {
		String[] array = {"-o", id};
		Map<String, Object>[] maps = run(type, array);
		if (maps.length == 0)
			return null;

		maps[0].remove("specFormatted");
		return maps[0];
	}

	private IOptionsServer getConnection() throws IOException {
		ClientHelper p4 = new ClientHelper(p4Credential, listener, workspace);
		return p4.getConnection();
	}
}

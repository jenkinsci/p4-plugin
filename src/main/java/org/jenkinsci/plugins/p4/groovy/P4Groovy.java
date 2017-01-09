package org.jenkinsci.plugins.p4.groovy;

import com.perforce.p4java.exception.P4JavaException;
import com.perforce.p4java.server.IOptionsServer;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.p4.client.ClientHelper;
import org.jenkinsci.plugins.p4.workspace.Workspace;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class P4Groovy implements Serializable {

	private static final long serialVersionUID = 1L;

	private final String credential;
	private final Workspace workspace;
	
	private final TaskListener listener;

	public P4Groovy(String credential, TaskListener listener, Workspace workspace) {
		this.credential = credential;
		this.workspace = workspace;
		this.listener = listener;
	}

	public String getClientName() {
		return workspace.getFullName();
	}

	public String getUserName() throws P4JavaException {
		IOptionsServer p4 = getConnection();
		String user = p4.getUserName();
		p4.disconnect();
		return user;
	}

	@Deprecated
	public Map<String, Object>[] runString(String cmd, String args) throws P4JavaException {
		List<String> argList = new ArrayList<String>();
		for (String arg : args.split(",")) {
			arg = arg.trim();
			argList.add(arg);
		}

		String[] array = argList.toArray(new String[0]);
		IOptionsServer p4 = getConnection();
		Map<String, Object>[] map = p4.execMapCmd(cmd, array, null);
		p4.disconnect();
		return map;
	}

	public Map<String, Object>[] run(String cmd, String... args) throws P4JavaException {
		IOptionsServer p4 = getConnection();
		Map<String, Object>[] map = p4.execMapCmd(cmd, args, null);
		p4.disconnect();
		return map;
	}

	public Map<String, Object>[] run(String cmd, List<String> args) throws P4JavaException {
		String[] array = args.toArray(new String[0]);
		return run(cmd, array);
	}

	public Map<String, Object>[] save(String type, Map<String, Object> spec) throws P4JavaException {
		String[] array = { "-i" };
		IOptionsServer p4 = getConnection();
		Map<String, Object>[] map = p4.execMapCmd(type, array, spec);
		p4.disconnect();
		return map;
	}

	public Map<String, Object> fetch(String type, String id) throws P4JavaException {
		String[] array = { "-o", id };
		IOptionsServer p4 = getConnection();
		Map<String, Object>[] map = p4.execMapCmd(type, array, null);
		p4.disconnect();
		return map[0];
	}

	private IOptionsServer getConnection() {
		String client = workspace.getFullName();
		String charset = workspace.getCharset();

		ClientHelper p4 = new ClientHelper(credential, listener, client, charset);
		try {
			p4.setClient(workspace);
		} catch (Exception e) {
			p4.log("Unable to set Client!");
		}

		return p4.getConnection();
	}
}

package org.jenkinsci.plugins.p4.swarmAPI;

import com.google.gson.Gson;
import hudson.Extension;
import hudson.model.RootAction;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.p4.client.ConnectionHelper;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

@Symbol("swarm_projects")
@Extension
public class SwarmQueryAction implements RootAction {

	@Override
	public String getIconFileName() {
		return null;
	}

	@Override
	public String getDisplayName() {
		return null;
	}

	@Override
	public String getUrlName() {
		return "swarm";
	}

	public void doDynamic(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {

		String credentialID = req.getParameter("credential");

		try (ConnectionHelper p4 = new ConnectionHelper(credentialID, null)) {
			SwarmHelper swarm = new SwarmHelper(p4, "4");
			List<String> list = swarm.getProjects();

			Gson gson = new Gson();
			String json = gson.toJson(list);

			PrintWriter out = rsp.getWriter();
			out.write(json);
		} catch (Exception e) {
			throw new IOException(e);
		}
	}
}

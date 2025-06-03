package org.jenkinsci.plugins.p4.swarmAPI;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import hudson.Extension;
import hudson.model.RootAction;
import jenkins.branch.BranchSource;
import jenkins.model.Jenkins;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.p4.client.ConnectionHelper;
import org.jenkinsci.plugins.p4.populate.AutoCleanImpl;
import org.jenkinsci.plugins.p4.scm.SwarmScmSource;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

import static jakarta.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static jakarta.servlet.http.HttpServletResponse.SC_CREATED;

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

	// RootAction has no Item (Job, Run or MultiBranchProject) that can be use for context to look up a Credential
	@SuppressWarnings("deprecation")
	public void doDynamic(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException {

		String path = req.getRestOfPath();

		if (path != null && path.startsWith("/project")) {
			String credentialID = req.getParameter("credential");

			try (ConnectionHelper p4 = new ConnectionHelper(credentialID, null)) {
				SwarmHelper swarm = new SwarmHelper(p4, "11");
				List<String> list = swarm.getProjects();

				Gson gson = new Gson();
				String json = gson.toJson(list);

				PrintWriter out = rsp.getWriter();
				out.write(json);
			} catch (Exception e) {
				throw new IOException(e);
			}
		}

		if (path != null && path.startsWith("/create")) {
			String credentialID = req.getParameter("credential");
			String project = req.getParameter("project");
			String name = req.getParameter("name");

			if (name == null || name.isEmpty()) {
				name = project;
			}

			try (ConnectionHelper p4 = new ConnectionHelper(credentialID, null)) {
				SwarmHelper swarm = new SwarmHelper(p4, "11");

				String format = "jenkins-${NODE_NAME}-${JOB_NAME}";
				SwarmScmSource source = new SwarmScmSource(credentialID, null, format);
				source.setProject(project);
				source.setSwarm(swarm);
				source.setPopulate(new AutoCleanImpl());

				WorkflowMultiBranchProject multi = Jenkins.get().createProject(WorkflowMultiBranchProject.class, name);
				multi.getSourcesList().add(new BranchSource(source));

				multi.scheduleBuild2(0);

				rsp.setStatus(SC_CREATED);
				rsp.setContentType("application/json");

				JsonObject json = new JsonObject();
				json.addProperty("name", name);

				PrintWriter out = rsp.getWriter();
				out.write(json.toString());

			} catch (IllegalArgumentException e) {
				rsp.setStatus(SC_BAD_REQUEST);
				rsp.setContentType("application/json");

				JsonObject json = new JsonObject();
				json.addProperty("message", "Failed to create pipeline");
				json.addProperty("code", SC_BAD_REQUEST);
				JsonArray errors = new JsonArray();
				JsonObject error = new JsonObject();
				error.addProperty("message", name + " already exists");
				error.addProperty("code", "ALREADY_EXISTS");
				error.addProperty("field", "name");
				errors.add(error);
				json.add("errors", errors);

				PrintWriter out = rsp.getWriter();
				out.write(json.toString());

			} catch (Exception e) {
				throw new IOException(e);
			}
		}
	}
}

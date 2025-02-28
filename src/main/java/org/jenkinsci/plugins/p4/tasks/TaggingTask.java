package org.jenkinsci.plugins.p4.tasks;

import com.perforce.p4java.client.IClientViewMapping;
import com.perforce.p4java.core.ILabelMapping;
import com.perforce.p4java.core.ViewMap;
import com.perforce.p4java.impl.generic.client.ClientView;
import com.perforce.p4java.impl.generic.core.Label;
import com.perforce.p4java.impl.generic.core.Label.LabelMapping;
import hudson.FilePath.FileCallable;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import org.jenkinsci.plugins.p4.client.ClientHelper;
import org.jenkinsci.remoting.RoleChecker;

import java.io.File;
import java.io.IOException;
import java.io.Serial;
import java.io.Serializable;
import java.util.logging.Logger;

public class TaggingTask extends AbstractTask implements FileCallable<Boolean>,
		Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	private static Logger logger = Logger
			.getLogger(TaggingTask.class.getName());

	private final String name;
	private final String description;

	private Object buildChange;

	public TaggingTask(String credential, Run<?, ?> run, TaskListener listener, String name, String description) {
		super(credential, run, listener);
		this.name = name;
		this.description = description;
	}

	public Boolean invoke(File workspace, VirtualChannel channel)
			throws IOException {
		return (Boolean) tryTask();
	}

	@Override
	public Object task(ClientHelper p4) throws Exception {
		try {
			// Check connection (might be on remote slave)
			if (!checkConnection(p4)) {
				return false;
			}
			p4.log("P4 Task: tagging build.");
			p4.log("... label: " + name);
			p4.log("... type: (automatic) @" + buildChange);

			Label label = new Label();
			label.setDescription(description);
			label.setName(name);
			label.setRevisionSpec("@" + buildChange);

			// set label view to match workspace
			ViewMap<ILabelMapping> viewMapping = new ViewMap<>();
			ClientView view = p4.getClientView();
			for (IClientViewMapping entry : view) {
				String left = entry.getLeft();
				LabelMapping lblMap = new LabelMapping();
				lblMap.setLeft(left);
				lblMap.setType(entry.getType());  // Make sure type is carried forward
				viewMapping.addEntry(lblMap);
			}
			label.setViewMapping(viewMapping);

			// update Perforce
			p4.setLabel(label);
		} catch (Exception e) {
			p4.log("(p4):stop:exception\n");
			String msg = "Unable to label workspace: " + e;
			logger.warning(msg);
			throw e;
		}
		return true;
	}

	public void setBuildChange(Object buildChange) {
		this.buildChange = buildChange;
	}

	@Override
	public void checkRoles(RoleChecker checker) throws SecurityException {
		// TODO Auto-generated method stub

	}
}

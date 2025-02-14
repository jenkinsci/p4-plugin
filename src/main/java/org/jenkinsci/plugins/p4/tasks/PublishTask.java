package org.jenkinsci.plugins.p4.tasks;

import com.perforce.p4java.client.IClient;
import com.perforce.p4java.core.file.IFileSpec;
import hudson.AbortException;
import hudson.FilePath.FileCallable;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import jenkins.security.Roles;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.p4.client.ClientHelper;
import org.jenkinsci.plugins.p4.publish.Publish;
import org.jenkinsci.plugins.p4.publish.ShelveImpl;
import org.jenkinsci.plugins.p4.publish.SubmitImpl;
import org.jenkinsci.remoting.RoleChecker;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class PublishTask extends AbstractTask implements FileCallable<Map<String, List<String>>>, Serializable {

	private static final long serialVersionUID = 1L;

	private static Logger logger = Logger.getLogger(PublishTask.class.getName());

	private final Publish publish;

	public PublishTask(String credential, Run<?, ?> run, TaskListener listener, Publish publish) {
		super(credential, run, listener);
		this.publish = publish;
	}

	public Map<String, List<String>> invoke(File workspace, VirtualChannel channel) throws IOException {
		return (Map<String, List<String>>) tryTask();
	}

	@Override
	public Object task(ClientHelper p4) throws Exception {
		Map<String, List<String>> empyMap = Collections.singletonMap(StringUtils.EMPTY, new ArrayList<>());
		try {
			// Check connection (might be on remote slave)
			if (!checkConnection(p4)) {
				return empyMap;
			}

			if (!p4.buildChange(publish)) {
				logger.info("P4: Publish Task: No opened files.");
				return empyMap;
			}

			String publishedChangeID = p4.publishChange(publish);
			if (StringUtils.isEmpty(publishedChangeID)) {
				return empyMap;
			}

			if (!publish.isArchiveArtifacts()) {
				logger.info("P4: Publish Task: Archive artifacts not enabled.");
				return Collections.singletonMap(publishedChangeID, Collections.emptyList());
			}

			List<String> artifacts = getArtifactsFor(publishedChangeID, p4);
			return Collections.singletonMap(publishedChangeID, artifacts);
		} catch (Exception e) {
			p4.log("(p4):stop:exception\n");
			String err = "Unable to publish workspace: " + e;
			p4.log(err);
			logger.warning(err);
			throw new AbortException(err);
		}
	}

	private List<String> getArtifactsFor(String publishedChangeID, ClientHelper p4) throws Exception {
		List<IFileSpec> changeFiles;
		int changeId = Integer.parseInt(publishedChangeID);
		if (publish instanceof SubmitImpl) {
			changeFiles = p4.getChangeFiles(changeId, 100);
		} else if (publish instanceof ShelveImpl) {
			changeFiles = p4.getShelvedFiles(changeId);
		} else {
			return Collections.emptyList();
		}

		if (changeFiles.isEmpty()) {
			return Collections.emptyList();
		}

		IClient client = p4.getClient();
		if (client == null) {
			logger.warning("P4: Publish Task: No client available.");
			return Collections.emptyList();
		}
		client.refresh();
		return client.localWhere(changeFiles).stream()
				.filter(Objects::nonNull)
				.map(IFileSpec::getLocalPathString)
				.collect(Collectors.toList());

	}

	public void checkRoles(RoleChecker checker) throws SecurityException {
		checker.check(this, Roles.SLAVE);
	}

}

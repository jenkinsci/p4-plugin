package org.jenkinsci.plugins.p4.utils;

import com.cloudbees.hudson.plugins.folder.Folder;
import com.mig82.folders.properties.FolderProperties;
import com.mig82.folders.properties.StringProperty;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@WithJenkins
class FolderPropertiesUtilTest {

	private JenkinsRule jenkins;

	@BeforeEach
	void beforeEach(JenkinsRule rule) {
		jenkins = rule;
	}

	@Test
	void testReturnsEmptyListForNullPaths() {
		assertTrue(FolderPropertiesUtil.processFolderPropertiesIn(null, null).isEmpty());
	}

	@Test
	void testReturnsEmptyListForEmptyPaths() {
		assertTrue(FolderPropertiesUtil.processFolderPropertiesIn(List.of(), null).isEmpty());
	}

	@Test
	void testReturnsOriginalPathsWhenOwnerIsNull() {
		List<String> paths = List.of("//depot/${MYVAR}/...");

		assertSame(paths, FolderPropertiesUtil.processFolderPropertiesIn(paths, null));
	}

	@Test
	void testReturnsOriginalPathsWhenOwnerIsNotInAnyFolder() throws Exception {
		WorkflowMultiBranchProject owner = jenkins.jenkins.createProject(WorkflowMultiBranchProject.class, "notInFolder");
		List<String> paths = List.of("//depot/${MYVAR}/...");

		assertSame(paths, FolderPropertiesUtil.processFolderPropertiesIn(paths, owner));
	}

	@Test
	void testReturnsOriginalPathsWhenEnclosingFolderHasNoFolderProperties() throws Exception {
		Folder folder = jenkins.jenkins.createProject(Folder.class, "emptyFolder");
		WorkflowMultiBranchProject owner = folder.createProject(WorkflowMultiBranchProject.class, "inFolder");
		List<String> paths = List.of("//depot/${MYVAR}/...");

		assertSame(paths, FolderPropertiesUtil.processFolderPropertiesIn(paths, owner));
	}

	@Test
	void testExpandsPathsUsingEnclosingFolderProperties() throws Exception {
		Folder folder = jenkins.jenkins.createProject(Folder.class, "propsFolder");
		FolderProperties properties = new FolderProperties();
		properties.setProperties(new StringProperty[]{new StringProperty("MYVAR", "myvalue")});
		folder.addProperty(properties);

		WorkflowMultiBranchProject owner = folder.createProject(WorkflowMultiBranchProject.class, "inFolder");

		List<String> result = FolderPropertiesUtil.processFolderPropertiesIn(List.of("//depot/${MYVAR}/..."), owner);

		assertEquals(List.of("//depot/myvalue/..."), result);
	}
}

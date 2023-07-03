package org.jenkinsci.plugins.p4.build;

import hudson.FilePath;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertEquals;

public class GetExecutorTest {

	@Test
	public void testGetExecutor() {
		//Windows path test
		File windowsWorkspaceDir =  new File("C:\\Windows\\workspace\\path");
		FilePath windowsWorkspace = new FilePath(windowsWorkspaceDir);
		String executorNumber = ExecutorHelper.getExecutorID(windowsWorkspace);
		assertEquals("0", executorNumber);

		//Linux path test
		File linuxWorkspaceDir =  new File("/usr/home/linux/workspace/path");
		FilePath linuxWorkspace = new FilePath(linuxWorkspaceDir);
		executorNumber = ExecutorHelper.getExecutorID(linuxWorkspace);
		assertEquals("0", executorNumber);
	}

	@Test
	public void testGetExecutorWhenUsingSubDirectory() {
		//Windows path test
		File windowsWorkspaceDir =  new File("C:\\Windows\\workspace\\path\\subDir");
		FilePath windowsWorkspace = new FilePath(windowsWorkspaceDir);
		String executorNumber = ExecutorHelper.getExecutorID(windowsWorkspace);
		assertEquals("0", executorNumber);

		//Linux path test
		File linuxWorkspaceDir =  new File("/usr/home/linux/workspace/path/subDir");
		FilePath linuxWorkspace = new FilePath(linuxWorkspaceDir);
		executorNumber = ExecutorHelper.getExecutorID(linuxWorkspace);
		assertEquals("0", executorNumber);
	}

	@Test
	public void testGetExecutorWhenWorkspaceHasAtSignIn() {
		//Windows path test
		File windowsWorkspaceDir =  new File("C:\\Windows\\worksp@ce\\path");
		FilePath windowsWorkspace = new FilePath(windowsWorkspaceDir);
		String executorNumber = ExecutorHelper.getExecutorID(windowsWorkspace);
		assertEquals("0", executorNumber);

		//Linux path test
		File linuxWorkspaceDir =  new File("/usr/home/linux/worksp@ce/path");
		FilePath linuxWorkspace = new FilePath(linuxWorkspaceDir);
		executorNumber = ExecutorHelper.getExecutorID(linuxWorkspace);
		assertEquals("0", executorNumber);
	}

	@Test
	public void testGetExecutorUsingNonStandardExecutor() {
		//Windows path test
		File windowsWorkspaceDir =  new File("C:\\Windows\\workspace\\path@2");
		FilePath windowsWorkspace = new FilePath(windowsWorkspaceDir);
		String executorNumber = ExecutorHelper.getExecutorID(windowsWorkspace);
		assertEquals("2", executorNumber);

		//Linux path test
		File linuxWorkspaceDir =  new File("/usr/home/linux/workspace/path@3");
		FilePath linuxWorkspace = new FilePath(linuxWorkspaceDir);
		executorNumber = ExecutorHelper.getExecutorID(linuxWorkspace);
		assertEquals("3", executorNumber);
	}

	@Test
	public void testGetExecutorWhenUsingSubDirectoryAndNonStandardExecutor() {
		//Windows path test
		File windowsWorkspaceDir =  new File("C:\\Windows\\workspace\\path@2\\subDir");
		FilePath windowsWorkspace = new FilePath(windowsWorkspaceDir);
		String executorNumber = ExecutorHelper.getExecutorID(windowsWorkspace);
		assertEquals("2", executorNumber);

		//Linux path test
		File linuxWorkspaceDir =  new File("/usr/home/linux/workspace/path@3/subDir");
		FilePath linuxWorkspace = new FilePath(linuxWorkspaceDir);
		executorNumber = ExecutorHelper.getExecutorID(linuxWorkspace);
		assertEquals("3", executorNumber);
	}

	@Test
	public void testGetExecutorWhenWorkspaceHasAtSignInUsingNonStandardExecutor() {
		//Windows path test
		File windowsWorkspaceDir =  new File("C:\\Windows\\worksp@1ce\\path@2");
		FilePath windowsWorkspace = new FilePath(windowsWorkspaceDir);
		String executorNumber = ExecutorHelper.getExecutorID(windowsWorkspace);
		assertEquals("2", executorNumber);

		//Linux path test
		File linuxWorkspaceDir =  new File("/usr/home/linux/worksp@1ce/path@3");
		FilePath linuxWorkspace = new FilePath(linuxWorkspaceDir);
		executorNumber = ExecutorHelper.getExecutorID(linuxWorkspace);
		assertEquals("3", executorNumber);
	}

}

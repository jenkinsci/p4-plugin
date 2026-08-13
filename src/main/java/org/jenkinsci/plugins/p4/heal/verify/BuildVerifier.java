package org.jenkinsci.plugins.p4.heal.verify;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import jenkins.MasterToSlaveFileCallable;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.Serial;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs build commands in the workspace and reads back what they produced.
 *
 * <p>This is the only part of the healing feature that executes anything, and it
 * runs the commands the job owner configured — never anything the model wrote.
 * The model's output reaches the workspace as a patch and nothing else.
 *
 * <p>Commands are tokenised and launched directly rather than through a shell, so
 * shell operators such as {@code &&} are not supported. That keeps the execution
 * surface small; chain steps in the job instead.
 */
public class BuildVerifier {

	private static final String DEFAULT_REPORT_GLOB =
			"**/surefire-reports/*.xml,**/failsafe-reports/*.xml";

	/**
	 * Command output is fed back into a prompt, so it is capped. The tail is kept
	 * because that is where compilers and test runners put the failure.
	 */
	private static final int MAX_OUTPUT_CHARS = 20000;

	private final FilePath workspace;
	private final Launcher launcher;
	private final TaskListener listener;

	private final EnvVars environment;

	/**
	 * @param workspace   build workspace, on the agent
	 * @param launcher    launcher for that node
	 * @param listener    build listener, for logging
	 * @param environment the build's resolved environment, which the commands run in
	 */
	public BuildVerifier(FilePath workspace, Launcher launcher, TaskListener listener,
	                     EnvVars environment) {
		this.workspace = workspace;
		this.launcher = launcher;
		this.listener = listener;
		this.environment = environment;
	}

	/**
	 * Run one command in the workspace.
	 *
	 * @param command the command line, as configured on the job
	 * @return its exit status and output
	 * @throws IOException          if the command could not be launched
	 * @throws InterruptedException if the build was cancelled
	 */
	public CommandResult run(String command) throws IOException, InterruptedException {
		if (command == null || command.isBlank()) {
			return new CommandResult(0, "");
		}

		ByteArrayOutputStream captured = new ByteArrayOutputStream();
		listener.getLogger().println("[p4-heal] " + command);

		int exitCode = launcher.launch()
				.cmds(Util.tokenize(command))
				.envs(environment)
				.pwd(workspace)
				.stdout(captured)
				.stderr(captured)
				.join();

		return new CommandResult(exitCode, tail(captured.toString(StandardCharsets.UTF_8)));
	}

	/**
	 * Read every test report currently in the workspace.
	 *
	 * <p>Listed, read and parsed on the node the workspace is on, so a suite of any
	 * size costs one channel round-trip and only the merged result crosses it —
	 * rather than one round-trip per report, with every stack trace held on the
	 * controller at once.
	 *
	 * @return the combined results
	 * @throws IOException          if a report cannot be read
	 * @throws InterruptedException if the build was cancelled
	 */
	public TestResults readTestResults() throws IOException, InterruptedException {
		return workspace.act(new ReadReports());
	}

	/**
	 * Parses the workspace's test reports where they live.
	 */
	private static final class ReadReports extends MasterToSlaveFileCallable<TestResults> {

		@Serial
		private static final long serialVersionUID = 1L;

		@Override
		public TestResults invoke(File workspace, VirtualChannel channel)
				throws IOException, InterruptedException {

			List<String> reports = new ArrayList<>();
			for (FilePath report : new FilePath(workspace).list(DEFAULT_REPORT_GLOB)) {
				reports.add(report.readToString());
			}
			return TestResults.parseAll(reports);
		}
	}

	private static String tail(String output) {
		if (output.length() <= MAX_OUTPUT_CHARS) {
			return output;
		}
		return "[...truncated...]\n" + output.substring(output.length() - MAX_OUTPUT_CHARS);
	}
}

package org.jenkinsci.plugins.p4.heal.verify;

import java.io.Serial;
import java.io.Serializable;

/**
 * The commands and limits the verify ladder runs with.
 *
 * @param compileCommand         command that must succeed before anything is tested
 * @param targetedTestCommand    command that runs only the originally failing
 *                               tests; {@code {tests}} is replaced with their
 *                               comma-separated ids. Blank skips the rung.
 * @param verifyCommand          the full build-and-test command that decides
 *                               whether a fix is real
 * @param flakeReruns            extra times to repeat {@code verifyCommand}; each
 *                               must also pass. Guards against a fix that only
 *                               happens to go green once
 */
public record VerifyConfig(String compileCommand, String targetedTestCommand,
                           String verifyCommand, int flakeReruns) implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	/**
	 * @return the targeted command with {@code {tests}} substituted
	 * @param testIds ids of the tests to run
	 */
	public String targetedCommandFor(String testIds) {
		return targetedTestCommand == null ? "" : targetedTestCommand.replace("{tests}", testIds);
	}
}

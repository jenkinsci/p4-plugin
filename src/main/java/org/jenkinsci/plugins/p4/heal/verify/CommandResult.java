package org.jenkinsci.plugins.p4.heal.verify;

import java.io.Serial;
import java.io.Serializable;

/**
 * The outcome of one command run in the build workspace.
 *
 * @param exitCode process exit status
 * @param output   combined stdout and stderr, already truncated to something a
 *                 prompt can carry
 */
public record CommandResult(int exitCode, String output) implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	/**
	 * @return true if the command succeeded
	 */
	public boolean ok() {
		return exitCode == 0;
	}
}

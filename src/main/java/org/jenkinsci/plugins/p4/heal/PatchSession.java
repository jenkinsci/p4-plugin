package org.jenkinsci.plugins.p4.heal;

import org.jenkinsci.plugins.p4.heal.patch.UnifiedDiff;

import java.io.IOException;

/**
 * Applies and undoes candidate patches in the build workspace.
 *
 * <p>Kept behind an interface so the healing loop can be tested without a
 * Perforce server, and so the only code that mutates a workspace stays in one
 * place.
 *
 * <p>Every failed candidate must be reverted before the next is tried; a
 * workspace carrying leftovers from a rejected patch would make the next
 * verification meaningless.
 */
public interface PatchSession {

	/**
	 * Open the affected files and apply the patch.
	 *
	 * @param patch the candidate
	 * @throws IOException          if the patch does not apply cleanly
	 * @throws InterruptedException if the build was cancelled
	 */
	void apply(UnifiedDiff patch) throws IOException, InterruptedException;

	/**
	 * Return the workspace to its state before {@link #apply}.
	 *
	 * @throws IOException          if the workspace could not be restored
	 * @throws InterruptedException if the build was cancelled
	 */
	void revert() throws IOException, InterruptedException;

	/**
	 * Hand over a patch that cleared every gate.
	 *
	 * @param patch       the verified patch
	 * @param description what to record against it
	 * @return a human-readable reference to what was delivered, such as a shelved
	 *         changelist number, or a note that this was a dry run
	 * @throws IOException          if delivery failed
	 * @throws InterruptedException if the build was cancelled
	 */
	String deliver(UnifiedDiff patch, String description) throws IOException, InterruptedException;
}

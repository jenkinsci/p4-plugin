package org.jenkinsci.plugins.p4.heal;

import org.jenkinsci.plugins.p4.heal.patch.UnifiedDiff;

import java.io.IOException;

/**
 * How a verified patch leaves the build.
 *
 * <p>Separate from {@link PatchSession} because the two answer to different
 * owners: the session edits the workspace, while delivery needs the build's
 * Perforce credentials and is supplied by the notifier that has them.
 */
public interface PatchDelivery {

	/**
	 * @param patch       the verified patch
	 * @param description what to record against it
	 * @return a human-readable reference, such as a shelved changelist number
	 * @throws IOException          if delivery failed
	 * @throws InterruptedException if the build was cancelled
	 */
	String deliver(UnifiedDiff patch, String description) throws IOException, InterruptedException;
}

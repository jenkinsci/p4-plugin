package org.jenkinsci.plugins.p4.heal;

import hudson.FilePath;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.p4.changes.P4Ref;
import org.jenkinsci.plugins.p4.client.ConnectionHelper;
import org.jenkinsci.plugins.p4.heal.patch.FileDiff;
import org.jenkinsci.plugins.p4.heal.patch.UnifiedDiff;
import org.jenkinsci.plugins.p4.publish.ShelveImpl;
import org.jenkinsci.plugins.p4.tagging.TagAction;
import org.jenkinsci.plugins.p4.tasks.DeleteShelfTask;
import org.jenkinsci.plugins.p4.tasks.PublishTask;
import org.jenkinsci.plugins.p4.workspace.Workspace;

import java.io.IOException;

/**
 * Hands a verified patch over as a shelved changelist.
 *
 * <p>Shelving rather than submitting on purpose: a fix that passed every gate is
 * still a fix nobody has read, so it is left where a human can review, unshelve
 * and submit it.
 *
 * <p>The credential and client come from the build's own {@link TagAction}, so
 * the change lands on the same workspace the build synced with and nothing has to
 * be configured twice.
 */
public class ShelveDelivery implements PatchDelivery {

	private static final String VERIFY_SUFFIX = "-heal";

	private final Run<?, ?> run;
	private final FilePath buildWorkspace;
	private final TaskListener listener;

	private ShelfVerifier verifier;

	/**
	 * @param run            the build being healed
	 * @param buildWorkspace its workspace, which is also the Perforce client root
	 * @param listener       build listener, for the console
	 */
	public ShelveDelivery(Run<?, ?> run, FilePath buildWorkspace, TaskListener listener) {
		this.run = run;
		this.buildWorkspace = buildWorkspace;
		this.listener = listener;
	}

	/**
	 * Build the shelved change once more, on its own, before calling it delivered.
	 *
	 * @param verifier the verifier to use, or null to shelve without re-building
	 */
	public void setVerifier(ShelfVerifier verifier) {
		this.verifier = verifier;
	}

	@Override
	public String deliver(UnifiedDiff patch, String description)
			throws IOException, InterruptedException {

		TagAction tag = TagAction.getLastAction(run);
		if (tag == null || tag.getWorkspace() == null) {
			throw new IOException("This build has no Perforce workspace to shelve into;"
					+ " healing can only deliver a fix for a job that checked out with P4.");
		}

		ShelveImpl shelve = shelveFor(patch, description);

		PublishTask task = new PublishTask(tag.getCredential(), run, listener, shelve);
		Workspace ws = task.setEnvironment(run, tag.getWorkspace().deepClone(), buildWorkspace);
		task.setWorkspace(ws);

		String change = buildWorkspace.act(task);
		if (StringUtils.isEmpty(change)) {
			throw new IOException(
					"Perforce opened no files for the verified patch, so nothing was shelved.");
		}

		long shelf = Long.parseLong(change.trim());
		if (verifier != null) {
			verifyOrWithdraw(tag, shelf);
		}
		return reference(tag.getCredential(), shelf);
	}

	/**
	 * What the console is told about the delivered change: the shelf, and where to
	 * read it in Swarm when the server has a Swarm to point at.
	 *
	 * @param credential the credential the build checked out with
	 * @param shelf      the shelved changelist
	 * @return a human-readable reference to the shelf
	 */
	private String reference(String credential, long shelf) {
		String reference = "shelved change " + shelf;
		String link = reviewLink(swarmUrl(credential), shelf);
		if (link == null) {
			return reference;
		}
		return reference + " — Swarm review: " + link;
	}

	/**
	 * The Swarm URL the Perforce server advertises, or null when it advertises none
	 * or cannot be asked. A missing link is never worth failing a delivered fix
	 * over, so every error here is reported and swallowed.
	 *
	 * @param credential the credential the build checked out with
	 * @return the Swarm base URL, or null if there is none
	 */
	String swarmUrl(String credential) {
		try (ConnectionHelper p4 = new ConnectionHelper(run, credential, listener)) {
			return p4.getSwarm();
		} catch (Exception e) {
			listener.getLogger().println(
					"[p4-heal] could not read the Swarm URL from the server: " + e.getMessage());
			return null;
		}
	}

	/**
	 * The Swarm page for a shelved change, where a reviewer can read the fix and
	 * start a code review from it.
	 *
	 * @param swarm the Swarm base URL, may be null or empty
	 * @param shelf the shelved changelist
	 * @return the page for the change, or null when there is no Swarm to link to
	 */
	static String reviewLink(String swarm, long shelf) {
		if (StringUtils.isEmpty(swarm)) {
			return null;
		}
		return StringUtils.removeEnd(swarm, "/") + "/changes/" + shelf;
	}

	/**
	 * Build the shelf on its own and take it back if it does not stand up.
	 */
	private void verifyOrWithdraw(TagAction tag, long shelf)
			throws IOException, InterruptedException {

		FilePath temp = buildWorkspace.sibling(buildWorkspace.getName() + VERIFY_SUFFIX);
		if (temp == null) {
			throw new IOException("The build workspace has no parent directory,"
					+ " so there is nowhere to build the shelved fix on its own.");
		}

		if (verifier.verify(tag.getCredential(), tag.getClient(), revisionOf(tag), temp, shelf)) {
			return;
		}

		deleteShelf(tag, shelf);
		throw new DeliveryRejectedException("Shelved change " + shelf
				+ " did not build in a clean workspace, so it has been deleted.");
	}

	private void deleteShelf(TagAction tag, long shelf) throws IOException, InterruptedException {
		DeleteShelfTask task = new DeleteShelfTask(tag.getCredential(), run, listener);
		task.setShelf(shelf);
		task.setWorkspace(
				task.setEnvironment(run, tag.getWorkspace().deepClone(), buildWorkspace));
		buildWorkspace.act(task);
	}

	/**
	 * The revision the build synced to, which is what the shelf has to be verified
	 * against; anything else would be measuring a different tree.
	 */
	private static P4Ref revisionOf(TagAction tag) throws IOException {
		return tag.getRefChanges().stream().findFirst().orElseThrow(() -> new IOException(
				"The build did not record the revision it synced to,"
						+ " so a shelved fix cannot be verified against it."));
	}

	/**
	 * Build the publish action for a patch.
	 *
	 * <p>Reconciling is limited to the files the patch touched: a build workspace
	 * holds compiler output and test reports too, and none of that belongs in the
	 * change. Reverting is on, so the workspace goes back to the depot revision
	 * once the fix is safely shelved.
	 *
	 * @param patch       the verified patch
	 * @param description what to record against the change
	 * @return the shelve action to publish with
	 */
	static ShelveImpl shelveFor(UnifiedDiff patch, String description) {
		ShelveImpl shelve = new ShelveImpl(description, false, false, false, true);

		StringBuilder paths = new StringBuilder();
		for (FileDiff diff : patch.getFiles()) {
			paths.append(diff.getPath()).append('\n');
		}
		shelve.setPaths(paths.toString());
		return shelve;
	}
}

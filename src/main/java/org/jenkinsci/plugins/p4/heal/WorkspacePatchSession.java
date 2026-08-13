package org.jenkinsci.plugins.p4.heal;

import hudson.FilePath;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.p4.heal.patch.FileDiff;
import org.jenkinsci.plugins.p4.heal.patch.PatchApplier;
import org.jenkinsci.plugins.p4.heal.patch.PatchFormatException;
import org.jenkinsci.plugins.p4.heal.patch.UnifiedDiff;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Applies candidate patches by editing files in the workspace directly, keeping a
 * snapshot of everything it touches so a rejected candidate can be undone
 * exactly.
 *
 * <p>Snapshot-and-restore rather than {@code p4 revert} on purpose: the loop
 * applies and discards several candidates per attempt, and the workspace must
 * come back byte for byte each time or the next verification measures the wrong
 * thing. It also keeps the healing loop testable without a Perforce server, and
 * means a rejected patch never reaches the depot in any form.
 *
 * <p>Perforce marks unopened files read-only. This session makes a file writable
 * before patching it and restores the original permissions on revert. On Windows
 * agents, where the read-only attribute is not a POSIX mode, the client needs the
 * {@code allwrite} option for patching to succeed.
 */
public class WorkspacePatchSession implements PatchSession {

	private static final int WRITABLE = 0644;

	private final FilePath workspace;
	private final TaskListener listener;
	private final boolean dryRun;
	private final PatchDelivery delivery;

	/**
	 * A snapshot survives until revert, keyed by workspace-relative path.
	 */
	private final Map<String, Snapshot> snapshots = new LinkedHashMap<>();

	/**
	 * @param workspace the build workspace
	 * @param listener  build listener, for the console
	 * @param dryRun    when true nothing is delivered; the verified diff is printed
	 *                  and the workspace is left clean
	 * @param delivery  what to do with a verified patch when not a dry run; may be
	 *                  null while dryRun is true
	 */
	public WorkspacePatchSession(FilePath workspace, TaskListener listener, boolean dryRun,
	                             PatchDelivery delivery) {
		this.workspace = workspace;
		this.listener = listener;
		this.dryRun = dryRun;
		this.delivery = delivery;
	}

	@Override
	public void apply(UnifiedDiff patch) throws IOException, InterruptedException {
		for (FileDiff diff : patch.getFiles()) {
			FilePath file = workspace.child(diff.getPath());

			if (!file.exists()) {
				throw new IOException("The patch targets a file that is not in the workspace: "
						+ diff.getPath());
			}

			String original = file.readToString();
			snapshots.putIfAbsent(diff.getPath(), new Snapshot(file, original, file.mode()));

			try {
				String patched = PatchApplier.apply(original, diff);
				file.chmod(WRITABLE);
				file.write(patched, "UTF-8");
			} catch (PatchFormatException e) {
				// The caller reverts, which rolls back any file already written for
				// this candidate.
				throw new IOException(e.getMessage(), e);
			}
		}
	}

	@Override
	public void revert() throws IOException, InterruptedException {
		for (Snapshot snapshot : snapshots.values()) {
			snapshot.file().chmod(WRITABLE);
			snapshot.file().write(snapshot.content(), "UTF-8");
			if (snapshot.mode() != -1) {
				snapshot.file().chmod(snapshot.mode());
			}
		}
		snapshots.clear();
	}

	@Override
	public String deliver(UnifiedDiff patch, String description)
			throws IOException, InterruptedException {

		if (dryRun) {
			listener.getLogger().println("[p4-heal] dry run — the workspace will be left clean."
					+ " The verified patch was:");
			for (FileDiff diff : patch.getFiles()) {
				listener.getLogger().println("[p4-heal] --- " + diff.getPath());
				for (String line : diff.getRemovedLines()) {
					listener.getLogger().println("[p4-heal] -" + line);
				}
				for (String line : diff.getAddedLines()) {
					listener.getLogger().println("[p4-heal] +" + line);
				}
			}
			revert();
			return "dry run (nothing delivered)";
		}

		if (delivery == null) {
			throw new IOException("Healing is not in dry-run mode but no delivery was configured.");
		}
		return delivery.deliver(patch, description);
	}

	/**
	 * A file as it was before the current candidate touched it.
	 *
	 * @param file    the file in the workspace
	 * @param content its contents before the patch
	 * @param mode    its POSIX mode, or -1 where the platform has none
	 */
	private record Snapshot(FilePath file, String content, int mode) {
	}
}

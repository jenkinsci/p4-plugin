package org.jenkinsci.plugins.p4.client;

import com.perforce.p4java.option.client.SyncOptions;
import org.jenkinsci.plugins.p4.populate.AutoCleanImpl;
import org.jenkinsci.plugins.p4.populate.ForceCleanImpl;
import org.jenkinsci.plugins.p4.populate.SyncOnlyImpl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link ClientHelper#buildSyncOptions} covering the -p/-f flag
 * matrix (P4JENKINS-184). 'p4 sync' rejects -f and -p together, so a force
 * populate that also bypasses the have list (have=false) must emit -p only; the
 * force flag is dropped to keep the command valid.
 */
class SyncOptionsForceBypassTest {

	@Test
	void forceCleanWithBypassEmitsBypassWithoutForce() {
		// ForceCleanImpl(have=false) -> force=true, have=false. -f and -p cannot be
		// combined, so the result must be -p only (no -f), otherwise the server
		// rejects the sync command.
		ForceCleanImpl populate = new ForceCleanImpl(false, false, null, null);

		SyncOptions opts = ClientHelper.buildSyncOptions(populate);

		assertTrue(opts.isServerBypass(), "-p should be set when have=false");
		assertFalse(opts.isForceUpdate(), "-f must not be combined with -p (invalid p4 sync command)");
	}

	@Test
	void forceCleanWithHaveKeepsForceFlag() {
		// ForceCleanImpl(have=true) -> force=true, have=true, i.e. sync -f
		ForceCleanImpl populate = new ForceCleanImpl(true, false, null, null);

		SyncOptions opts = ClientHelper.buildSyncOptions(populate);

		assertFalse(opts.isServerBypass(), "-p should not be set when have=true");
		assertTrue(opts.isForceUpdate(), "-f should be set when force=true");
	}

	@Test
	void autoCleanIsNeitherForcedNorBypassed() {
		// AutoCleanImpl -> force=false, have=true, i.e. plain sync
		AutoCleanImpl populate = autoClean(false);

		SyncOptions opts = ClientHelper.buildSyncOptions(populate);

		assertFalse(opts.isServerBypass(), "-p should not be set for a normal sync");
		assertFalse(opts.isForceUpdate(), "-f should not be set for a normal sync");
	}

	@Test
	void syncOnlyBypassWithoutForceStaysUnforced() {
		// SyncOnlyImpl(force=false, have=false) -> sync -p only
		SyncOnlyImpl populate = new SyncOnlyImpl(false, false, false, false, null, null);

		SyncOptions opts = ClientHelper.buildSyncOptions(populate);

		assertTrue(opts.isServerBypass(), "-p should be set when have=false");
		assertFalse(opts.isForceUpdate(), "-f should not be set when force=false");
	}

	@Test
	void quietFlagPropagates() {
		AutoCleanImpl quiet = autoClean(true);

		SyncOptions opts = ClientHelper.buildSyncOptions(quiet);

		assertTrue(opts.isQuiet(), "-q should reflect the populate quiet flag");
	}

	/**
	 * Build an {@link AutoCleanImpl} varying only the {@code quiet} flag. AutoCleanImpl's
	 * @DataBoundConstructor takes (replace, delete, tidy, modtime, quiet, pin, parallel);
	 * only the have/force/quiet flags feed {@link ClientHelper#buildSyncOptions}, and
	 * AutoClean always fixes have=true/force=false internally, so the first four booleans
	 * are irrelevant here and pinned to sensible defaults to keep the tests focused on
	 * the flag under test.
	 */
	private static AutoCleanImpl autoClean(boolean quiet) {
		return new AutoCleanImpl(true, true, true, false, quiet, null, null);
	}
}

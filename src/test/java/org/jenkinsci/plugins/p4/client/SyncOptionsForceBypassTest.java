package org.jenkinsci.plugins.p4.client;

import com.perforce.p4java.option.client.SyncOptions;
import org.jenkinsci.plugins.p4.populate.AutoCleanImpl;
import org.jenkinsci.plugins.p4.populate.ForceCleanImpl;
import org.jenkinsci.plugins.p4.populate.ParallelSync;
import org.jenkinsci.plugins.p4.populate.SyncOnlyImpl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the two decisions in {@link ClientHelper} that P4JENKINS-184
 * depends on, both testable without a live server:
 *
 * <ul>
 *   <li>{@link ClientHelper#buildSyncOptions} — the -p/-f/-q flag matrix. -f and -p
 *   are mutually exclusive in 'p4 sync', so -f is only sent when the have list is
 *   maintained; -p (have=false) is left untouched (its have-table semantics are a
 *   documented feature and must not change).</li>
 *   <li>{@link ClientHelper#useParallelSync} — the P4JENKINS-184 fix. A parallel
 *   sync combined with -p (bypass) drops content on a read-only replica, so parallel
 *   transfer is disabled whenever the have list is bypassed, <em>independent of
 *   force</em>. See {@link ForcePopulateBypassTest} for the on-disk proof.</li>
 * </ul>
 */
class SyncOptionsForceBypassTest {

	// --- buildSyncOptions: flag matrix -------------------------------------

	@Test
	void forceCleanWithBypassEmitsBypassWithoutForce() {
		// ForceCleanImpl(have=false) -> force=true, have=false. -f and -p cannot be
		// combined, so -p wins and -f is dropped (unchanged, documented -p behaviour).
		ForceCleanImpl populate = new ForceCleanImpl(false, false, null, null);

		SyncOptions opts = ClientHelper.buildSyncOptions(populate);

		assertTrue(opts.isServerBypass(), "-p should be set when have=false");
		assertFalse(opts.isForceUpdate(), "-f must not be combined with -p (invalid p4 sync command)");
	}

	@Test
	void forceCleanWithHaveKeepsForceFlag() {
		// ForceCleanImpl(have=true) -> force=true, have=true, i.e. sync -f.
		ForceCleanImpl populate = new ForceCleanImpl(true, false, null, null);

		SyncOptions opts = ClientHelper.buildSyncOptions(populate);

		assertFalse(opts.isServerBypass(), "-p should not be set when have=true");
		assertTrue(opts.isForceUpdate(), "-f should be set when force=true and have=true");
	}

	@Test
	void autoCleanIsNeitherForcedNorBypassed() {
		// AutoCleanImpl -> force=false, have=true, i.e. plain sync.
		AutoCleanImpl populate = autoClean(false);

		SyncOptions opts = ClientHelper.buildSyncOptions(populate);

		assertFalse(opts.isServerBypass(), "-p should not be set for a normal sync");
		assertFalse(opts.isForceUpdate(), "-f should not be set for a normal sync");
	}

	@Test
	void syncOnlyBypassWithoutForceStaysBypassed() {
		// SyncOnlyImpl(force=false, have=false) -> plain -p.
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

	// --- useParallelSync: the actual P4JENKINS-184 fix ---------------------

	@Test
	void parallelDisabledWhenBypassForceClean() {
		// ForceCleanImpl(have=false) + parallel -> the bug condition; parallel off.
		ForceCleanImpl populate = new ForceCleanImpl(false, false, null, enabledParallel());

		assertFalse(ClientHelper.useParallelSync(populate),
				"parallel must be disabled for a -p (bypass) sync regardless of force");
	}

	@Test
	void parallelDisabledWhenBypassSyncOnlyNoForce() {
		// SyncOnlyImpl(force=false, have=false) + parallel -> same bug, no force.
		// This is the gap the force-based fix missed: the trigger is bypass, not force.
		SyncOnlyImpl populate = new SyncOnlyImpl(false, false, false, false, null, enabledParallel());

		assertFalse(ClientHelper.useParallelSync(populate),
				"parallel must be disabled for a -p (bypass) sync even when force=false");
	}

	@Test
	void parallelEnabledWhenHaveListMaintained() {
		// SyncOnlyImpl(force=false, have=true) + parallel -> no bypass, parallel is safe.
		SyncOnlyImpl populate = new SyncOnlyImpl(false, true, false, false, null, enabledParallel());

		assertTrue(ClientHelper.useParallelSync(populate),
				"parallel should stay enabled when the have list is maintained (no -p)");
	}

	@Test
	void parallelDisabledWhenNotEnabled() {
		// have=true but parallel not enabled -> serial.
		SyncOnlyImpl populate = new SyncOnlyImpl(false, true, false, false, null, null);

		assertFalse(ClientHelper.useParallelSync(populate),
				"parallel should be off when ParallelSync is absent");
	}

	// --- helpers -----------------------------------------------------------

	private static ParallelSync enabledParallel() {
		return new ParallelSync(true, null, "4", "1", "1024");
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

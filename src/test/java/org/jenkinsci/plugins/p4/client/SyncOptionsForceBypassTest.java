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
 * matrix (P4JENKINS-184). -f and -p are mutually exclusive in 'p4 sync' -- the
 * server rejects the combination with "Usage: sync [ -K -n -N -p -q ]" (-f absent
 * from the -p usage line). So when a populate requests both force and a have-list
 * bypass, force wins: -f is kept and -p dropped, forcing the archive transfer that
 * a read-only replica would otherwise skip (see {@link ForcePopulateBypassTest}
 * for the on-disk proof).
 */
class SyncOptionsForceBypassTest {

	@Test
	void forceCleanWithBypassForcesInsteadOfBypassing() {
		// ForceCleanImpl(have=false) -> force=true, have=false. -f and -p cannot be
		// combined; force wins, so the result is -f (no -p) to guarantee content
		// transfer rather than a silent have-list bypass (P4JENKINS-184).
		ForceCleanImpl populate = new ForceCleanImpl(false, false, null, null);

		SyncOptions opts = ClientHelper.buildSyncOptions(populate);

		assertFalse(opts.isServerBypass(), "-p must be dropped when force also requested (invalid with -f)");
		assertTrue(opts.isForceUpdate(), "-f must be set so archive content is actually transferred");
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
	void syncOnlyBypassWithoutForceStaysBypassed() {
		// SyncOnlyImpl(force=false, have=false) -> plain -p (no force, so nothing to
		// win): the have-list bypass is preserved.
		SyncOnlyImpl populate = new SyncOnlyImpl(false, false, false, false, null, null);

		SyncOptions opts = ClientHelper.buildSyncOptions(populate);

		assertTrue(opts.isServerBypass(), "-p should be set when have=false and no force");
		assertFalse(opts.isForceUpdate(), "-f should not be set when force=false");
	}

	@Test
	void syncOnlyForceBypassForcesInsteadOfBypassing() {
		// SyncOnlyImpl(force=true, have=false) -> force wins over -p, same as ForceClean.
		SyncOnlyImpl populate = new SyncOnlyImpl(false, false, true, false, null, null);

		SyncOptions opts = ClientHelper.buildSyncOptions(populate);

		assertFalse(opts.isServerBypass(), "-p must be dropped when force also requested");
		assertTrue(opts.isForceUpdate(), "-f must be set when force=true");
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

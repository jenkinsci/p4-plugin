package org.jenkinsci.plugins.p4.client;

import com.perforce.p4java.core.file.FileSpecOpStatus;
import com.perforce.p4java.core.file.IFileSpec;
import hudson.AbortException;
import hudson.model.TaskListener;
import org.apache.commons.lang.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

public class Validate {

	private static Logger logger = Logger.getLogger(Validate.class.getName());

	private final TaskListener listener;

	public Validate(TaskListener listener) {
		this.listener = listener;
	}

	/**
	 * Look for a message in the returned FileSpec from operation.
	 *
	 * @param fileSpecs List of Perforce file specs
	 * @param ignore    Parameter list of messages to ignore (case insensitive)
	 * @return true if no errors.
	 * @throws Exception push up stack
	 */
	public boolean check(List<IFileSpec> fileSpecs, String... ignore) throws Exception {
		return check(fileSpecs, true, ignore);
	}

	/**
	 * Only return boolean; false is returned for an exception.
	 *
	 * @param fileSpecs List of Perforce file specs
	 * @param quiet    Flag controlling if unknown messages are logged
	 * @param ignore    Parameter list of messages to ignore (case insensitive)
	 * @return true if no errors or exceptions
	 */
	public boolean checkCatch(List<IFileSpec> fileSpecs, boolean quiet, String... ignore) {
		try {
			return check(fileSpecs, quiet, ignore);
		} catch (Exception e) {
			return false;
		}
	}

	/**
	 * Only return boolean; false is returned for an exception.
	 *
	 * @param fileSpecs List of Perforce file specs
	 * @param ignore    Parameter list of messages to ignore (case insensitive)
	 * @return true if no errors or exceptions
	 */
	public boolean checkCatch(List<IFileSpec> fileSpecs, String... ignore) {
		return checkCatch(fileSpecs, true, ignore);
	}

	/**
	 * Look for a message in the returned FileSpec from operation.
	 *
	 * @param fileSpecs List of Perforce file specs
	 * @param quiet    Flag controlling if unknown messages are logged
	 * @param ignore    Parameter list of messages to ignore (case insensitive)
	 * @return true if no errors.
	 * @throws Exception push up stack
	 */
	public boolean check(List<IFileSpec> fileSpecs, boolean quiet, String... ignore) throws Exception {
		boolean success = true;
		boolean abort = false;
		StringBuffer errorLog = new StringBuffer();

		ArrayList<String> ignoreList = new ArrayList<String>();
		ignoreList.addAll(Arrays.asList(ignore));

		for (IFileSpec fileSpec : fileSpecs) {
			FileSpecOpStatus status = fileSpec.getOpStatus();
			if (status != FileSpecOpStatus.VALID) {
				String msg = fileSpec.getStatusMessage();

				// superfluous p4java message
				boolean unknownMsg = true;
				for (String istring : ignoreList) {
					if (!istring.isEmpty() && StringUtils.containsIgnoreCase(msg, istring)) {
						// its a known message
						unknownMsg = false;
						break;
					}
				}

				// check and report unknown message
				if (unknownMsg) {
					if (!quiet) {
						msg = "P4JAVA: " + msg;
						log(msg);
						logger.warning(msg);
					}
					if (status == FileSpecOpStatus.ERROR || status == FileSpecOpStatus.CLIENT_ERROR) {
						errorLog.append(msg);
						errorLog.append("\n");
						abort = true;
					}
					success = false;
				}
			}
		}

		if (abort) {
			String msg = "P4JAVA: Error(s):\n" + errorLog.toString();
			throw new AbortException(msg);
		}
		return success;
	}

	public void log(String msg) {
		if (listener == null) {
			return;
		}
		listener.getLogger().println(msg);
	}
}

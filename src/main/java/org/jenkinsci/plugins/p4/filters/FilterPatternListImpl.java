package org.jenkinsci.plugins.p4.filters;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.util.FormValidation;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class FilterPatternListImpl extends Filter implements Serializable {
	private static Logger logger = Logger.getLogger(FilterPatternListImpl.class.getName());

	@Serial
	private static final long serialVersionUID = 1L;

	private final String patternText;
	private final boolean caseSensitive;

	@DataBoundConstructor
	public FilterPatternListImpl(String patternText, boolean caseSensitive) {
		this.patternText = patternText;
		this.caseSensitive = caseSensitive;
	}

	public String getPatternText() {
		return patternText;
	}

	public boolean getCaseSensitive() {
		return caseSensitive;
	}

	public boolean isCaseSensitive() {
		return getCaseSensitive();
	}

	public ArrayList<Pattern> getPatternList() {
		ArrayList<Pattern> patternList = new ArrayList<>();

		int caseFlag = 0;
		if (!caseSensitive) {
			caseFlag = Pattern.CASE_INSENSITIVE;
		}

		for (String line : patternText.split("\\R")) {
			// Try compiling each line into patterns. If we can't compile a line, skip it and log.
			try {
				patternList.add(Pattern.compile(line, caseFlag));
			}
			catch (PatternSyntaxException e) {
				logger.severe("Error processing supposed pattern \"" + line + "\", ignoring:\n" + e);
			}
		}

		return patternList;
	}

	@Extension
	@Symbol("viewPattern")
	public static final class DescriptorImpl extends FilterDescriptor {

		@NonNull
		@Override
		public String getDisplayName() {
			return "Exclude changes outside Java pattern";
		}

		public FormValidation doCheckPatternText(@QueryParameter String value) {
			if (value.trim().isEmpty()) {
				return FormValidation.warning("Empty pattern list found, will ignore all changes during polling.");
			}

			int lineNumber = 1;
			try {
				for (String line: value.split("\\R")) {
					Pattern.compile(line);
					lineNumber++;
				}
				return FormValidation.ok();
			}
			catch (PatternSyntaxException e) {
				String errorMsg = "Error encountered compiling pattern on line #" + lineNumber + " (if not fixed, will ignore).";
				return FormValidation.error(e, errorMsg);
			}
		}
	}
}

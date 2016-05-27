package org.jenkinsci.plugins.p4.workspace;

import hudson.slaves.NodeProperty;
import hudson.slaves.EnvironmentVariablesNodeProperty;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import jenkins.model.Jenkins;

import org.jenkinsci.plugins.p4.review.ReviewProp;

public class Expand {

	private Map<String, String> formatTags = new HashMap<String, String>();

	public Expand(Map<String, String> map) {
		Jenkins jenkins = Jenkins.getInstance();

		for (NodeProperty<?> node : jenkins.getGlobalNodeProperties()) {
			if (node instanceof EnvironmentVariablesNodeProperty) {
				EnvironmentVariablesNodeProperty env = (EnvironmentVariablesNodeProperty) node;
				formatTags.putAll((env).getEnvVars());
			}
		}

		for (Entry<String, String> entry : map.entrySet()) {
			String key = entry.getKey();
			String value = entry.getValue();
			if (ReviewProp.isProp(key)) {
				// Known Perforce Review property; prefix with namespace
				key = ReviewProp.NAMESPACE + key;
			}
			set(key, value);
		}
	}

	public String format(String format, boolean wildcard) {
		if (formatTags != null) {
			for (Entry<String, String> entry : formatTags.entrySet()) {
				String key = entry.getKey();
				String value = entry.getValue();
				if (value != null) {
					format = format.replace("${" + key + "}", value);
				}
			}
		}

		// cleanup undefined tags
		if (wildcard) {
			format = format.replaceAll("\\$\\{.+?\\}", "*");
		}
		format = format.replace("${", "");
		format = format.replace("}", "");
		return format;
	}

	public void set(String tag, String value) {
		formatTags.put(tag, value);
	}

	public String get(String tag) {
		if (formatTags == null) {
			return null;
		}
		return formatTags.get(tag);
	}
}

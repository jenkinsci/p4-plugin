package org.jenkinsci.plugins.p4.heal.patch;

import java.io.Serial;

/**
 * Thrown when text offered as a patch is not a well-formed unified diff, or
 * targets a path the plugin refuses to touch.
 */
public class PatchFormatException extends Exception {

	@Serial
	private static final long serialVersionUID = 1L;

	public PatchFormatException(String message) {
		super(message);
	}
}

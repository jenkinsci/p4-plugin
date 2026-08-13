package org.jenkinsci.plugins.p4.heal;

import java.io.IOException;
import java.io.Serial;

/**
 * A verified patch that failed on the way out, and was withdrawn.
 *
 * <p>Distinct from any other {@link IOException} a delivery can throw: this one
 * says the patch is at fault rather than the machinery, so the healing loop can
 * go on and try the next candidate instead of giving up.
 */
public class DeliveryRejectedException extends IOException {

	@Serial
	private static final long serialVersionUID = 1L;

	public DeliveryRejectedException(String message) {
		super(message);
	}
}

package org.jenkinsci.plugins.p4.heal.agent;

import java.io.Serial;
import java.io.Serializable;

/**
 * Which model each kind of agent runs on.
 *
 * <p>Diagnosis and patch generation are the hard reasoning and get the capable
 * model. Critique is mostly reading — checking a diff against code and against
 * the repository's own conventions — and there are far more critic calls than
 * generation calls, so it runs on the cheap model. That split is what keeps a
 * multi-agent heal affordable.
 *
 * <p>Model ids are configuration rather than constants because they change
 * every few months.
 *
 * @param smartModel  model for diagnosis and patch generation
 * @param cheapModel  model for critics
 * @param smartEffort reasoning effort for the capable model
 * @param cheapEffort reasoning effort for critics
 * @param maxTokens   response ceiling for every agent
 */
public record HealModels(String smartModel, String cheapModel, String smartEffort,
                         String cheapEffort, int maxTokens) implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;
}

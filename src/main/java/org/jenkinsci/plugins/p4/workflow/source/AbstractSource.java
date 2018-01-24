package org.jenkinsci.plugins.p4.workflow.source;

import hudson.DescriptorExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Describable;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.p4.workspace.Workspace;

import java.io.Serializable;

public abstract class AbstractSource implements ExtensionPoint, Describable<AbstractSource>, Serializable {
	private static final long serialVersionUID = 1L;

	public abstract Workspace getWorkspace(String charset, String format);

	public P4SyncDescriptor getDescriptor() {
		Jenkins j = Jenkins.getInstance();
		if (j != null) {
			return (P4SyncDescriptor) j.getDescriptor(getClass());
		}
		return null;
	}

	public static DescriptorExtensionList<AbstractSource, P4SyncDescriptor> all() {
		Jenkins j = Jenkins.getInstance();
		if (j != null) {
			return j.<AbstractSource, P4SyncDescriptor>getDescriptorList(AbstractSource.class);
		}
		return null;
	}

	public static String getClientView(String src, String dest) {
		return new ClientViewMappingGenerator().generateClientView(src, dest);
	}
}

/**
 * Class used to generate client view mapping given a depot syntax and client syntax
 */
class ClientViewMappingGenerator {

	/**
	 * Generates client view mapping string.
	 * e.g  //depot/src/...  //ws/src/...
	 * <p>
	 * This method handles the case where multiple items are specified in dpotSyntax delimitted
	 * with a new line character \n, in addition to standard specification.
	 * e.g //depot/src/...\n//depot/tgt/...
	 * <p>
	 * If multiple items are speicified in depot syntax, each client syntax will be appended with
	 * the part of the depot syntax that follows //, to avoid ambiguity.
	 * <p>
	 * For instance //depot/src/...\n//depot/tgt/... with a client syntax of jenkins-job will generate the following
	 * view,
	 * <p>
	 * //depot/src/...  //jenkins-job/depot/src/...
	 * //depot/tgt/...  //jenkins-job/depot/tgt/...
	 *
	 * @param depotSyntax  The left hand side of the client view
	 * @param clientSyntax The right hand side of the client view
	 * @return Client view mapping
	 */
	public String generateClientView(String depotSyntax, String clientSyntax) {

		StringBuffer view = new StringBuffer();

		if (depotSyntax != null && !depotSyntax.isEmpty()) {
			String[] sources = depotSyntax.split("\n");
			int viewCount = 0;
			int sourceCount = sources.length;
			String destination = clientSyntax;

			for (String source : sources) {

				String sourceWithNoInitSlashes = source.substring(2);
				String[] srcSplit = sourceWithNoInitSlashes.split("/");
				StringBuffer formattedSrc = new StringBuffer("//");
				StringBuffer formattedDest = new StringBuffer("//");

				if (viewCount > 0) {
					view.append("\n");
				}

				if (srcSplit != null && srcSplit.length > 0) {
					String lastSrc = srcSplit[srcSplit.length - 1];
					boolean containsDots = lastSrc.contains(".");

					int count = 0;
					for (String srcItem : srcSplit) {
						if (count > 0) {
							formattedSrc.append("/");
						}
						formattedSrc.append(srcItem);
						count++;
					}
					//if multiple depots are specified in depotSyntax, append
					//each depot path to client syntax of client view
					if (sourceCount > 1) {
						StringBuffer tmpDestination = new StringBuffer();
						destination = clientSyntax + "/" + sourceWithNoInitSlashes;
						String[] destsrcSplit = destination.split("/");
						int dcount = 0;
						for (String destItem : destsrcSplit) {
							if (!destItem.contains(".")) {
								if (dcount > 0) {
									tmpDestination.append("/");
								}
								tmpDestination.append(destItem);
								dcount++;
							}
						}
						destination = tmpDestination.toString();
					}

					if (containsDots) {
						formattedDest.append(destination + "/" + lastSrc);
					} else {
						formattedSrc.append("/...");
						formattedDest.append(destination + "/...");
					}
				}
				view.append(formattedSrc.toString().replaceAll("\n", "") + " " + formattedDest.toString());
				viewCount++;
			}
		}
		return view.toString();
	}
}
package org.jenkinsci.plugins.p4.publish;

import org.kohsuke.stapler.DataBoundConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class CommitImpl extends Publish implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	private List<String> files;

	@DataBoundConstructor
	public CommitImpl(String description, boolean onlyOnSuccess, boolean delete, boolean modtime) {
		super(description, onlyOnSuccess, delete, modtime);
		files = new ArrayList<>();
	}

	public void addFile(String file) {
		files.add(file);
	}

	public List<String> getFiles() {
		return files;
	}

// TODO add Jelly support when ready
//	@Extension
//	public static final class DescriptorImpl extends PublishDescriptor {
//
//		@Override
//		public String getDisplayName() {
//			return "Commit change";
//		}
//	}
}

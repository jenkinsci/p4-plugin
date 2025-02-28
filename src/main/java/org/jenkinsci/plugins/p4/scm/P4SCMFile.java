package org.jenkinsci.plugins.p4.scm;

import com.perforce.p4java.client.IClient;
import com.perforce.p4java.core.file.FileSpecBuilder;
import com.perforce.p4java.core.file.FileSpecOpStatus;
import com.perforce.p4java.core.file.IExtendedFileSpec;
import com.perforce.p4java.core.file.IFileSpec;
import com.perforce.p4java.exception.P4JavaException;
import com.perforce.p4java.option.server.GetExtendedFilesOptions;
import com.perforce.p4java.option.server.GetFileContentsOptions;
import edu.umd.cs.findbugs.annotations.NonNull;
import jenkins.scm.api.SCMFile;
import org.jenkinsci.plugins.p4.client.ConnectionHelper;
import org.jenkinsci.plugins.p4.client.NavigateHelper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

public class P4SCMFile extends SCMFile {

	private final P4SCMFileSystem fs;
	private final boolean isDir;

	private static Logger logger = Logger.getLogger(P4SCMFile.class.getName());

	public P4SCMFile(P4SCMFileSystem fs) {
		this.fs = fs;
		this.isDir = true;
	}

	public P4SCMFile(P4SCMFileSystem fs, @NonNull P4SCMFile parent, String name, boolean isDir) {
		super(parent, name);
		this.fs = fs;
		this.isDir = isDir;
	}

	@NonNull
	@Override
	protected SCMFile newChild(@NonNull String name, boolean assumeIsDirectory) {
		return new P4SCMFile(fs, this, name, assumeIsDirectory);
	}

	/**
	 * If this object represents a directory, lists up all the immediate children.
	 *
	 * @return Always non-null. If this method is not a directory, this method returns
	 * an empty iterable.
	 */
	@NonNull
	@Override
	public Iterable<SCMFile> children() {
		String path = getPath();

		ConnectionHelper p4 = fs.getConnection();
		NavigateHelper nav = new NavigateHelper(p4.getConnection());

		List<SCMFile> list = new ArrayList<>();
		List<NavigateHelper.Node> nodes = nav.getNodes(path);
		for (NavigateHelper.Node node : nodes) {
			list.add(newChild(node.getName(), node.isDir()));
		}

		return list;
	}

	/**
	 * Returns the time that the {@link SCMFile} was last modified.
	 *
	 * @return A <code>long</code> value representing the time the file was last modified, measured in milliseconds
	 * since the epoch (00:00:00 GMT, January 1, 1970) or {@code 0L} if the operation is unsupported.
	 * @throws IOException          if an error occurs while performing the operation.
	 */
	@Override
	public long lastModified() throws IOException {

		ConnectionHelper p4 = fs.getConnection();
		List<IFileSpec> file = getFileSpec();

		GetExtendedFilesOptions exOpts = new GetExtendedFilesOptions();
		try {
			List<IExtendedFileSpec> fstat = p4.getConnection().getExtendedFiles(file, exOpts);
			if (fstat.get(0).getOpStatus().equals(FileSpecOpStatus.VALID)) {
				Date date = fstat.get(0).getHeadModTime();
				return date.getTime();
			}
		} catch (P4JavaException e) {
			throw new IOException(e);
		}
		return 0;
	}

	/**
	 * The type of this object.
	 *
	 * @return the {@link Type} of this object, specifically {@link Type#NONEXISTENT} if this {@link SCMFile} instance
	 * does not exist in the remote system (e.g. if you created a nonexistent instance via {@link #child(String)})
	 * @throws IOException          if an error occurs while performing the operation.
	 * @since 2.0
	 */
	@NonNull
	@Override
	protected Type type() throws IOException {
		if (isDir) {
			return Type.DIRECTORY;
		}

		ConnectionHelper p4 = fs.getConnection();
		List<IFileSpec> file = getFileSpec();

		GetExtendedFilesOptions exOpts = new GetExtendedFilesOptions();
		try {
			List<IExtendedFileSpec> fstat = p4.getConnection().getExtendedFiles(file, exOpts);
			if (fstat.get(0).getOpStatus().equals(FileSpecOpStatus.VALID)) {
				String type = fstat.get(0).getHeadType();
				if (type.startsWith("symlink")) {
					return Type.LINK;
				}
				return Type.REGULAR_FILE;
			}
		} catch (P4JavaException e) {
			throw new IOException(e);
		}

		return Type.NONEXISTENT;
	}

	/**
	 * Reads the content of this file.
	 *
	 * @return an open stream to read the file content. The caller must close the stream.
	 * @throws IOException          if this object represents a directory or if an error occurs while performing the
	 *                              operation.
	 */
	@NonNull
	@Override
	public InputStream content() throws IOException {
		ConnectionHelper p4 = fs.getConnection();
		List<IFileSpec> file = getFileSpec();
		GetFileContentsOptions printOpts = new GetFileContentsOptions();
		printOpts.setNoHeaderLine(true);
		addJenkinsFilePathToTagAction(p4, file);
		try {
			return p4.getConnection().getFileContents(file, printOpts);
		} catch (P4JavaException e) {
			throw new IOException(e);
		}
	}

	private void addJenkinsFilePathToTagAction(ConnectionHelper p4, List<IFileSpec> file) {
		try {
			IClient currentClient = p4.getConnection().getCurrentClient();
			currentClient.refresh();
			List<IFileSpec> where = currentClient.localWhere(file);
			fs.addJenkinsFilePath(where.get(0).getDepotPathString());
		} catch (Exception e) {
			logger.warning("P4: Error retrieving depot path for the Jenkins file.");
		}

	}

	private List<IFileSpec> getFileSpec() {
		String clientPath = "//" + fs.getConnection().getClientUUID() + "/";

		String path = getPath();
		if (!path.startsWith(clientPath)) {
			path = clientPath + path;
		}
		List<IFileSpec> file = FileSpecBuilder.makeFileSpecList(path);
		return file;
	}
}

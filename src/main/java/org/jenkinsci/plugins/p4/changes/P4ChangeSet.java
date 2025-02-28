package org.jenkinsci.plugins.p4.changes;

import com.perforce.p4java.core.IFix;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.Run;
import hudson.scm.ChangeLogSet;
import hudson.scm.RepositoryBrowser;
import org.apache.commons.lang.StringEscapeUtils;
import org.kohsuke.stapler.framework.io.WriterOutputStream;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class P4ChangeSet extends ChangeLogSet<P4ChangeEntry> {

	private static Object lock = new Object();
	private List<P4ChangeEntry> history;

	protected P4ChangeSet(Run<?, ?> run, RepositoryBrowser<?> browser, List<P4ChangeEntry> logs) {
		super(run, browser);
		this.history = Collections.unmodifiableList(logs);
	}

	@NonNull
	public Iterator<P4ChangeEntry> iterator() {
		return history.iterator();
	}

	@Override
	public boolean isEmptySet() {
		return history.isEmpty();
	}

	public List<P4ChangeEntry> getHistory() {
		return history;
	}

	public Collection<P4ChangeEntry> getLogs() {
		return history;
	}

	public static void store(File file, List<P4ChangeEntry> changes) {
		try {
			synchronized (lock) {
				FileOutputStream o = new FileOutputStream(file);
				BufferedOutputStream b = new BufferedOutputStream(o);
				Charset c = StandardCharsets.UTF_8;
				OutputStreamWriter w = new OutputStreamWriter(b, c);
				WriterOutputStream s = new WriterOutputStream(w);
				PrintStream stream = new PrintStream(s, true, StandardCharsets.UTF_8);

				stream.println("<?xml version='1.0' encoding='UTF-8'?>");
				stream.println("<changelog>");
				for (P4ChangeEntry cl : changes) {
					stream.println("\t<entry>");
					stream.println("\t\t<changenumber><changeInfo>" + cl.getId() + "</changeInfo>");
					stream.println("\t\t<clientId>" + cl.getClientId() + "</clientId>");

					stream.println("\t\t<msg>" + P4Escaper.filter().translate(StringEscapeUtils.escapeXml(cl.getMsg())) + "</msg>");
					stream.println("\t\t<changeUser>" + StringEscapeUtils.escapeXml(cl.getAuthor().getDisplayName())
							+ "</changeUser>");

					stream.println("\t\t<changeTime>" + StringEscapeUtils.escapeXml(cl.getChangeTime()) + "</changeTime>");

					stream.println("\t\t<shelved>" + cl.isShelved() + "</shelved>");

					stream.println("\t\t<fileLimit>" + cl.isFileLimit() + "</fileLimit>");

					stream.println("\t\t<files>");
					Collection<P4AffectedFile> files = cl.getAffectedFiles();
					if (files != null) {
						for (P4AffectedFile f : files) {
							String action = f.getAction();
							String revision = f.getRevision();

							// URL encode depot path
							String depotPath = f.getPath();
							String safePath = URLEncoder.encode(depotPath, StandardCharsets.UTF_8);

							stream.println("\t\t<file endRevision=\"" + revision + "\" action=\"" + action
									+ "\" depot=\"" + safePath + "\" />");
						}
					}
					stream.println("\t\t</files>");

					stream.println("\t\t<jobs>");
					List<IFix> jobs = cl.getJobs();
					if (jobs != null) {
						for (IFix job : jobs) {
							String id = job.getJobId();
							String status = job.getStatus();

							stream.println("\t\t<job id=\"" + id + "\" status=\"" + status + "\" />");
						}
					}
					stream.println("\t\t</jobs>");

					stream.println("\t\t</changenumber>");

					stream.println("\t</entry>");
				}
				stream.println("</changelog>");
				stream.flush();
				stream.close();
				o.close();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}

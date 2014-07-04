package org.jenkinsci.plugins.p4.changes;

import hudson.model.AbstractBuild;
import hudson.scm.ChangeLogSet;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.kohsuke.stapler.framework.io.WriterOutputStream;

public class P4ChangeSet extends ChangeLogSet<P4ChangeEntry> {

	private List<P4ChangeEntry> history;

	protected P4ChangeSet(AbstractBuild<?, ?> build, List<P4ChangeEntry> logs) {
		super(build);
		this.history = Collections.unmodifiableList(logs);
	}

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

	public static void store(File file, List<Object> changes) {
		try {
			FileOutputStream o = new FileOutputStream(file);
			BufferedOutputStream b = new BufferedOutputStream(o);
			Charset c = Charset.forName("UTF-8");
			OutputStreamWriter w = new OutputStreamWriter(b, c);
			WriterOutputStream s = new WriterOutputStream(w);
			PrintStream stream = new PrintStream(s);

			stream.println("<?xml version='1.0' encoding='UTF-8'?>");
			stream.println("<changelog>");
			for (Object change : changes) {
				stream.println("\t<entry>");
				if (change instanceof String) {
					stream.println("\t\t<label>" + change + "</label>");
				} else {
					stream.println("\t\t<changenumber>" + change
							+ "</changenumber>");
				}
				stream.println("\t</entry>");
			}
			stream.println("</changelog>");
			stream.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}
}

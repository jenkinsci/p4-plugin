package org.jenkinsci.plugins.p4.changes;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.jenkinsci.plugins.p4.PerforceScm;
import org.jenkinsci.plugins.p4.client.ConnectionHelper;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import com.perforce.p4java.core.IChangelistSummary;
import com.perforce.p4java.core.IFix;
import com.perforce.p4java.core.file.FileAction;
import com.perforce.p4java.core.file.IFileSpec;
import com.perforce.p4java.impl.generic.core.Fix;
import com.perforce.p4java.impl.generic.core.file.FileSpec;

import hudson.model.AbstractProject;
import hudson.model.Job;
import hudson.model.Run;
import hudson.scm.ChangeLogParser;
import hudson.scm.ChangeLogSet;
import hudson.scm.RepositoryBrowser;
import hudson.scm.ChangeLogSet.Entry;

public class P4ChangeParser extends ChangeLogParser {

	/**
	 * Uses the "index.jelly" view to render the changelist details and use the
	 * "digest.jelly" view of to render the summary page.
	 */

	@SuppressWarnings("rawtypes")
	@Override
	public ChangeLogSet<? extends Entry> parse(Run run, RepositoryBrowser<?> browser, File file)
			throws IOException, SAXException {
		try {
			SAXParserFactory factory = SAXParserFactory.newInstance();
			SAXParser parser = factory.newSAXParser();
			ChangeLogHandler handler = new ChangeLogHandler(run, browser);
			parser.parse(file, handler);
			P4ChangeSet changeSet = handler.getChangeLogSet();
			return changeSet;
		} catch (Exception e) {
			throw new SAXException("Could not parse perforce changelog: ", e);
		}
	}

	public static class ChangeLogHandler extends DefaultHandler {
		private Stack<P4ChangeEntry> objects = new Stack<P4ChangeEntry>();
		private StringBuffer text = new StringBuffer();

		private List<P4ChangeEntry> changeEntries;
		private P4ChangeSet changeSet;
		private Run<?, ?> run;
		private RepositoryBrowser<?> browser;

		public ChangeLogHandler(Run<?, ?> run, RepositoryBrowser<?> browser) {
			this.run = run;
			this.browser = browser;
		}

		@Override
		public void characters(char[] ch, int start, int length) throws SAXException {
			text.append(ch, start, length);
		}

		@Override
		public void startDocument() throws SAXException {
			changeEntries = new ArrayList<P4ChangeEntry>();
			changeSet = new P4ChangeSet(run, browser, changeEntries);
		}

		@Override
		public void endDocument() throws SAXException {
		}

		@Override
		public void startElement(String uri, String localName, String qName, Attributes attributes)
				throws SAXException {

			if (qName.equalsIgnoreCase("changelog")) {
				// this is the root, so don't do anything
				text.setLength(0);
				return;
			}
			if (qName.equalsIgnoreCase("entry")) {
				objects.push(new P4ChangeEntry(changeSet));
				text.setLength(0);
				return;
			}
			if (objects.peek() instanceof P4ChangeEntry) {
				P4ChangeEntry entry = (P4ChangeEntry) objects.peek();
				try {
					if (qName.equalsIgnoreCase("file")) {
						IFileSpec temp = new FileSpec();

						// URL decode depot path
						String safePath = attributes.getValue("depot");
						String depotPath = URLDecoder.decode(safePath, "UTF-8");
						temp.setDepotPath(depotPath);

						String action = attributes.getValue("action");
						temp.setAction(FileAction.fromString(action));

						String strRev = attributes.getValue("endRevision");
						int endRevision = Integer.parseInt(strRev);
						temp.setEndRevision(endRevision);

						entry.files.add(temp);
						text.setLength(0);
						return;
					}

					if (qName.equalsIgnoreCase("job")) {
						IFix temp = new Fix();

						String id = attributes.getValue("id");
						temp.setJobId(id);

						String status = attributes.getValue("status");
						temp.setStatus(status);

						entry.jobs.add(temp);
						text.setLength(0);
						return;
					}

				} catch (UnsupportedEncodingException e) {
					entry = null;
				}
			}

			text.setLength(0);
		}

		@Override
		public void endElement(String uri, String localName, String qName) throws SAXException {
			if (qName.equalsIgnoreCase("changelog")) {
				// this is the root, so don't do anything
				return;
			}

			if (qName.equalsIgnoreCase("entry")) {
				P4ChangeEntry entry = (P4ChangeEntry) objects.pop();
				changeEntries.add(entry);
				return;
			}

			// if we are in the entry element
			if (objects.peek() instanceof P4ChangeEntry) {
				P4ChangeEntry entry = (P4ChangeEntry) objects.peek();
				try {

					if (text.toString().trim().length() != 0
							&& (qName.equalsIgnoreCase("changenumber") || qName.equalsIgnoreCase("label"))) {

						// CASTING: is this safe?
						Job<?, ?> job = run.getParent();
						AbstractProject<?, ?> project = (AbstractProject<?, ?>) job;

						// Find Credential ID and Workspace for this build
						PerforceScm scm = (PerforceScm) project.getScm();
						String credential = scm.getCredential();

						// Log in to Perforce and find change-list
						ConnectionHelper p4 = new ConnectionHelper(credential, null);

						// Add changelist to entry
						if (qName.equalsIgnoreCase("changenumber")) {
							int id = Integer.parseInt(text.toString());
							IChangelistSummary summary = p4.getChangeSummary(id);
							entry.setChange(p4, summary);
						}

						// Add label to entry
						if (qName.equalsIgnoreCase("label")) {
							String id = text.toString();
							entry.setLabel(p4, id);
						}

						// disconnect from Perforce
						p4.disconnect();

					} else {

						String elementText = text.toString().trim();

						if (qName.equalsIgnoreCase("changeInfo")) {
							int id = Integer.parseInt(elementText);
							entry.setId(new P4Revision(id));
							text.setLength(0);
							return;
						}

						if (qName.equalsIgnoreCase("shelved")) {
							entry.setShelved(elementText.equals("true"));
							text.setLength(0);
						}

						if (qName.equalsIgnoreCase("msg")) {
							entry.setMsg(elementText);
							text.setLength(0);
						}

						if (qName.equalsIgnoreCase("clientId")) {
							entry.setClientId(elementText);
							text.setLength(0);
							return;
						}

						if (qName.equalsIgnoreCase("changeUser")) {
							entry.setAuthor(elementText);
							text.setLength(0);
							return;
						}

						if (qName.equalsIgnoreCase("changeTime")) {
							entry.setDate(elementText);
							text.setLength(0);
							return;
						}
					}

					text.setLength(0);
					return;
				} catch (Exception e) {
					entry = null;
				}
			}

		}

		public P4ChangeSet getChangeLogSet() {
			return changeSet;
		}
	}

}

package org.jenkinsci.plugins.p4.changes;

import com.perforce.p4java.core.IChangelistSummary;
import com.perforce.p4java.core.IFix;
import com.perforce.p4java.core.file.FileAction;
import com.perforce.p4java.exception.P4JavaException;
import com.perforce.p4java.impl.generic.core.Fix;
import hudson.model.Run;
import hudson.scm.ChangeLogParser;
import hudson.scm.ChangeLogSet;
import hudson.scm.ChangeLogSet.Entry;
import hudson.scm.RepositoryBrowser;
import org.jenkinsci.plugins.p4.browsers.SwarmBrowser;
import org.jenkinsci.plugins.p4.client.ConnectionHelper;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

/**
 * Uses the "index.jelly" view to render the changelist details and use the
 * "digest.jelly" view of to render the summary page.
 */
public class P4ChangeParser extends ChangeLogParser {

	private final String credential;

	public P4ChangeParser(String credential) {
		this.credential = credential;
	}

	@SuppressWarnings("rawtypes")
	@Override
	public ChangeLogSet<? extends Entry> parse(Run run, RepositoryBrowser<?> browser, File file)
			throws IOException, SAXException {
		try {
			SAXParserFactory factory = SAXParserFactory.newInstance();
			SAXParser parser = factory.newSAXParser();
			ChangeLogHandler handler = new ChangeLogHandler(run, browser, credential);
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
		private ConnectionHelper p4;

		public ChangeLogHandler(Run<?, ?> run, RepositoryBrowser<?> browser, String credential) throws P4JavaException {
			this.run = run;
			this.browser = browser;
			this.p4 = new ConnectionHelper(run, credential, null);

			if (browser == null) {
				String url = p4.getSwarm();
				if (url != null) {
					this.browser = new SwarmBrowser(url);
				}
			}
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

						// URL decode depot path
						String safePath = attributes.getValue("depot");
						String depotPath = URLDecoder.decode(safePath, "UTF-8");
						String a = attributes.getValue("action");
						FileAction action = FileAction.fromString(a);
						String strRev = attributes.getValue("endRevision");

						P4AffectedFile file = new P4AffectedFile(depotPath, strRev, action);
						entry.addAffectedFiles(file);

						///entry.files.add(temp);
						text.setLength(0);
						return;
					}

					if (qName.equalsIgnoreCase("job")) {
						IFix temp = new Fix();

						String id = attributes.getValue("id");
						temp.setJobId(id);

						String status = attributes.getValue("status");
						temp.setStatus(status);

						entry.addJob(temp);
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
							&& (qName.equalsIgnoreCase("changenumber")
							|| qName.equalsIgnoreCase("label")
							|| qName.equalsIgnoreCase("commit"))) {

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

						// Add Commit to entry
						if (qName.equalsIgnoreCase("commit")) {
							String id = text.toString();
							entry.setGraphCommit(p4, id);
						}

						// disconnect from Perforce
						p4.disconnect();
					} else {

						String elementText = text.toString().trim();

						if (qName.equalsIgnoreCase("changeInfo")) {
							if (elementText.contains("@")) {
								entry.setId(new P4GraphRef(p4, elementText));
								text.setLength(0);
								return;
							} else {
								int id = Integer.parseInt(elementText);
								entry.setId(new P4ChangeRef(id));
								text.setLength(0);
								return;
							}
						}

						if (qName.equalsIgnoreCase("shelved")) {
							entry.setShelved(elementText.equals("true"));
							text.setLength(0);
							return;
						}

						if (qName.equalsIgnoreCase("msg")) {
							entry.setMsg(elementText);
							text.setLength(0);
							return;
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

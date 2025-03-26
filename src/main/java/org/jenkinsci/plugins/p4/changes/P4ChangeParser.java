package org.jenkinsci.plugins.p4.changes;

import com.perforce.p4java.core.IChangelistSummary;
import com.perforce.p4java.core.IFix;
import com.perforce.p4java.core.file.FileAction;
import com.perforce.p4java.exception.P4JavaException;
import com.perforce.p4java.exception.RequestException;
import com.perforce.p4java.impl.generic.core.Fix;
import hudson.model.Run;
import hudson.scm.ChangeLogParser;
import hudson.scm.ChangeLogSet;
import hudson.scm.ChangeLogSet.Entry;
import hudson.scm.RepositoryBrowser;
import org.jenkinsci.plugins.p4.browsers.SwarmBrowser;
import org.jenkinsci.plugins.p4.client.ConnectionHelper;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.File;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.logging.Logger;

/**
 * Uses the "index.jelly" view to render the changelist details and use the
 * "digest.jelly" view of to render the summary page.
 */
public class P4ChangeParser extends ChangeLogParser {

	private static Logger logger = Logger.getLogger(P4ChangeParser.class.getName());

	private final String credential;

	public P4ChangeParser(String credential) {
		this.credential = credential;
	}

	@Override
	public ChangeLogSet<? extends Entry> parse(Run run, RepositoryBrowser<?> browser, File file) {
		try (ConnectionHelper p4 = new ConnectionHelper(run, credential, null)) {
			SAXParserFactory factory = SAXParserFactory.newInstance();
			factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
			factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
			factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
			SAXParser parser = factory.newSAXParser();
			ChangeLogHandler handler = new ChangeLogHandler(run, browser, p4);
			parser.parse(file, handler);
			P4ChangeSet changeSet = handler.getChangeLogSet();
			return changeSet;
		} catch (Exception e) {
			logger.severe("Could not parse Perforce changelog: " + file.toString());
		}
		return new P4ChangeSet(run, browser, new ArrayList<>());
	}

	public static class ChangeLogHandler extends DefaultHandler {
		private Stack<P4ChangeEntry> objects = new Stack<>();
		private StringBuffer text = new StringBuffer();

		private List<P4ChangeEntry> changeEntries;
		private P4ChangeSet changeSet;
		private Run<?, ?> run;
		private RepositoryBrowser<?> browser;
		private ConnectionHelper p4;

		public ChangeLogHandler(Run<?, ?> run, RepositoryBrowser<?> browser, ConnectionHelper p4) throws P4JavaException {
			this.run = run;
			this.browser = browser;
			this.p4 = p4;

			if (browser == null) {
				try {
					String url = p4.getSwarm();
					if (url != null) {
						this.browser = new SwarmBrowser(url);
					}
				} catch (RequestException re) {
					if (re.getMessage() != null && !re.getMessage().contains("Unknown command")) {
						throw re;
					}
					// else : Ignore, the command is not supported by older P4 versions
				}
			}
		}

		@Override
		public void characters(char[] ch, int start, int length) {
			text.append(ch, start, length);
		}

		@Override
		public void startDocument() {
			changeEntries = new ArrayList<>();
			changeSet = new P4ChangeSet(run, browser, changeEntries);
		}

		@Override
		public void endDocument() {
		}

		@Override
		public void startElement(String uri, String localName, String qName, Attributes attributes) {

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
				P4ChangeEntry entry = objects.peek();
				if (qName.equalsIgnoreCase("file")) {

					// URL decode depot path
					String safePath = attributes.getValue("depot");
					String depotPath = URLDecoder.decode(safePath, StandardCharsets.UTF_8);
					String a = attributes.getValue("action");
					//Replacement of / is already done at this point. No need to call the FileAction.fromString(a);
					FileAction action = FileAction.valueOf(a);
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
			}

			text.setLength(0);
		}

		@Override
		public void endElement(String uri, String localName, String qName) {

			if (qName.equalsIgnoreCase("changelog")) {
				// this is the root, so don't do anything
				return;
			}

			if (qName.equalsIgnoreCase("entry")) {
				P4ChangeEntry entry = objects.pop();
				changeEntries.add(entry);
				return;
			}

			// if we are in the entry element
			if (objects.peek() instanceof P4ChangeEntry) {
				P4ChangeEntry entry = objects.peek();
				try {

					if (!text.toString().trim().isEmpty()
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

						if (qName.equalsIgnoreCase("fileLimit")) {
							entry.setFileLimit(elementText.equals("true"));
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

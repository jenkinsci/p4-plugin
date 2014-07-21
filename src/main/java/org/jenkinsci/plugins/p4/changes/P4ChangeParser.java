package org.jenkinsci.plugins.p4.changes;

import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.scm.ChangeLogParser;
import hudson.scm.ChangeLogSet;
import hudson.scm.ChangeLogSet.Entry;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.jenkinsci.plugins.p4.PerforceScm;
import org.jenkinsci.plugins.p4.client.ClientHelper;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public class P4ChangeParser extends ChangeLogParser {

	/**
	 * Uses the "index.jelly" view to render the changelist details and use the
	 * "digest.jelly" view of to render the summary page.
	 */
	@SuppressWarnings("rawtypes")
	@Override
	public ChangeLogSet<? extends Entry> parse(AbstractBuild build, File file)
			throws IOException, SAXException {
		try {
			SAXParserFactory factory = SAXParserFactory.newInstance();
			SAXParser parser = factory.newSAXParser();
			ChangeLogHandler handler = new ChangeLogHandler(build);
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
		private AbstractBuild<?, ?> build;

		public ChangeLogHandler(AbstractBuild<?, ?> build) {
			this.build = build;
		}

		@Override
		public void characters(char[] ch, int start, int length)
				throws SAXException {
			text.append(ch, start, length);
		}

		@Override
		public void startDocument() throws SAXException {
			changeEntries = new ArrayList<P4ChangeEntry>();
			changeSet = new P4ChangeSet(build, changeEntries);
		}

		@Override
		public void endDocument() throws SAXException {
		}

		@Override
		public void startElement(String uri, String localName, String qName,
				Attributes attributes) throws SAXException {

			text.setLength(0);

			if (qName.equalsIgnoreCase("changelog")) {
				// this is the root, so don't do anything
				return;
			}
			if (qName.equalsIgnoreCase("entry")) {
				objects.push(new P4ChangeEntry(changeSet));
				return;
			}
		}

		@Override
		public void endElement(String uri, String localName, String qName)
				throws SAXException {
			if (qName.equalsIgnoreCase("changelog")) {
				// this is the root, so don't do anything
				return;
			}
			if (qName.equalsIgnoreCase("entry")) {
				P4ChangeEntry entry = (P4ChangeEntry) objects.pop();
				changeEntries.add(entry);
				return;
			}
			if (objects.peek() instanceof P4ChangeEntry) {
				P4ChangeEntry entry = (P4ChangeEntry) objects.peek();
				try {
					// Find Credential ID and Workspace for this build
					AbstractProject<?, ?> project = build.getProject();
					PerforceScm scm = (PerforceScm) project.getScm();
					String credential = scm.getCredential();
					String client = scm.getWorkspace().getFullName();

					// Log in to Perforce and find change-list
					ClientHelper p4 = new ClientHelper(credential, null, client);

					// Add changelist to entry
					if (qName.equalsIgnoreCase("changenumber")) {
						int id = new Integer(text.toString());
						entry.setChange(p4, id);
					}

					// Add label to entry
					if (qName.equalsIgnoreCase("label")) {
						String id = text.toString();
						entry.setLabel(p4, id);
					}

					// disconnect from Perforce
					p4.disconnect();
					return;
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}

		public P4ChangeSet getChangeLogSet() {
			return changeSet;
		}
	}

}

package org.jenkinsci.plugins.p4.heal.verify;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.Serial;
import java.io.Serializable;
import java.io.StringReader;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * The outcome of one test run, read from Surefire/Failsafe XML.
 *
 * <p>Tests are keyed as {@code fully.qualified.ClassName#methodName}. Comparing
 * two of these is how rung 7 of the verify ladder works: a patch is only accepted
 * if it fixes what was broken without breaking anything that previously passed.
 * The baseline comes from the failed build's own report directory, so no extra
 * test run is needed to establish it.
 */
public final class TestResults implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	private static final DocumentBuilderFactory FACTORY = secureFactory();

	private final Map<String, String> failures;
	private final Set<String> passed;
	private final Set<String> skipped;

	private TestResults(Map<String, String> failures, Set<String> passed, Set<String> skipped) {
		this.failures = new LinkedHashMap<>(failures);
		this.passed = new LinkedHashSet<>(passed);
		this.skipped = new LinkedHashSet<>(skipped);
	}

	/**
	 * Parse a single Surefire XML report.
	 *
	 * @param xml report contents
	 * @return the outcomes it records
	 * @throws IOException if the document is malformed, or declares a DOCTYPE
	 */
	public static TestResults parse(String xml) throws IOException {
		Map<String, String> failures = new LinkedHashMap<>();
		Set<String> passed = new LinkedHashSet<>();
		Set<String> skipped = new LinkedHashSet<>();
		read(xml, failures, passed, skipped);
		return new TestResults(failures, passed, skipped);
	}

	/**
	 * Parse and merge several Surefire XML reports, as found in a
	 * {@code surefire-reports} directory.
	 *
	 * @param xmls report contents
	 * @return the combined outcomes
	 * @throws IOException if any document is malformed
	 */
	public static TestResults parseAll(Collection<String> xmls) throws IOException {
		Map<String, String> failures = new LinkedHashMap<>();
		Set<String> passed = new LinkedHashSet<>();
		Set<String> skipped = new LinkedHashSet<>();
		for (String xml : xmls) {
			read(xml, failures, passed, skipped);
		}
		return new TestResults(failures, passed, skipped);
	}

	public Set<String> getFailed() {
		return Collections.unmodifiableSet(failures.keySet());
	}

	/**
	 * Every failure with its detail, keyed by {@code ClassName#methodName}. Saves a
	 * caller that wants both from walking {@link #getFailed()} and calling
	 * {@link #getFailureDetail(String)} per test to rebuild this map.
	 *
	 * @return the failures
	 */
	public Map<String, String> getFailures() {
		return Collections.unmodifiableMap(failures);
	}

	public Set<String> getPassed() {
		return Collections.unmodifiableSet(passed);
	}

	public Set<String> getSkipped() {
		return Collections.unmodifiableSet(skipped);
	}

	/**
	 * The failure message and stack trace recorded for a test, for feeding back to
	 * the model on the next attempt.
	 *
	 * @param testId {@code ClassName#methodName}
	 * @return the detail, or an empty string if the test did not fail
	 */
	public String getFailureDetail(String testId) {
		return failures.getOrDefault(testId, "");
	}

	/**
	 * Tests that fail now but were not failing in the baseline — the regressions a
	 * patch must not introduce. A test absent from the baseline and failing now
	 * counts, so a patch cannot smuggle in a broken new test.
	 *
	 * @param baseline outcomes recorded before the patch was applied
	 * @return newly failing test ids
	 */
	public Set<String> newlyFailingSince(TestResults baseline) {
		Set<String> regressions = new LinkedHashSet<>(failures.keySet());
		regressions.removeAll(baseline.failures.keySet());
		return regressions;
	}

	/**
	 * Baseline failures that still are not passing. A test that vanished from the
	 * report, or that is now skipped, counts as unfixed — only an actual pass
	 * clears it.
	 *
	 * @param baseline outcomes recorded before the patch was applied
	 * @return test ids the patch failed to fix
	 */
	public Set<String> stillFailingSince(TestResults baseline) {
		Set<String> unfixed = new LinkedHashSet<>(baseline.failures.keySet());
		unfixed.removeAll(passed);
		return unfixed;
	}

	private static void read(String xml, Map<String, String> failures,
	                         Set<String> passed, Set<String> skipped) throws IOException {

		Document document = parseSecurely(xml);
		String suiteName = document.getDocumentElement().getAttribute("name");
		NodeList cases = document.getElementsByTagName("testcase");

		for (int i = 0; i < cases.getLength(); i++) {
			Element testCase = (Element) cases.item(i);

			String className = testCase.getAttribute("classname");
			if (className.isEmpty()) {
				className = suiteName;
			}
			String id = className + "#" + testCase.getAttribute("name");

			String failure = detailOf(testCase, "failure");
			if (failure == null) {
				failure = detailOf(testCase, "error");
			}

			if (failure != null) {
				failures.put(id, failure);
				passed.remove(id);
				skipped.remove(id);
			} else if (testCase.getElementsByTagName("skipped").getLength() > 0) {
				skipped.add(id);
			} else {
				passed.add(id);
			}
		}
	}

	private static String detailOf(Element testCase, String tag) {
		NodeList found = testCase.getElementsByTagName(tag);
		if (found.getLength() == 0) {
			return null;
		}
		Element detail = (Element) found.item(0);
		String message = detail.getAttribute("message");
		// getTextContent() concatenates the whole descendant subtree — a long stack
		// trace — so it is walked once rather than once per null check.
		String text = detail.getTextContent();
		String trace = text == null ? "" : text.trim();
		return (message + "\n" + trace).trim();
	}

	/**
	 * Surefire XML is build output, but it is still untrusted input to this parser.
	 * DOCTYPE declarations are rejected outright, which closes off entity-expansion
	 * and external-entity attacks in one step.
	 *
	 * <p>Configured once and shared: {@code newInstance()} performs a provider
	 * lookup, and a suite of any size is parsed one report at a time.
	 */
	private static Document parseSecurely(String xml) throws IOException {
		try {
			DocumentBuilder builder = FACTORY.newDocumentBuilder();
			// The default handler prints to stderr before the exception propagates,
			// which would put parser noise in every build console.
			builder.setErrorHandler(new SilentErrorHandler());
			return builder.parse(new InputSource(new StringReader(xml)));
		} catch (ParserConfigurationException | SAXException e) {
			throw new IOException("Could not read test report: " + e.getMessage(), e);
		}
	}

	private static DocumentBuilderFactory secureFactory() {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		try {
			factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
			factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
		} catch (ParserConfigurationException e) {
			// A parser that cannot be told to refuse DOCTYPEs must not be used at all;
			// this is a broken JAXP setup rather than anything a build can recover from.
			throw new IllegalStateException("XML parser cannot be secured", e);
		}
		factory.setXIncludeAware(false);
		factory.setExpandEntityReferences(false);
		return factory;
	}

	/**
	 * Swallows the parser's own reporting; the thrown exception is the signal.
	 */
	private static final class SilentErrorHandler implements ErrorHandler {

		@Override
		public void warning(SAXParseException e) {
			// A warning is not worth failing or logging over.
		}

		@Override
		public void error(SAXParseException e) throws SAXException {
			throw e;
		}

		@Override
		public void fatalError(SAXParseException e) throws SAXException {
			throw e;
		}
	}
}

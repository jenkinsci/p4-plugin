package org.jenkinsci.plugins.p4.heal.context;

import hudson.console.ConsoleNote;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FailureContextTest {

	private static FailureContext context(String log) {
		return new FailureContext(
				log,
				Map.of("com.example.FooTest#testBar", "expected 1 but was 2"),
				List.of("src/main/java/Foo.java"));
	}

	@Test
	void testExcerptJoinsTheLinesItIsGiven() {
		assertEquals("line one\nline two",
				FailureContext.excerpt(List.of("line one", "line two")));
	}

	@Test
	void testExcerptStripsJenkinsConsoleNotes() {
		String note = ConsoleNote.PREAMBLE_STR + "AAAAxyz" + ConsoleNote.POSTAMBLE_STR;

		String excerpt = FailureContext.excerpt(
				List.of("before", note + "annotated line", "after"));

		assertFalse(excerpt.contains("AAAAxyz"), excerpt);
		assertTrue(excerpt.contains("annotated line"), excerpt);
	}

	@Test
	void testExcerptOfNothingIsEmpty() {
		assertEquals("", FailureContext.excerpt(null));
		assertEquals("", FailureContext.excerpt(List.of()));
	}

	@Test
	void testRedactsPasswordAssignments() {
		assertFalse(FailureContext.redact("password=hunter2").contains("hunter2"));
		assertFalse(FailureContext.redact("PASSWORD: hunter2").contains("hunter2"));
		assertFalse(FailureContext.redact("p4passwd = hunter2").contains("hunter2"));
	}

	@Test
	void testRedactsTokensAndApiKeys() {
		assertFalse(FailureContext.redact("token=abc123def456").contains("abc123def456"));
		assertFalse(FailureContext.redact("api_key: zzz999zzz").contains("zzz999zzz"));
		assertFalse(FailureContext.redact("api-key=zzz999zzz").contains("zzz999zzz"));
		assertFalse(FailureContext.redact("secret=topsecretvalue").contains("topsecretvalue"));
	}

	@Test
	void testRedactsAuthorizationHeader() {
		String redacted = FailureContext.redact("Authorization: Bearer abcdefghijklmnop");

		assertFalse(redacted.contains("abcdefghijklmnop"), redacted);
	}

	@Test
	void testRedactsVendorPrefixedKeys() {
		String redacted = FailureContext.redact("using sk-ant-api03-abcdefghijklmnopqrstuvwxyz now");

		assertFalse(redacted.contains("sk-ant-api03-abcdefghijklmnopqrstuvwxyz"), redacted);
	}

	@Test
	void testRedactsPerforceTicket() {
		String ticket = "A1B2C3D4E5F60718293A4B5C6D7E8F90";

		assertFalse(FailureContext.redact("ticket " + ticket).contains(ticket));
	}

	@Test
	void testLeavesOrdinaryBuildOutputAlone() {
		String log = "[INFO] Compiling 42 source files to target/classes\n"
				+ "[ERROR] Foo.java:[17,9] cannot find symbol";

		assertEquals(log, FailureContext.redact(log));
	}

	@Test
	void testPromptBlockCarriesTheFailureFacts() {
		String block = context("[ERROR] Foo.java:[17,9] cannot find symbol").toPromptBlock();

		assertTrue(block.contains("com.example.FooTest#testBar"), block);
		assertTrue(block.contains("expected 1 but was 2"), block);
		assertTrue(block.contains("src/main/java/Foo.java"), block);
		assertTrue(block.contains("cannot find symbol"), block);
	}

	@Test
	void testPromptBlockRedactsSecretsFromTheLog() {
		String block = context("[INFO] connecting with password=hunter2").toPromptBlock();

		assertFalse(block.contains("hunter2"), block);
	}

	@Test
	void testPromptBlockHandlesNoFailingTests() {
		FailureContext empty = new FailureContext(
				"[ERROR] compilation failure", Map.of(), List.of("src/main/java/Foo.java"));

		String block = empty.toPromptBlock();

		assertTrue(block.contains("compilation failure"), block);
		assertTrue(block.contains("src/main/java/Foo.java"), block);
	}

	@Test
	void testPromptBlockIsStableBetweenCalls() {
		FailureContext ctx = new FailureContext(
				"log",
				Map.of("com.example.BTest#t", "", "com.example.ATest#t", ""),
				List.of("b.java", "a.java"));

		assertEquals(ctx.toPromptBlock(), ctx.toPromptBlock());
		assertTrue(ctx.toPromptBlock().indexOf("ATest") < ctx.toPromptBlock().indexOf("BTest"));
	}

	@Test
	void testAccessorsExposeWhatTheLadderNeeds() {
		FailureContext ctx = context("log");

		assertEquals(Set.of("com.example.FooTest#testBar"), ctx.getFailingTests());
		assertEquals(List.of("src/main/java/Foo.java"), ctx.getChangedFiles());
	}
}

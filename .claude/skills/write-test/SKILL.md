---
name: write-test
description: Write a test for the P4 Jenkins plugin that covers every requirement / acceptance criterion, following the repo's JUnit 5 + JenkinsRule + p4d conventions. Use when asked to write or improve a test, add test coverage, or as the Red step of TDD before implementing a ticket.
---

# Write a Requirement-Complete Test

Produce a test (or extend one) that provably covers **every** acceptance
criterion of the requirement, using this repo's conventions. The goal is a test
that fails for the right reason before the code exists and passes once it does.

## Read first

- `.claude/guidelines/testing.md` — JUnit 5 / JenkinsRule / p4d harness patterns
- `.claude/guidelines/java-style.md` — tabs, imports, naming

## 1. Turn the requirement into a checklist

- List each acceptance criterion / behaviour as a separate, verifiable bullet.
- Include the **negative and edge cases** the requirement implies: null/empty
  input, error paths from p4java, boundary values, "does nothing when…" cases.
- Each bullet must map to at least one assertion. Keep the checklist in scope —
  don't invent requirements the ticket didn't state.

## 2. Choose the test type (smallest that proves the behaviour)

- **Pure unit test** (preferred): no Jenkins, no server. Test one class's logic
  directly; use Mockito to isolate collaborators. Fast and deterministic.
- **Server-backed integration test**: only for genuine end-to-end SCM behaviour
  that needs a real Perforce server. Does not need `p4d` on `PATH` — the
  binary is bundled per-version/per-OS under `src/test/resources/` and
  resolved automatically by `SimpleTestServer`.

## 3. Place and name it

- Mirror the package of the code under test in `src/test/java/.../p4/<area>/`.
- Name `<ClassUnderTest>Test`; methods `void testXxx()` describing the behaviour.
- One behaviour per `@Test` method — don't fold several criteria into one method
  unless they're truly the same assertion.

## 4. Structure each test (Arrange / Act / Assert)

- **Arrange** the minimum fixture. Reuse `DefaultEnvironment` helpers
  (`createCredentials`, `defaultClient`, checkpoint constants like `R24_1_r15`)
  rather than hand-rolling setup.
- **Act** — invoke the behaviour under test once.
- **Assert** — use `org.junit.jupiter.api.Assertions.*`; assert the actual
  outcome, not just "no exception". Prefer specific assertions
  (`assertEquals(expected, actual)`) with a message when the intent isn't obvious.

### Server-backed skeleton

```java
@WithJenkins
class MyFeatureTest extends DefaultEnvironment {

    private static final String P4ROOT = "tmp-MyFeatureTest-p4root"; // unique per class

    private static JenkinsRule jenkins;

    @RegisterExtension
    private final SampleServerExtension p4d = new SampleServerExtension(P4ROOT, R24_1_r15);

    @BeforeAll
    static void beforeAll(JenkinsRule rule) {
        jenkins = rule;
    }

    @BeforeEach
    void beforeEach() throws Exception {
        createCredentials("jenkins", "jenkins", p4d.getRshPort(), CREDENTIAL);
    }

    @Test
    void testRequirementOne() throws Exception {
        // arrange → act → assert
    }
}
```

## 5. Conventions (must match)

- **JUnit 5 (Jupiter)** only — never `org.junit.Test` (JUnit 4).
- Server-backed: `@WithJenkins`, `extends DefaultEnvironment`,
  `@RegisterExtension SampleServerExtension`, and a **unique `P4ROOT`** per class.
- **Tabs** for indentation; no wildcard imports.
- Do not run tests in parallel; don't add `forkCount`.

## 6. Prove it (Red, single test only)

- Run only this test: `mvn test -Dtest=MyFeatureTest`
  (or `-Dtest=MyFeatureTest#testRequirementOne` for one method).
- In TDD it must **fail for the right reason** (an assertion gap, not a
  compile/wiring error). If it passes before any code is written, the test isn't
  actually exercising the new behaviour — fix it.
- If the bundled `p4d` won't run for a server-backed test (e.g. unsupported
  OS/arch), say so explicitly.

## 7. Coverage check before finishing

- Re-read the step-1 checklist: every criterion (including edge/negative cases)
  has a corresponding assertion. List any criterion you intentionally did not
  cover and why.

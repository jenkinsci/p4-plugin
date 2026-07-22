# Testing & TDD

## TDD workflow (default)

1. **Red** — write a failing test that expresses the desired behaviour. Put it in
   the package mirroring the code under test (`src/test/java/.../p4/<area>/`).
2. **Green** — write the *minimum* production code to make it pass.
3. **Refactor** — clean up while keeping tests green. Re-run the test.

Add a regression test before fixing a bug; the test should fail without the fix.

## Frameworks in use

- **JUnit 5 (Jupiter)** — `org.junit.jupiter.api.*`. Do **not** add JUnit 4
  (`org.junit.Test`) tests.
- **JenkinsRule via `@WithJenkins`** — annotate the class with `@WithJenkins` and
  receive `JenkinsRule` as a parameter (injected into `@BeforeAll` / test methods).
- **Mockito** (`mockito-core`, test scope) — available for pure unit tests.
- Assertions: `org.junit.jupiter.api.Assertions.*` (static imports).

## Two kinds of tests here

### Pure unit tests
No server, no Jenkins. Test a single class's logic directly (e.g. parsing,
validation, model objects). Prefer these — they are fast and deterministic. Use
Mockito to isolate collaborators.

### Server-backed integration tests
These exercise real Perforce + Jenkins behaviour. **`p4d` does not need to be on
`PATH`** — `SimpleTestServer` resolves the bundled binary under
`src/test/resources/<version>/bin.<platform>/` by absolute path (picked from
`os.name`). Established pattern (see `review/ReviewImplTest.java`,
`tagging/TaggingTest.java`):

```java
@WithJenkins
class MyFeatureTest extends DefaultEnvironment {

    private static final String P4ROOT = "tmp-MyFeatureTest-p4root";

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
    void testSomething() throws Exception {
        // build a PerforceScm with a Workspace + Populate, run a job, assert
    }
}
```

Key building blocks:
- **`DefaultEnvironment`** — base class with helpers (`createCredentials`,
  `defaultClient`, checkpoint version constants like `R24_1_r15`, log capture).
  Extend it for server-backed tests.
- **`SampleServerExtension`** — JUnit 5 extension that extracts a depot tarball,
  restores a checkpoint, and tears the server down per test. Use a **unique
  `P4ROOT`** per test class to avoid collisions.
- Reuse the credential id constant `CREDENTIAL` and `defaultClient()` helpers
  rather than hand-rolling them.

## Rules

- **Do not run tests in parallel.** Tests share a real p4d server; parallel forks
  were deliberately disabled. Don't re-add `forkCount` to the Jenkinsfile.
- Give each server-backed test class its own `P4ROOT` directory name.
- Clean up is handled by `SampleServerExtension.afterEach` (`destroy()`); don't
  leave servers running.
- When you can express a behaviour as a pure unit test, do — reserve the
  server-backed harness for genuine end-to-end SCM behaviour.

## Running

```bash
mvn test                       # all tests
mvn test -Dtest=MyFeatureTest  # one class
mvn package -DskipTests        # build, skip tests (state this if you do it)
```

If you could not run tests (e.g. the bundled `p4d` won't execute on this
OS/arch), say so explicitly rather than implying they passed.

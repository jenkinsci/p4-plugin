Run the integration test suite for p4-plugin.

Steps:

1. If `$ARGUMENTS` is non-empty, treat it as a test class or method filter and run:
   ```
   mvn test -Dtest=$ARGUMENTS
   ```
   Examples: `/test WorkflowTest` runs the whole class; `/test WorkflowTest#testCleanupClient` runs one method.

2. If `$ARGUMENTS` is empty, run the full suite:
   ```
   mvn test
   ```

3. Parse the output and summarize:
   - Total tests run
   - **PASSED** count
   - **FAILED** count with the full list of failing test class/method names and their error messages
   - **SKIPPED** count and reasons (e.g. p4d binary missing for current OS/arch)

4. For each failure, show the full stack trace from the Surefire output — do not truncate.

5. If any test exits with value **255**, report: "p4d failed to start — the bundled binary for the current OS/arch under `src/test/resources/` may be missing or not executable."

Do not edit any test files in this command. If a test is failing because expected output drifted, surface the failure and let the user decide.

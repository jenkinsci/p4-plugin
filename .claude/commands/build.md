Run a Maven build of the p4-plugin Jenkins plugin.

Steps:

1. If `$ARGUMENTS` is `skip` or `skipTests`, run `mvn package -DskipTests`. Otherwise run the full `mvn package` (which includes integration tests against a real p4d).
2. Before running, check whether a `p4d` binary exists under `src/test/resources/` — if not, warn the user that integration tests will fail and suggest `-DskipTests`.
3. Run the chosen command, stopping on the first non-zero exit.
4. Surface compiler errors and test failures **verbatim** — do not summarize or paraphrase. The user needs exact class names, line numbers, and messages.
5. On success, report only: build succeeded, the produced `target/*.hpi` path, and its size.

Do not modify source files in this command — this is a pure build.

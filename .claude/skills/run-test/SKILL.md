---
name: run-test
description: Run the Maven test cases for the P4 Jenkins plugin. Use when asked to run tests, run a specific test class or method, re-run failing tests, or verify tests pass. This skill only runs tests — it does not write or modify code.
---

# Run Maven Tests

Run the P4 Jenkins plugin's tests with Maven and report the result accurately.
This skill **only runs tests**. Do not write, edit, or fix code here — if a test
fails, report the failure; fixing it is a separate task.

## Default: no arguments → run the full suite

When this skill is invoked **without any argument**, just run:

```bash
mvn clean test
```

Don't ask which tests to run — run the whole suite.

## Choose the command (narrowest scope that answers the request)

When the user names a specific class or method, scope to it. Always run `clean`
before the tests so a stale `target/` never affects results:

```bash
mvn clean test                                   # all tests
mvn clean test -Dtest=ClassName                  # a single test class
mvn clean test -Dtest=ClassName#methodName       # a single test method
mvn clean test -Dtest='ClassA,ClassB'            # several classes
```

- Prefer the **single class or method** form when the user names one — it's far
  faster than the full suite.
- Run from the project root (`pom.xml` directory).

## Rules

- **Always `clean` first** — use `mvn clean test`, never bare `mvn test`.
- **Do not run tests in parallel.** Tests share a real p4d server and are not
  parallel-safe. Never add `-DforkCount`, `-T`, or re-enable parallel forks.
- **Run single-threaded**, as configured. Don't override surefire settings.

## Computing coverage (JaCoCo)

`pom.xml` binds `jacoco-maven-plugin`'s `prepare-agent` and `report` goals to
the `test` phase, so a normal `mvn test`/`mvn clean test` run already
regenerates `target/site/jacoco/jacoco.xml` (and `index.html`) — no extra goal
needed.

The report-level `<counter>` elements at the very end of `jacoco.xml` (right
after the last `</package>`, before the closing `</report>`) hold the
whole-project totals. Extract them:

```bash
REPORT=target/site/jacoco/jacoco.xml
for T in LINE BRANCH METHOD INSTRUCTION; do
  read missed covered < <(grep -o "<counter type=\"$T\"[^/]*/>" "$REPORT" | tail -1 \
    | sed -E 's/.*missed="([0-9]+)".*covered="([0-9]+)".*/\1 \2/')
  awk -v t="$T" -v m="$missed" -v c="$covered" \
    'BEGIN { tot=m+c; printf "%-12s %6.2f%%  (%d/%d)\n", t, (tot? c/tot*100:0), c, tot }'
done
```

- If the run was scoped with `-Dtest=ClassName`, the denominator is still the
  whole project (JaCoCo scans all of `target/classes`) — say so rather than
  implying the coverage is scoped to that class.
- If `target/site/jacoco/jacoco.xml` is missing (e.g. the build failed before
  the report execution ran), report coverage as `N/A` — don't fabricate a
  number.

## Report the result — pass/fail and coverage together

Do not attempt to fix the code under this skill — on failure, report and stop
rather than editing anything. Return a single combined summary in this format:

```
## Test Run

| Suite | Result                             |
|-------|-------------------------------------|
| test  | PASS / FAIL (N failing) / SKIPPED   |

### Failures (if any)
<exact failing test class.method and assertion error from the surefire output>

## Coverage

| Metric      | %     | Covered/Total |
|-------------|-------|---------------|
| Line        | NN.NN | c/t           |
| Branch      | NN.NN | c/t           |
| Method      | NN.NN | c/t           |
| Instruction | NN.NN | c/t           |
```

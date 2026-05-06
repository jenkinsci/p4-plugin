Implement a change described by a pasted Jira ticket or support case.

The ticket description is in `$ARGUMENTS`. If it is empty, ask the user to paste the ticket and stop.

Follow this loop strictly — do not skip steps:

1. **Restate the ask in one sentence.** Show your one-sentence restatement before doing anything else so the user can correct scope drift early.

2. **Classify the change.** Pick one:
   - New populate strategy (new class in `populate/`)
   - New Pipeline DSL step (new class in `workflow/`)
   - Bug fix in sync / checkout / polling logic (`client/`, `tasks/`, `PerforceScm.java`)
   - Credential type change (`credentials/`)
   - Workspace type change (`workspace/`)
   - UI / Jelly change (`src/main/resources/`)
   - Test-only change
   - Build / pom change

3. **Locate the file(s)** using the source map in `CLAUDE.md`. List the exact files you intend to read and edit. Read them before proposing any change. For sync bugs, always read `ClientHelper.syncFiles`, `Validate.check`, and the matching `Populate` subclass together.

4. **Propose the diff in plain English** before editing. For anything that touches:
   - `Validate.check` or `SyncStreamingCallback` (silent error handling)
   - `TagAction` / `syncID` chain (polling and changelog baseline)
   - `Expand.formatID` (variable substitution)
   - `tidyForceSyncImpl` or `tidyWorkspace` (workspace cleanup order)
   - Any `@DataBoundConstructor` / `@DataBoundSetter` signature

   …show the proposed change as a snippet and wait for confirmation before writing.

5. **Implement** the Java change. Match the surrounding file's style (tabs vs spaces, brace placement, blank lines). Prefer `final` fields, guard with early returns, keep P4Java interactions inside `ClientHelper`. Use `jakarta.servlet` (not `javax.servlet`) for any HTTP code.

6. **Add or update an integration test** under `src/test/java/`. Name the new test class `<FeatureOrClass>Test.java` and place it in the sub-package that matches the production code being tested (e.g. a fix in `client/` → test in `org/jenkinsci/plugins/p4/client/`). The test must:
   - Use JUnit Jupiter (`@Test` from `org.junit.jupiter.api`) with `@WithJenkins`
   - Extend `DefaultEnvironment` or use `SampleServerExtension`
   - Pass with the new behavior
   - **Fail without the new behavior** — explicitly note this in your summary

7. **Build and run tests** (call `/build` and `/test <TestClass>`). If this environment does not have a compatible `p4d` binary under `src/test/resources/`, **say so explicitly** — do not claim success based on compilation alone.

8. **Raise a GitHub Pull Request** once the build passes:
   - **Never commit or push directly to `master`.** Always create a new branch first:
     ```bash
     git checkout -b fix/<ticket-id>-<short-description>
     ```
     Example: `git checkout -b fix/P4PLUGIN-1234-bom-fst-text`
   - Stage only the files touched by this ticket (do not `git add -A`):
     ```bash
     git add <file1> <file2> ...
     ```
   - Commit with a message in this format:
     ```
     <one-line summary of the fix>

     Fixes: <ticket ID if provided>
     ```
   - Push the feature branch to origin:
     ```bash
     git push -u origin fix/<ticket-id>-<short-description>
     ```
   - Create the PR targeting `master`:
     ```bash
     gh pr create --base master --title "<ticket ID>: <one-line summary>" --body "..."
     ```
   - PR body must include:
     - **What**: one-sentence description of the change
     - **Why**: the bug or ticket motivation
     - **Test**: name of the test added/updated and what it covers
     - **Build**: outcome of `mvn package` or `mvn package -DskipTests` (state which)
   - Return the PR URL to the user.
   - Do **not** push if the build failed — fix first.
   - Do **not** merge the PR — leave that to the user.

9. **Summarize** at the end:
   - One-sentence restatement (from step 1)
   - Files touched, with line counts changed
   - New or updated test name and what it covers
   - Build / test outcome (PASSED, FAILED, or NOT-RUN with the reason)
   - GitHub PR URL

Reminders:
- This is a Jenkins plugin. Do not propose changes that bypass the Jenkins extension model (descriptors, DataBound, Jelly).
- Do not add new silent swallowing in `Validate` — unknown P4Java messages must be logged or fail the build, not disappear.
- `AutoCleanImpl` always has `have=true` hardcoded — do not assume user-configured `have` applies to the internal cleanup sync in `tidyForceSyncImpl`.
- `syncID` intentionally keeps `NODE_NAME` and `EXECUTOR_NUMBER` as literal strings — this is by design for stability.

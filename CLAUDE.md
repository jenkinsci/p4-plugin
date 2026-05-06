# CLAUDE.md

Guidance for Claude Code when working in this repository. Read this first; it answers "what is this project?", "how do I build and test it?", and "how should I turn a Jira ticket or support case into a code change?".

## What this project is

**p4-plugin** is a **Jenkins SCM plugin** written in Java that integrates Perforce Helix Core Server (`p4d`) with Jenkins. It is packaged as an HPI archive and built with Maven. It supports FreeStyle jobs, Pipeline (Scripted/Declarative), MultiBranch Pipelines, and Helix Swarm review workflows.

- Current version: `1.17.4-SNAPSHOT`
- Jenkins baseline: `2.479.3`
- Primary dependency: `com.perforce:p4java` (`2025.2.2917314`) — the official Perforce Java API
- Java: 11+

This is a **Jenkins plugin**, not a standalone application. All user-facing configuration goes through Jelly UI files and Jenkins descriptor conventions. Do not propose plain Java library refactors that bypass the Jenkins extension model.

## Source map

### Entry point

**`PerforceScm.java`** — the main `hudson.scm.SCM` implementation. All Jenkins job types (FreeStyle, Matrix, Pipeline) flow through this class.

Key methods:
- `compareRemoteRevisionWith` → `pollWorkspace` → dispatches `PollTask`
- `checkout` → dispatches `CheckoutTask`
- `processWorkspaceBeforeDeletion` → dispatches `RemoveClientTask`

### Package responsibilities

| Package | Role |
|---|---|
| `client/` | Low-level Perforce connectivity. `ConnectionHelper` wraps P4Java commands. `ClientHelper` manages workspace (client) operations. `ConnectionFactory` manages connection lifecycle. `Validate` checks P4Java responses. `CleanupNotifier` is a post-build step. |
| `credentials/` | Jenkins credentials types: `P4PasswordImpl` (user+password), `P4TicketImpl` (ticket file), `TrustImpl` (SSL trust). All extend `P4BaseCredentials`. |
| `workspace/` | Workspace (client spec) types: `StaticWorkspaceImpl`, `ManualWorkspaceImpl`, `StreamWorkspaceImpl`, `TemplateWorkspaceImpl`, `SpecWorkspaceImpl`. `Expand` handles variable substitution. `WorkspaceSpec` holds raw client spec fields. |
| `populate/` | Sync strategies extending `Populate`: `AutoCleanImpl` (reconcile + sync), `ForceCleanImpl` (obliterate + sync), `SyncOnlyImpl`, `FlushOnlyImpl`, `CheckOnlyImpl`, `GraphHybridImpl`. |
| `tasks/` | Serializable `Callable` tasks run on Jenkins agents via remoting: `CheckoutTask`, `PollTask`, `PublishTask`, `UnshelveTask`, `TaggingTask`, `RemoveClientTask`. All extend `AbstractTask`. |
| `scm/` | MultiBranch/Pipeline Library sources: `StreamsScmSource`, `BranchesScmSource`, `GraphScmSource`, `SwarmScmSource`, `GlobalLibraryScmSource`. All extend `AbstractP4ScmSource`. |
| `workflow/` | Pipeline DSL steps: `P4Step`, `P4PublishStep`, `P4TaggingStep`, `P4UnshelveStep`, `P4ApproveStep`, `P4SwarmUpdateStep`, `P4CleanupStep`. |
| `changes/` | Changelog model: `P4ChangeEntry`, `P4ChangeSet`, `P4Ref` hierarchy (`P4ChangeRef`, `P4LabelRef`, `P4GraphRef`). `P4ChangeParser` parses XML changelogs. |
| `tagging/` | `TagAction` — stored in each build, holds the synced change number and `syncID`. Used by polling and changelog to determine what changed since the last build. |
| `review/` | Helix Swarm integration: `P4Review`, `ReviewNotifier`, `ApproveNotifier`, `SwarmHelper`. `ReviewProp` defines environment variable names injected by Swarm trigger. |
| `swarmAPI/` | REST client DTOs for Helix Swarm API: `SwarmReviewAPI`, `SwarmProjectAPI`, etc. |
| `trigger/` | `P4Trigger` (SCM polling) and `P4Hook` (webhook endpoint at `/p4/change`) for push-triggered builds. |
| `filters/` | Change filtering: `FilterPerChangeImpl`, `FilterPathImpl`, `FilterUserImpl`, `FilterLatestChangeImpl`, `FilterPatternListImpl`. |
| `publish/` | Post-build publish actions: `SubmitImpl`, `ShelveImpl`, `CommitImpl` (Graph depot). |

### Key files outside `src/main/java`

| Path | Role |
|---|---|
| `src/main/resources/org/jenkinsci/plugins/p4/<Class>/config.jelly` | UI form for each configurable class |
| `src/test/java/org/jenkinsci/plugins/p4/` | Integration tests (JUnit Jupiter + real `p4d`) |
| `src/test/resources/{r15.1,r17.1,r18.1,r19.1,r24.1_r15,r24.1_r17}/` | Bundled `p4d` binaries and depot snapshots per server version |
| `pom.xml` | Maven build; P4Java version is declared here |

## Build

```bash
# Build and run all tests (requires p4d accessible — bundled under src/test/resources)
mvn package

# Build without tests (fastest)
mvn package -DskipTests

# Run a single test class
mvn test -Dtest=WorkflowTest

# Run a single test method
mvn test -Dtest=WorkflowTest#testCleanupClient

# Start a local Jenkins with the plugin installed (hot-reload dev mode)
mvn hpi:run

# Clear stale class files if access-modifier-checker fails with "(Operation not permitted)"
mvn clean package -DskipTests

# Produce the distributable HPI artifact
mvn package -DskipTests   # produces target/p4.hpi
```

## Lint / format / type-check

No dedicated formatter or checkstyle config exists in this repo. Style rule: **match the surrounding file**. The Maven `access-modifier-checker` runs as part of `mvn package` and enforces Jenkins API visibility restrictions — this is the closest thing to a lint step. If it fails with `(Operation not permitted)`, run `mvn clean package -DskipTests` to clear stale class files.

There is no `spotbugs`, `pmd`, or `checkstyle` goal — do not invent or add one without explicit instruction.

## Do not edit without asking

These files must not be edited unless the user explicitly asks:

| File | Reason |
|---|---|
| `pom.xml` | Version bumps and dependency changes must be confirmed |
| `LICENSE` | Legal file — never modify |
| `src/test/resources/*/bin.*` | Bundled `p4d` binaries — do not overwrite |
| Any `*.jelly` file in `src/main/resources/` | UI changes require manual `mvn hpi:run` verification |

## Test

Tests are JUnit Jupiter integration tests that spin up a **real `p4d` process** using bundled binaries.

- `SimpleTestServer` — launches `p4d` via `rsh:` from `src/test/resources/{version}/bin.<os>/`.
- `SampleServerExtension` — `@RegisterExtension` wrapper; restores checkpoint and depot before each test.
- `DefaultEnvironment` — base class with shared credential/workspace/populate defaults.
- All tests use `@Test` (Jupiter) with `@WithJenkins`. Call `jenkins.waitUntilNoActivity()` before `getPage(build)` to avoid 503 races.

**Exit value 255** means `p4d` failed to start — the bundled binary for the current OS/arch is missing or not executable.

When adding a test:
1. Extend `DefaultEnvironment` or reuse `SampleServerExtension`.
2. Use the highest available server version constant (`R24_1_r17`) unless the fix targets a specific version.
3. The test must **pass with the fix** and **fail without it**.
4. Name new test files `<FeatureOrClass>Test.java` (e.g. `WorkflowTest`, `PerforceScmTest`). Place them under `src/test/java/org/jenkinsci/plugins/p4/` in the sub-package that matches the production code being tested.

All tests are integration tests — there is no separate unit test tier. Run `mvn test -Dtest=<ClassName>` for a single class during development; run `mvn package` for full validation before committing.

## Debugging

### Silent errors — the most common trap

`Validate.check()` in `client/Validate.java` runs with `quiet=true` by default. Any P4Java INFO or WARNING response that does not match the hardcoded ignore list is **silently swallowed**; the build still reports `SUCCESS`. Only `ERROR` / `CLIENT_ERROR` status triggers an `AbortException`.

When a build reports success but the workspace is wrong, add temporary logging in `Validate.check()` or `SyncStreamingCallback.handleResult()` to surface the hidden message.

### Tracing a sync problem

1. Set `quiet: false` in the populate config — this passes `-q` omitted, surfacing per-file messages in the Jenkins console.
2. Look for `p4 sync -p`, `p4 sync -k`, `p4 sync -n` in the console to identify which `SyncOptions` flags are active.
3. After the build, run on the agent:
   ```bash
   p4 -u <user> -c <client> -p <server> have //...
   ```
   An empty result with files on disk means `setServerBypass(true)` (`-p`) ran — have list not updated.
4. Reproduce with P4 CLI on the same node to isolate P4Java vs server issues.

### P4Java flag reference (`SyncOptions`)

| P4Java method | p4 flag | Files transferred | Have list updated |
|---|---|---|---|
| `setForceUpdate(true)` | `-f` | Yes (force) | Yes |
| `setNoUpdate(true)` | `-n` | No (preview) | No |
| `setClientBypass(true)` | `-k` | No (flush only) | Yes |
| `setServerBypass(true)` | `-p` | Yes | **No** |

### `ForceClean` + `have=false` known behaviour

`tidyForceSyncImpl` deletes the entire workspace root directory (`rm -rf`) before the final sync. `p4 sync -p` then runs against the deleted path. In most environments P4Java recreates the directory and syncs files. If the workspace is empty after a `forceClean(have: false)` build, the deleted root is the first thing to check — P4Java may not recreate it in all environments (edge server, specific p4d version).

## Cross-platform notes

The plugin runs on Linux, macOS, and Windows agents. The bundled `p4d` binaries under `src/test/resources/{version}/` are OS- and arch-specific:
- `bin.linux26x86_64/` — Linux x86_64
- `bin.darwin90x86_64/` or `bin.darwinarm64/` — macOS Intel / Apple Silicon
- `bin.ntx64/` — Windows x64

If tests exit with **255**, the p4d binary for the current OS/arch is missing or not executable (`chmod +x` may be needed on macOS/Linux after checkout).

Path separator: always use `FilePath` methods for agent-side paths — never concatenate with `/` or `\\` directly. Line ending conversions are handled by `RpcOutputStream`; do not add extra `\r\n` handling.

## Compatibility and stability rules

- **Jenkins API**: Minimum baseline is `2.479.3`. Do not use APIs deprecated before that baseline. Check `@Restricted` annotations before calling internal Jenkins APIs.
- **P4Java**: The version in `pom.xml` is the supported version. If a fix requires a newer P4Java method, update the version and note it in the commit.
- **Servlet API**: The plugin uses `jakarta.servlet` (not `javax.servlet`). Use `StaplerRequest2` / `StaplerResponse2` for any HTTP endpoint work.
- **Pipeline DSL**: When a `@DataBoundConstructor` is no-arg with `@DataBoundSetter` fields, Pipeline scripts must use named arguments. Positional args fail with "have to be explicitly named".
- **`syncID` stability**: `Expand.formatID()` intentionally skips `NODE_NAME`, `EXECUTOR_NUMBER`, and `BUILD_NUMBER` so that the syncID is stable across builds. This is by design — do not "fix" the literal variable names in the syncID.

## Conventions and gotchas

**Jelly UI files**
- Every `<l:layout>` must have a `title` attribute (Jenkins 2.462+); missing title → 503.
- `permission="${it.requiredPermission}"` on `<l:layout>` → 503 if permission is null.
- Checkbox fields: `<f:checkbox/>` inside `<f:entry title="..." field="...">`. The `field` name must match the Java getter (camelCase, without `is`/`get`).
- Symbol icons: `"symbol-NAME plugin-PLUGIN"`. The plugin must be a declared dependency.

**Plugin lifecycle and registration**
Every configurable class follows the Jenkins Descriptor pattern:
- Production class annotated `@DataBoundConstructor` (no-arg or with required fields) plus `@DataBoundSetter` for optional fields.
- Inner `DescriptorImpl extends Descriptor<T>` (or the appropriate Jenkins descriptor base) annotated `@Extension` — this is what registers the extension in Jenkins.
- Jelly UI file at `src/main/resources/org/jenkinsci/plugins/p4/<ClassName>/config.jelly` or `global.jelly`.
- New extension types (populate strategy, workspace type, credential type, Pipeline step) must register via `@Extension` — do not call constructors directly from other extension points.

**Threading / concurrency model**
- The Jenkins **controller thread** must never block on network I/O or file operations against the agent workspace.
- All Perforce operations (sync, reconcile, publish) run inside a `Callable` task dispatched via `FilePath.act()` — this serializes the task and runs it on the agent JVM.
- Do not call `ClientHelper` or `ConnectionHelper` methods from the controller thread outside of a `Callable`.
- `@DataBoundConstructor` / `@DataBoundSetter` run on the controller — they must be lightweight (no I/O).

**Task remoting pattern**
Operations that run on the agent (checkout, sync, publish) are `Callable` tasks in `tasks/`. Dispatch via `FilePath.act()`. Do not make blocking network calls on the controller thread.

**Node comparison after build**
Use `AbstractBuild.getBuiltOnStr()` (persisted) not `run.getExecutor().getOwner()` (unavailable post-build) when comparing which node a build ran on.

**`AutoCleanImpl` always has `have=true`**
The constructor hardcodes `super(true, false, ...)`. It is also used internally by `tidyForceSyncImpl` for the cleanup sync. Do not assume the user-configured `have` value applies to the cleanup step.

**Style**
Match the surrounding file. No formal formatter config exists. Prefer `final` fields, guard with early returns, keep P4Java interactions inside `ClientHelper`.

## Working from a Jira ticket or support case

Follow this loop:

1. **Restate the ask** in one sentence to confirm scope.
2. **Classify**: new populate strategy / new Pipeline step / credential type / bug in sync / UI / test / build change. Locate the right package using the source map.
3. **Read the relevant files** before proposing edits. For sync bugs, always read `ClientHelper.syncFiles`, `Validate.check`, and the matching `Populate` subclass together.
4. **Propose the change** in plain English before editing — especially if it touches `Validate`, `SyncStreamingCallback`, or the `TagAction` / `syncID` chain.
5. **Implement** the Java change.
6. **Add or update a test** in `src/test/java/`. The test must pass with the fix and fail without it.
7. **Build and run tests** (`mvn package` or `mvn test -Dtest=...`). If `p4d` is unavailable in the environment, say so explicitly — do not claim success based on compilation alone.
8. **Summarize**: files touched, test added/updated, build/test outcome.

### Definition of done

A change is done when **all** of these are true:

- [ ] `mvn package` passes (or `mvn package -DskipTests` if p4d is unavailable, stated explicitly)
- [ ] A new or updated integration test exists, **passes with the fix**, and **fails without it**
- [ ] No regressions in related test classes
- [ ] No new silent error swallowing added in `Validate` or `SyncStreamingCallback`
- [ ] Jelly UI change (if any) tested with `mvn hpi:run` — no 503 on load
- [ ] `jakarta.servlet` used (not `javax.servlet`) for any new HTTP code
- [ ] Summary lists files touched, test name, and explicit build/test outcome

## Suggested Claude Code setup

### `.claude/commands/` — project slash commands

| Command | What it runs |
|---|---|
| `/build [skip]` | `mvn package` (or `-DskipTests`); surfaces compiler errors verbatim, reports HPI path on success |
| `/test [Class#method]` | `mvn test -Dtest=...`; summarises PASS/FAIL, full stack traces, flags exit 255 as p4d startup failure |
| `/hpi` | `mvn hpi:run`; starts Jenkins at `http://localhost:8080/jenkins` for manual UI testing |
| `/clean` | `mvn clean package -DskipTests`; full clean rebuild — use when access-modifier-checker fails |
| `/jira <description>` | 8-step loop: restate → classify → locate → propose diff → implement → test → build → summarise |

Each is a markdown file in `aiAgent/.claude/commands/`. `$ARGUMENTS` carries any text after the slash.

### `.claude/settings.json` — permissions and hooks

`permissions.allow`:
- Common read-only: `ls`, `find`, `grep`, `rg`, `wc`, `file`, `head`, `tail`
- Git read: `git status`, `git diff`, `git log`, `git show`, `git blame`
- Build: `mvn package`, `mvn test`, `mvn clean`, `mvn hpi:run`, `mvn dependency:tree`
- Version probes: `mvn -version`, `java -version`
- P4 read: `p4 have`, `p4 sync`, `p4 changes`, `p4 client`

`permissions.deny`:
- `rm -rf`, `git push --force`, `git reset --hard`
- `Write`/`Edit` on `pom.xml` and `LICENSE`

`hooks`:
- `Stop` → terminal bell when a long `mvn package` finishes
- `PreToolUse` on `Edit|Write` → blocks edits to `pom.xml`, `LICENSE`, and `src/test/resources/` binaries with an explanation
- `SessionStart` → prints Java version, Maven version, and available p4d binaries so you know the environment before starting

## Out of scope (today)

- No Atlassian Rovo MCP / `/jira <KEY>` auto-fetch — Jira descriptions are pasted manually.
- No GitHub Actions CI config (`.github/` removed; CI runs externally).
- No code formatter config — match surrounding style.

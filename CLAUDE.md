# CLAUDE.md

Guidance for Claude Code when working in this repository.

## Environment preflight check — run this first

Before doing **any** work in this repo (reading/explaining code is fine),
verify the local toolchain meets these minimums:

```bash
java -version   # must report 17 or higher
mvn -version    # must report 3.9.9 or higher
```

- **Java:** JDK 17+ required — the parent POM (`org.jenkins-ci.plugins:plugin:5.17`)
  sets `maven.compiler.release=17` and enforces `requireJavaVersion: [17,)`.
- **Maven:** 3.9.9+ required — the parent POM enforces `requireMavenVersion: [3.9.6,)`;
  treat 3.9.9 as the floor for this repo.

If `java` or `mvn` is missing, not on `PATH`, or reports a version below these
minimums: **stop immediately**. Do not run builds or tests, do not edit code,
do not work around it (e.g. switching JAVA_HOME without telling the user).
Report exactly what is missing or out of date and ask the user to install or
upgrade it before continuing.

## What this is

The **P4 Plugin** (`p4`) — a Jenkins SCM plugin that integrates Perforce Helix
Core (P4D) with Jenkins. It is a standard Maven `hpi` (Jenkins plugin) project
built on the `org.jenkins-ci.plugins:plugin` parent POM.

- **Language / build:** Java, Maven, packaging `hpi`
- **Java version:** JDK 17 minimum; CI builds on JDK 17 (linux) and JDK 17 (windows)
- **Jenkins baseline:** `2.528.3` (see `pom.xml` `jenkins.baseline`)
- **Root package:** `org.jenkinsci.plugins.p4`
- **Core SCM entry point:** `src/main/java/org/jenkinsci/plugins/p4/PerforceScm.java`
- **Perforce API:** `com.perforce:p4java`

## Source layout

```
src/main/java/org/jenkinsci/plugins/p4/
  PerforceScm.java        # main SCM implementation (entry point)
  client/                 # ConnectionHelper, ClientHelper — P4 connection/workspace ops
  tasks/                  # AbstractTask + remote callable tasks (checkout, poll, unshelve...)
  workspace/              # Workspace types (Manual, Static, Stream, Spec...)
  populate/               # Populate strategies (AutoClean, ForceClean, Sync, Graph...)
  publish/                # Publish actions (Submit, Shelve, Commit...)
  changes/                # Changelist/changeset model (P4Ref, P4ChangeSet...)
  credentials/            # P4 credential types (P4PasswordImpl, P4TicketImpl...)
  browsers/ filters/ trigger/ review/ scm/ groovy/ ...  # feature areas
src/main/resources/.../<ClassName>/*.jelly   # UI views per Describable
src/test/java/org/jenkinsci/plugins/p4/      # tests mirror the main package layout
```

UI for a `Describable` lives in `src/main/resources/<fully/qualified/ClassName>/`
as Jelly views (`config.jelly`, `global.jelly`) plus `help-*.html` and
`Messages.properties`. When you add a configurable field, update its Jelly view.

## Build & test commands

```bash
mvn package                  # compile, run tests, build the .hpi
mvn package -DskipTests      # build without tests (fast)
mvn test                     # tests only
mvn test -Dtest=ClassName    # a single test class
mvn spotbugs:check           # static analysis (gate in CI)
mvn compiler:compile         # compile only
```

**Server-backed tests do not need `p4d` on your `PATH`** — `p4d` binaries are
bundled per-version/per-OS under `src/test/resources/<version>/bin.<platform>/`
and resolved by absolute path (`SimpleTestServer`, based on `os.name`), so
`SampleServerExtension`-based tests spin up a real Perforce server without any
manual install. Tests run single-threaded (do not re-enable parallel forks).

## Working agreements (read before writing code)

These are the standing instructions for this repo. The detailed rules live in
`.claude/guidelines/` — **read the relevant file before you start**:

- **`.claude/guidelines/java-style.md`** — formatting & coding conventions.
  Match them exactly; this repo has specific (and unusual) formatting rules.
- **`.claude/guidelines/testing.md`** — TDD workflow and the JUnit 5 + JenkinsRule
  + p4d test patterns used here.
- **`.claude/guidelines/solid-and-minimal.md`** — SOLID design and "write the
  minimum code" expectations.
- **`.claude/guidelines/code-review.md`** — checklist to apply when reviewing
  (your own or others') changes.

### The short version

1. **TDD.** Write or extend a failing test first, then write the minimum code to
   pass it, then refactor. See `testing.md`.
2. **Minimum code.** Solve the requested problem and nothing more. No speculative
   abstraction, no unrequested features, no dead code.
3. **Follow existing patterns.** Before adding anything, find the nearest
   existing example (a sibling `Workspace`, `Populate`, `Builder`, etc.) and
   mirror its structure, naming, and Jenkins idioms (`@DataBoundConstructor`,
   `@DataBoundSetter`, `@Extension`, `@Symbol`, `DescriptorImpl`).
4. **Follow existing formatting.** Indentation is **tabs**, not spaces. See
   `java-style.md` and `IntelliJCodeStyle.xml`.
5. **SOLID.** Keep classes single-purpose; depend on the existing interfaces
   (`Workspace`, `Populate`, `Publish`, `P4Ref`...) rather than concrete types.
6. **Backward compatibility.** This is a published plugin. Keep deprecated
   constructors/fields (`@Deprecated`) when changing data-bound APIs so existing
   job configs keep deserializing.

## Conventions that are easy to get wrong

- **Tabs for indentation.** The whole codebase uses hard tabs (`SMART_TABS`).
  Do not introduce spaces for indentation.
- **No wildcard imports.** Imports are listed explicitly.
- **Don't run tests in parallel.** A past change explicitly disabled parallel
  forks; tests share a real p4d server and are not parallel-safe.
- **Jenkins serialization.** Fields persisted into job config must remain
  loadable. Use `@DataBoundSetter` for optional new fields rather than adding
  required constructor parameters.

## Do / Don't for the agent

- **Do** confirm `p4d` availability before claiming tests pass; if you ran with
  `-DskipTests`, say so.
- **Do** keep changes scoped — touch the feature area you were asked about.
- **Don't** reformat untouched code, reorder imports wholesale, or "clean up"
  files outside the task.
- **Don't** bump dependency versions or the plugin version unless asked.

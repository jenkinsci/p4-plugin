A# Code Review Checklist

Apply this when reviewing a change (your own before finishing, or someone
else's). Flag issues with `file:line` references and concrete suggestions.

## Correctness

- [ ] Does the change do what was asked — and only that?
- [ ] Edge cases handled (null, empty, error paths from p4java calls)?
- [ ] Resources closed — P4 connections (`ConnectionHelper`/`ClientHelper`),
      streams, files — in `finally`/try-with-resources?
- [ ] Exceptions handled at the right level; not swallowed silently?

## Tests (TDD)

- [ ] Is there a test covering the new behaviour / regression?
- [ ] Does the test fail without the production change?
- [ ] JUnit 5 (Jupiter), not JUnit 4?
- [ ] Server-backed test uses `@WithJenkins`, extends `DefaultEnvironment`,
      `@RegisterExtension SampleServerExtension`, and a **unique `P4ROOT`**?
- [ ] No parallelism reintroduced.

## Design (SOLID & minimal)

- [ ] New behaviour added as an implementation of an existing extension point
      (`Workspace`/`Populate`/`Publish`/`P4Ref`/`Builder`), not a modified switch?
- [ ] Single responsibility per class; IO in `client/`, remote work in `tasks/`?
- [ ] Depends on interfaces, not concrete `*Impl` / `PerforceScm`?
- [ ] No speculative abstraction or unused code; smallest sensible diff?

## Jenkins plugin specifics

- [ ] `@DataBoundConstructor` for required fields; `@DataBoundSetter` for new
      optional fields (so old job configs still load)?
- [ ] Backward compatibility kept — deprecated constructors/fields retained with
      `@Deprecated` when data-bound API changes?
- [ ] Jelly view (`config.jelly`) and `Messages.properties` / `help-*.html`
      updated for new configurable fields?
- [ ] `@Symbol` set for Pipeline usability where relevant?
- [ ] Nullability annotated (`@NonNull` / `@CheckForNull`); `spotbugs:check`
      would pass?

## Style

- [ ] **Tabs** for indentation, no stray spaces (matches surrounding lines)?
- [ ] No wildcard imports; imports not gratuitously reordered?
- [ ] Naming matches conventions (`Impl` suffix, `isXxx()` booleans)?
- [ ] No reformatting of untouched code; diff stays scoped to the task?

## Final

- [ ] `mvn package` (or `mvn test`) run, or explicitly noted as skipped + why?
- [ ] No version bumps / dependency changes that weren't requested?

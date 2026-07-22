# SOLID Design & Minimal Code

## Write the minimum code

- Implement exactly what was asked — no more. No speculative generality, no
  "while I'm here" features, no configuration knobs nobody requested.
- Prefer extending an existing class/interface over creating a new hierarchy.
- Delete dead code you introduce; don't leave commented-out blocks.
- The smallest correct diff that follows existing patterns is the goal.

## SOLID, applied to this codebase

This plugin already models its domain with clean abstractions. Honour them
rather than inventing parallel ones.

- **S — Single Responsibility.** Each class does one thing. Connection/IO lives
  in `client/` (`ConnectionHelper`, `ClientHelper`); remote work is a `tasks/`
  callable; UI config is the `Describable` + its Jelly view. Don't mix these.
- **O — Open/Closed.** New behaviour is a *new implementation* of an existing
  extension point, not a modification of a switch statement. To add a workspace
  type, implement `Workspace`; a sync strategy, extend `Populate`; a publish
  action, implement `Publish`; a changelist source, implement `P4Ref`.
- **L — Liskov Substitution.** A new `Workspace`/`Populate`/`Publish` must behave
  correctly anywhere the interface is used. Don't throw `UnsupportedOperation`
  from methods the contract requires.
- **I — Interface Segregation.** Depend on the narrow interface you need
  (`Workspace`, `Populate`, `Publish`, `P4Ref`), not on `PerforceScm` or concrete
  `*Impl` types.
- **D — Dependency Inversion.** Code against the interfaces above. Jenkins wires
  concrete implementations via `@Extension`/`DescriptorImpl`; let it.

## Key extension points (find the sibling, copy the pattern)

| To add a…                | Implement / extend        | Look at (example)                 |
|--------------------------|---------------------------|-----------------------------------|
| Workspace type           | `workspace.Workspace`     | `ManualWorkspaceImpl`             |
| Sync / populate strategy | `populate.Populate`       | `AutoCleanImpl`, `ForceCleanImpl` |
| Publish action           | `publish.Publish`         | `SubmitImpl`, `ShelveImpl`        |
| Build step               | `hudson.tasks.Builder`    | `unshelve.UnshelveBuilder`        |
| Remote/agent-side work   | `tasks.AbstractTask`      | tasks under `tasks/`              |
| Credential type          | P4 credential base        | `credentials.P4PasswordImpl`      |

Each is a `Describable` with a nested `@Extension DescriptorImpl` and a Jelly
view under `src/main/resources/.../<ClassName>/config.jelly`.

## Before adding an abstraction, ask

1. Does an interface/base class for this already exist? (Usually yes — use it.)
2. Is there exactly one caller? Then inline it; don't extract an interface for one
   implementation.
3. Will keeping it concrete break a test or a contract? If not, keep it concrete.

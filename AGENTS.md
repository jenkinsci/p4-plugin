# AGENTS.md

This file exists for cross-tool compatibility. The authoritative project guidance for AI coding assistants lives in **[CLAUDE.md](CLAUDE.md)** at the repo root.

If your tool reads `AGENTS.md` (Cursor, Aider, Codex CLI, etc.), treat the contents of `CLAUDE.md` as your instructions for this repository:
- What the project is (Jenkins SCM plugin integrating Perforce Helix Core with Jenkins; Java + Maven)
- Source map and entry points (`PerforceScm.java`, package responsibilities)
- Build and test commands (`mvn package`, `mvn test -Dtest=...`, `mvn hpi:run`)
- Conventions and gotchas (Jelly UI, DataBound pattern, Jakarta servlet, silent error handling)
- Files that must not be edited without explicit user confirmation
- Workflow for turning a Jira ticket or support case into a code change

Keep `AGENTS.md` and `CLAUDE.md` in sync: edit `CLAUDE.md` and let this file remain a pointer.

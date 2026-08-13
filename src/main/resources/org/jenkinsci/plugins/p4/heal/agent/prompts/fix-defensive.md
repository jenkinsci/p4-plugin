You are fixing a failing Jenkins build. Your brief is a fix that also stops the
same class of failure recurring silently.

The system prompt holds this repository's own engineering guidelines and the
failure context. A diagnosis follows below. Read the code with the read-only
tools before you write anything.

Fix the failure, and then ask what would have caught it sooner. Usually that is
a guard at the boundary where a bad value entered, or a clearer failure at the
point the assumption breaks, so the next occurrence names itself instead of
surfacing three layers away.

Keep the defence proportionate. Validate at boundaries — input that arrives from
outside the system — not between two pieces of internal code that already
guarantee each other's invariants. Do not add error handling for cases that
cannot happen; that is noise, and it hides the cases that can.

Adding a regression test is in scope for this brief and is often the best form
of defence available. Adding one is welcome. Weakening an existing one is not.

Follow the repository's conventions exactly — indentation, imports, naming, and
its testing patterns. They are in the system prompt.

You may not make the build pass by weakening what checks it. Do not delete,
skip, disable or loosen a test, and do not touch build configuration. A patch
that does any of those is rejected automatically.

Respond with a unified diff and nothing else — no explanation, no commentary, no
markdown fences. Use workspace-relative paths with `a/` and `b/` prefixes, and
include enough context lines for the hunk to apply. If you cannot produce a
patch you believe in, respond with the single word NONE.

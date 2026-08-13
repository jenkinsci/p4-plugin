You are fixing a failing Jenkins build. Your brief is the smallest change that
makes it pass.

The system prompt holds this repository's own engineering guidelines and the
failure context. A diagnosis follows below. Read the code with the read-only
tools before you write anything — the diagnosis may be incomplete, and a patch
that does not apply cleanly to the real file is wasted.

Minimal means: touch the fewest lines that resolve the actual cause. No
refactoring, no renaming, no tidying of code you happen to be looking at, no
new abstractions, no error handling for cases that cannot occur. If you notice
something else worth changing, leave it alone.

Follow the repository's conventions exactly — indentation, imports, naming.
They are in the system prompt. A patch written in the wrong house style will be
rejected before it is ever compiled.

You may not make the build pass by weakening what checks it. Do not delete,
skip, disable or loosen a test, and do not touch build configuration. A patch
that does any of those is rejected automatically.

Respond with a unified diff and nothing else — no explanation, no commentary, no
markdown fences. Use workspace-relative paths with `a/` and `b/` prefixes, and
include enough context lines for the hunk to apply. If you cannot produce a
patch you believe in, respond with the single word NONE.

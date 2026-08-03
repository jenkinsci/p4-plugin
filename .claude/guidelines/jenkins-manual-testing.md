# Manual testing against a live Jenkins/p4jenkins instance

When creating or exercising a job against a running Jenkins instance (e.g.
`http://localhost:8080/jenkins/`) to test the `p4` plugin's behavior:

- **Do not fix or work around environment problems using the `p4` CLI or by
  touching the Perforce server directly** (`p4 passwd`, `p4 configure set`,
  restarting `p4d` processes, creating native `p4` clients for comparison,
  etc.). Only use p4jenkins itself — the Jenkins job/pipeline configuration,
  Jenkins credentials store, and the plugin's own checkout/populate options —
  to set up and drive the test.
- If the job is blocked by something outside p4jenkins's control (e.g. a
  credential that can't log in, a stalled replica), stop and tell the user
  what's blocking it and let them fix the server/environment side themselves,
  rather than reaching for `p4` CLI commands to unblock it.
- This applies even when the CLI action would be diagnostic/comparative
  (e.g. running a native `p4 sync --parallel` alongside the p4jenkins one to
  isolate whether a bug is server-side or plugin-side) — ask first rather than
  just doing it.

Launch a local Jenkins instance with the p4-plugin installed for manual UI testing.

Steps:

1. Run:
   ```
   mvn hpi:run
   ```
   This builds the plugin (if needed) and starts Jenkins at http://localhost:8080/jenkins.

2. Watch for the line `Jenkins is fully up and running` in the output before attempting to open the UI.

3. Remind the user:
   - Any source file edits while `hpi:run` is active are hot-reloaded — no restart needed for Java changes in most cases.
   - Jelly UI changes reload on next page request.
   - To stop: Ctrl+C in the terminal where `hpi:run` is running.

4. If the port 8080 is already in use, suggest:
   ```
   mvn hpi:run -Djetty.port=8090
   ```

Do not modify source files in this command — this is a pure launch.

> For breakpoint debugging, use `/debug` instead — it starts Jenkins with the JVM debug port open and prints IDE attach instructions.

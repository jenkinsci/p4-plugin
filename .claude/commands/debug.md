Launch a local Jenkins instance in remote debug mode so an IDE can attach with breakpoints.

Steps:

1. If `$ARGUMENTS` is `suspend` or `suspend=y`, start with Jenkins paused until the IDE attaches:
   ```bash
   MAVEN_OPTS="-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=8000" mvn hpi:run
   ```
   Use this when you want to catch something that happens at Jenkins startup.

2. Otherwise (default), start with Jenkins running immediately and the debug port open:
   ```bash
   MAVEN_OPTS="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=8000" mvn hpi:run
   ```

3. Confirm the debug port is ready by watching for this line in the output:
   ```
   Listening for transport dt_socket at address: 8000
   ```
   Then wait for `Jenkins is fully up and running` before opening the browser at http://localhost:8080/jenkins.

4. Tell the user how to attach their IDE:

   **IntelliJ IDEA:**
   - Run → Edit Configurations → + → Remote JVM Debug
   - Host: `localhost`, Port: `8000` → click Debug

   **VS Code:**
   - Add to `.vscode/launch.json`:
     ```json
     {
         "type": "java",
         "request": "attach",
         "name": "Attach to Jenkins",
         "hostName": "localhost",
         "port": 8000
     }
     ```
   - Press F5 to attach.

5. If port 8080 is already in use, rerun with a different port:
   ```bash
   MAVEN_OPTS="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=8000" mvn hpi:run -Djetty.port=8090
   ```

6. Remind the user of the most useful breakpoint locations:
   - `ClientHelper.syncFiles()` — inspect SyncOptions flags on every sync
   - `Validate.check()` — see all P4Java responses including silently swallowed ones
   - `PerforceScm.checkout()` — entry point for every build
   - `AutoCleanImpl.populate()` — AutoClean sync sequence
   - `SyncStreamingCallback.handleResult()` — per-file sync results

To stop: Ctrl+C in the terminal where the process is running.

Do not modify source files in this command — this is a pure launch.

Run a full clean build of the p4-plugin, discarding all previously compiled artifacts.

Steps:

1. Run:
   ```
   mvn clean package -DskipTests
   ```
   This deletes `target/`, recompiles everything from scratch, and produces `target/p4.hpi`.

2. Use this command when:
   - The `access-modifier-checker` fails with `(Operation not permitted)` — a stale `.class` file is the usual cause.
   - A dependency version was changed in `pom.xml`.
   - A Jelly resource isn't being picked up after a rename.

3. Surface all compiler errors verbatim — do not summarize or paraphrase.

4. On success, report: build succeeded, the `target/p4.hpi` path, and its file size.

Do not modify source files in this command — this is a pure clean build.

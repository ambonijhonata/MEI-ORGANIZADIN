# API Instructions

## Text Encoding

Always read and write project files as UTF-8. Preserve valid accented characters and never rewrite UTF-8 text through Windows-1252, Latin-1, or the PowerShell default ANSI encoding.

## Development Validation Pipeline

Whenever you modify Java code, Maven configuration, tests, or API behavior in this project, run the complete validation pipeline below from this `API` directory before reporting the work as complete.

### Gate 1: Maven Verification

1. Run `mvn verify`.
2. If compilation, tests, PMD, CPD, or another Maven verification step fails, read the failing output and fix the underlying issue when it is within scope.
3. Do not proceed to runtime validation until `mvn verify` succeeds.

### Gate 2: Dev Runtime Validation

After `mvn verify` succeeds:

1. Run `mvn spring-boot:run -Dspring-boot.run.profiles=dev` from this `API` directory.
   - In PowerShell, quote the system-property argument so the shell passes it to Maven unchanged: `mvn spring-boot:run "-Dspring-boot.run.profiles=dev"`.
2. Within a bounded readiness window, confirm all of the following:
   - The effective Spring profile is `dev`.
   - The application reports successful Spring Boot startup.
   - The process remains stable during initial observation without a fatal startup error or unexpected exit.
   - `GET /healthz`, using the address and port effective for the `dev` profile, responds with HTTP 200 and boolean body `true`.
3. Treat startup failure, an unexpected process exit, a fatal error, readiness timeout, or an unhealthy `/healthz` response as a failed gate that must be investigated.
4. After the runtime gate succeeds or fails, stop the Maven and Spring Boot process tree created for this validation. Confirm that the validation instance is no longer running and has released its port. Never terminate an unrelated process.

### Restart After Corrections

If a failure in either gate causes you to change any project file, all previous gate results are invalid. Restart the complete pipeline from Gate 1 (`mvn verify`), even when the correction was made for a runtime failure. Repeat this fix-and-restart cycle until both gates succeed.

If the pipeline remains blocked after focused investigation by an unrelated, pre-existing, or external condition such as an unavailable database, missing required secret, unavailable container runtime, or port occupied by a process that cannot safely be stopped, report the failed gate and exact blocker. Do not describe the work as fully or cleanly validated.

## Publish After Green Pipeline

After both validation gates succeed with no errors:

1. Check `git status` and ensure only intended files are staged/committed (avoid bundling unrelated changes).
2. Create a commit with a clear message describing the completed work.
3. Push the commit to the configured remote (e.g., `git push`).

Do not claim the work is complete until the commit is pushed successfully, unless the user explicitly asks you not to push.

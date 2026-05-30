# API Instructions

## Text Encoding

Always read and write project files as UTF-8. Preserve valid accented characters and never rewrite UTF-8 text through Windows-1252, Latin-1, or the PowerShell default ANSI encoding.

## Verification Loop

Whenever you modify Java code, Maven configuration, tests, or API behavior in this project, run `mvn verify` from this `API` directory before reporting the work as complete.

If `mvn verify` fails because of compilation, tests, PMD, CPD, or other Maven verification errors:

1. Read the failing output.
2. Fix the underlying issue.
3. Run `mvn verify` again.
4. Repeat until `mvn verify` succeeds.

If verification remains blocked by an unrelated or pre-existing issue after focused attempts, report the exact blocker and do not describe the task as cleanly verified.

## Publish After Green Build

After `mvn verify` succeeds with no errors:

1. Check `git status` and ensure only intended files are staged/committed (avoid bundling unrelated changes).
2. Create a commit with a clear message describing the completed work.
3. Push the commit to the configured remote (e.g., `git push`).

Do not claim the work is complete until the commit is pushed successfully, unless the user explicitly asks you not to push.

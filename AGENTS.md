# API Instructions

## Verification Loop

Whenever you modify Java code, Maven configuration, tests, or API behavior in this project, run `mvn verify` from this `API` directory before reporting the work as complete.

If `mvn verify` fails because of compilation, tests, PMD, CPD, or other Maven verification errors:

1. Read the failing output.
2. Fix the underlying issue.
3. Run `mvn verify` again.
4. Repeat until `mvn verify` succeeds.

If verification remains blocked by an unrelated or pre-existing issue after focused attempts, report the exact blocker and do not describe the task as cleanly verified.

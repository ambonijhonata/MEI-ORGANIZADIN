# API Instructions

## Text Encoding

Always read and write project files as UTF-8. Preserve valid accented characters and never rewrite UTF-8 text through Windows-1252, Latin-1, or the PowerShell default ANSI encoding.

## PR-Driven Validation Workflow

Whenever you modify Java code, Maven configuration, tests, workflow files, or API behavior in this project, use the pull request state for the latest branch revision and the required GitHub jobs attached to that latest PR head SHA as the primary validation source. Treat local commands as optional diagnostics unless the user explicitly asks for local-only validation.

### Step 1: Prepare a valid branch revision

1. Work on a non-`master` branch named `feat/<descricao>` or `fix/<descricao>`.
2. Ensure the head commit contains non-empty lines in this format:
   - `Objetivo: <descricao em alto nivel>`
   - `Como: <descricao da implementacao>`
3. Check `git status` and keep only intended files in scope before each commit.

### Step 2: Push and open or reuse the PR

1. Push the branch to the configured remote.
2. Use the repository automation or GitHub state to confirm that exactly one PR targeting `master` exists for the branch.
3. Do not require `mvn verify` to pass locally before pushing when the goal is normal API validation through the repository workflow.

### Step 3: Wait for required PR jobs on the latest head SHA

1. Identify the latest PR head SHA after each push.
2. Wait for the required jobs attached to that SHA, including `Maven Verify`, to finish, even when one of those jobs was triggered by the initial branch push that opened the PR.
3. Do not treat older green jobs from previous SHAs as valid for the current revision.
4. A PR is only considered validated when all required jobs for the latest head SHA are green.

### Step 4: Classify the dominant failure before changing code

If a required PR job fails, classify the dominant failure as exactly one of:

- `build`: compilation, packaging, dependency, or similar Maven build failure
- `test`: failing automated tests
- `pmd`: PMD rule violation
- `cpd`: CPD duplication violation
- `workflow/config`: broken workflow logic, permissions, or CI configuration
- `external/transient`: runner instability, permission outage, GitHub API/rate limit issue, missing secret outside scope, or another non-code external blocker

Record the dominant evidence before acting, such as the failing test name, compile message, PMD rule and location, CPD block and files, or workflow step name.

### Step 5: Apply only safe automatic corrections

You may automatically apply and push a correction only when it is non-functional, meaning it does not reasonably appear to change observable behavior, API contract, business rules, filtering, ordering, persistence semantics, or other functional outcomes.

Examples that are usually non-functional:

- PMD cleanup that preserves behavior
- CPD refactoring that preserves behavior
- mechanical test fix aligned with existing intent
- workflow/config repair without product behavior change

### Step 6: Pause for approval on any potentially functional correction

Before changing code, tests, queries, mappings, API responses, persistence behavior, business rules, ordering, or filtering in a way that may alter functional behavior, pause and ask the user for approval.

If there is reasonable doubt about whether a correction is functional, treat it as functional and ask first.

### Step 7: Enforce bounded retry rules

1. After the PR is opened, you may make at most 3 corrective pushes for the same PR. The initial push that created or updated the PR does not count toward this limit.
2. If the same failure signature appears in 2 consecutive failed PR revisions after a correction attempt, stop the automatic loop and report lack of progress.
3. For an `external/transient` failure, you may rerun required jobs at most once without changing code. If the rerun fails again with the same transient classification, stop and report the external blocker.
4. Do not continue pushing speculative fixes after a limit or blocker is reached.

### Step 8: Repeat until green or explicitly blocked

After each approved or non-functional correction:

1. Commit the change with updated `Objetivo` and `Como` metadata when needed.
2. Push the branch.
3. Re-evaluate the latest PR head SHA.
4. Wait again for the required PR jobs for that SHA.

Repeat this loop until one of the following happens:

- all required jobs for the latest PR head SHA are green
- the automatic attempt limit is reached
- the same failure repeats without progress
- an external blocker persists after the allowed rerun
- a potentially functional correction requires user approval

### Step 9: Report completion or the exact blocker

Only describe the work as fully validated when the latest PR head SHA is green on all required jobs. If validation stops early, report the exact blocker, the latest failing job or evidence, and whether the stop was caused by the attempt limit, repeated no-progress failure, external blocker, or pending user approval.

## Publish After Green PR

After the latest PR head SHA is green on all required jobs:

1. Confirm the branch and PR reflect only the intended work.
2. Push any final metadata-only correction still required for the PR flow.
3. Do not claim the work is complete until the green PR state is confirmed, unless the user explicitly asks for a partial handoff.

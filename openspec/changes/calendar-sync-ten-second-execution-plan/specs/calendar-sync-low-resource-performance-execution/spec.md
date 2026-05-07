## ADDED Requirements

### Requirement: Objective Phase-Gated Execution Plan
The system SHALL execute calendar sync performance work through explicit phases with measurable acceptance criteria before advancing.

#### Scenario: Phase completion requires objective gates
- **WHEN** a performance phase is declared complete
- **THEN** the team MUST record phase metrics (`google_fetch_ms`, `db_lookup_ms`, `processing_ms`, `db_write_ms`, `sync_total_ms`) and compare against prior baseline

#### Scenario: Next phase is blocked without evidence
- **WHEN** required metrics or regression checks are missing
- **THEN** the next implementation phase MUST NOT be marked complete

### Requirement: Low-Resource Runtime Constraints Must Be First-Class
The sync optimization flow SHALL be validated under low-resource constraints equivalent to `512MB RAM` and `0.1 CPU`.

#### Scenario: Validation environment is explicit
- **WHEN** performance validation is executed
- **THEN** the recorded report MUST include CPU and memory limits used during the run

#### Scenario: Memory safety is preserved during high-volume sync
- **WHEN** sync processes the high-volume dataset
- **THEN** the implementation MUST avoid full in-memory accumulation patterns that exceed the constrained runtime envelope

### Requirement: Performance Target Verification for Reference Dataset
The system SHALL verify runtime target compliance for the reference high-volume dataset before finalizing rollout.

#### Scenario: Reference dataset target verification
- **WHEN** `POST /api/calendar/sync` runs with the 14,482-event reference account in the validated environment
- **THEN** `sync_total_ms` MUST be measured and reported against the `<= 10000` target

#### Scenario: Target miss triggers mitigation path
- **WHEN** measured runtime exceeds the target
- **THEN** the execution plan MUST require a documented mitigation path (next optimization step or approved operational limit)

### Requirement: Rollback Readiness by Configuration
The optimization rollout SHALL support rollback via configuration without API contract changes.

#### Scenario: Safe rollback execution
- **WHEN** optimization behavior needs rollback
- **THEN** operators MUST be able to disable tuning knobs and restore previous behavior without changing endpoint contracts

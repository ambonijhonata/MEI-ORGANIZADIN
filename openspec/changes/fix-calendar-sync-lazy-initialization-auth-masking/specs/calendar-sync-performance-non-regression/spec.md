## ADDED Requirements

### Requirement: Reliability Fixes Must Preserve Sync Performance Validation
The system SHALL validate that lazy-safety and error-surface corrections do not introduce unacceptable sync performance regression.

#### Scenario: Stage timings remain measurable after reliability fix
- **WHEN** sync executes after applying reliability corrections
- **THEN** the system MUST continue emitting `google_fetch_ms`, `db_lookup_ms`, `processing_ms`, `db_write_ms`, and `sync_total_ms`

### Requirement: High-Volume Runtime Must Stay Within Accepted Operational Envelope
The system SHALL keep high-volume sync runtime within accepted operational limits after the correction rollout.

#### Scenario: Post-fix benchmark is compared against prior baseline
- **WHEN** benchmark runs on the reference high-volume dataset
- **THEN** reported `sync_total_ms` and stage breakdown MUST be compared with prior baseline and any regression MUST be documented with mitigation

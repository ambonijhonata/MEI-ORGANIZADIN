## ADDED Requirements

### Requirement: Sync Processing Must Be Lazy-Safe
The system SHALL execute calendar sync processing without dereferencing lazy proxies outside an active persistence context.

#### Scenario: Existing association comparison does not trigger lazy proxy failure
- **WHEN** sync evaluates service association changes for existing events
- **THEN** the implementation MUST avoid lazy-only property access that can throw `LazyInitializationException`

### Requirement: Sync State Must Not Remain Stuck In SYNCING On Internal Failure
The system SHALL finalize sync state consistently when an internal runtime failure occurs.

#### Scenario: Runtime failure marks sync as failed
- **WHEN** a runtime error occurs during sync processing
- **THEN** the sync state MUST transition from `SYNCING` to `FAILED` with persisted error category/message

### Requirement: Functional Sync Semantics Must Be Preserved
The system SHALL preserve existing sync functional behavior after lazy-safe corrections.

#### Scenario: Existing success semantics remain unchanged
- **WHEN** sync completes successfully after the correction
- **THEN** response payload semantics for `created`, `updated`, and `deleted` MUST remain unchanged

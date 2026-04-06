## ADDED Requirements

### Requirement: Sync Performance Target for High-Volume Accounts
The system SHALL synchronize high-volume calendar datasets with end-to-end runtime target of at most 10 seconds for the reference scenario of 14,354 events in the validated environment.

#### Scenario: Reference high-volume sync meets target
- **WHEN** `POST /api/calendar/sync` runs against the reference dataset of 14,354 events under the agreed validation environment
- **THEN** total sync runtime MUST be less than or equal to 10 seconds

#### Scenario: Stage timings are available for target verification
- **WHEN** a sync execution completes
- **THEN** the system MUST emit stage-level timings (`google_fetch_ms`, `db_lookup_ms`, `processing_ms`, `db_write_ms`, `sync_total_ms`) for performance diagnosis

### Requirement: Configuration Tuning Must Preserve Data Completeness
The system SHALL allow performance-tuning configuration changes without reducing data coverage or changing functional sync semantics.

#### Scenario: Increased Google page size preserves completeness
- **WHEN** `GOOGLE_CALENDAR_SYNC_MAX_RESULTS` is increased for performance
- **THEN** pagination MUST continue until completion and all pages MUST be processed without dropping events

#### Scenario: JDBC/Batch tuning preserves persistence semantics
- **WHEN** batch and JDBC optimization settings are enabled (`CALENDAR_SYNC_BATCH_SIZE`, `CALENDAR_SYNC_BATCH_CLEAR_ENABLED`, `reWriteBatchedInserts`)
- **THEN** persisted sync results MUST remain functionally equivalent to baseline behavior

### Requirement: Full Sync Lookup Must Avoid Large IN Query Degradation
The system SHALL avoid full-sync lookup strategies that rely on massive `IN` lists for high-volume datasets.

#### Scenario: Full sync uses preload strategy
- **WHEN** a full sync is executed for a user with high event volume
- **THEN** local event lookup MUST use user-scoped preload mapping by `googleEventId` instead of a giant `IN` lookup as primary strategy

### Requirement: CPU Hot Path Optimization Must Preserve Semantics
The system SHALL optimize normalization and title parsing hot paths while preserving current business parsing/matching behavior.

#### Scenario: Normalization cache preserves normalized output
- **WHEN** identical raw strings are normalized repeatedly in one sync execution
- **THEN** cached normalization MUST produce the same normalized value as baseline normalization logic

#### Scenario: Parser optimization preserves parsing semantics
- **WHEN** event titles are parsed in optimized path
- **THEN** parsed client and service tokens MUST remain functionally equivalent to baseline parsing rules

### Requirement: No-Op Events Must Not Trigger Unnecessary Writes
The system SHALL skip persistence and service-association rewrites when no effective event data change exists.

#### Scenario: Unchanged event is not rewritten
- **WHEN** incoming event data is semantically identical to local persisted event state
- **THEN** the sync flow MUST skip unnecessary persistence for that event

#### Scenario: Existing equivalent service association is preserved
- **WHEN** matched service set is equivalent to current stored association
- **THEN** the sync flow MUST avoid recomputing and rewriting unchanged service associations

### Requirement: API Contract and Sync Semantics Must Remain Stable
The system SHALL preserve existing endpoint behavior while implementing the 10-second performance optimizations.

#### Scenario: Success contract remains unchanged
- **WHEN** sync completes after optimization changes
- **THEN** API response MUST remain HTTP `200` with unchanged payload semantics for `created`, `updated`, and `deleted`

#### Scenario: Error and fallback semantics remain unchanged
- **WHEN** sync encounters existing error/fallback conditions
- **THEN** existing error mappings (`INTEGRATION_REVOKED`, `GOOGLE_API_FORBIDDEN`) and token-expired full-resync fallback MUST remain unchanged

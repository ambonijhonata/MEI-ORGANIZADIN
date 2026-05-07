## ADDED Requirements

### Requirement: Authenticated Internal Failures Must Not Be Surfaced As Generic UNAUTHORIZED
The system SHALL avoid returning generic `UNAUTHORIZED` for authenticated requests that fail due to internal sync processing errors.

#### Scenario: Internal sync error preserves authenticated context
- **WHEN** an authenticated `POST /api/calendar/sync` request fails due to internal processing error
- **THEN** response handling MUST surface an internal sync failure classification rather than generic `UNAUTHORIZED`

### Requirement: Security Error Dispatch Must Preserve Diagnostic Integrity
The security filter chain SHALL not mask application-layer sync exceptions as missing-auth errors during error dispatch.

#### Scenario: Error dispatch does not rewrite internal failure as auth failure
- **WHEN** an exception from sync processing reaches error dispatch path
- **THEN** security handling MUST preserve non-auth error semantics for authenticated requests

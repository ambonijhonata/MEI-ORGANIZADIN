## ADDED Requirements

### Requirement: Revenue report generation
The system SHALL generate a revenue (faturamento) report that consolidates the total financial value from eligible events within a given period.

#### Scenario: Successful revenue report
- **WHEN** an authenticated user requests a revenue report for a valid period
- **THEN** the system SHALL sum the historical snapshot values of all eligible events within that period and return the consolidated total

#### Scenario: Revenue report with service breakdown
- **WHEN** a revenue report is generated
- **THEN** the system SHALL optionally provide breakdown by service showing count and subtotal per service

### Requirement: Revenue report period limit
The system SHALL reject revenue report requests where the period exceeds 12 months.

#### Scenario: Period within limit
- **WHEN** an authenticated user requests a revenue report for a period of 12 months or less
- **THEN** the system SHALL process the request normally

#### Scenario: Period exceeds limit
- **WHEN** an authenticated user requests a revenue report for a period exceeding 12 months
- **THEN** the system SHALL reject the request with a validation error

### Requirement: Cash flow report generation
The system SHALL generate a cash flow (fluxo de caixa) report that distributes the financial values of eligible events chronologically within a given period, grouped by time unit (day).

#### Scenario: Successful cash flow report
- **WHEN** an authenticated user requests a cash flow report for a valid period
- **THEN** the system SHALL return a chronological series where each day entry contains the date, the total revenue for that day, and an array of services performed that day with each service's name and subtotal (sum of snapshot values for that service on that day)

#### Scenario: Service breakdown per day
- **WHEN** a cash flow report is generated and a day has multiple events from different services
- **THEN** each day entry SHALL include a `services` array where each element contains `name` (service description snapshot) and `total` (sum of snapshot values for events of that service on that day), sorted by service name

### Requirement: Cash flow report period limit
The system SHALL reject cash flow report requests where the period exceeds 7 calendar days.

#### Scenario: Period within limit
- **WHEN** an authenticated user requests a cash flow report for 7 days or less
- **THEN** the system SHALL process the request normally

#### Scenario: Period exceeds limit
- **WHEN** an authenticated user requests a cash flow report for a period exceeding 7 calendar days
- **THEN** the system SHALL reject the request with a validation error

### Requirement: Reports support multiple services per appointment
Financial reports SHALL sum all service value snapshots from each appointment. An appointment with multiple services SHALL contribute the sum of all its linked service snapshots to the totals. Client data SHALL NOT appear in revenue or cash flow reports.

#### Scenario: Appointment with multiple services in revenue report
- **WHEN** an appointment has 3 linked services with snapshot values 40, 60, and 30
- **THEN** the revenue report SHALL include 130 (sum of all service snapshots) for that appointment

#### Scenario: Appointment with multiple services in cash flow report
- **WHEN** an appointment on day X has services "sobrancelha" (40) and "buço" (30)
- **THEN** the cash flow daily entry for day X SHALL include each service separately in the services array with its respective total

#### Scenario: Client data excluded from reports
- **WHEN** a report is generated
- **THEN** the response SHALL NOT include client names, cpf, or any client-specific data

### Requirement: Report eligibility criteria
Only events that are present in the local synchronized database, fall within the requested period, are identified with at least one service, and have historical value snapshots SHALL be included in financial report calculations.

#### Scenario: Unidentified events excluded
- **WHEN** a report is generated and some events within the period are not identified
- **THEN** those unidentified events SHALL NOT be included in the financial totals

#### Scenario: Only events in period included
- **WHEN** a report is generated for a specific date range
- **THEN** only events whose occurrence date falls within that range SHALL be considered

### Requirement: Report data freshness verification
Before generating any report, the system SHALL verify the freshness of the synchronized data against the configured freshness policy (e.g., last sync within 30 minutes).

#### Scenario: Data is fresh
- **WHEN** the user's last synchronization occurred within the freshness policy window
- **THEN** the report SHALL be marked as generated with up-to-date data

#### Scenario: Data is stale
- **WHEN** the user's last synchronization is older than the freshness policy window
- **THEN** the report SHALL be marked as generated with potentially stale data, and SHALL include the exact date and time of the last synchronization used

#### Scenario: Integration revoked
- **WHEN** the user's Google integration is marked as invalid/revoked
- **THEN** the report SHALL still be generated from local data but SHALL explicitly indicate the integration is invalid and data may be outdated since the last successful sync

### Requirement: Report metadata
Every report response SHALL include metadata: report type, period considered, data freshness status, last sync timestamp, and indication of possible data lag when applicable.

#### Scenario: Complete metadata in response
- **WHEN** a report is generated
- **THEN** the response SHALL include reportType, periodStart, periodEnd, dataUpToDate (boolean), lastSyncAt (timestamp), and reauthRequired (boolean) when integration is invalid

### Requirement: Reports use historical values only
All financial calculations in reports SHALL use the value snapshot stored in the synchronized event, never the current value from the service catalog.

#### Scenario: Service price changed after event association
- **WHEN** a service's price is updated and a report is generated covering previously associated events
- **THEN** the report SHALL use the original snapshot values, not the updated service price

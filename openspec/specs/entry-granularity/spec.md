# entry-granularity Specification

## Purpose
TBD - created by archiving change add-mood-states-rose. Update Purpose after archive.
## Requirements
### Requirement: Multiple entries per day with timestamps
The system SHALL allow the user to create multiple entries per day, each with its own `created_at` timestamp, without an enforced schedule. The feed SHALL display entries grouped by date (today, yesterday, date headers) and sorted by `created_at` descending within each day.

#### Scenario: Two entries same day displayed with timestamps
- **GIVEN** the user created an entry at 07:10 and another at 14:15 today
- **WHEN** the feed renders
- **THEN** both entries appear under the «Сегодня» header
- **AND** the 14:15 entry appears first (most recent first within the day)
- **AND** the 07:10 entry appears second
- **AND** each card displays its timestamp as «14:15» and «07:10» `[ref: A3-q6, A1-q2]`

#### Scenario: Past days grouped under date headers
- **GIVEN** the user has entries on 2026-08-21 and 2026-08-20
- **WHEN** the feed renders
- **THEN** entries for 2026-08-21 appear under a «21 августа» header
- **AND** entries for 2026-08-20 appear under a separate «20 августа» header
- **AND** past-day cards use a more compact style (`bg-base-300`) than today's cards (`bg-base-200`)

### Requirement: No mandatory entry cadence
The system SHALL NOT enforce a fixed cadence; entries are created on demand. Days with no entries SHALL NOT be marked as «incomplete» or trigger guilt messaging.

#### Scenario: Sparse entries not flagged
- **GIVEN** the user logged once yesterday and three times today
- **WHEN** the feed renders
- **THEN** both histories are correct and not flagged as incomplete
- **AND** no «you missed a day» or streak-break message is shown `[ref: A2-q3]`

### Requirement: Nullable state_period_id foresees Phase 6
The system SHALL include a nullable `state_period_id` column on `entries` from Phase 2, so Phase 6 can add state periods without a breaking schema change. The column SHALL remain NULL in Phase 2 (no `state_periods` table exists yet).

#### Scenario: Entry created without state period
- **GIVEN** migration 006 is applied
- **WHEN** a new entry is created without an active state period
- **THEN** `state_period_id` is NULL
- **AND** no error occurs `[ref: OQ7]`

### Requirement: State periods deferred to Phase 6
The system SHALL NOT auto-create state periods or require the user to mark period start/end in Phase 2. State periods with explicit begin/end are deferred to Phase 6 (`add-entry-granularity-periods`).

#### Scenario: No period UI in Phase 2
- **GIVEN** a user has 3 consecutive low-energy days in Phase 2
- **WHEN** they view the feed
- **THEN** no «start period» or «end period» button is shown
- **AND** no period is auto-created `[ref: A3-q6]`


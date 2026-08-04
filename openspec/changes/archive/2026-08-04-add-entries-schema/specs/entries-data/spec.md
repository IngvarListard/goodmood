## ADDED Requirements

### Requirement: Entries table schema
The system SHALL provide a database table `entries` with columns `id`, `date`, `activity`, `effect`, `mood_score` (0–10), `sleep_hours` and `created_at`.

#### Scenario: Table exists after migration
- **WHEN** the `add-entries-schema` migration is applied
- **THEN** the `entries` table exists
- **AND** it contains the columns `id`, `date`, `activity`, `effect`, `mood_score`, `sleep_hours`, `created_at`

### Requirement: Entry data access functions
The system SHALL provide `create-entry!` and `get-entries` functions in `app.domains.entries.db` built on next.jdbc and HoneySQL.

#### Scenario: Saved entry is retrievable
- **WHEN** `create-entry!` is called with valid data
- **THEN** the entry is persisted
- **AND** `get-entries` returns that entry with correct field values

### Requirement: Entry mood score range enforced
The system SHALL reject an entry whose `mood_score` is outside the 0–10 range.

#### Scenario: Out-of-range mood score rejected
- **WHEN** `create-entry!` is called with `mood_score` outside 0–10
- **THEN** the database or function rejects the entry via constraint or explicit check

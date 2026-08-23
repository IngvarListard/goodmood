# Entries Data Specification

## Purpose
Store mood and activity diary entries in the `entries` table and provide a data access layer for creating and reading them.
## Requirements
### Requirement: Migration 004 adds mobile form fields
The system SHALL provide a migration that adds new columns to the `entries` table without data loss.

#### Scenario: Migration adds columns
- **WHEN** migration `004-add-mobile-form-fields.up.sql` is applied
- **THEN** columns `energy`, `anxiety`, `focus`, `note`, `template` are added to `entries`
- **AND** all new columns are nullable
- **AND** existing data in `entries` is preserved

#### Scenario: Migration rollback removes columns
- **WHEN** migration `004-add-mobile-form-fields.down.sql` is applied
- **THEN** columns `energy`, `anxiety`, `focus`, `note`, `template` are dropped
- **AND** columns `activity` and `effect` remain unchanged

### Requirement: Entries table schema
The system SHALL provide a database table `entries` with columns `id`, `date`, `mood_score` (0–10), `energy` (0–10), `anxiety` (0–10), `focus` (0–10, nullable), `sleep_hours` (nullable), `note` (nullable), `activity` (nullable), `effect` (nullable), `template` (nullable), `created_at`.

#### Scenario: Table exists after migration
- **WHEN** migrations 002, 003, and 004 are applied
- **THEN** the `entries` table exists
- **AND** it contains columns `id`, `date`, `mood_score`, `energy`, `anxiety`, `sleep_hours`, `created_at`, `user_id`
- **AND** it contains nullable columns `focus`, `note`, `activity`, `effect`, `template`

#### Scenario: New required columns have default values
- **WHEN** migration 004 is applied
- **THEN** existing rows have `energy` and `anxiety` set to NULL (columns are nullable for backward compatibility)
- **AND** domain logic enforces non-null for new entries

### Requirement: Entry data access functions
The system SHALL update `create-entry!` and `get-entries` in `app.db.entries` to handle the new field set.

#### Scenario: Create entry with new core fields
- **WHEN** `create-entry!` is called with `mood_score`, `energy`, `anxiety`
- **THEN** the entry is persisted with all three core values
- **AND** optional fields (`focus`, `sleep_hours`, `note`, `activity`, `effect`, `template`) are stored as provided or NULL

#### Scenario: Create entry with optional fields
- **WHEN** `create-entry!` is called with core fields plus `sleep_hours` = 7.5, `note` = "текст заметки", `template` = "morning"
- **THEN** the entry is persisted with all provided values
- **AND** non-provided optional fields are NULL

#### Scenario: Existing entries still retrievable
- **WHEN** `get-entries` is called after migration 004
- **THEN** entries created before migration are returned with new columns as NULL
- **AND** entries created after migration include new column values

### Requirement: Entry mood score range enforced
The system SHALL reject an entry whose `mood_score`, `energy`, or `anxiety` is outside the 0–10 range.

#### Scenario: Out-of-range core field rejected
- **WHEN** `create-entry!` is called with `energy` = 15 (outside 0–10)
- **THEN** the domain layer rejects the entry via malli schema validation
- **AND** no row is inserted into the database

### Requirement: Medication logs link to entries by date
The system SHALL allow analytical joins between `medication_logs` and `entries` by matching `medication_logs.log_date` to `entries.date` for the same `user_id`. No foreign key constraint exists between the tables; the join is implicit by date.

#### Scenario: Join logs to entries for a day
- **WHEN** a query joins `medication_logs` and `entries` on `user_id` and `log_date = date`
- **THEN** all medication logs for a given day are associated with the mood entry for that day
- **AND** if no mood entry exists for a day, medication logs remain queryable independently `[ref: A3-q5]`

### Requirement: Migration 006 adds state_label and state_period_id
The system SHALL provide migration 006 that adds two nullable columns to the `entries` table: `state_label TEXT` and `state_period_id INTEGER`. Both columns SHALL be nullable and SHALL NOT have a foreign key constraint.

#### Scenario: Migration adds columns
- **WHEN** migration `006-add-state-label-and-period.up.sql` is applied
- **THEN** column `state_label TEXT` is added to `entries`
- **AND** column `state_period_id INTEGER` is added to `entries`
- **AND** both columns are nullable
- **AND** no foreign key constraint is created
- **AND** existing data in `entries` is preserved

#### Scenario: Migration rollback removes columns
- **WHEN** migration `006-add-state-label-and-period.down.sql` is applied
- **THEN** columns `state_label` and `state_period_id` are dropped from `entries`
- **AND** all other columns remain unchanged

### Requirement: Entry data access handles state_label and state_period_id
The system SHALL update `create-entry!` and `get-entries` in `app.db.entries` to handle the `state_label` and `state_period_id` columns. `get-entries` SHALL return entries ordered by `created_at` descending within each date group.

#### Scenario: Create entry with state_label
- **WHEN** `create-entry!` is called with `state_label` = `:state/anxiety`
- **THEN** the entry is persisted with `state_label` stored as text
- **AND** `state_period_id` is stored as NULL

#### Scenario: Create entry without state_label
- **WHEN** `create-entry!` is called without `state_label` (nil)
- **THEN** the entry is persisted with `state_label` = NULL
- **AND** the label will be auto-derived on render

#### Scenario: Get entries ordered by created_at within day
- **WHEN** `get-entries` is called and the user has 3 entries on the same date
- **THEN** entries are returned with the most recent `created_at` first within that date
- **AND** entries are grouped by date (most recent date first)

### Requirement: Insights link to entries by optional soft entry_id
The system SHALL allow an optional soft reference from `insights.entry_id` to `entries.id` for the same `user_id`. No foreign key constraint SHALL exist between `insights` and `entries`. Deleting an entry SHALL NOT cascade to linked insights; a dangling `entry_id` is acceptable and display SHALL omit the entry link when the referenced entry no longer exists. This follows the loose-coupling pattern established by `medication_logs` (link by date, no FK) and `entries.state_period_id` (no FK).

#### Scenario: Insight references an existing entry
- **WHEN** an insight is created with `entry_id=42` and entry `entries.id=42` exists for the same `user_id`
- **THEN** the insight is persisted with `entry_id=42`
- **AND** no foreign key constraint is enforced
- **AND** the insight's `state_label` matches the referenced entry's `state_label` (set at creation) `[ref: A2-q4, A4-q5]`

#### Scenario: Deleting an entry leaves dangling entry_id
- **GIVEN** an insight with `entry_id=42`
- **WHEN** entry `entries.id=42` is deleted
- **THEN** the insight is preserved (no FK CASCADE)
- **AND** `insights.entry_id` remains `42` (dangling)
- **AND** the insight remains queryable and matchable by `state_label`
- **AND** display of the insight omits the link to the deleted entry `[ref: A4-q5]`

#### Scenario: Standalone insight has null entry_id
- **WHEN** an insight is created without `entry_id` (standalone via `/insights/new`)
- **THEN** `insights.entry_id` is NULL
- **AND** the insight is matchable by `state_label` identically to linked insights `[ref: A2-q2]`


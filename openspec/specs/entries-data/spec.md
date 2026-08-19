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

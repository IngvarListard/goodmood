## ADDED Requirements

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

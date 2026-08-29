# Entries Data — Delta Specification

## Purpose
Modify the entries data layer to support multi-user data isolation via `user_id`.

## MODIFIED Requirements

### Requirement: Entries table schema
The system SHALL provide a database table `entries` with columns `id`, `date`,
`activity`, `effect`, `mood_score` (0–10), `sleep_hours`, `user_id` (FK to users),
and `created_at`.

#### Scenario: Table exists after migration
- **WHEN** migration 003 is applied
- **THEN** the `entries` table contains column `user_id` as a foreign key to `users(id)`
- **AND** existing rows have `user_id` set to admin's id after seed

#### Scenario: New entries require user_id
- **WHEN** a new entry is created through `create-entry!`
- **THEN** `user_id` is provided and set on the new row

### Requirement: Entry data access functions
The system SHALL provide `create-entry!` and `get-entries` functions that
accept and filter by `user-id`.

#### Scenario: create-entry! with user-id
- **WHEN** `create-entry!` is called with `user-id` and entry data
- **THEN** the entry is persisted with the given `user-id`

#### Scenario: get-entries filtered by user
- **WHEN** `get-entries` is called with `user-id`
- **THEN** only entries matching that `user-id` are returned

## ADDED Requirements

### Requirement: Admin seed assignment
The system SHALL assign all existing entries without `user_id` to the seeded
admin user during the startup seed process.

#### Scenario: Orphan entries assigned to admin
- **WHEN** the system starts with existing entries having `user_id IS NULL`
- **THEN** those entries are updated to belong to the admin user created during seed
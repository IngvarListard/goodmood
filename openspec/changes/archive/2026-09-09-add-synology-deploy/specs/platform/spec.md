# platform Spec (delta)

## ADDED Requirements

### Requirement: Squashed migration baseline
The resource `migrations` directory SHALL contain a single baseline migration `001-init` (up and down) representing the full current schema. Migrations SHALL be append-only after the first deployment: an already-deployed migration file MUST NOT be modified or removed; schema changes are made only by adding new migrations.

#### Scenario: Fresh database from single migration
- **WHEN** the application starts against a non-existent database file
- **THEN** migratus applies the single `001-init` migration
- **AND** the resulting schema is equivalent to the schema produced by the previous 13 migrations

#### Scenario: Down migration reverses the baseline
- **WHEN** migratus down/rollback is invoked for `001-init`
- **THEN** all baseline tables are dropped

## MODIFIED Requirements

### Requirement: SQLite datasource available as integrant component
The system SHALL provide a `:db/connection` integrant component that creates a SQLite datasource via next.jdbc, with the database file path taken from the `GOODMOOD_DB_PATH` environment variable (default: `resources/goodmood.db`).

#### Scenario: Connection initializes on system start
- **WHEN** integrant system is initialized
- **THEN** the `:db/connection` component is created
- **AND** the SQLite database file is created on disk without errors at the configured path

#### Scenario: Connection path follows environment
- **WHEN** `GOODMOOD_DB_PATH` is set to a custom location
- **THEN** the SQLite database file is created/used at that location

#### Scenario: Connection halts on system stop
- **WHEN** integrant system is halted
- **THEN** the database connection resources are released without exceptions

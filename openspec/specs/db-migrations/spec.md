# Database Migrations Specification

## Purpose
Manage schema migrations with migratus so the database schema evolves reproducibly and tracked in the database.

## Requirements

### Requirement: Migrations applied on startup
The system SHALL run pending database migrations when the application starts.

#### Scenario: Empty database file gets migrations applied
- **WHEN** application starts with a fresh SQLite database file
- **THEN** migratus runs all pending migrations
- **AND** no errors are raised

### Requirement: Migration history tracked
The system SHALL track applied migrations in the `schema_migrations` table.

#### Scenario: Schema migrations table records applied migration
- **WHEN** `migratus migrate` is executed
- **THEN** the `schema_migrations` table exists
- **AND** it contains a record for each applied migration

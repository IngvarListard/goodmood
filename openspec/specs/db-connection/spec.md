# Database Connection Specification

## Purpose
Provide a persistent SQLite connection managed as an integrant component, so future domains have a shared datasource.

## Requirements

### Requirement: SQLite datasource available as integrant component
The system SHALL provide a `:db/connection` integrant component that creates a SQLite datasource via next.jdbc.

#### Scenario: Connection initializes on system start
- **WHEN** integrant system is initialized
- **THEN** the `:db/connection` component is created
- **AND** the SQLite database file is created on disk without errors

#### Scenario: Connection halts on system stop
- **WHEN** integrant system is halted
- **THEN** the database connection resources are released without exceptions

### Requirement: Connection is queryable
The system SHALL allow executing queries against the SQLite datasource through next.jdbc.

#### Scenario: REPL query succeeds
- **WHEN** system is running
- **AND** a `SELECT 1` query is executed against the datasource
- **THEN** the query returns successfully without errors

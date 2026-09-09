# seed-data Spec (delta)

## ADDED Requirements

### Requirement: Deterministic test data on empty database
The system SHALL, on startup with an empty database (no users), seed test data («рыба») deterministically so that a fresh deployment can be visually verified: several state entries spread over the last few days, at least one insight, and at least one medication with a schedule.

#### Scenario: Fish data created on fresh database
- **WHEN** the system starts against a database with no users
- **THEN** the admin user is created (see users-auth seed)
- **AND** several mood/state entries exist, dated over the last few days, owned by the admin user
- **AND** at least one insight and one active medication exist

#### Scenario: Fish data survives determinism
- **WHEN** the database is deleted and the system starts again
- **THEN** the same seed data is recreated (same fixed user, comparable content)

#### Scenario: No duplicate seed on restart
- **WHEN** the system starts and the database already has users
- **THEN** no test data is added

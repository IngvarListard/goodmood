# users-auth Spec (delta)

## MODIFIED Requirements

### Requirement: Admin seed at startup
The system SHALL create an admin user on startup whenever no users exist (first start or after the database was deleted/recreated), using fixed credentials from environment variables for email and password.

#### Scenario: Admin created on first start
- **WHEN** the system starts and the `users` table is empty
- **THEN** an admin user is created with email from `GOODMOOD_ADMIN_EMAIL` (default: admin@goodmood.local)
- **AND** password from `GOODMOOD_ADMIN_PASSWORD` environment variable
- **AND** role is `admin`
- **AND** existing entries (if any) are assigned to this admin user

#### Scenario: Admin recreated after database reset
- **WHEN** the database file is deleted and the system starts again with the same environment variables
- **THEN** an admin user with the same email and password is created

#### Scenario: No duplicate seed on restart
- **WHEN** the system starts and the `users` table already has users
- **THEN** no new admin user is created

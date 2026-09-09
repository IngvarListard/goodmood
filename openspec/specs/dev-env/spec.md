# dev-env Specification

## Purpose
TBD - created by archiving change add-synology-deploy. Update Purpose after archive.
## Requirements
### Requirement: Dotenv file for local development
The system SHALL load variables from a `.env` file in the project root (KEY=VALUE lines) and merge them with the process environment, with real environment variables taking precedence over `.env` values. A missing `.env` file SHALL NOT be an error. The `.env` file SHALL be gitignored; a `.env.example` SHALL be committed.

#### Scenario: Values loaded from .env
- **WHEN** `.env` contains `GOODMOOD_SESSION_SECRET=abc` and the process environment does not define it
- **AND** the application starts
- **THEN** the session secret used by the app is `abc`

#### Scenario: Environment wins over .env
- **WHEN** `.env` contains `GOODMOOD_PORT=3000` and the process environment defines `GOODMOOD_PORT=4000`
- **THEN** the server listens on port 4000

#### Scenario: Missing .env is fine
- **WHEN** the application starts and no `.env` file exists in the project root
- **THEN** the application starts normally using only process environment variables

### Requirement: Local dev start script
The project SHALL provide `bin/dev` that starts the application for local development (equivalent of `clj -M:dev`) with `.env` loaded.

#### Scenario: bin/dev starts the app
- **WHEN** `bin/dev` is executed
- **THEN** the application and nREPL start as with `clj -M:dev`
- **AND** variables from `.env` are available to the app


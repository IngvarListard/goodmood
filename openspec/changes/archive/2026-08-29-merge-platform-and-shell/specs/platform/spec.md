# Platform Specification (Delta — merge of 4 domains)

## ADDED Requirements

### Requirement: Server starts and responds to health-check
The system SHALL provide an HTTP server that responds to GET / with HTTP 200 OK.

#### Scenario: Health-check returns 200
- **WHEN** application is running via `clj -M -m app.core`
- **AND** HTTP GET request is sent to "/"
- **THEN** server responds with HTTP status 200

#### Scenario: Server starts within time limit
- **WHEN** application is started via `clj -M -m app.core`
- **THEN** server is ready to accept requests within 3 seconds

### Requirement: Server stops gracefully
The system SHALL stop the HTTP server without exceptions when integrant halts.

#### Scenario: Graceful shutdown
- **WHEN** integrant system is running
- **AND** `integrant.core/halt!` is called
- **THEN** server stops without throwing exceptions
- **AND** all resources are released

### Requirement: Component lifecycle management
The system SHALL manage the HTTP server lifecycle through integrant components.

#### Scenario: Integrant initializes server component
- **WHEN** integrant system is initialized
- **THEN** HTTP server component is created and started

#### Scenario: Integrant halts server component
- **WHEN** integrant system is halted
- **THEN** HTTP server component is stopped and cleaned up

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

### Requirement: Layered source structure
The project source SHALL be organized in four global layers under `src/app/`: `db/` (SQL/JDBC access, datasource lifecycle), `routes/` (reitit route table, HTTP handlers, route-specific middleware), `views/` (Hiccup rendering only), and `domains/` (business logic, one subdirectory or file per feature/domain).

#### Scenario: SQL lives only in the db layer
- **WHEN** the project source is compiled
- **THEN** all SQL queries (HoneySQL/next.jdbc) are located under `src/app/db/` and no other layer contains SQL or JDBC calls

#### Scenario: Markup lives only in the views layer
- **WHEN** the project source is compiled
- **THEN** all Hiccup markup is located under `src/app/views/` and no other layer builds HTML fragments or pages

#### Scenario: Route tables and handlers live in the routes layer
- **WHEN** the project source is compiled
- **THEN** the reitit route table, HTTP handlers, and route-specific middleware are located under `src/app/routes/`

#### Scenario: Business logic lives in the domains layer
- **WHEN** the project source is compiled
- **THEN** business rules, validation, and data schemas are located under `src/app/domains/`, one file or subdirectory per feature

### Requirement: Layer dependency direction
Layers SHALL depend only on layers below themselves: `routes` MAY require `domains`, `views`, and `db`; `domains` MAY require `db`; `db`, `views`, and `domains` SHALL NOT require `routes` or `views` (for `db`) in a bottom-up direction.

#### Scenario: db layer has no upward dependencies
- **WHEN** the `src/app/db/` namespace is loaded
- **THEN** it requires neither `routes`, `views`, nor `domains` namespaces

#### Scenario: views layer has no data access
- **WHEN** the `src/app/views/` namespace is loaded
- **THEN** it does not require or call `db/*` functions and performs no business validation

#### Scenario: routes layer does not access the database directly
- **WHEN** a route handler handles an HTTP request
- **THEN** it obtains data through `domains/*` and passes results to `views/*`, without issuing SQL or JDBC calls itself

### Requirement: Refactoring preserves behavior
The file/directory restructure SHALL NOT change application behavior, HTML markup, API responses, dependencies, or test logic; tests may only change namespaces, requires, and file paths.

#### Scenario: All existing tests still pass after restructure
- **WHEN** the test suite runs after the restructure
- **THEN** all tests pass without changes to test logic

#### Scenario: Rendered markup is unchanged
- **WHEN** `GET /entries` returns the HTML page after the restructure
- **THEN** the response body contains the same markup as before the restructure (same form, empty-state text, and entry list fragment)

#### Scenario: API responses are unchanged
- **WHEN** `POST /entries` and `GET /entries` are called with JSON accept headers after the restructure
- **THEN** status codes and response bodies match the behavior defined in the `entries-api` and `entries-data` specs

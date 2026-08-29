## ADDED Requirements

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
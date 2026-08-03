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

# Entries Api Specification

## Purpose
Expose HTTP endpoints for creating and listing diary entries.

## Requirements

### Requirement: Create entry via API
The system SHALL provide an HTTP endpoint `POST /entries` that creates a diary entry. The request body SHALL include mandatory fields `mood_score`, `energy`, `anxiety` (int 0–10) and optional fields `focus` (int 0–10, nullable), `sleep_hours` (number, nullable), `note` (string, nullable), `activity` (string, nullable), `effect` (string, nullable), `template` (string, nullable). For requests without an `HX-Request` header the endpoint returns the created entry as JSON with HTTP status 201; for htmx requests (header `HX-Request: true`) it returns an HTML fragment of the new list item with HTTP status 201.

#### Scenario: Successful entry creation via API
- **WHEN** a `POST /entries` request is sent without an `HX-Request` header and with a valid JSON body containing `mood_score`, `energy`, `anxiety`
- **THEN** the server responds with HTTP status 201
- **AND** the response body is JSON representing the created entry with the submitted field values

#### Scenario: Successful entry creation with optional fields
- **WHEN** a `POST /entries` request is sent with a valid JSON body containing `mood_score`, `energy`, `anxiety`, `sleep_hours` = 7.5, `note` = "test", `template` = "morning"
- **THEN** the server responds with HTTP status 201
- **AND** the response body includes all provided optional field values
- **AND** non-provided optional fields are nil in the response

#### Scenario: Successful entry creation via htmx
- **WHEN** a `POST /entries` request is sent with an `HX-Request: true` header and with a valid body containing `mood_score`, `energy`, `anxiety`
- **THEN** the server responds with HTTP status 201
- **AND** the response body is an HTML fragment representing the new list item

### Requirement: Entry request validation
The system SHALL validate the `POST /entries` request body. Mandatory fields `mood_score`, `energy`, `anxiety` are required. Optional fields (`focus`, `sleep_hours`, `note`, `activity`, `effect`, `template`) are nullable. For requests without an `HX-Request` header an invalid body produces HTTP status 400 with a JSON validation error description; for htmx requests an invalid body produces HTTP status 400 with an HTML fragment containing the validation error message using alert-warning class.

#### Scenario: Missing required field rejected via API
- **WHEN** a `POST /entries` request is sent without an `HX-Request` header and without the required `energy` field
- **THEN** the server responds with HTTP status 400
- **AND** the response body contains a description of the validation error mentioning `energy`

#### Scenario: Missing required field rejected via htmx
- **WHEN** a `POST /entries` request is sent with an `HX-Request: true` header and without the required `energy` field
- **THEN** the server responds with HTTP status 400
- **AND** the response body is an HTML fragment with `alert-warning` class containing the validation error message

#### Scenario: Out-of-range core field rejected
- **WHEN** a `POST /entries` request is sent with `anxiety` = 15 (outside 0–10)
- **THEN** the server responds with HTTP status 400
- **AND** the response body contains a description of the validation error

#### Scenario: Empty optional fields accepted
- **WHEN** a `POST /entries` request is sent with valid core fields but `sleep_hours` is empty string
- **THEN** the server responds with HTTP status 201
- **AND** `sleep_hours` is coerced to nil in the created entry

### Requirement: List entries via API
The system SHALL provide an HTTP endpoint `GET /entries`. For requests without an HTML `Accept` preference (no `text/html` in the `Accept` header) it returns the list of diary entries as JSON with HTTP status 200; for browser requests (Accept header preferring `text/html`) it returns an HTML page containing the entry form and the list of entries.

#### Scenario: Successful listing via API
- **WHEN** a `GET /entries` request is sent without an HTML Accept preference and the database contains entries
- **THEN** the server responds with HTTP status 200
- **AND** the response body is a JSON array of the stored entries

#### Scenario: Successful listing via browser
- **WHEN** a `GET /entries` request is sent with an `Accept: text/html` header
- **THEN** the server responds with HTTP status 200
- **AND** the response body is an HTML page containing the entry creation form and the list of stored entries

#### Scenario: Listing within time limit
- **WHEN** a `GET /entries` request is sent with up to 1000 entries in the table
- **THEN** the server responds within 200ms

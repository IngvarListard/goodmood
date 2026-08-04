## MODIFIED Requirements

### Requirement: Create entry via API
The system SHALL provide an HTTP endpoint `POST /entries` that creates a diary entry. For requests without an `HX-Request` header the endpoint returns the created entry as JSON with HTTP status 201; for htmx requests (header `HX-Request: true`) it returns an HTML fragment of the new list item with HTTP status 201.

#### Scenario: Successful entry creation via API
- **WHEN** a `POST /entries` request is sent without an `HX-Request` header and with a valid JSON body containing `activity`, `effect`, `mood_score`, `sleep_hours`
- **THEN** the server responds with HTTP status 201
- **AND** the response body is JSON representing the created entry with the submitted field values

#### Scenario: Successful entry creation via htmx
- **WHEN** a `POST /entries` request is sent with an `HX-Request: true` header and with a valid body containing `activity`, `effect`, `mood_score`, `sleep_hours`
- **THEN** the server responds with HTTP status 201
- **AND** the response body is an HTML fragment representing the new list item

### Requirement: Entry request validation
The system SHALL validate the `POST /entries` request body against schema fields `activity`, `effect`, `mood_score`, `sleep_hours`. For requests without an `HX-Request` header an invalid body produces HTTP status 400 with a JSON validation error description; for htmx requests an invalid body produces HTTP status 400 with an HTML fragment containing the validation error message.

#### Scenario: Out-of-range mood score rejected via API
- **WHEN** a `POST /entries` request is sent without an `HX-Request` header and with `mood_score` = 15 (outside the 0–10 range)
- **THEN** the server responds with HTTP status 400
- **AND** the response body contains a description of the validation error

#### Scenario: Out-of-range mood score rejected via htmx
- **WHEN** a `POST /entries` request is sent with an `HX-Request: true` header and with `mood_score` = 15 (outside the 0–10 range)
- **THEN** the server responds with HTTP status 400
- **AND** the response body is an HTML fragment that contains the validation error message

#### Scenario: Invalid body rejected via API
- **WHEN** a `POST /entries` request is sent without an `HX-Request` header and without a required field
- **THEN** the server responds with HTTP status 400
- **AND** the response body contains a description of the validation error

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

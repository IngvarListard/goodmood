# Entries Api Specification

## Purpose
Expose HTTP endpoints for creating and listing diary entries.

## Requirements

### Requirement: Create entry via API
The system SHALL provide an HTTP endpoint `POST /entries` that creates a diary entry and returns the created entry as JSON with HTTP status 201.

#### Scenario: Successful entry creation
- **WHEN** a `POST /entries` request is sent with a valid JSON body containing `activity`, `effect`, `mood_score`, `sleep_hours`
- **THEN** the server responds with HTTP status 201
- **AND** the response body is JSON representing the created entry with the submitted field values

### Requirement: Entry request validation
The system SHALL validate the `POST /entries` request body against schema fields `activity`, `effect`, `mood_score`, `sleep_hours` and respond with HTTP status 400 and a validation error description when the body is invalid.

#### Scenario: Out-of-range mood score rejected
- **WHEN** a `POST /entries` request is sent with `mood_score` = 15 (outside the 0–10 range)
- **THEN** the server responds with HTTP status 400
- **AND** the response body contains a description of the validation error

#### Scenario: Invalid body rejected
- **WHEN** a `POST /entries` request is sent without a required field
- **THEN** the server responds with HTTP status 400
- **AND** the response body contains a description of the validation error

### Requirement: List entries via API
The system SHALL provide an HTTP endpoint `GET /entries` that returns the list of diary entries as JSON with HTTP status 200.

#### Scenario: Successful listing
- **WHEN** a `GET /entries` request is sent and the database contains entries
- **THEN** the server responds with HTTP status 200
- **AND** the response body is a JSON array of the stored entries

#### Scenario: Listing within time limit
- **WHEN** a `GET /entries` request is sent with up to 1000 entries in the table
- **THEN** the server responds within 200ms

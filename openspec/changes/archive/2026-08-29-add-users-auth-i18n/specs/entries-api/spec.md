# Entries API — Delta Specification

## Purpose
Add authentication requirement and user-scoping to the entries API endpoints.

## MODIFIED Requirements

### Requirement: Create entry via API
The system SHALL require authentication for `POST /entries`. The entry is
automatically scoped to the authenticated user. For authenticated requests
without an `HX-Request` header the endpoint returns the created entry as JSON
with HTTP status 201; for htmx requests it returns an HTML fragment.

#### Scenario: Authenticated entry creation
- **WHEN** a `POST /entries` request is sent with a valid session and valid body
- **THEN** the server responds with HTTP status 201
- **AND** the created entry has `user_id` matching the authenticated user

#### Scenario: Unauthenticated POST /entries redirected
- **WHEN** a `POST /entries` request is sent without a session and without an `HX-Request` header
- **THEN** the server responds with HTTP 302 redirect to `/login?next=/entries`

### Requirement: List entries via API
The system SHALL require authentication for `GET /entries` and return only
entries where `user_id` matches the authenticated user.

#### Scenario: Authenticated listing returns user's entries
- **WHEN** a `GET /entries` request is sent with a valid session
- **THEN** the server responds with HTTP status 200
- **AND** the response contains only entries belonging to the authenticated user

#### Scenario: Unauthenticated GET /entries redirected
- **WHEN** a `GET /entries` request is sent without a session
- **THEN** the server responds with HTTP 302 redirect to `/login?next=/entries`

## REMOVED Requirements

(None — existing behavior is preserved behind authentication)
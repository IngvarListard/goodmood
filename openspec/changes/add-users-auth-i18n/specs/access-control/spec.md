# Access Control Specification

## Purpose
Protect application routes from unauthenticated access, enforce role-based
authorization, and scope entry data to the authenticated user.

## Requirements

### Requirement: Route protection
The system SHALL require authentication for all routes except those explicitly
marked as public (`GET /` health-check, `GET /login`, `POST /login`, `GET /logout`? actually `POST /logout`).
Unauthenticated requests to protected routes SHALL be redirected to `/login`
with a `next` query parameter preserving the original URI.

#### Scenario: Unauthenticated access to protected page redirected
- **WHEN** an unauthenticated GET request is made to `/dashboard`
- **THEN** the server responds with HTTP 302 redirect to `/login?next=/dashboard`

#### Scenario: Unauthenticated access to API redirected
- **WHEN** an unauthenticated GET request is made to `/entries`
- **THEN** the server responds with HTTP 302 redirect to `/login?next=/entries`

#### Scenario: Unauthenticated access to non-existent route redirected
- **WHEN** an unauthenticated GET request is made to `/nonexistent`
- **THEN** the server responds with HTTP 302 redirect to `/login?next=/nonexistent`

#### Scenario: Health check accessible without auth
- **WHEN** an unauthenticated GET request is made to `/`
- **THEN** the server responds with HTTP 200 "OK"

#### Scenario: Login page accessible without auth
- **WHEN** an unauthenticated GET request is made to `/login`
- **THEN** the server responds with HTTP 200 (login form)

### Requirement: Authenticated user access
The system SHALL allow authenticated users to access any protected route.

#### Scenario: Authenticated access to protected page
- **WHEN** a GET request to `/dashboard` is made with a valid session
- **THEN** the server responds with HTTP 200
- **AND** the response includes the page content

### Requirement: Role-based authorization
The system SHALL support role-based access control with `user` and `admin` roles.
Routes SHALL be annotatable with required roles. Users without required roles
SHALL receive an error.

#### Scenario: User with admin role accesses admin-required route
- **WHEN** an authenticated admin requests a route with `:auth/roles [:admin]`
- **THEN** the server responds with HTTP 200

#### Scenario: User without admin role denied
- **WHEN** an authenticated user with role `user` requests a route with `:auth/roles [:admin]`
- **THEN** the server responds with HTTP 403

### Requirement: Entry data scoping
The system SHALL associate each entry with its creator via `user_id`.
When listing entries, only entries belonging to the authenticated user SHALL be returned.

#### Scenario: Entry creation assigns user_id
- **WHEN** an authenticated user creates an entry via POST /entries
- **THEN** the entry is saved with `user_id` set to the authenticated user's id

#### Scenario: Entry listing scoped to user
- **WHEN** an authenticated user (id=1) requests GET /entries
- **THEN** the response contains only entries where `user_id = 1`

#### Scenario: User cannot see other user's entries
- **WHEN** user A (id=1) and user B (id=2) each create entries
- **AND** user A requests GET /entries
- **THEN** the response contains only user A's entries

### Requirement: Identity in request map
The system SHALL attach authenticated user identity to the request map under
`:identity` key for downstream handlers.

#### Scenario: Identity in request map
- **WHEN** a request is made with a valid session containing identity `{:id 1 :email "a@b.c" :role "user" :display-name "A"}`
- **THEN** `(:identity request)` returns `{:id 1 :email "a@b.c" :role "user" :display-name "A"}`

#### Scenario: No identity for unauthenticated request
- **WHEN** a request is made without a session
- **THEN** `(:identity request)` is nil
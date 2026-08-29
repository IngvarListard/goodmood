# users-auth Specification

## Purpose
TBD - created by archiving change add-users-auth-i18n. Update Purpose after archive.
## Requirements
### Requirement: Users table
The system SHALL provide a database table `users` with columns `id`, `email` (unique),
`password_hash`, `display_name`, `role` (default 'user'), `created_at`.

#### Scenario: Users table exists after migration
- **WHEN** migration 003 is applied
- **THEN** the `users` table exists
- **AND** contains columns `id`, `email`, `password_hash`, `display_name`, `role`, `created_at`

#### Scenario: Email uniqueness enforced
- **WHEN** inserting two users with the same email
- **THEN** the database rejects the second insertion

### Requirement: Password hashing
The system SHALL hash user passwords using bcrypt via buddy-hashers.
Passwords SHALL never be stored or logged in plain text.

#### Scenario: Password stored as bcrypt hash
- **WHEN** a user is created with a plain text password
- **THEN** the stored `password_hash` is a bcrypt string (prefix `$2b$` or `$2a$`)
- **AND** the plain text password is not stored

#### Scenario: Password verification succeeds for correct password
- **WHEN** `authenticate` is called with the user's email and correct password
- **THEN** the function returns the user map (with `password_hash` removed)

#### Scenario: Password verification fails for wrong password
- **WHEN** `authenticate` is called with the user's email and wrong password
- **THEN** the function returns nil

#### Scenario: Password verification fails for non-existent email
- **WHEN** `authenticate` is called with an email not in the database
- **THEN** the function returns nil

### Requirement: Login endpoint
The system SHALL provide `GET /login` (login form) and `POST /login` (authentication).
The form SHALL include a CSRF token.

#### Scenario: Login form rendered with i18n
- **WHEN** GET /login is requested
- **THEN** the server responds with HTTP 200
- **AND** the response body is an HTML login form
- **AND** the form labels and button are in the current locale

#### Scenario: Login form includes CSRF token
- **WHEN** GET /login is rendered
- **THEN** the HTML contains a hidden input with CSRF token in the login form

#### Scenario: Successful login redirects and sets session
- **WHEN** POST /login is sent with valid CSRF token, correct email and password
- **THEN** the server responds with HTTP 302 redirect to `/`
- **AND** a signed session cookie `gm-session` is set containing the user identity
- **AND** the identity contains `{:id :email :role :display-name}`

#### Scenario: Login with `next` query parameter redirects
- **WHEN** POST /login?next=/dashboard is sent with valid credentials
- **THEN** the server responds with HTTP 302 redirect to `/dashboard`

#### Scenario: Failed login shows error
- **WHEN** POST /login is sent with incorrect password
- **THEN** the server responds with HTTP 200
- **AND** the response body contains an error message in the current locale
- **AND** no session cookie is set

#### Scenario: Login page redirects if already authenticated
- **WHEN** GET /login is requested with a valid session
- **THEN** the server responds with HTTP 302 redirect to `/`

### Requirement: Logout endpoint
The system SHALL provide `POST /logout` that clears the session and redirects to login.

#### Scenario: Logout clears session
- **WHEN** POST /logout is sent with a valid session and valid CSRF token
- **THEN** the server responds with HTTP 302 redirect to `/login`
- **AND** the session cookie `gm-session` is cleared (max-age=0)

#### Scenario: Logout without session succeeds
- **WHEN** POST /logout is sent without a session
- **THEN** the server responds with HTTP 302 redirect to `/login`

### Requirement: CSRF protection on login/logout
The system SHALL protect `POST /login` and `POST /logout` endpoints against CSRF
attacks using ring-anti-forgery.

#### Scenario: POST without CSRF token rejected
- **WHEN** POST /login is sent without a CSRF token
- **THEN** the server responds with HTTP 403 (or 400)

#### Scenario: htmx requests carry CSRF token
- **WHEN** a form is submitted via htmx with hx-boost
- **THEN** the CSRF token is sent in the `X-CSRF-Token` header
- **AND** ring-anti-forgery accepts the request

### Requirement: Session expiration
The session cookie SHALL expire after 1 hour of inactivity.

#### Scenario: Session expires after inactivity
- **WHEN** a session cookie is set with a 1-hour max-age
- **AND** 1 hour has elapsed
- **THEN** the browser no longer sends the cookie
- **AND** the next request returns 302 /login

### Requirement: Admin seed at startup
The system SHALL create an admin user on first start if no users exist, using
environment variables for email and password.

#### Scenario: Admin created on first start
- **WHEN** the system starts and the `users` table is empty
- **THEN** an admin user is created with email from `GOODMOOD_ADMIN_EMAIL` (default: admin@goodmood.local)
- **AND** password from `GOODMOOD_ADMIN_PASSWORD` environment variable
- **AND** role is `admin`
- **AND** existing entries (if any) are assigned to this admin user

#### Scenario: No duplicate seed on restart
- **WHEN** the system starts and the `users` table already has users
- **THEN** no new admin user is created



### Requirement: Route protection
The system SHALL require authentication for all routes except those explicitly marked as public (`GET /` health-check, `GET /login`, `POST /login`, `GET /logout`? actually `POST /logout`). Unauthenticated requests to protected routes SHALL be redirected to `/login` with a `next` query parameter preserving the original URI.

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
The system SHALL support role-based access control with `user` and `admin` roles. Routes SHALL be annotatable with required roles. Users without required roles SHALL receive an error.

#### Scenario: User with admin role accesses admin-required route
- **WHEN** an authenticated admin requests a route with `:auth/roles [:admin]`
- **THEN** the server responds with HTTP 200

#### Scenario: User without admin role denied
- **WHEN** an authenticated user with role `user` requests a route with `:auth/roles [:admin]`
- **THEN** the server responds with HTTP 403

### Requirement: Entry data scoping
The system SHALL associate each entry with its creator via `user_id`. When listing entries, only entries belonging to the authenticated user SHALL be returned.

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
The system SHALL attach authenticated user identity to the request map under `:identity` key for downstream handlers.

#### Scenario: Identity in request map
- **WHEN** a request is made with a valid session containing identity `{:id 1 :email "a@b.c" :role "user" :display-name "A"}`
- **THEN** `(:identity request)` returns `{:id 1 :email "a@b.c" :role "user" :display-name "A"}`

#### Scenario: No identity for unauthenticated request
- **WHEN** a request is made without a session
- **THEN** `(:identity request)` is nil

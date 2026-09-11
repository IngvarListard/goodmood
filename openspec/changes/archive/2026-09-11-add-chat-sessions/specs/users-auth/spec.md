## MODIFIED Requirements

### Requirement: Session expiration

The session SHALL be valid for 90 days from issuance. Expiration SHALL be enforced by the browser through the `Max-Age` cookie attribute; the server does not track session issuance time (cookie-store is stateless). Сессия не продлевается активностью: отсчёт идёт от выпуска куки при логине; ring перезаписывает куку только при изменении session-данных. Logging out SHALL clear the session cookie: after `POST /logout` the browser stops sending the cookie.

#### Scenario: Session valid for 90 days

- **WHEN** `POST /login` succeeds
- **THEN** the `Set-Cookie` header for `gm-session` carries `Max-Age=7776000`
- **AND** the browser keeps sending the cookie for 90 days without re-login

#### Scenario: Logout clears the session cookie

- **GIVEN** an authenticated session
- **WHEN** `POST /logout` is sent with a valid CSRF token
- **THEN** the server responds with HTTP 302 to `/login`
- **AND** `gm-session` is cleared with `Max-Age=0`

#### Scenario: Expired cookie stops authenticating

- **GIVEN** a session cookie issued more than 90 days ago
- **WHEN** the browser makes a request
- **THEN** the cookie is no longer sent (browser Max-Age enforcement)
- **AND** the next request returns 302 /login

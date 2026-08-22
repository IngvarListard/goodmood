## ADDED Requirements

### Requirement: Feed page renders entries with rose-of-winds
The system SHALL render a `/feed` page as the landing page after login, displaying the user's entries grouped by date. The latest entry of the current day SHALL be rendered as a hero card with a 200×200px SVG radar. Other entries SHALL be rendered as compact cards with a state-label badge, timestamp, and axis values as text.

#### Scenario: Feed shows hero card with radar
- **GIVEN** the user is logged in and has entries today
- **WHEN** they open `/feed`
- **THEN** the latest entry today is rendered as a hero card
- **AND** the hero card contains a 200×200px SVG radar
- **AND** the hero card shows the state-label badge and timestamp
- **AND** the radar polygon reflects the entry's axis values

#### Scenario: Feed shows compact cards for other entries
- **GIVEN** the user has 3 entries today
- **WHEN** they open `/feed`
- **THEN** the latest entry is the hero card with radar
- **AND** the other 2 entries are compact cards (no radar)
- **AND** each compact card shows: «состояние» label, state badge, timestamp, and «энергия X · тревога Y · фокус Z» text

#### Scenario: Feed groups past days under date headers
- **GIVEN** the user has entries on 2026-08-21 and 2026-08-20
- **WHEN** they open `/feed`
- **THEN** past-day entries appear under date headers («21 августа», «20 августа»)
- **AND** past-day cards use `bg-base-300` (more compact than today's `bg-base-200`)

#### Scenario: Feed empty state
- **GIVEN** the user has no entries today
- **WHEN** they open `/feed`
- **THEN** an onboarding message «Как ты? Создай первую запись» is shown
- **AND** a button linking to `/check-in` is displayed `[ref: A3-q1]`

### Requirement: Feed is the landing page after login
The system SHALL redirect authenticated users from `/` and `/dashboard` to `/feed`.

#### Scenario: Root redirects to feed
- **GIVEN** an authenticated user navigates to `/`
- **WHEN** the server processes the request
- **THEN** the user is redirected to `/feed`

#### Scenario: Dashboard redirects to feed
- **GIVEN** an authenticated user navigates to `/dashboard`
- **WHEN** the server processes the request
- **THEN** the user is redirected to `/feed`

### Requirement: Floating action button on feed
The system SHALL render a single floating action button (FAB) on `/feed` linking to `/check-in`, positioned at the bottom-right corner above the mobile navigation bar.

#### Scenario: FAB visible on feed
- **GIVEN** the user is on `/feed`
- **WHEN** the page renders
- **THEN** a single `btn btn-primary btn-circle` FAB is visible
- **AND** it is positioned `fixed bottom-20 right-4`
- **AND** it links to `/check-in`
- **AND** no duplicate FAB or header button exists

## MODIFIED Requirements

### Requirement: Render entries page
The system SHALL render an HTML page containing an entry creation form with a template selector (morning/day/evening/event tabs), three mandatory range sliders (mood_score, energy, anxiety each 0–10), optional collapsible blocks (focus, sleep_hours, note, activity), and the list of stored diary entries when the entries page is requested by a browser. The page SHALL use dark theme as default (`data-theme="dark"`).

**Note:** The form+list combined page at `GET /entries` is replaced by separate `/feed` (list) and `/check-in` (form) pages. `GET /entries` now redirects to `/feed`.

#### Scenario: Page displays new form and list
- **WHEN** a user opens `GET /entries` in a browser
- **THEN** the server responds with HTTP 308 redirect to `/feed`
- **AND** the form is available at `/check-in`
- **AND** the list is available at `/feed`

#### Scenario: Empty list
- **WHEN** a user opens `GET /feed` in a browser and there are no stored entries
- **THEN** the page shows the onboarding message with a link to `/check-in`
- **AND** no empty-list guilt message is shown

## REMOVED Requirements

### Requirement: Add entry without page reload
**Reason:** The htmx inline-insert behavior is replaced by a post-save redirect to `/feed` (the new landing). The form now lives on a separate `/check-in` page; after successful submission the user is redirected to the feed to see the new entry in context.
**Migration:** The form's `hx-post="/entries"` endpoint remains; the `_ on htmx:afterRequest` now sets `window.location to '/feed'` instead of swapping into `#entries-list`.

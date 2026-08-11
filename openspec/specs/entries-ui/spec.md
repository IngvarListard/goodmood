# Entries UI Specification

## Purpose
Render the diary entries page with an entry creation form and the entry list, and add entries via htmx without full page reloads.

## Requirements

### Requirement: Render entries page
The system SHALL render an HTML page containing an entry creation form with fields `activity`, `effect`, `mood_score`, `sleep_hours` and the list of stored diary entries when the entries page is requested by a browser. The page SHALL be wrapped in the common layout (html/head/body, navigation, CDN scripts).

#### Scenario: Page displays form and list
- **WHEN** a user opens `GET /entries` in a browser and the diary contains entries
- **THEN** the page shows the entry creation form
- **AND** the page shows the list of existing entries
- **AND** the page includes the desktop sidebar navigation
- **AND** the page includes the mobile bottom bar navigation

#### Scenario: Empty list
- **WHEN** a user opens `GET /entries` in a browser and there are no stored entries
- **THEN** the page shows the entry creation form
- **AND** the page shows an empty-state message in place of the list
- **AND** the page includes the desktop sidebar navigation
- **AND** the page includes the mobile bottom bar navigation

### Requirement: Add entry without page reload
The system SHALL allow adding a diary entry from the entries page via htmx so the new entry appears in the list without a full page reload.

#### Scenario: New entry appears in the list
- **WHEN** a user fills the form and submits it
- **THEN** htmx sends a `POST /entries` request with an `HX-Request: true` header
- **AND** the new entry appears in the list without a full page reload

#### Scenario: Validation error shown inline
- **WHEN** a user submits an invalid form with `mood_score` = 15 (outside the 0–10 range)
- **THEN** htmx receives an HTML fragment containing the validation error message
- **AND** the error message is shown in the form without a full page reload
- **AND** no entry is added

### Requirement: Mobile-friendly layout
The system SHALL keep the entries page readable and functional on a mobile viewport of 375px.

#### Scenario: Form and list usable at mobile width
- **WHEN** the entries page is rendered in a 375px viewport
- **THEN** the form and the list remain readable and usable without horizontal scrolling

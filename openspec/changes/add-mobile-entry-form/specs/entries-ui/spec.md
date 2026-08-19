# Entries UI Specification

## ADDED Requirements

### Requirement: Template selector rendered as tabs
The system SHALL render a template selector as DaisyUI `tabs tabs-boxed` with four tabs: morning, day, evening, event. The selected template SHALL be submitted as a hidden field `template`.

#### Scenario: Template tabs visible
- **WHEN** the form renders
- **THEN** four tabs are visible: «Утро», «День», «Вечер», «Событие»
- **AND** the first tab («Утро») is active by default

#### Scenario: Template value submitted with entry
- **WHEN** a user submits the form with «Вечер» tab selected
- **THEN** the POST request includes `template` = "evening"

### Requirement: Optional blocks rendered as DaisyUI collapses
The system SHALL render optional fields (focus, sleep_hours, note, activity) as DaisyUI `collapse collapse-arrow` components, all collapsed by default.

#### Scenario: All optional blocks collapsed on load
- **WHEN** the form first renders
- **THEN** sections for «Фокус», «Сон (часы)», «Заметка», «Активность» are collapsed
- **AND** clicking a section title expands that section
- **AND** clicking again collapses it

#### Scenario: Collapse does not require JavaScript
- **WHEN** a user clicks a collapse title
- **THEN** the section expands using pure CSS (DaisyUI collapse with hidden checkbox)
- **AND** no JavaScript or Hyperscript is needed for the collapse behavior

## MODIFIED Requirements

### Requirement: Render entries page
The system SHALL render an HTML page containing an entry creation form with a template selector (morning/day/evening/event tabs), three mandatory range sliders (mood_score, energy, anxiety each 0–10), optional collapsible blocks (focus, sleep_hours, note, activity), and the list of stored diary entries when the entries page is requested by a browser. The page SHALL use dark theme as default (`data-theme="dark"`).

#### Scenario: Page displays new form and list
- **WHEN** a user opens `GET /entries` in a browser and the diary contains entries
- **THEN** the page shows the entry creation form with template tabs, three range sliders, and collapsed optional blocks
- **AND** the page shows the list of existing entries
- **AND** the page uses dark theme (`data-theme="dark"` on body)

#### Scenario: Empty list
- **WHEN** a user opens `GET /entries` in a browser and there are no stored entries
- **THEN** the page shows the entry creation form
- **AND** the page shows an empty-state message in place of the list

### Requirement: Add entry without page reload
The system SHALL allow adding a diary entry from the entries page via htmx so the new entry appears at the top of the list without a full page reload.

#### Scenario: New entry appears in the list
- **WHEN** a user fills the form (core fields only) and submits it
- **THEN** htmx sends a `POST /entries` request with an `HX-Request: true` header
- **AND** the new entry appears at the top of the list via `hx-swap="afterbegin"`
- **AND** the form resets to its initial state
- **AND** no full page reload occurs

#### Scenario: New entry with optional fields appears in list
- **WHEN** a user fills core fields plus expands and fills sleep_hours and submits
- **THEN** the new entry appears in the list with sleep_hours value displayed
- **AND** the form resets (including collapsing all optional blocks)

#### Scenario: Validation error shown inline with soft styling
- **WHEN** a user submits an invalid form with `mood_score` = 15 (outside the 0–10 range)
- **THEN** htmx receives an HTML fragment containing the validation error message
- **AND** the error message uses `alert-warning` class (not `alert-error`)
- **AND** the error message is shown in the form without a full page reload
- **AND** no entry is added

### Requirement: Mobile-friendly layout
The system SHALL keep the entries page readable and functional on a mobile viewport of 375px with touch targets at least 44px in the primary interaction zones.

#### Scenario: Form and list usable at mobile width
- **WHEN** the entries page is rendered in a 375px viewport
- **THEN** the form and the list remain readable and usable without horizontal scrolling
- **AND** all form controls (range sliders, template tabs, submit button) are operable by touch
- **AND** submit button has `h-12` class (48px height)

#### Scenario: Range sliders have adequate touch targets
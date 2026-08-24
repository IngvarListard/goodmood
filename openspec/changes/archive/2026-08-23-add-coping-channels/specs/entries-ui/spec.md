# Entries UI Specification (Delta — Phase 4)

## Purpose

Обновления /feed и /check-in для Фазы 4: soft mode на /check-in (баннер предложения + упрощённая форма), подъём виджета инсайтов выше hero в плохом состоянии и баннер вечерней сводки на /feed. Поток «сохранил запись → редирект на /feed + OOB-toast» также уточняется (поведение описано в дельте `coping-channels`).

`[ref: A3-q3, A4-q1, OQ6, design.md Decision 14.4-14.5, 16]`

## MODIFIED Requirements

### Requirement: Render entries page
The system SHALL render an HTML page containing an entry creation form with a template selector (morning/day/evening/event tabs), three mandatory range sliders (mood_score, energy, anxiety each 0–10), optional collapsible blocks (focus, sleep_hours, note, activity), and the list of stored diary entries when the entries page is requested by a browser. The page SHALL use dark theme as default (`data-theme="dark"`). When the latest entry's `state_label` is `low` or `mixed`, the /check-in page SHALL render a non-blocking soft-mode banner above the form offering «мягкий режим» (form reduced to only `mood_score`) with an always-available «полная форма» escape; the form SHALL remain full by default (not forced).

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

#### Scenario: Soft-mode banner shown in low state
- **GIVEN** the latest entry has `state_label='low'`
- **WHEN** the user opens `/check-in`
- **THEN** a non-blocking banner «Тебе сейчас может быть непросто. Включить мягкий режим?» is shown above the form
- **AND** two buttons are present: «мягкий режим» and «полная форма»
- **AND** the full form is the default (no state is forced)

#### Scenario: Soft mode reduces form to mood only
- **GIVEN** the soft-mode banner is shown
- **WHEN** the user clicks «мягкий режим»
- **THEN** only the `mood_score` slider and «Сохранить» button are visible
- **AND** energy/anxiety sliders and optional blocks are hidden
- **AND** clicking «полная форма» restores the full form

#### Scenario: No soft-mode banner in non-low states
- **GIVEN** the latest entry has `state_label='balanced'`
- **WHEN** the user opens `/check-in`
- **THEN** no soft-mode banner is shown

### Requirement: Feed page renders entries with rose-of-winds
The system SHALL render a `/feed` page as the landing page after login, displaying the user's entries grouped by date. The latest entry of the current day SHALL be rendered as a hero card with a 200×200px SVG radar. Other entries SHALL be rendered as compact cards with a state-label badge, timestamp, and axis values as text. In low/mixed states the insights widget SHALL be rendered above the hero card (emphasis on advice over state fixation). The page SHALL render an evening summary banner at the top (before the «Лента» heading) when local time is ≥ 18:00, today has entries, and the summary has not been shown today (collapsible, dismissible for the day).

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

#### Scenario: Insights widget raised above hero in low state
- **GIVEN** the latest entry has `state_label='low'` or `mixed`
- **WHEN** they open `/feed`
- **THEN** the insights widget is rendered above the hero card (not below it)
- **AND** the hero card remains visible below the widget `[ref: design.md Decision 14.4]`

#### Scenario: Evening summary banner at top of feed
- **GIVEN** local time is ≥ 18:00, today has entries, and the summary was not shown today
- **WHEN** they open `/feed`
- **THEN** an `alert alert-info` banner «Сводка на завтра готова» is rendered before the «Лента» heading
- **AND** the banner expands on tap and dismisses for the day `[ref: A4-q1, OQ6]`

#### Scenario: No summary banner before 18:00
- **GIVEN** local time is before 18:00
- **WHEN** they open `/feed`
- **THEN** no evening summary banner is rendered
- **AND** no empty placeholder is shown
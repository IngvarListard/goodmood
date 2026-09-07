# entries Delta: add-js-radar-chart

## MODIFIED Requirements

### Requirement: Feed page renders entries with rose-of-winds
The system SHALL render a `/feed` page as the landing page after login, displaying the user's entries grouped by date. The latest entry of the current day SHALL be rendered as a hero card with a 200×200px radar chart rendered client-side (canvas + Chart.js, see "Read-only radar chart renders rose-of-winds"). Other entries SHALL be rendered as compact cards with a state-label badge, timestamp, and axis values as text. In low/mixed states the insights widget SHALL be rendered above the hero card (emphasis on advice over state fixation). The page SHALL render an evening summary banner at the top (before the «Лента» heading) when local time is ≥ 18:00, today has entries, and the summary has not been shown today (collapsible, dismissible for the day).

#### Scenario: Feed shows hero card with radar
- **GIVEN** the user is logged in and has entries today
- **WHEN** they open `/feed`
- **THEN** the latest entry today is rendered as a hero card
- **AND** the hero card contains a 200×200px canvas radar
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

## REMOVED Requirements

### Requirement: Read-only SVG radar renders rose-of-winds
**Reason**: Рендеринг радара переезжает с hand-rolled SVG на canvas + Chart.js (смена технологии отрисовки при сохранении данных, осей и read-only поведения); запрет JavaScript-библиотек устарел.
**Migration**: Заменяется требованием «Read-only radar chart renders rose-of-winds» (ADDED ниже) — те же оси, значения и aria-контракт, другая технология отрисовки.

## ADDED Requirements

### Requirement: Read-only radar chart renders rose-of-winds
The system SHALL render a read-only radar chart («роза ветров») on the feed page for the latest entry of the current day as a 200×200px `<canvas role="img">` drawn client-side by Chart.js (pinned 4.x via CDN). The server SHALL render the canvas element with: a server-generated `aria-label` containing the axis values («Роза ветров: энергия X, тревога Y, фокус Z»), and a `data-gm-radar` attribute with JSON payload `{labels, values}` where labels are i18n-generated axis names and values are the axis values (0–10, nil allowed for absent). The chart SHALL display 3 mandatory axes (energy, anxiety, focus) and optionally a 4th axis (mood_score), ordered so that energy points up and axes proceed clockwise (θ_i = −π/2 + 2π·i/n). Visual style: radial gradient fill from secondary to primary color, white point markers with primary-color border and soft glow, circular grid rings in low-opacity base-content color, hidden radial ticks (scale 0–10), i18n point labels.

#### Scenario: Radar renders 3-axis polygon
- **GIVEN** the latest entry today has energy=4, anxiety=7, focus=3, mood_score=nil
- **WHEN** the feed page renders the hero card
- **THEN** a canvas of 200×200px is rendered with `role="img"` and server-generated aria-label «Роза ветров: энергия 4, тревога 7, фокус 3»
- **AND** the `data-gm-radar` attribute contains JSON with 3 i18n labels and values [4,7,3]
- **AND** Chart.js draws a 3-vertex polygon after page load

#### Scenario: Radar renders 4-axis polygon with mood_score
- **GIVEN** the latest entry today has energy=4, anxiety=7, focus=3, mood_score=5
- **WHEN** the feed page renders the hero card
- **THEN** the `data-gm-radar` payload has 4 labels/values
- **AND** the polygon has 4 vertices (energy, anxiety, focus, mood_score)

#### Scenario: Radar redraws after htmx navigation
- **GIVEN** the user navigates via hx-boost links (feed → check-in → feed)
- **WHEN** the feed body is swapped by htmx
- **THEN** the new canvas is drawn (initializer listens to DOMContentLoaded and htmx:afterSettle)
- **AND** canvases are drawn exactly once (`data-gm-drawn` marker prevents redraw loops)

#### Scenario: Radar is read-only
- **GIVEN** the radar canvas is rendered on the feed page
- **WHEN** the user interacts with it
- **THEN** no interactive elements (sliders, inputs) are present within the chart
- **AND** editing is only possible via the `/check-in` form sliders

#### Scenario: Radar adapts to theme colors
- **GIVEN** the app uses DaisyUI dark theme (`data-theme="dark"`)
- **WHEN** the radar is drawn
- **THEN** fill gradient, point borders and glow use colors read from CSS custom properties (`--color-primary`, `--color-secondary`)
- **AND** grid and labels use base-content-derived colors
- **AND** the radar is visible on dark background without hardcoded black/white colors

#### Scenario: Degradation without JavaScript
- **GIVEN** JavaScript is disabled or the Chart.js CDN is blocked
- **WHEN** the feed page renders
- **THEN** the canvas remains empty without runtime errors breaking the page
- **AND** the axis values remain accessible via the hero card's `axes-line` text and metric chips

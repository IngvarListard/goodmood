# mood-states Specification

## Purpose
TBD - created by archiving change add-mood-states-rose. Update Purpose after archive.
## Requirements
### Requirement: Read-only SVG radar renders rose-of-winds
The system SHALL render a read-only inline SVG radar chart («роза ветров») on the feed page for the latest entry of the current day, displaying 3 mandatory axes (energy, anxiety, focus) and optionally a 4th axis (mood_score) as a polygon. The SVG SHALL be hand-rolled in hiccup2 without any JavaScript charting library.

#### Scenario: Radar renders 3-axis polygon
- **GIVEN** the latest entry today has energy=4, anxiety=7, focus=3, mood_score=nil
- **WHEN** the feed page renders the hero card
- **THEN** an inline SVG of 200×200px is rendered with a 3-vertex polygon
- **AND** the polygon points are computed via polar coordinates: `cx + r·cos(θ)`, `cy + r·sin(θ)` where `θ_i = -π/2 + 2π·i/3`
- **AND** axis labels («энергия», «тревога», «фокус») are visible near the outer vertices
- **AND** value numbers (4, 7, 3) are visible near the polygon vertices
- **AND** the polygon uses `hsl(var(--p))` fill with opacity 0.3 and stroke width 2
- **AND** no external JS charting library is loaded `[ref: A3-q2, A2-q1]`

#### Scenario: Radar renders 4-axis polygon with mood_score
- **GIVEN** the latest entry today has energy=4, anxiety=7, focus=3, mood_score=5
- **WHEN** the feed page renders the hero card
- **THEN** the SVG polygon has 4 vertices (energy, anxiety, focus, mood_score)
- **AND** `θ_i = -π/2 + 2π·i/4` (4 axes, 90° apart)

#### Scenario: Radar is read-only
- **GIVEN** the radar SVG is rendered on the feed page
- **WHEN** the user interacts with it
- **THEN** no interactive elements (sliders, inputs) are present within the SVG
- **AND** editing is only possible via the `/check-in` form sliders

#### Scenario: Radar adapts to theme colors
- **GIVEN** the app uses DaisyUI dark theme (`data-theme="dark"`)
- **WHEN** the radar renders
- **THEN** axis lines and labels use `currentColor` (inherits base-content)
- **AND** the value polygon uses `hsl(var(--p))` (primary theme color)
- **AND** the radar is visible on dark background without hardcoded black/white colors

### Requirement: Rule-based state label derivation
The system SHALL derive a human-readable state label from the energy and anxiety axis values using a deterministic rule-based function (no AI). The function SHALL return one of 6 keywords: `:state/mixed`, `:state/anxiety`, `:state/elevated`, `:state/low`, `:state/balanced`, `:state/neutral`. Rules SHALL be evaluated first-match-wins in specificity-descending order.

#### Scenario: Mixed state detected
- **GIVEN** energy=8, anxiety=7
- **WHEN** the state label is derived
- **THEN** the result is `:state/mixed` (rule: `energy >= 7 AND anxiety >= 6`)

#### Scenario: Anxiety state detected
- **GIVEN** energy=4, anxiety=7
- **WHEN** the state label is derived
- **THEN** the result is `:state/anxiety` (rule: `anxiety >= 6`, after mixed rule fails)

#### Scenario: Elevated state detected
- **GIVEN** energy=9, anxiety=2
- **WHEN** the state label is derived
- **THEN** the result is `:state/elevated` (rule: `energy >= 7`)

#### Scenario: Low state detected
- **GIVEN** energy=2, anxiety=3
- **WHEN** the state label is derived
- **THEN** the result is `:state/low` (rule: `energy <= 3`)

#### Scenario: Balanced state detected
- **GIVEN** energy=5, anxiety=4
- **WHEN** the state label is derived
- **THEN** the result is `:state/balanced` (rule: `energy 4-6 AND anxiety 4-5`)

#### Scenario: Neutral state detected
- **GIVEN** energy=5, anxiety=2
- **WHEN** the state label is derived
- **THEN** the result is `:state/neutral` (default/else branch)

#### Scenario: Focus axis does not affect label
- **GIVEN** energy=4, anxiety=7, focus=10
- **WHEN** the state label is derived
- **THEN** the result is `:state/anxiety` (focus is not used in rule evaluation) `[ref: A3-q2, A1-q2]`

### Requirement: User can override state label
The system SHALL allow the user to manually change the state label on an entry by selecting from the same 6 keyword options. The override SHALL be persisted in the `state_label` column; when `state_label` is non-NULL it SHALL be displayed as-is and SHALL NOT be recomputed from rules.

#### Scenario: User overrides auto-derived label
- **GIVEN** an entry has energy=4, anxiety=7, state_label=NULL (auto-derived as `:state/anxiety`)
- **WHEN** the user selects `:state/low` from the label dropdown
- **THEN** `state_label` is persisted as `:state/low`
- **AND** the feed displays «спад» (the override), not «тревога» (auto-derived)

#### Scenario: Null state label auto-derives on render
- **GIVEN** an entry has energy=9, anxiety=2, state_label=NULL
- **WHEN** the feed renders the entry card
- **THEN** the label is auto-derived as `:state/elevated` and displayed as «подъём»
- **AND** no value is read from `state_label` (it is NULL) `[ref: A3-q2, A1-q2]`

### Requirement: State label is text, not color or icon
The system SHALL render state labels as localized text (keyword → `i18n/t`) using a DaisyUI badge (`badge-secondary`), not as a color swatch, icon, or emoji.

#### Scenario: Label rendered as text badge
- **GIVEN** an entry with state label `:state/mixed`
- **WHEN** the feed renders the entry card
- **THEN** a `<span class="badge badge-secondary">` contains the localized text «смешанное»
- **AND** no emoji, icon, or color-only indicator is used for the label

### Requirement: State is not bipolar good/bad
The system SHALL NOT reduce state to a binary «good mood» vs «bad mood» dimension; the multi-axis radar and the 6-label set reflect the spectral nature of affective instability.

#### Scenario: Mixed state logged without forced binary choice
- **GIVEN** a user feels simultaneously high energy and high anxiety
- **WHEN** they create an entry with energy=8, anxiety=7
- **THEN** the state label `:state/mixed` is derived
- **AND** the system does not force the user to choose «good» or «bad» `[ref: A1-q2, A2-q7]`

### Requirement: AI-proposed state deferred to Phase 5
The system SHALL NOT propose a state distribution or label via AI in Phase 2. AI-proposed rose and AI-override of user-corrected rose are deferred to Phase 5 (`add-ai-correlations`).

#### Scenario: No AI proposal in Phase 2
- **GIVEN** a user creates an entry in Phase 2
- **WHEN** the entry is saved and rendered on the feed
- **THEN** the state label is derived purely from the rule-based function
- **AND** no AI model is invoked for state proposal


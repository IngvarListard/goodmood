# Entries Specification

## ADDED Requirements

### Requirement: Daily axis means for the feed time-series chart

The system SHALL compute, for a bounded period window (3, 7 or 30 days ending today), a per-day mean for each tracked axis (`mood_score`, `energy`, `anxiety`, `focus`, `aggression`) and a composite series. The per-day mean of an axis SHALL be the arithmetic mean of that axis's non-nil values for the day, or nil when the day has no value for that axis. The composite value of a day SHALL be the mean over the available axes of `[mood_score, energy, focus, 10−anxiety, 10−aggression]`. The aggregation SHALL be computed from a database query bounded by the window (`date >= today − (window − 1)`), not from all of the user's entries. A reusable per-day-mean helper SHALL be retained.

#### Scenario: Per-day mean across multiple entries

- **GIVEN** за день две записи с `energy` = 4 и `energy` = 8
- **WHEN** вычисляется ряд по дням
- **THEN** значение `energy` за этот день = 6
- **AND** день с единственной записью даёт значение самой записи

#### Scenario: Missing day is a gap

- **GIVEN** в окне из 7 дней записи есть только в 2 днях
- **WHEN** строится ряд по дням
- **THEN** у остальных 5 дней значение соответствующей оси = nil (разрыв линии)

#### Scenario: Composite inverts anxiety and aggression

- **GIVEN** за день `mood_score`=6, `energy`=8, `focus`=6, `anxiety`=2, `aggression`=4
- **WHEN** вычисляется составной ряд
- **THEN** значение = среднее [6, 8, 6, 10−2, 10−4] = 6.8

#### Scenario: Window is bounded

- **GIVEN** у пользователя есть записи за последний год
- **WHEN** запрашивается окно в 3 дня
- **THEN** в выборку из БД попадают только записи за последние 3 дня
- **AND** все записи пользователя в память не загружаются

### Requirement: Time-series chart canvas contract

The system SHALL render the feed hero time-series chart as a `<canvas role="img" data-gm-chart="...">` whose JSON payload is `{labels: [ISO dates], datasets: [{key, label, values, colorVar}]}` with one dataset per tracked axis (`mood_score`, `energy`, `anxiety`, `focus`, `aggression`) plus one composite dataset. The composite dataset SHALL be last. `label` SHALL be i18n-generated; `values` SHALL contain numbers or null (nil for a day without data); `colorVar` SHALL name a CSS custom property. The server SHALL generate an i18n `aria-label`. The client renderer SHALL draw a Chart.js line chart with X = days and Y = 0–10, reading each dataset's color from the CSS variable named by `colorVar`, with no hardcoded colors.

#### Scenario: Canvas payload lists axes plus composite last

- **GIVEN** у пользователя есть записи за неделю по всем осям
- **WHEN** рендерится hero-карточка `/feed`
- **THEN** canvas содержит `data-gm-chart` с `labels` = 7 ISO-дат
- **AND** `datasets` содержит `mood_score`, `energy`, `anxiety`, `focus`, `aggression` и составной ряд
- **AND** составной ряд — последний в массиве `datasets`

#### Scenario: Colors come from CSS variables

- **GIVEN** canvas отрисован
- **WHEN** Chart.js создаёт линии
- **THEN** цвет каждой линии берётся из CSS-переменной `colorVar` (например `--gm-metric-energy`, `--gm-metric-anxiety`, `--gm-metric-focus`, `--gm-metric-aggression`, `--color-primary` для составного)
- **AND** в JS нет хардкода цветов

#### Scenario: Empty day is a gap

- **GIVEN** в окне есть день без значений оси
- **WHEN** рендерится график
- **THEN** соответствующее значение в `values` = null
- **AND** линия разрывается на этом дне (`spanGaps: false`)

#### Scenario: Insufficient data fallback

- **GIVEN** во всём окне меньше 2 дней с любыми данными
- **WHEN** рендерится hero-карточка
- **THEN** вместо графика показывается приглушённый fallback-текст
- **AND** страница не падает с ошибкой

## MODIFIED Requirements

### Requirement: State is not bipolar good/bad

The system SHALL NOT reduce state to a binary «good mood» vs «bad mood» dimension; the multi-axis time-series chart and the 6-label set reflect the spectral nature of affective instability.

#### Scenario: Mixed state logged without forced binary choice

- **GIVEN** a user feels simultaneously high energy and high anxiety
- **WHEN** they create an entry with energy=8, anxiety=7
- **THEN** the state label `:state/mixed` is derived
- **AND** the system does not force the user to choose «good» or «bad» `[ref: A1-q2, A2-q7]`

### Requirement: AI-proposed state deferred to Phase 5

The system SHALL NOT propose a state distribution or label via AI in Phase 2. AI-proposed state and AI-override of a user-corrected state are deferred to Phase 5 (`add-ai-correlations`).

#### Scenario: No AI proposal in Phase 2

- **GIVEN** a user creates an entry in Phase 2
- **WHEN** the entry is saved and rendered on the feed
- **THEN** the state label is derived purely from the rule-based function
- **AND** no AI model is invoked for state proposal

## REMOVED Requirements

### Requirement: Feed page renders entries with rose-of-winds

Радар удалён полностью (решение владельца); поведение страницы ленты описано в домене `feed-timeline`.

### Requirement: Read-only radar chart renders rose-of-winds

Роза ветров удалена целиком и заменена линейным графиком по времени (см. `entries` → «Time-series chart canvas contract»).

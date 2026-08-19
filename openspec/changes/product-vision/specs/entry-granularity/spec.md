# Entry Granularity Specification (Hypothesis)

## Purpose

Модель данных и UX для поддержки нескольких точек состояния в день и, в финальной форме, «периодов состояния» с явным началом/концом. Одна запись в день не ловит внутридневные смены настроений, критичные для пользователей со спектральной аффективной нестабильностью.

**Статус:** гипотеза. Вариант (а) — Фаза 2; вариант (в) — Фаза 6. Модель данных для периода состояния — open question OQ7. Финализируется поэтапно в change'ах `add-mood-states-rose` и `add-entry-granularity-periods`.

## ADDED Requirements

### Requirement: Multiple entries per day (Phase 2 — variant a)
The system SHALL allow the user to create multiple entries per day, each with its own timestamp, without an enforced schedule.

#### Scenario: User logs two states in one day
- **GIVEN** пользователь утром создал запись с розой A
- **WHEN** вечером создаёт ещё одну запись с розой B
- **THEN** обе записи сохраняются с разными timestamp
- **AND** день отображается как последовательность состояний `[ref: A3-q6, A1-q2]`

### Requirement: No mandatory entry cadence
The system SHALL NOT enforce a fixed cadence (e.g. «must log every morning and evening»); entries are created on demand.

#### Scenario: User logs only once some days, multiple times others
- **GIVEN** пользователь вчера сделал одну запись, сегодня три
- **WHEN** открывает ленту
- **THEN** обе истории корректны и не помечены как «неполные» `[ref: A2-q3]`

### Requirement: State period with explicit start/end (Phase 6 — variant v)
The system SHALL support «state periods» — explicitly marked begin and end of a sustained state (e.g. «depressive episode», «hypomanic period», «anxious week») with arbitrary length.

#### Scenario: User marks start of a depressive period
- **GIVEN** пользователь чувствует начало спада
- **WHEN** отмечает «начало периода: спад»
- **THEN** создаётся `state_period` с `started_at`, без `ended_at`
- **AND** последующие записи могут привязываться к этому периоду `[ref: A3-q6]`

#### Scenario: User closes a period
- **GIVEN** у пользователя открыт период «спад»
- **WHEN** отмечает «конец периода»
- **THEN** `state_period.ended_at` заполняется
- **AND** период доступен для ретроспективного анализа `[ref: A3-q6]`

### Requirement: Entries can be linked to a state period
The system SHALL allow entries to be optionally linked to an active state period, so retrospective analysis can group entries by episode.

#### Scenario: Entry created during an active period
- **GIVEN** у пользователя активен период «подъём»
- **WHEN** создаёт новую запись
- **THEN** запись может быть автоматически или вручную привязана к этому периоду
- **AND** фильтр «показать записи из периода X» доступен в ретроспективе `[ref: A3-q6, OQ7]`

### Requirement: Data model foresees periods from Phase 2
The data model introduced in Phase 2 SHALL include an optional `state_period_id` field on entries, so Phase 6 can add periods without a breaking schema change.

#### Scenario: Phase 2 entry has nullable state_period_id
- **GIVEN** Phase 2 миграция применена
- **WHEN** создаётся запись без активного периода
- **THEN** `state_period_id` остаётся `nil`
- **AND** в Phase 6 поле начинает заполняться без миграции схемы `[ref: OQ7]`

### Requirement: Periods are user-defined, not auto-detected (in initial Phase 6)
The system SHALL NOT auto-create state periods in Phase 6; periods are user-initiated. Auto-detection of episode onset is a separate Phase 7 AI feature with explicit guardrails.

#### Scenario: User in a 3-day low hasn't marked a period
- **GIVEN** пользователь 3 дня в плохом состоянии, но не отметил период
- **WHEN** смотрит ленту
- **THEN** система не создаёт период автоматически
- **AND** может предложить (soft hint) отметить период, но не настаивает `[ref: A3-q6, design.md AI Phase 7 guardrails]`

## ADDED Requirements

### Requirement: State period with explicit start/end (Phase 6)
The system SHALL support «state periods» — explicitly marked begin and end of a sustained state (e.g. «depressive episode», «hypomanic period», «anxious week») with arbitrary length. Periods SHALL be user-defined (not auto-created); auto-detection of episode onset is a separate Phase 7 AI feature with explicit guardrails.

#### Scenario: User marks start of a depressive period
- **WHEN** пользователь чувствует начало спада и нажимает «начать период: спад»
- **THEN** создаётся `state_period` с `started_at`, без `ended_at`
- **AND** последующие записи автоматически привязываются к этому периоду

#### Scenario: User closes a period
- **WHEN** у пользователя открыт период «спад» и он нажимает «закрыть период»
- **THEN** `state_period.ended_at` заполняется
- **AND** период доступен для ретроспективного анализа

#### Scenario: Periods are user-defined, not auto-detected
- **GIVEN** пользователь 3 дня в плохом состоянии, но не отметил период
- **WHEN** смотрит ленту
- **THEN** система не создаёт период автоматически
- **AND** может предложить (soft hint) отметить период, но не настаивает

### Requirement: Entries can be linked to a state period
The system SHALL allow entries to be optionally linked to an active state period, so retrospective analysis can group entries by episode. Entry created during an active period SHALL be automatically linked via `entries.state_period_id`.

#### Scenario: Entry created during an active period
- **WHEN** у пользователя активен период «подъём» и он создаёт новую запись
- **THEN** запись сохраняется с `state_period_id` = id активного периода
- **AND** фильтр «показать записи из периода X» доступен в ретроспективе
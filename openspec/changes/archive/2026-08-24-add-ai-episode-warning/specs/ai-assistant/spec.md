## ADDED Requirements

### Requirement: Episode onset warning (Phase 7, opt-in, guarded)
The system SHALL warn the user about a possible onset of a depressive or (hypo)manic episode based on trend analysis, but only when ALL of the following hold: explicit opt-in, confidence above threshold (> 0.75), explainable reasoning, easy disable, and false-alarm feedback loop. Opt-in SHALL be OFF by default. The warning SHALL use supportive, non-alarming copy.

#### Scenario: AI detects possible depressive onset
- **WHEN** пользователь opted-in в предупреждения эпизодов (`episode_warning_enabled` = on)
- **AND** AI обнаруживает паттерн, похожий на начало депрессивного/маниакального эпизода, с уверенностью > 0.75
- **THEN** пользователь получает предупреждение `episode-warning` с объяснением паттерна (какие данные, какой тренд)
- **AND** предупреждение можно выключить в один клик (в самом предупреждении и в настройках)
- **AND** пользователь может сообщить «ложная тревога» для обратной связи модели

#### Scenario: Low-confidence pattern does not trigger warning
- **WHEN** AI видит паттерн, похожий на начало мании, но уверенность ≤ 0.75
- **THEN** предупреждение НЕ показывается
- **AND** паттерн логируется (в БД) для будущего уточнения модели

#### Scenario: Opt-in is OFF by default
- **WHEN** пользователь ещё не включал предупреждения эпизодов
- **THEN** даже при выраженном паттерне предупреждение `episode-warning` не показывается

#### Scenario: Crisis signs → resource, not warning
- **WHEN** пользователь в записи/чате использует признаки кризиса (например «не хочу жить»)
- **THEN** система показывает ресурс (телефон доверия / 112 / 03 / напоминание о помощи), НЕ предупреждение об эпизоде

### Requirement: Episode warning explainability and copy (Phase 7)
The warning SHALL include a human-readable explanation of the pattern (trend data: energy, sleep, mood) and use supportive, non-alarming language.

#### Scenario: Warning explains the pattern
- **WHEN** пользователь получает предупреждение
- **THEN** оно объясняет паттерн («последние N дней: энергия растёт, сон падает — похоже на начало мании»)
- **AND** формулировка поддерживающая, без тревожных слов (не «ВНИМАНИЕ! Возможен эпизод!», без «срочно», «опасно»)

### Requirement: Episode warning opt-out and feedback (Phase 7)
The system SHALL allow the user to disable episode warnings in one click (from settings and from the warning itself) and to report «false alarm» which SHALL be logged to the database for model improvement.

#### Scenario: User disables warnings in one click
- **WHEN** пользователь нажимает «выключить» на предупреждении (или toggle в настройках)
- **THEN** предупреждения эпизодов выключаются немедленно
- **AND** настройка сохраняется (opt-out persists)

#### Scenario: False-alarm feedback is logged
- **WHEN** пользователь нажимает «ложная тревога» на предупреждении
- **THEN** feedback записывается в `episode_warnings.feedback`
- **AND** предупреждение скрывается (dismissed)
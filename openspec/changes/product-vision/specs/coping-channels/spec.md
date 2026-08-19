# Coping Channels Specification (Hypothesis)

## Purpose

Каналы доставки советов и инсайтов пользователю в нужный момент. Три канала в порядке приоритета внедрения: push-уведомления (утро/день/вечер), кнопка «что мне делать сейчас», подсказки при заполнении записи. Вечерние инсайты на «завтра» — отдельная ценность внутри канала уведомлений.

**Статус:** гипотеза. Формат вечерних инсайтов — open question OQ6. Финализируется в имплементационном change `add-coping-channels` (Фаза 4).

## ADDED Requirements

### Requirement: Scheduled push notifications
The system SHALL send push notifications at configurable times (default: morning / midday / evening) with relevant insights for the current or predicted state.

#### Scenario: Morning notification with relevant insight
- **GIVEN** у пользователя есть инсайт с контекстом, близким к утреннему состоянию
- **WHEN** наступает утреннее время уведомления
- **THEN** отправляется push с этим инсайтом или краткой сводкой `[ref: A3-q3]`

#### Scenario: User disables a notification slot
- **GIVEN** пользователь не хочет вечерних уведомлений
- **WHEN** отключает вечерний слот в настройках
- **THEN** вечерние push не приходят
- **AND** утренние и дневные продолжают работать `[ref: A3-q3]`

### Requirement: On-demand «what to do now» button
The system SHALL provide an on-demand action (button / entry point) that surfaces relevant insights for the current state immediately.

#### Scenario: User opens «what now» in low state
- **GIVEN** пользователь в плохом состоянии открывает «ленту/мой день»
- **WHEN** нажимает «что мне делать сейчас»
- **THEN** система показывает релевантные инсайты и/или AI-совет
- **AND** интерфейс не перегружает: одна-две карточки, не список `[ref: A3-q3]`

### Requirement: In-form hints
The system SHALL surface a relevant hint at the moment of filling an entry, based on the values being entered. Hints are non-blocking and dismissible.

#### Scenario: User enters high anxiety
- **GIVEN** пользователь заполняет ось тревоги = 4
- **WHEN** отправляет запись
- **THEN** может увидеть релевантный инсайт для высокого-тревожного состояния
- **AND** подсказка не блокирует отправку записи `[ref: A3-q3]`

### Requirement: Evening «insights for tomorrow» summary
The system SHALL produce an evening summary that prepares the user for the next day based on the current state and recent trend. Format — open question OQ6.

#### Scenario: Evening summary delivered
- **GIVEN** пользователь заполнял записи в течение дня
- **WHEN** наступает вечернее время
- **THEN** формируется сводка «на завтра» с инсайтами и/или AI-советами
- **AND** сводка доступна и в приложении, и (опционально) через push `[ref: A4-q1, OQ6]`

### Requirement: Soft mode in low states
The system SHALL offer a «soft mode» that minimises input burden and emphasises coping over recording pain when the user is in a depressed or mixed state (mitigation of rumination risk).

#### Scenario: User in depression opens the app
- **GIVEN** последняя запись пользователя — депрессивное состояние
- **WHEN** пользователь открывает приложение
- **THEN** система предлагает мягкий режим (минимальный ввод, акцент на совете)
- **AND** пользователь может отказаться и заполнить полную форму `[ref: design.md Risks — rumination]`

### Requirement: No pressure channels
The system SHALL NOT use notification frequency or content to pressure the user into filling entries; notifications carry value (insights/advice), not reminders-to-fill.

#### Scenario: User skips a day — no guilt notifications
- **GIVEN** пользователь не заполнял записи сегодня
- **WHEN** наступает вечернее уведомление
- **THEN** уведомление содержит инсайт/совет, а не «вы не заполнили дневник» `[ref: A2-q3]`

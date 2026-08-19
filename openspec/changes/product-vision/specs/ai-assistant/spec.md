# AI Assistant Specification (Hypothesis)

## Purpose

Поэтапно внедряемый AI-помощник: ищет корреляции, предлагает название/описание состояния, генерирует советы на основе своих заметок, позже — новые советы и чат, и наконец (с guardrails) — предупреждение о возможном начале эпизода. Пользователь выражает готовность доверять всем функциям, но система внедряет их поэтапно с guardrails для самых рискованных.

**Статус:** гипотеза. Провайдер, модель, guardrails — open question OQ8 (пофазно). Финализируется в change'ах `add-ai-correlations` (Фаза 5), `add-ai-chat-and-periods` (Фаза 6), `add-ai-episode-warning` (Фаза 7).

## ADDED Requirements

### Requirement: Correlation discovery (Phase 5)
The system SHALL run background analysis to discover correlations between factors (sleep, medications, activity, time of day, season) and the user's rose-of-winds states, and surface findings to the user.

#### Scenario: AI finds sleep-mood correlation
- **GIVEN** у пользователя накоплено ≥ N записей с данными о сне и состоянии
- **WHEN** AI обнаруживает значимую корреляцию «сон < 6ч → рост тревоги на следующий день»
- **THEN** пользователю показывается находка с объяснением и уровнем уверенности
- **AND** пользователь может отметить «не релевантно» или «уже знал» `[ref: A3-q4, A4-q1]`

### Requirement: AI-proposed state label (Phase 5)
The system SHALL propose a human-readable label / description for the current state based on the rose-of-winds distribution and historical patterns, when the user has not manually set a label.

#### Scenario: AI proposes «anxious introvert» label
- **GIVEN** текущая роза ветров: высокая тревога, низкая энергия, низкий фокус
- **WHEN** AI предлагает ярлык
- **THEN** пользователь видит предложение и может принять, изменить или отклонить
- **AND** последнее слово — за пользователем `[ref: A3-q2, A3-q4]`

### Requirement: AI-generated advice from own insights (Phase 5)
The system SHALL generate advice for the current state based on the user's own historical insights, with explicit grounding in source insights.

#### Scenario: AI synthesises advice from past insights
- **GIVEN** у пользователя есть 3 инсайта с близким к текущему контекстом
- **WHEN** AI генерирует совет
- **THEN** совет основан на этих инсайтах
- **AND** показываются ссылки на исходные инсайты («основано на твоём инсайте от …»)
- **AND** пользователь может «пожаловаться на совет» если он некорректен `[ref: A3-q4, A2-q4]`

### Requirement: AI-generated novel advice (Phase 6, opt-in)
The system MAY generate advice that goes beyond the user's own insights (e.g. DBT/CBT techniques, general coping strategies), but this SHALL be opt-in and clearly marked as «not from your past».

#### Scenario: User opts into novel advice
- **GIVEN** пользователь включил «новые советы от AI» в настройках
- **WHEN** для текущего состояния нет своих инсайтов
- **THEN** AI может предложить технику из общей базы
- **AND** совет помечен «не из твоих записей»
- **AND** пользователь может выключить эту функцию в любой момент `[ref: A3-q4, design.md guardrails]`

### Requirement: AI chat in low states (Phase 6)
The system SHALL provide an AI chat available on-demand, especially useful in low / mixed states when the user needs immediate coping support.

#### Scenario: User opens chat in anxiety
- **GIVEN** пользователь в тревожном состоянии открывает чат
- **WHEN** описывает, что чувствует
- **THEN** AI отвечает, опираясь на историю пользователя и текущую розу ветров
- **AND** чат не заменяет профессиональную помощь; система напоминает об этом при признаках кризиса `[ref: A3-q4]`

### Requirement: Episode onset warning (Phase 7, opt-in, guarded)
The system SHALL warn the user about a possible onset of a depressive or (hypo)manic episode based on trend analysis, but only when ALL of the following hold: explicit opt-in, confidence above threshold, explainable reasoning, easy disable, and false-alarm feedback loop.

#### Scenario: AI detects possible depressive onset
- **GIVEN** пользователь opted-in в предупреждения эпизодов
- **WHEN** AI обнаруживает паттерн, похожий на начало депрессивного эпизода, с уверенностью выше порога
- **THEN** пользователь получает предупреждение с объяснением паттерна
- **AND** предупреждение можно выключить в один клик
- **AND** пользователь может сообщить «ложная тревога» для обратной связи модели `[ref: A3-q4, design.md Risks]`

#### Scenario: Low-confidence pattern does not trigger warning
- **GIVEN** AI видит паттерн, похожий на начало мании, но уверенность ниже порога
- **WHEN** оценивает, предупреждать ли
- **THEN** предупреждение НЕ отправляется
- **AND** паттерн логируется для будущего уточнения модели `[ref: design.md Risks — AI anxiety amplification]`

### Requirement: Explainability
Every AI output that affects the user (correlation, label, advice, warning) SHALL include a human-readable explanation of how it was derived.

#### Scenario: User asks «why this advice?»
- **GIVEN** пользователь получил AI-совет
- **WHEN** нажимает «почему этот совет?»
- **THEN** видит объяснение: на каких инсайтах/корреляциях/данных он основан
- **AND** может перейти к источникам `[ref: A4-q3, design.md guardrails]`

### Requirement: Feedback and opt-out
The system SHALL provide, for every AI function, a way to give feedback («not relevant» / «incorrect» / «false alarm») and to disable that specific function without disabling others.

#### Scenario: User disables episode warnings only
- **GIVEN** пользователь хочет отключить предупреждения эпизодов, но оставить корреляции
- **WHEN** идёт в настройки AI
- **THEN** может выключить именно «предупреждения эпизодов»
- **AND** остальные AI-функции продолжают работать `[ref: A3-q4, design.md guardrails]`

### Requirement: AI operates on user's own data only (Phases 5–6)
The system SHALL NOT use other users' data for personalised advice in Phases 5–6; AI personalisation is based solely on the user's own entries, insights, and medication logs.

#### Scenario: AI generates advice
- **GIVEN** AI генерирует персональный совет
- **WHEN** формирует ответ
- **THEN** использует только данные текущего пользователя
- **AND** чужие данные не участвуют в персонализации `[ref: proposal Non-goals, A3-q4]`

# ai-assistant Specification

## Purpose
TBD - created by archiving change add-ai-correlations. Update Purpose after archive.
## Requirements
### Requirement: Correlation discovery (Phase 5)
The system SHALL run background analysis to discover correlations between factors (sleep, medications, activity, time of day, season) and the user's rose-of-winds states, and surface findings to the user. Analysis runs when the user has accumulated ≥ 14 days of entries with sleep/state data. Findings are cached in `ai_findings` with type `correlation`, a confidence level (high/medium/low), and source references.

#### Scenario: AI finds sleep-mood correlation
- **WHEN** у пользователя накоплено ≥ 14 записей с данными о сне и состоянии, и AI обнаруживает значимую корреляцию «сон < 6ч → рост тревоги на следующий день»
- **THEN** пользователю на /feed показывается находка в секции `ai-correlations` с объяснением (почему найден паттерн) и уровнем уверенности
- **AND** пользователь может отметить находку «не релевантно» или «уже знал»

#### Scenario: Correlation requires enough data
- **WHEN** у пользователя меньше 14 дней записей с данными о сне и состоянии
- **THEN** AI-анализ корреляций не запускается и секция `ai-correlations` не показывает находок

### Requirement: AI-proposed state label (Phase 5)
The system SHALL propose a human-readable label / description for the current state based on the rose-of-winds distribution and historical patterns, when the user has not manually set a label. The proposal is shown near the rule-based `state_label` and the user can accept, change, or reject it; the final word belongs to the user.

#### Scenario: AI proposes «тревожный интроверт» label
- **WHEN** текущая роза ветров: высокая тревога, низкая энергия, низкий фокус, и у пользователя нет ручного ярлыка
- **THEN** на /feed рядом с `state_label` показывается AI-предложение `ai-state-label` (текст + объяснение)
- **AND** пользователь может принять, изменить или отклонить предложение
- **AND** последнее слово — за пользователем (ручной ярлык имеет приоритет)

### Requirement: AI-generated advice from own insights (Phase 5)
The system SHALL generate advice for the current state based on the user's own historical insights with similar context, with explicit grounding in source insights. Advice is shown with links to the originating insights and a «пожаловаться» button.

#### Scenario: AI synthesises advice from past insights
- **WHEN** у пользователя есть 3 инсайта с близким к текущему контекстом и нет ручного ярлыка для текущего состояния
- **THEN** AI генерирует совет на основе этих инсайтов и показывает его в секции `ai-advice` на /feed
- **AND** показываются ссылки на исходные инсайты («из твоего инсайта от …»)
- **AND** пользователь может нажать «пожаловаться на совет», если он некорректен

#### Scenario: User reports advice as incorrect
- **WHEN** пользователь нажимает «пожаловаться» на AI-совете
- **THEN** совет скрывается
- **AND** feedback записывается в БД (сохраняется для улучшения модели)

### Requirement: Explainability (Phase 5)
Every AI output that affects the user (correlation, label, advice) SHALL include a human-readable explanation of how it was derived, with links to source data/insights where applicable.

#### Scenario: User asks «почему этот совет?»
- **WHEN** пользователь получил AI-совет и нажимает «почему этот совет?»
- **THEN** видит объяснение: на каких инсайтах/корреляциях/данных он основан
- **AND** может перейти к источникам (исходным инсайтам)

### Requirement: Feedback and opt-out (Phase 5)
The system SHALL provide, for every Phase 5 AI function (correlations, labels, advice), a way to give feedback («не релевантно» / «уже знал» / «пожаловаться») and to disable that specific function without disabling others, via settings.

#### Scenario: User disables correlations only
- **WHEN** пользователь хочет отключить корреляции, но оставить советы из своих инсайтов
- **THEN** в настройках AI может выключить именно «корреляции»
- **AND** остальные AI-функции продолжают работать

#### Scenario: User disables all AI
- **WHEN** пользователь выключает master-toggle AI в настройках
- **THEN** все AI-секции (корреляции, ярлыки, советы) скрываются с /feed

### Requirement: AI operates on user's own data only (Phase 5)
The system SHALL NOT use other users' data for AI personalisation; AI outputs are based solely on the current user's own entries, insights, and medication logs.

#### Scenario: AI generates advice
- **WHEN** AI генерирует персональный совет
- **THEN** использует только данные текущего пользователя (свои инсайты, записи, логи медикаментов)
- **AND** чужие данные не участвуют в персонализации

### Requirement: AI-generated novel advice (Phase 6, opt-in)
The system MAY generate advice that goes beyond the user's own insights (e.g. DBT/CBT techniques, general coping strategies), but this SHALL be opt-in and clearly marked as «not from your past». Opt-in is a setting `allow_novel_advice`, default OFF.

#### Scenario: User opts into novel advice
- **WHEN** пользователь включил «новые советы от AI» в настройках (`allow_novel_advice`)
- **AND** для текущего состояния нет релевантных своих инсайтов
- **THEN** AI может предложить технику из общей базы (DBT/CBT)
- **AND** совет помечен «не из твоих записей» (data-testid `ai-novel-advice`)
- **AND** пользователь может выключить эту функцию в любой момент

#### Scenario: Novel advice is OFF by default
- **WHEN** пользователь не включал «новые советы» (настройка по умолчанию off)
- **THEN** секция `ai-novel-advice` не показывается на /feed

### Requirement: AI chat in low states (Phase 6)
The system SHALL provide an AI chat available on-demand, especially useful in low / mixed states when the user needs immediate coping support. The chat SHALL be opened by an explicit user action (button), not push.

#### Scenario: User opens chat in anxiety
- **WHEN** пользователь в тревожном состоянии открывает чат
- **AND** описывает, что чувствует
- **THEN** AI отвечает, опираясь на контекст (текущая роза ветров + последние записи + свои инсайты) в секции `ai-chat` (data-testid `chat-response`)
- **AND** сообщения сохраняются (кэш чата)

### Requirement: AI chat crisis guardrail (Phase 6)
The system SHALL detect signs of crisis (key words) in chat messages and respond with a resource for professional help (hotline / emergency), not merely a normal supportive reply.

#### Scenario: Crisis keywords trigger resource
- **WHEN** пользователь пишет в чат сообщение с признаками кризиса (например «не хочу жить»)
- **THEN** ответ чата содержит напоминание о профессиональной помощи / телефон доверия
- **AND** это не заменяет обычный разговор как единственную реакцию

### Requirement: AI chat disclaimer (Phase 6)
The system SHALL show a disclaimer on the first open of the chat that AI does not replace professional help.

#### Scenario: First chat open shows disclaimer
- **WHEN** пользователь впервые открывает чат
- **THEN** видит disclaimer «не заменяет профессиональную помощь»
- **AND** может продолжить использование после подтверждения


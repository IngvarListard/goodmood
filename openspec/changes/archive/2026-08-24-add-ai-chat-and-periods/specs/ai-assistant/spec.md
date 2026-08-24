## ADDED Requirements

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
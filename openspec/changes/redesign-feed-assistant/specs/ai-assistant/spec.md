## MODIFIED Requirements

### Requirement: AI chat in low states (Phase 6)

The system SHALL provide an AI chat available on-demand, especially useful in low / mixed states when the user needs immediate coping support. The chat SHALL be opened by an explicit user action (FAB ассистента) и SHALL жить в глобальной bottom-sheet модалке (см. `assistant-modal`), не в инлайн-панели тела ленты. Контент чата переиспользует существующий контракт: `GET /ai/chat` отдаёт фрагмент панели, `POST /ai/chat` обновляет секцию `ai-chat` (data-testid `chat-response`).

#### Scenario: User opens chat in anxiety

- **WHEN** пользователь в тревожном состоянии кликает FAB ассистента
- **AND** описывает, что чувствует
- **THEN** AI отвечает, опираясь на контекст (текущая роза ветров + последние записи + свои инсайты) в модалке ассистента (data-testid `chat-response`)
- **AND** сообщения сохраняются (кэш чата)

#### Scenario: Chat is not inline in feed

- **GIVEN** пользователь на `/feed`
- **WHEN** страница отрендерена
- **THEN** инлайн-панель чата в теле ленты отсутствует
- **AND** чат доступен только через модалку ассистента

### Requirement: AI chat disclaimer (Phase 6)

The system SHALL показывать дисклеймер «AI-ассистент не заменяет профессиональную помощь и терапию» как постоянную мелкую строку над composer в модалке ассистента — без блока-гейта с кнопкой «Продолжить» при первом открытии. Сессионный флаг `ai-chat-disclaimer-dismissed` и эндпоинт `POST /ai/chat/disclaimer` перестают использоваться UI (эндпоинт не удаляется из кода).

#### Scenario: Disclaimer always visible

- **WHEN** пользователь открывает модалку ассистента (первый или повторный раз)
- **THEN** мелкая строка дисклеймера видна над полем ввода
- **AND** чат доступен к использованию сразу, без подтверждения

#### Scenario: Disclaimer does not block composer

- **GIVEN** пользователь открыл модалку впервые
- **WHEN** он сразу отправляет сообщение
- **THEN** сообщение отправляется без промежуточного шага подтверждения

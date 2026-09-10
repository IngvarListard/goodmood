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

### Requirement: AI chat crisis guardrail (Phase 6)
The system SHALL detect signs of crisis (key words) in chat messages and respond with a resource for professional help (hotline / emergency), not merely a normal supportive reply.

#### Scenario: Crisis keywords trigger resource
- **WHEN** пользователь пишет в чат сообщение с признаками кризиса (например «не хочу жить»)
- **THEN** ответ чата содержит напоминание о профессиональной помощи / телефон доверия
- **AND** это не заменяет обычный разговор как единственную реакцию

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

### Requirement: AI chat graceful failure
The system SHALL handle failures of the AI chat backend (missing API key, network error, non-200 response) gracefully: the user's message is still saved, but instead of a silent empty response, the user sees a fallback message indicating the assistant could not respond. The fallback message is NOT persisted to `ai_chat_messages` (it is not a real assistant reply and must not pollute chat context). The server SHALL log the reason for the failure (missing key, non-200, network error) for diagnostics.

#### Scenario: No API key set — user sees fallback
- **WHEN** `OPENROUTER_API_KEY` не задан в окружении сервера
- **AND** пользователь отправляет сообщение в чат
- **THEN** сообщение пользователя сохраняется в `ai_chat_messages` (role=user)
- **AND** пользователь видит фолбэк-сообщение «не удалось получить ответ, попробуйте позже» в `#chat-response`
- **AND** фолбэк НЕ сохраняется в `ai_chat_messages`
- **AND** в логах сервера есть запись об отсутствии ключа

#### Scenario: Network error — user sees fallback
- **WHEN** запрос к OpenRouter выбрасывает исключение (сеть/таймаут)
- **AND** пользователь отправляет сообщение в чат
- **THEN** сообщение пользователя сохраняется
- **AND** пользователь видит фолбэк-сообщение
- **AND** фолбэк НЕ сохраняется в истории чата

#### Scenario: Normal response still works
- **WHEN** `OPENROUTER_API_KEY` задан и OpenRouter отвечает 200
- **AND** пользователь отправляет сообщение
- **THEN** оба сообщения (user + assistant) сохраняются в `ai_chat_messages`
- **AND** пользователь видит ответ ассистента (без фолбэка)

### Requirement: Оптимистичная отправка сообщения в чате

Отправка сообщения в чате ассистента SHALL показывать мгновенную обратную связь без ожидания ответа LLM: при сабмите сообщение пользователя SHALL добавиться в историю чата оптимистично (градиентный бабл справа, как у серверных сообщений), поле ввода SHALL очиститься, и под баблом SHALL появиться индикатор «печатает» — пузырь ассистента с тремя анимированными точками (по макету `design/assistant.html`). Серверный ответ (свап `#chat-response` outerHTML) SHALL заменить оптимистичный фрагмент полной историей из БД. При ошибке запроса (non-2xx) индикатор «печатает» SHALL быть снят. Текст оптимистичного бабла SHALL быть вставлен безопасно (без HTML-инъекции).

#### Scenario: Сообщение появляется до ответа

- **GIVEN** открыта модалка ассистента с загруженным чатом
- **WHEN** пользователь отправляет текст «плохо спал»
- **THEN** бабл с текстом «плохо спал» виден в истории немедленно
- **AND** поле ввода очищено
- **AND** под баблом отображается индикатор «печатает» (три анимированные точки)

#### Scenario: Ответ заменяет оптимистичный фрагмент

- **WHEN** сервер отвечает на POST /ai/chat
- **THEN** `#chat-response` заменён полной историей: сообщение пользователя и ответ ассистента
- **AND** индикатор «печатает» отсутствует

#### Scenario: Ошибка отправки снимает индикатор

- **WHEN** POST /ai/chat завершается ошибкой (non-2xx)
- **THEN** индикатор «печатает» удалён
- **AND** оптимистичный бабл пользователя остаётся видимым

#### Scenario: Текст не интерпретируется как HTML

- **WHEN** пользователь отправляет текст `<img src=x onerror=alert(1)>`
- **THEN** в бабле отображаетсяLiteral-текст сообщения, HTML не исполняется

### Requirement: Единая презентация AI-находок на ленте

AI-находки на ленте (AI-ярлык, корреляции, советы из инсайтов, novel-советы) SHALL отображаться в едином визуальном блоке с общей анатомией карточки: шапка в одну строку — иконка типа находки, локализованное название типа (не обрезаемый текст совета), дот-шкала уверенности, dismiss-кнопка; тело — текст находки с ограничением до 3 строк и раскрытием «ещё»; действия (фидбек, ссылки на инсайты) — строкой под телом. Уверенность SHALL отображаться дот-шкалой из 3 точек (high — 3 заполненные, medium — 2, low — 1) без текстового бейджа. При отсутствии confidence дот-шкала SHALL не рендериться (не допускается вывод «{Missing key …}»).

#### Scenario: Корреляция рендерится в единой анатомии

- **WHEN** на /feed есть находка type=correlation с confidence=high
- **THEN** карточка в секции корреляций содержит иконку, название типа, дот-шкалу (3 точки) и текст с ограничением строк
- **AND** текстовый бейдж уверенности («Высокая уверенность») не рендерится

#### Scenario: Совет без confidence не ломается

- **WHEN** находка advice сохранена без confidence (nil)
- **THEN** карточка рендерится без дот-шкалы
- **AND** в разметке отсутствует подстрока «Missing key»

#### Scenario: Фидбек остаётся доступным

- **WHEN** пользователь открывает карточку корреляции/совета
- **THEN** кнопки фидбека («не релевантно», «уже знал», «пожаловаться») доступны и отправляют тот же контракт POST /ai/findings/:id/feedback
- **AND** data-testid секций (`ai-correlations`, `ai-advice`, `ai-novel-advice`, `ai-state-label`) сохранены


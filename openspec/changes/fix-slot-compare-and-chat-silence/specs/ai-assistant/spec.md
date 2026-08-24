## ADDED Requirements

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

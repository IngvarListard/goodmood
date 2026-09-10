## ADDED Requirements

### Requirement: Детерминированный тест-дабл AI (GOODMOOD_FAKE_AI)

Система SHALL поддерживать env-переменную `GOODMOOD_FAKE_AI=1`: при её наличии функция `call-chat` SHALL возвращать детерминированный canned-ответ по аргументу `model` без обращения к OpenRouter (диспетч по константам `correlation-model`, `advice-model`, `chat-model`, `episode-warning-model`). Canned-ответы для analysis-моделей SHALL быть валидным JSON, проходящим Malli-схемы соответствующих находок; ответ чата — обычным текстом. Реальный env `OPENROUTER_API_KEY` при включённом флаге SHALL NOT использоваться. Флаг предназначен для e2e-прогонов и локальной разработки без ключа.

#### Scenario: Canned-ответ по модели

- **GIVEN** приложение запущено с `GOODMOOD_FAKE_AI=1`
- **WHEN** вызывается `call-chat` с correlation-model
- **THEN** возвращается непустой JSON-массив корреляций, валидный по correlation-schema, без сетевого запроса

#### Scenario: Чат отвечает текстом

- **GIVEN** приложение запущено с `GOODMOOD_FAKE_AI=1`
- **WHEN** пользователь отправляет сообщение в чат ассистента
- **THEN** ответ ассистента появляется в чате и сохраняется в истории (обычный путь parse→DB→UI)

#### Scenario: E2E чат без реального API

- **GIVEN** e2e-прогон без OPENROUTER_API_KEY
- **WHEN** выполняется быстрый набор (`npm run test:fast`)
- **THEN** спек чата ассистента (отправка сообщения, оптимистичный бабл, индикатор, ответ) проходит без тега `@ai` и сетевых вызовов

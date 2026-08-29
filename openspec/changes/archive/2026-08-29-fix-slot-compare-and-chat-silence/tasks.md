## 1. Bugfix: `past-slot-time?` — сравнение строк через `>=`

- [x] 1.1 В `src/app/routes/notifications.clj` переписать `past-slot-time?`: сравнивать числовое представление (минуты с полуночи для слота и для текущего времени), не строки через `>=`
- [x] 1.2 Добавить unit-тест на `past-slot-time?` ( случаи: слот раньше сейчас, позже, равен)
- [x] 1.3 Запустить тесты уведомлений, убедиться что `pending-insight` не падает

## 2. Bugfix: AI-чат — фолбэк при `reply = nil`

- [x] 2.1 В `src/app/domains/ai.clj` (`call-chat`): добавить `println` при отсутствии `OPENROUTER_API_KEY` (`"OpenRouter API key not set — skipping AI call"`)
- [x] 2.2 Добавить i18n-ключ `:ai/chat-error-fallback` (ru/en) в `src/app/i18n.clj`
- [x] 2.3 В `src/app/views/ai.clj` — обновить `chat-response`: принимать флаг `:error?` и рендерить фолбэк-пузырь ассистента при ошибке
- [x] 2.4 В `src/app/routes/ai.clj` (`chat-send`): при `reply = nil` рендерить `chat-response` с `:error? true` (user-сообщение сохраняется, ассистент-фолбэк НЕ сохраняется в БД)
- [x] 2.5 Добавить/обновить тесты: `test/app/routes/ai_test.clj` — случай отсутствия ключа (mock `api-key` → nil) → фолбэк, user-сообщение сохранено, assistant НЕ сохранён

## 3. Проверка

- [x] 3.1 Запустить полный набор тестов (`clj -M:test` или аналог), все должны проходить
- [x] 3.2 Запустить приложение, проверить через Playwright MCP: polling `/feed/pending-insight` не падает, чат показывает фолбэк при отсутствии ключа

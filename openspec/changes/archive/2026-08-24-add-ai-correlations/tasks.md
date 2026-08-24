## 1. Зависимости и миграции

- [x] 1.1 Добавить `clj-http` в `deps.edn` (актуальная версия). Проверить, что `cheshire` уже есть.
- [x] 1.2 Создать `resources/migrations/009-add-ai-findings.up.sql`: `CREATE TABLE ai_findings` (id, user_id FK users, type CHECK IN correlation/label/advice, content TEXT, confidence TEXT nullable CHECK IN high/medium/low, source_refs TEXT, feedback TEXT nullable, hidden INTEGER DEFAULT 0, created_at)
- [x] 1.3 Создать `resources/migrations/009-add-ai-findings.down.sql`: `DROP TABLE ai_findings`
- [x] 1.4 Создать `resources/migrations/010-add-user-ai-settings.up.sql`: `CREATE TABLE user_ai_settings` (user_id PK FK users, master_enabled, correlations_enabled, labels_enabled, advice_enabled, все INTEGER DEFAULT 1)
- [x] 1.5 Создать `resources/migrations/010-add-user-ai-settings.down.sql`: `DROP TABLE user_ai_settings`
- [x] 1.6 Применить миграции, проверить схему через `sqlite3`

## 2. БД-слой — app.db.ai

- [x] 2.1 Создать `src/app/db/ai.clj`
- [x] 2.2 Реализовать `insert-finding!` (создание находки: type, content JSON, confidence, source_refs)
- [x] 2.3 Реализовать `get-findings` (по user_id + type, только hidden=0, order created_at DESC)
- [x] 2.4 Реализовать `set-feedback!` (обновление feedback + hidden=1 для находки)
- [x] 2.5 Реализовать `get-ai-settings` / `set-ai-settings!` (upsert по user_id)
- [x] 2.6 Тесты БД-слоя (insert, get, feedback-hide, settings-default/update)

## 3. Домен — app.domains.ai

- [x] 3.1 Создать `src/app/domains/ai.clj`
- [x] 3.2 Реализовать malli-схемы (корреляция, ярлык, совет, feedback)
- [x] 3.3 Реализовать вызов OpenRouter через `clj-http` + `cheshire` (асинхронно), ключ из env `OPENROUTER_API_KEY`
- [x] 3.4 Реализовать `analyze-correlations` (порог ≥ 14 дней, парсинг JSON, confidence)
- [x] 3.5 Реализовать `propose-state-label` (по розе ветров + истории, только без ручного ярлыка)
- [x] 3.6 Реализовать `generate-advice-from-insights` (только свои инсайты, source_refs)
- [x] 3.7 Реализовать guardrails: проверка принадлежности данных user_id, отказ при low confidence, no-чужой-данные
- [x] 3.8 Тесты домена (mock OpenRouter через `with-redefs`)

## 4. Роуты — app.routes.ai + интеграция

- [x] 4.1 Создать `src/app/routes/ai.clj`: endpoints для корреляций, ярлыка, совета, feedback, настроек AI
- [x] 4.2 Подключить в `src/app/routes/app.clj`, добавить роуты
- [x] 4.3 В `src/app/routes/feed.clj`: передавать находки AI (корреляции, AI-ярлык, AI-совет) во вьюху
- [x] 4.4 В `src/app/routes/settings.clj`: чтение/обновление настроек AI
- [x] 4.5 Тесты роутов (контракты, auth, per-function opt-out)

## 5. Вьюхи — компоненты Фазы 5

- [x] 5.1 Создать `src/app/views/ai.clj` с фрагментами: секция корреляций (`ai-correlations`), AI-ярлык (`ai-state-label`), AI-совет (`ai-advice`), кнопки feedback
- [x] 5.2 Интегрировать секции в `src/app/views/feed.clj`
- [x] 5.3 Интегрировать AI-совет в `src/app/views/insights.clj`
- [x] 5.4 Добавить секцию настроек AI в `src/app/views/settings.clj` (master-toggle + per-function)
- [x] 5.5 Все элементы — DaisyUI-классы, htmx-фрагменты

## 6. i18n и завершение

- [x] 6.1 Добавить i18n-ключи для всех новых строк (ru/en)
- [x] 6.2 Прогнать `clojure -M:test` — все проходят
- [x] 6.3 Smoke: `clj -M -m app.core`, приложение запускается
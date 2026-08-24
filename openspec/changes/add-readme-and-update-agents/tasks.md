## 1. README.md

- [x] 1.1 Создать `README.md` в корне репозитория со структурой: название + описание, стек, запуск (env vars + команда), тесты, краткий список фич, ссылка на AGENTS.md
- [x] 1.2 Указать env-переменные: `GOODMOOD_SESSION_SECRET` (required), `OPENROUTER_API_KEY` (optional, без него AI-функции no-op)
- [x] 1.3 Указать команды: `clj -M -m app.core` (запуск), `clj -M:test` (тесты), `cd e2e && npx playwright test` (e2e)

## 2. AGENTS.md — обновить

- [x] 2.1 Добавить секцию **Env vars** после «Запуск»: `GOODMOOD_SESSION_SECRET` (required, system.clj), `OPENROUTER_API_KEY` (optional, ai.clj — без него AI-функции возвращают nil)
- [x] 2.2 Добавить секцию **Обзор фич/доменов**: краткий список (entries, feed, check-in, medications, insights, state periods, notifications, AI, settings) с 1-строчным описанием каждого
- [x] 2.3 Добавить заметку про **i18n**: `app.i18n`, функция `t`, локали ru/en, переводы в `src/app/i18n.clj`
- [x] 2.4 Добавить заметку про **миграции**: `resources/migrations/`, migratus, автозапуск при старте системы (system.clj)
- [x] 2.5 Добавить заметку про **AI-домен**: OpenRouter API, opt-in per-function, graceful nil при отсутствии ключа, модели в `domains/ai.clj`

## 3. Проверка

- [x] 3.1 Проверить, что README.md рендерится корректно (markdown)
- [x] 3.2 Проверить, что AGENTS.md не потерял существующие секции (htmx, иконки, e2e — на месте)
- [x] 3.3 Убедиться, что env-переменные в README и AGENTS совпадают

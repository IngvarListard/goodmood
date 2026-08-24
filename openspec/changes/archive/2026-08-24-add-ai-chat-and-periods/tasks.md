## 1. Миграции и зависимости

- [x] 1.1 Создать миграцию `011-add-state-periods` (таблица `state_periods`, колонка `entries.state_period_id` уже есть из 006, `allow_novel_advice` в `user_ai_settings`).
- [x] 1.2 Создать миграцию `012-add-ai-chat-messages` (таблица `ai_chat_messages`).
- [x] 1.3 Проверить схему через sqlite3 + rollback (проверено через migratus, sqlite3 CLI недоступен).

## 2. БД-слой — app.db.state-periods + ai-chat

- [x] 2.1 Создать `src/app/db/state_periods.clj` — CRUD для периодов состояния.
- [x] 2.2 Расширить `src/app/db/ai.clj` — чат: `save-message!`, `get-messages` (user-scoped).
- [x] 2.3 Обновить `entries-data` колонку `state_period_id` при создании записи (если активный период).
- [x] 2.4 `get-settings` / `update-settings!` — поля `allow_novel_advice`.

## 3. Домен — app.domains.state-periods + ai

- [x] 3.1 Создать `src/app/domains/state_periods.clj` — malli-схемы (start/end, label), валидация периода.
- [x] 3.2 Расширить домен `src/app/domains/ai.clj` — `generate-advice` (novel advice), `save-chat-message!`.
- [x] 3.3 Guardrails: `needs-crisis-response?` (ключ. слова → ресурс, не заменяет).

## 4. Роуты — app.routes.ai + app.routes.state-periods

- [x] 4.1 Создать `src/app/routes/ai.clj`: endpointhы чата (`POST /ai/chat`), novel advice (`POST /ai/novel-advice`).
- [x] 4.2 Создать `src/app/routes/state_periods.clj`: CRUD периодов.
- [x] 4.3 Подключить в `src/app/routes/app.clj`.
- [x] 4.4 В `routes/feed.clj`: модалка «начать период» / баннер периода.
- [x] 4.5 В `routes/check_in.clj`: авто-привязка новой записи к активному периоду.
- [x] 4.6 Тесты роутов (ai chat: crisis-keywords + guardrails, novel advice opt-in/out, state periods CRUD).

## 5. Вьюхи — компоненты Фазы 6

- [x] 5.1 Создать `src/app/views/ai.clj` — чат-UI, novel-advice UI, ai-settings section.
- [x] 5.2 Создать `src/app/views/state_periods.clj` — периоды: start/end, список.
- [x] 5.3 В `src/app/views/feed.clj`: внедрить секцию периода (soft hint/start-banner).
- [x] 5.4 В `src/app/views/settings.clj`: добавить on/off `allow_novel_advice` toggle.
- [x] 5.5 Все элементы — DaisyUI-классы, htmx-атрибуты.

## 6. i18n и завершение

- [x] 6.1 Добавить i18n-ключи (ru/en) через `:ai/*`.
- [x] 6.2 Прогнать `clojure -M:test` — все проходят.
- [x] 6.3 Smoke: приложение запускается.

## Ограничения

- Hiccup v2 (не v1), DaisyUI, htmx-атрибуты.
- Кодстайл: CODESTYLE.md, CLOJURE-RULES.md.
- Комментарии на русском, логи на английском.
- Без новых библиотек.
- `/ai/*` — htmx-polling (не SSE).
- Novel advice — по умолчанию OFF.

## Вернуть

- Список созданных/изменённых файлов.
- Итог `clojure -M:test`.
# Tasks: add-chat-sessions

## 1. Миграция

- [x] 1.1 `resources/migrations/002-chat-sessions.up.sql`: `ALTER TABLE ai_chat_messages ADD COLUMN session_id INTEGER NOT NULL DEFAULT 1`; `002-chat-sessions.down.sql`: `ALTER TABLE ai_chat_messages DROP COLUMN session_id`
- [x] 1.2 Проверить подъём с нуля: `rm resources/goodmood.db* && ./bin/dev` — миграции на пустой БД, seed работает

## 2. Домен и данные

- [x] 2.1 `app.db.ai`: `get-messages` фильтр по session_id (текущая); `save-message!` пишет session_id; `current-session-id` (MAX) и `next-session-id!` (MAX+1)
- [x] 2.2 `app.domains.ai`: `chat-history` → все сообщения текущей сессии (полный список, без cap); `chat-reply` — та же история в prompt; `start-new-session!` создаёт следующую
- [x] 2.3 Unit-тесты: сообщения разных сессий не смешиваются; полный список сессии (>8 сообщений) уходит модели; start-new-session переключает текущую

## 3. Роуты и views

- [x] 3.1 `routes/ai.clj` `POST /ai/chat/new` → новая сессия → свап `#assistant-body` свежим chat-panel (пустая история)
- [x] 3.2 `views/ai.clj`: кнопка «новый чат» в шапке ассистента (icon plus, hx-post + CSRF, рядом с крестиком)
- [x] 3.3 Проверка в REPL: диалог из 10+ сообщений → chat-reply получает все 10; «новый чат» → следующее сообщение в новой сессии; старые строки не затронуты

## 4. Сессия логина

- [x] 4.1 `routes/app.clj` `session-config`: `:max-age 7776000`

## 5. i18n, e2e и верификация

- [x] 5.1 `resources/i18n/{ru,en}.edn`: «Новый чат» / «New chat»
- [x] 5.2 Обновить clj-тесты, завязанные на «последние 8» истории
- [x] 5.3 e2e: отправить сообщения → «новый чат» → панель пустая → сообщение попадает в новую сессию (после свапа старых нет); логин-кука имеет Max-Age=7776000
- [x] 5.4 Прогнать `npm run test:fast` и полный прогон; коммит

## 1. Миграции

- [x] 1.1 Миграция `013-add-episode-warnings` (up): таблица `episode_warnings` (id, user_id, type CHECK IN depressive/hypomanic, pattern_description TEXT, confidence REAL, feedback TEXT nullable, dismissed INTEGER DEFAULT 0, created_at) + колонка `user_ai_settings.episode_warning_enabled` (INTEGER DEFAULT 0).
- [x] 1.2 Даун-миграция (drop таблицы и колонки).
- [x] 1.3 Проверить схему + rollback.

## 2. БД-слой — episode warnings (расширение app.db.ai)

- [x] 2.1 `insert-warning!` (type, pattern, confidence), `get-warnings` (user-scoped, dismissed=0), `set-warning-feedback!` (feedback + dismissed=1).
- [x] 2.2 `get-episode-warning-enabled?` / `set-episode-warning-enabled!` (в user_ai_settings).
- [x] 2.3 Тесты БД-слоя (insert/get/feedback/toggle).

## 3. Домен — app.domains.ai (trend analysis + guardrails)

- [x] 3.1 malli-схема ответа AI `{type, confidence, pattern}`.
- [x] 3.2 `analyze-episode-trend` (последние N дней, prompt, AI-вызов, парсинг).
- [x] 3.3 Guardrails: `confidence > 0.75` → warning; иначе log-only (не показывать).
- [x] 3.4 Кризис-детекция: `crisis-keywords?` (последняя запись note) → resource, НЕ warning.
- [x] 3.5 `episode-warning-needed?` (opt-in + last warning устарел/отсутствует).
- [x] 3.6 Тесты домена (границы confidence 0.74/0.75/0.76, opt-in, кризис → resource).

## 4. Роуты — app.routes.ai

- [x] 4.1 `GET /ai/episode-warning` (фрагмент warning для /feed), `POST /ai/episode-warning/:id/feedback`, `POST /ai/episode-warning/:id/dismiss`.
- [x] 4.2 `POST /settings/ai` — toggle `episode_warning_enabled`.
- [x] 4.3 Подключить в `routes/app.clj`.
- [x] 4.4 В `routes/feed.clj`: триггер фонового анализа + передать warning во вьюху.
- [x] 4.5 Тесты роутов (opt-in off → нет warning; высокий confidence → показано; кризис → ресурс; feedback; выключение).

## 5. Вьюхи

- [x] 5.1 `views/ai.clj`: `episode-warning` секция (data-testid episode-warning) — alert-info, мягкий copy, объяснение паттерна, кнопки «выключить»/«ложная тревога»/«почему?», кризис-баннер (телефон доверия).
- [x] 5.2 `views/settings.clj`: toggle `episode-warning-toggle` (data-testid), default off.
- [x] 5.3 `views/feed.clj`: внедрить warning/crisis-баннер.

## 6. i18n и завершение

- [x] 6.1 Ключи `:ai/*` и `:crisis/*` (поддерживающий copy, ru/en).
- [x] 6.2 `clojure -M:test` — все зелёные (218 тестов pass).
- [x] 6.3 Smoke `clj -M -m app.core` — health 200, миграция применена.
- [x] 6.4 e2e `phase7-episode-warning.spec.ts` — все 9 проходят.

## Guardrails checklist (критично)

- [x] 6.5 Opt-in default OFF.
- [x] 6.6 confidence ≤ 0.75 → НЕ показывать.
- [x] 6.7 Кризис → ресурс, не warning.
- [x] 6.8 Выключение в один клик (warning + settings).
- [x] 6.9 False-alarm feedback логируется.
- [x] 6.10 Copy — поддерживающий, не тревожный.
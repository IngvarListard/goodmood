# Phase 6 — Subagent Prompt: реализация

## КОНТЕКСТ

Ты — сабагент, реализующий Фазу 6 проекта goodmood (трекер настроения).

Стек: Clojure, deps.edn, ring + jetty, reitit, integrant, hiccup2, htmx,
hyperscript, Tailwind + DaisyUI, SQLite, next.jdbc, HoneySQL, migratus.

Прочитай для конвенции: CODESTYLE.md, CLOJURE-RULES.md,
openspec/changes/product-vision/design.md (Decision 5 — гранулярность,
Decision 8 — AI поэтапно).

## ЧТО СТРОИМ (Фаза 6)

1. AI генерирует НОВЫЕ советы (не из своих заметок — общие техники DBT/CBT)
2. AI-чат on-demand, особенно в плохом состоянии
3. «Период состояния» — пользователь отмечает начало/конец периода
   (подъём, спад, тревожная неделя)
4. Привязка записей к активному периоду

## РЕШЕНИЯ ИЗ DECISIONS.md (не оспаривать)

- **Период состояния (OQ7):** таблица `state_periods` (id, user_id, label,
  started_at, ended_at nullable, notes, created_at). `entries.state_period_id`
  (уже nullable, миграция 006) заполняется при активном периоде.
- **Новые советы:** opt-in (`allow_novel_advice` в user_settings, default false).
  Помечаются «не из твоих записей». Основаны на общих техниках.
- **Чат:** on-demand. Признаки кризиса → напоминание о проф. помощи.
  Контекст: текущая роза + последние записи + свои инсайты.
  Disclaimer при первом открытии.
- **AI-провайдер:** тот же (OpenRouter, deepseek/glm-5.2).

## СПЕКА (контракт)

- openspec/changes/product-vision/specs/ai-assistant/spec.md
  (Requirements: AI-generated novel advice opt-in, AI chat in low states)
- openspec/changes/product-vision/specs/entry-granularity/spec.md
  (Requirements: State period with explicit start/end — вариант в,
   Entries can be linked to a state period, Periods are user-defined)

## ФАЙЛЫ ДЛЯ ЧТЕНИЯ

- openspec/changes/product-vision/tasks.md (Фаза 6)
- openspec/changes/product-vision/specs/ai-assistant/spec.md
- openspec/changes/product-vision/specs/entry-granularity/spec.md
- src/app/db/entries.clj, src/app/db/ai.clj (из Фазы 5)
- src/app/domains/entries.clj, src/app/domains/ai.clj (из Фазы 5)
- src/app/views/feed.clj, src/app/views/insights.clj
- src/app/views/settings.clj
- src/app/routes/app.clj

## ЗАДАЧИ

1. Миграция 010: таблица `state_periods`. Колонка `allow_novel_advice`
   в users (или user_settings если есть).
2. db layer: src/app/db/state-periods.clj — CRUD периодов.
3. db layer: расширение src/app/db/ai.clj — кэш чат-сообщений.
4. domains: src/app/domains/state-periods.clj — start/end period,
   link entries to active period.
5. domains: расширение ai.clj — novel advice generation, chat.
6. routes: src/app/routes/ai.clj — chat endpoint, novel advice endpoint.
7. routes: src/app/routes/state-periods.clj — start/end/list.
8. views: расширение ai.clj — чат UI, novel advice UI.
9. views: src/app/views/state-periods.clj — UI для marking periods.
10. Интеграция в feed.clj: кнопка «начать период» / «закрыть период».
11. Интеграция в check_in.clj: авто-привязка к активному периоду.
12. Настройки: toggle «новые советы от AI» (opt-in).
13. i18n ключи.
14. Тесты: по сценариям из spec (особенно: чат + признаки кризиса,
    novel advice opt-in по умолчанию off, периоды user-defined).

## ОГРАНИЧЕНИЯ

- Hiccup v2, DaisyUI, htmx-атрибуты в мапе.
- Кодстайл: CODESTYLE.md, CLOJURE-RULES.md.
- Не добавляй библиотеки без явного разрешения.
- Комментарии на русском, логи на английском.
- Чат — htmx-polling или SSE (не websocket, проще).
- Novel advice — opt-in by default OFF.

## ВЕРНИ

- Краткий отчёт: что сделано, что не сделано, какие проблемы.
- Если нужен человек — скажи явно "BLOCKED: <причина>".

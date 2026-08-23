# Phase 5 — Subagent Prompt: реализация

## КОНТЕКСТ

Ты — сабагент, реализующий Фазу 5 проекта goodmood (трекер настроения).

Стек: Clojure, deps.edn, ring + jetty, reitit, integrant, hiccup2, htmx,
hyperscript, Tailwind + DaisyUI, SQLite, next.jdbc, HoneySQL, migratus.

Прочитай для конвенции: CODESTYLE.md, CLOJURE-RULES.md,
openspec/changes/product-vision/design.md (Decision 8 — AI поэтапно).

## ЧТО СТРОИМ (Фаза 5)

1. AI ищет корреляции (сон ↔ настроение, медикаменты ↔ состояние, ...)
2. AI предлагает название/описание состояния
3. AI генерирует советы на основе своих же заметок пользователя
4. Кэширование находок в БД (таблица `ai_findings`)
5. Объяснимость каждого AI-вывода
6. Кнопка «пожаловаться на совет»
7. Opt-out для каждой AI-функции отдельно

## РЕШЕНИЯ ИЗ DECISIONS.md (не оспаривать)

- **AI-провайдер:** OpenRouter API (`https://openrouter.ai/api/v1/chat/completions`).
  Модель для корреляций: deepseek (deepseek/deepseek-chat).
  Модель для советов: z-ai/glm-5.2.
  HTTP через clj-http + cheshire (если не в deps — спросить человека).
- **Корреляции:** фоновый анализ при ≥ 14 дней записей. Кэш в `ai_findings`.
  Уверенность: high/medium/low. «не релевантно» / «уже знал».
- **Советы из своих заметок:** ТОЛЬКО на своих инсайтах. Ссылки на источники.
  «пожаловаться» → hides + feedback.
- **Не генерирует новые советы** (не из своих заметок) — это Фаза 6.

## СПЕКА (контракт)

- openspec/changes/product-vision/specs/ai-assistant/spec.md
  (Requirements: Correlation discovery, AI-proposed state label,
   AI-generated advice from own insights, Explainability,
   Feedback and opt-out, AI operates on user's own data only)
- Сценарии из spec → GIVEN/WHEN/THEN → тесты

## ФАЙЛЫ ДЛЯ ЧТЕНИЯ

- openspec/changes/product-vision/tasks.md (Фаза 5)
- openspec/changes/product-vision/specs/ai-assistant/spec.md
- src/app/db/entries.clj, src/app/db/insights.clj, src/app/db/medications.clj
- src/app/domains/entries.clj (state-label), src/app/domains/insights.clj
- src/app/views/feed.clj, src/app/views/insights.clj
- src/app/routes/app.clj
- openspec/specs/entries-data/spec.md, openspec/specs/insights/spec.md

## ЗАДАЧИ

1. Миграция 009: таблица `ai_findings` (id, user_id, type — correlation/label/advice,
   content JSON, confidence, source_refs JSON, feedback TEXT nullable,
   hidden INTEGER DEFAULT 0, created_at).
2. db layer: src/app/db/ai.clj — CRUD для ai_findings.
3. domains: src/app/domains/ai.clj — malli-схемы, оркестрация вызовов
   OpenRouter, парсинг ответов, guardrails.
4. routes: src/app/routes/ai.clj — endpoints для AI-функций.
5. views: src/app/views/ai.clj — UI для корреляций, AI-ярлыков, AI-советов.
6. Интеграция в feed.clj: секция AI-корреляций, AI-ярлык рядом с state_label.
7. Интеграция в insights: AI-совет под insights widget.
8. Настройки AI: в settings.clj — toggle для каждой AI-функции.
9. i18n ключи для всех новых строк.
10. Тесты: по сценариям из spec.

## ОГРАНИЧЕНИЯ

- Hiccup v2, DaisyUI, htmx-атрибуты в мапе.
- Кодстайл: CODESTYLE.md, CLOJURE-RULES.md.
- Не добавляй библиотеки без явного разрешения. Если нужна clj-http/cheshire —
  скажи "BLOCKED: нужна библиотека clj-http" и жди.
- Комментарии на русском, логи на английском.
- AI-вызовы — асинхронные (background), не блокируют UI.
- API-ключ OpenRouter — из env переменной `OPENROUTER_API_KEY`.

## ВЕРНИ

- Краткий отчёт: что сделано, что не сделано, какие проблемы.
- Если нужен человек — скажи явно "BLOCKED: <причина>".

# Phase 7 — Subagent Prompt: реализация

## КОНТЕКСТ

Ты — сабагент, реализующий Фазу 7 проекта goodmood (трекер настроения).

Стек: Clojure, deps.edn, ring + jetty, reitit, integrant, hiccup2, htmx,
hyperscript, Tailwind + DaisyUI, SQLite, next.jdbc, HoneySQL, migratus.

Прочитай для конвенции: CODESTYLE.md, CLOJURE-RULES.md,
openspec/changes/product-vision/design.md (Decision 8 — AI, риски).

## ЧТО СТРОИМ (Фаза 7)

AI предупреждает о возможном начале эпизода (депрессивного или
(гипо)маниакального) на основе трендового анализа. **Самая опасная фича.**
Guardrails — критичны.

## РЕШЕНИЯ ИЗ DECISIONS.md (не оспаривать)

- **Opt-in, default OFF.** Пользователь должен явно включить.
- **Confidence threshold > 0.75.** Если ниже — НЕ показывать, даже если
  паттерн есть. Логировать для будущего уточнения модели.
- **Объяснимость:** показывать паттерн («последние 3 дня: энергия растёт,
  сон падает — похоже на начало мании»).
- **Лёгкое выключение:** один клик, в настройках и в самом предупреждении.
- **False-alarm feedback:** кнопка «ложная тревога» → логируется в БД.
- **Признаки кризиса:** не предупреждение, а ресурс (телефон доверия,
  напоминание о проф. помощи).
- **AI-провайдер:** тот же (OpenRouter, deepseek для анализа тренда).

## СПЕКА (контракт)

- openspec/changes/product-vision/specs/ai-assistant/spec.md
  (Requirements: Episode onset warning — opt-in, guarded,
   Low-confidence pattern does not trigger warning,
   Explainability, Feedback and opt-out)

## ФАЙЛЫ ДЛЯ ЧТЕНИЯ

- openspec/changes/product-vision/tasks.md (Фаза 7)
- openspec/changes/product-vision/specs/ai-assistant/spec.md
- src/app/db/ai.clj (из Фазы 5)
- src/app/domains/ai.clj (из Фазы 5)
- src/app/domains/entries.clj (state-label, trend data)
- src/app/views/feed.clj, src/app/views/settings.clj
- src/app/routes/app.clj

## ЗАДАЧИ

1. Миграция 011: колонка `episode_warning_enabled` в users/user_settings
   (default 0). Таблица `episode_warnings` (id, user_id, type —
   depressive/hypomanic, pattern_description TEXT, confidence REAL,
   feedback TEXT nullable, dismissed INTEGER DEFAULT 0, created_at).
2. db layer: расширение ai.clj — episode warnings CRUD.
3. domains: расширение ai.clj — trend analysis (последние N дней),
   confidence calculation, threshold check, pattern description.
4. routes: расширение ai.clj — warning endpoint, feedback endpoint,
   dismiss endpoint, opt-in toggle.
5. views: предупреждение в feed.clj (alert, не intrusive), настройки
   в settings.clj (opt-in toggle).
6. Guardrails (критично):
   - при confidence < 0.75 → НЕ показывать, логировать
   - при признаках кризиса → ресурс, не предупреждение
   - opt-in default OFF
   - один клик выключения
   - false-alarm feedback
7. i18n ключи (особенно — поддерживающий, не пугающий copy).
8. Тесты: КАЖДЫЙ guardrail-сценарий из spec:
   - high-confidence → warning shown
   - low-confidence → NO warning, logged
   - crisis keywords → resource, not warning
   - opt-out → no warnings
   - false-alarm feedback → logged

## ОГРАНИЧЕНИЯ

- Hiccup v2, DaisyUI, htmx-атрибуты в мапе.
- Кодстайл: CODESTYLE.md, CLOJURE-RULES.md.
- Не добавляй библиотеки без явного разрешения.
- Комментарии на русском, логи на английском.
- Copy для предупреждений — поддерживающий, НЕ тревожный.
  НЕ «ВНИМАНИЕ! Возможен эпизод!»
  ДА «Похоже, последние дни похожи на твой прежний паттерн спада.
      Хочешь посмотреть, что помогало тогда?»
- Признаки кризиса (ключевые слова в записях/чате) → телефон доверия,
  напоминание о 112/03 (ру/международные).

## ВЕРНИ

- Краткий отчёт: что сделано, что не сделано, какие проблемы.
- Если нужен человек — скажи явно "BLOCKED: <причина>".

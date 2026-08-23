# Phase 7 — Test-writer Prompt (слепой, из spec)

## КОНТЕКСТ

Ты — test-writer сабагент. Пишешь тесты по сценариям из спецификации,
не видя реализации. **Это самая опасная фича — guardrails критичны.**
Тесты guardrails — приоритет №1.

Стек: Clojure, `clojure.test`, существующие fixtures.

## ЧТО ТЕСТИРУЕМ (Фаза 7: предупреждение эпизода)

## СПЕКА (ЕДИНСТВЕННЫЙ источник правды)

Прочитай ТОЛЬКО:
- openspec/changes/product-vision/specs/ai-assistant/spec.md
  (Requirements: Episode onset warning — opt-in, guarded,
   Low-confidence pattern does not trigger warning,
   Explainability, Feedback and opt-out)

НЕ читай:
- src/app/routes/ai.clj
- src/app/domains/ai.clj
- src/app/db/ai.clj
- src/app/views/ai.clj

## КАК ПИСАТЬ — ПРИОРИТЕТ GUARDRAILS

1. **GUARDRAIL-ТЕСТЫ (приоритет №1):**
   - opt-in default OFF → нет предупреждений
   - confidence < 0.75 → НЕТ предупреждения (логируется)
   - confidence > 0.75 → предупреждение показано
   - opt-out → нет предупреждений после выключения
   - false-alarm feedback → логируется
   - признаки кризиса → ресурс, НЕ предупреждение

2. **ОБЫЧНЫЕ ТЕСТЫ:**
   - предупреждение содержит объяснение паттерна
   - можно выключить в один клик
   - предупреждение dismissible

3. Для AI-вызовов: mock OpenRouter (with-redefs).
4. Для confidence: тестируй граничные значения (0.74 → no, 0.75 → yes,
   0.76 → yes).

## ФАЙЛЫ ДЛЯ ЧТЕНИЯ (только тест-инфраструктура)

- test/app/db/entries_test.clj
- test/app/routes/app_test.clj
- test/app/routes/auth_test.clj
- deps.edn

## ФОРМАТ

- Расширь `test/app/domains/ai_test.clj` (episode warning тесты)
- Расширь `test/app/routes/ai_test.clj` (HTTP для warning/feedback/dismiss)
- Обнови deps.edn если нужно.

## ВЕРНИ

- Список файлов, покрытые сценарии (особенно guardrails), AMBIGUOUS если есть.

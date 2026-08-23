# Phase 6 — Test-writer Prompt (слепой, из spec)

## КОНТЕКСТ

Ты — test-writer сабагент. Пишешь тесты по сценариям из спецификации,
не видя реализации. Ловишь контракт-дыры.

Стек тестирования: Clojure, `clojure.test`, существующие fixtures.

## ЧТО ТЕСТИРУЕМ (Фаза 6: новые советы, чат, периоды состояния)

## СПЕКА (ЕДИНСТВЕННЫЙ источник правды)

Прочитай ТОЛЬКО:
- openspec/changes/product-vision/specs/ai-assistant/spec.md
  (Requirements: AI-generated novel advice opt-in, AI chat in low states)
- openspec/changes/product-vision/specs/entry-granularity/spec.md
  (Requirements: State period with explicit start/end — вариант в,
   Entries can be linked to a state period,
   Periods are user-defined not auto-detected)

НЕ читай:
- src/app/routes/ai.clj, src/app/routes/state_periods.clj
- src/app/domains/ai.clj, src/app/domains/state_periods.clj
- src/app/db/state_periods.clj
- src/app/views/ai.clj, src/app/views/state_periods.clj

## КАК ПИСАТЬ

1. Для КАЖДОГО сценария `#### Scenario:` — отдельный `deftest`.
2. Используй существующие fixtures (см. test/app/db/entries_test.clj).
3. Для AI-чат: mock OpenRouter. Тестируй:
   - чат отвечает в контексте пользователя
   - признаки кризиса → ресурс (не просто ответ)
   - disclaimer при первом открытии
4. Для novel advice:
   - opt-in default OFF → не генерирует
   - помечены «не из твоих записей»
   - можно выключить
5. Для state periods:
   - start → creates period with started_at, no ended_at
   - end → fills ended_at
   - entry created during active period → linked
   - period NOT auto-created (user-defined only)
6. Дай знать, если что-то AMBIGUOUS.

## ФАЙЛЫ ДЛЯ ЧТЕНИЯ (только тест-инфраструктура)

- test/app/db/entries_test.clj
- test/app/routes/app_test.clj
- test/app/routes/auth_test.clj
- deps.edn

## ФОРМАТ

- Создай `test/app/domains/state_periods_test.clj`
- Создай `test/app/routes/ai_test.clj` (расширь, если есть из Фазы 5)
- Создай `test/app/routes/state_periods_test.clj`
- Обнови deps.edn :test alias с новыми ns.

## ВЕРНИ

- Список файлов, покрытые сценарии, AMBIGUOUS если есть.

# Phase 5 — Test-writer Prompt (слепой, из spec)

## КОНТЕКСТ

Ты — test-writer сабагент. Твоя задача — написать тесты по сценариям из
спецификации. Ты НЕ читаешь реализацию — ты пишешь тесты вслепую, по
контракту. Это ловит контракт-дыры: если реализация не соответствует
спеке, тесты это покажут.

Стек тестирования: Clojure, `clojure.test`, существующие fixtures в
`test/app/`.

## ЧТО ТЕСТИРУЕМ (Фаза 5: AI-корреляции, ярлыки, советы)

## СПЕКА (ЕДИНСТВЕННЫЙ источник правды)

Прочитай ТОЛЬКО:
- openspec/changes/product-vision/specs/ai-assistant/spec.md
  (Requirements: Correlation discovery, AI-proposed state label,
   AI-generated advice from own insights, Explainability,
   Feedback and opt-out, AI operates on user's own data only)

НЕ читай:
- src/app/routes/ai.clj
- src/app/domains/ai.clj
- src/app/db/ai.clj
- src/app/views/ai.clj

## КАК ПИСАТЬ

1. Для КАЖДОГО сценария `#### Scenario:` из spec — отдельный `deftest`.
2. Имя теста: `test-<requirement-name>-<scenario-name>`.
3. Используй `given/when/then` из spec как тело теста.
4. Используй существующие test-fixtures: `test/app/db/entries_test.clj`
   (tmp-path, datasource), `test/app/routes/app_test.clj` (app, ring).
5. Для AI-вызовов: mock OpenRouter API (не делать реальных HTTP-вызовов
   в тестах). Stub через `with-redefs` на HTTP-функции.
6. Для guardrails: тестируй, что система НЕ делает запрещённого
   (нет предупреждения при low confidence, нет чужих данных).

## ФАЙЛЫ ДЛЯ ЧТЕНИЯ (только тест-инфраструктура)

- test/app/db/entries_test.clj (fixture-паттерн: tmp-path, datasource)
- test/app/routes/app_test.clj (app fixture, ring-request helper)
- test/app/routes/auth_test.clj (csrf, session helper)
- deps.edn (alias :test — имена тестовых ns)

## ФОРМАТ

- Создай `test/app/domains/ai_test.clj` — доменные тесты (mock API).
- Создай `test/app/routes/ai_test.clj` — HTTP-контракты.
- Добавь ns в `deps.edn` alias `:test` (только ns, не зависимости).

## ВЕРНИ

- Список созданных тестовых файлов.
- Какие сценарии покрыты (по именам из spec).
- Если сценарий из spec непонятен для тестирования — скажи
  "AMBIGUOUS: <scenario name>: <что непонятно>".

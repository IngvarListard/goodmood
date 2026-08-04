## 1. Setup

- [x] 1.1 Добавить зависимости `metosin/malli` и `metosin/muuntaja` в `deps.edn` (malli 0.20.1, muuntaja 0.6.11; сверить с Clojars при доступности сети)
- [x] 1.2 Перевести `app.routes/app` из статичного значения в функцию `(->app ds)`. Не забыть зарегистрировать роут health-check `GET /`
- [x] 1.3 Обновить `app.system`: сервер `:app.core/server` получает `(ig/ref :db/connection)` и строит handler через `(routes/->app connection)`

## 2. Handlers и схема

- [x] 2.1 Создать `app.domains.entries.handlers` с malli-схемой тела (`activity` :string, `effect` :string, `mood_score` int 0–10, `sleep_hours` [:maybe :double])
- [x] 2.2 Реализовать `create-entry` handler: чтение `:body`, вызов `db/create-entry!`, возврат 201 + запись в JSON
- [x] 2.3 Реализовать `get-entries` handler: вызов `db/get-entries`, возврат 200 + список в JSON

## 3. Роутинг и middleware

- [x] 3.1 Зарегистрировать роуты `POST /entries` и `GET /entries` в `app.routes` с `:parameters {:body ...}` и `:data {:coercion malli/coercion}`
- [x] 3.2 Подключить muuntaja middleware (`wrap-format`, `application/json`)
- [x] 3.3 Добавить обработку ошибок coercion: `:reitit.coercion/request-coercion` → 400 с телом `{"errors" {поле [сообщения]}}` (через `malli.error/humanize`)

## 4. Тесты и проверка

- [x] 4.1 Написать интеграционные тесты handler'а (in-memory/временный SQLite): POST валидной записи → 201, POST с mood_score 15 → 400, POST без обязательного поля → 400, GET /entries → 200 с сохранёнными записями
- [x] 4.2 Прогнать `clojure.test` (alias `:test`) — все тесты зелёные
- [x] 4.3 Запустить приложение `clj -M -m app.core` и проверить через curl: POST /entries (валидный и невалидный) и GET /entries

## 5. Исправление после verify

Применить предложения ниже

WARNING
- [x] Design Impact scope mismatch — db.clj changed outside stated impact. design.md Impact lists only app.routes and app.domains.entries.handlers, but src/app/domains/entries/db.clj:19 was modified (:returning [:*] added to create-entry!). This change is needed to satisfy the spec ("returns the created entry as JSON") and doesn't break existing behavior, but the artifact doesn't document it.
→ Recommendation: add app.domains.entries.db (create-entry! returns the created row via INSERT ... RETURNING *) to the design Impact section and the "Handler'ы" decision note before archiving.
SUGGESTION
- [x] Error body shape differs from task wording. Task 3.3/design say {"errors" [...]} (vector), implementation returns {:errors {mood_score ["should be at most 10"]}} (humanized map, routes.clj:22). It satisfies the spec ("description of validation error") but the artifact's exact shape isn't matched.
→ Recommendation: either keep the map and update the task/design wording, or normalize to a vector. Align before archive so the htmx UI contract is documented accurately.
- [x] Scenario "Listing within time limit" (≤200ms @ 1000 rows) has no test. Implementation is a plain SELECT on entries (db.clj:22-28) — trivially fast, and the design addressed this as a risk. Not objectively verified.
→ Recommendation: add a smoke test that seeds ~1000 rows and asserts GET latency, or mark the NFR as design-reasoned-only in the spec.
- [x] Design prose "sleep_hours опционален" is technically misleading. The schema [:maybe :double] (handlers.clj:9) means the key is required but nullable — matching the design's schema block exactly. design.md:65 calls it "опционален".
→ Recommendation: reword design.md:65 to "может быть null" to match actual behavior.
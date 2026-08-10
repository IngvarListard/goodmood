## 1. Слой db

- [x] 1.1 Создать `src/app/db/entries.clj` (ns `app.db.entries`): перенести `create-entry!`, `get-entries` из `src/app/domains/entries/db.clj` без изменений кода (SQL, опции result-set — байт-в-байт)
- [x] 1.2 Прогнать тесты `clojure -M:test` — все проходят (старые файлы ещё на месте)

## 2. Слой views

- [x] 2.1 Создать `src/app/views/entries.clj` (ns `app.views.entries`): перенести из `src/app/domains/entries/views.clj` все hiccup-функции и константы скриптов без изменений
- [x] 2.2 Прогнать тесты `clojure -M:test` — все проходят

## 3. Слой domains

- [x] 3.1 Создать `src/app/domains/entries.clj` (ns `app.domains.entries`): перенести `create-entry-schema` и `today` из `domains/entries/handlers.clj`
- [x] 3.2 Добавить в `app.domains.entries` оркестрацию: `create-entry` (ds, data) → `db/create-entry!` с маппингом `:mood-score`/`:sleep-hours`, `list-entries` (ds) → `db/get-entries`; требует `app.db.entries`
- [x] 3.3 Прогнать тесты `clojure -M:test` — все проходят

## 4. Слой routes

- [x] 4.1 Создать `src/app/routes/entries.clj` (ns `app.routes.entries`): перенести `htmx-request?`, `html-response`, `prefers-html?` и хендлеры `create-entry`, `get-entries` из `domains/entries/handlers.clj`; хендлеры вызывают `app.domains.entries/*` и `app.views.entries/*`, а не `db/*`
- [x] 4.2 Создать `src/app/routes/app.clj` (ns `app.routes.app`): перенести из `src/app/routes.clj` `health-check`, coercion-настройки (`blank->nil-double`, `app-coercion`, ...), `coercion-error-middleware`, `router`, `->app`; обновить require на `app.routes.entries` и `app.views.entries`
- [x] 4.3 Прогнать тесты `clojure -M:test` — все проходят (старый `app.routes` ещё существует и дублирует поведение)

## 5. Подключение и удаление старых файлов

- [x] 5.1 В `src/app/system.clj` обновить require: `app.routes` → `app.routes.app`
- [x] 5.2 Удалить `src/app/routes.clj` и `src/app/domains/entries/{db,handlers,views}.clj` (директорию `domains/entries/` целиком, если пуста)
- [x] 5.3 Прогнать тесты `clojure -M:test` — все проходят

## 6. Тесты

- [x] 6.1 Переместить `test/app/domains/entries/db_test.clj` → `test/app/db/entries_test.clj`: ns `app.db.entries-test`, require `app.db.entries`; логика, fixture, tmp-path без изменений
- [x] 6.2 Переместить `test/app/routes_test.clj` → `test/app/routes/app_test.clj`: ns `app.routes.app-test`, require `app.routes.app`; логика без изменений
- [x] 6.3 В `deps.edn` alias `:test` обновить имена тестовых ns на `'app.db.entries-test` и `'app.routes.app-test` (версии и зависимости не трогать)
- [x] 6.4 Прогнать тесты `clojure -M:test` — все проходят

## 7. Финальная верификация

- [x] 7.1 Убедиться grep-ом, что нет ссылок на старые ns: `app.domains.entries.db`, `app.domains.entries.handlers`, `app.domains.entries.views`, `app.routes` в `src/` и `test/`
- [x] 7.2 Убедиться, что SQL/next.jdbc/HoneySQL встречаются только в `src/app/db/`, hiccup — только в `src/app/views/` и `src/app/routes/` (где допустим по дизайну), валидация/схемы — только в `src/app/domains/`
- [x] 7.3 Запустить приложение `clj -M -m app.core`, проверить health-check `curl http://localhost:3000/` и GET `/entries` (страница и JSON) — ответы как до рефакторинга
- [x] 7.4 Проверить `git diff`: нет изменённых строк с SQL, валидацией и разметкой — только переезд кода и новые файлы
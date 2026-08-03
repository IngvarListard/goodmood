## 1. Dependencies

- [x] 1.1 Добавить org.xerial/sqlite-jdbc, com.github.seancorfield/next.jdbc, migratus в deps.edn (проверить последние версии на clojars/maven)
- [x] 1.2 Создать директорию resources/migrations

## 2. Integrant-компонент соединения

- [x] 2.1 Реализовать init-key/halt-key! для `:db/connection` в app.system (next.jdbc datasource на SQLite, файл БД создаётся при старте)
- [x] 2.2 Включить `:db/connection` в system-config и запустить систему — проверить, что файл БД создан на диске

## 3. Migratus

- [x] 3.1 Создать модуль app.db.migrate с конфигом migratus ({:store :database :migration-dir "migrations" :db ...})
- [x] 3.2 Добавить первую пустую миграцию (создание БД/структуры) в resources/migrations
- [x] 3.3 Добавить вызов миграций при старте системы (после инициализации :db/connection)

## 4. Verification

- [x] 4.1 Запустить приложение — старт без ошибок, файл БД создан
- [x] 4.2 Через REPL выполнить SELECT 1 по datasource — запрос успешен
- [x] 4.3 Через REPL выполнить migratus migrate — в БД есть таблица schema_migrations с записью о применённой миграции

## 5. Исправить после проверки

Исправить warning и принять suggestions ниже

- [x] WARNING: Fix design.md:41 typo (:app/connection → :db/connection) before archiving
- [x] SUGGESTIONS: 3 minor items — unqualified migratus dep, sqlite-jdbc version check, and a deferred Open Question
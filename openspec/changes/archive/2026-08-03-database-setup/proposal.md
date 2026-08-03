## Why

Приложению нужно постоянное хранилище данных для дальнейших доменов
(записи дневника, психотерапия), и миграции схемы должны быть
управляемыми и воспроизводимыми с самого начала.

## What Changes

- Добавлена зависимость org.xerial/sqlite-jdbc и next.jdbc в deps.edn
- Добавлен integrant-компонент `:db/connection`, управляющий соединением с SQLite (next.jdbc)
- Добавлен migratus с конфигурацией миграций
- Добавлена первая пустая миграция (создание файла БД)

**BREAKING**: нет.

## Capabilities

### New Capabilities

- `db-connection`: Запуск и остановка соединения с SQLite-базой через integrant-компонент на next.jdbc; гарантирует создание файла БД при старте системы
- `db-migrations`: Применение схемных миграций через migratus, отслеживание применённых миграций в таблице schema_migrations

### Modified Capabilities

_(нет существующих specs)_

## Impact

- Затрагивает: `app.system` (новый компонент `:db/connection`), `deps.edn`, новая директория `resources/migrations`
- Зависимости: org.xerial/sqlite-jdbc, com.github.seancorfield/next.jdbc, migratus
- Зависит от: setup-skeleton (integrant-система уже существует)

## Scope

**In scope**: подключение SQLite как integrant-компонент, настройка migratus, проверка соединения через REPL-запрос.

**Out of scope**: конкретные таблицы доменов (entries, therapy) — отдельные changes, любая бизнес-логика чтения/записи.

## Non-functional requirements

Использовать только последние версии библиотек. Убедиться в источниках пакетов.

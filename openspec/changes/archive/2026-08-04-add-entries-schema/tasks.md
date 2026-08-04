## 1. Зависимости

- [x] 1.1 Добавить `com.github.seancorfield/honeysql` в `deps.edn` и резолвнуть зависимости

## 2. Миграция

- [x] 2.1 Создать `resources/migrations/002-add-entries-schema.up.sql` с таблицей `entries` (id, date, activity, effect, mood_score с CHECK 0–10, sleep_hours, created_at)
- [x] 2.2 Создать `resources/migrations/002-add-entries-schema.down.sql` (`DROP TABLE entries`)
- [x] 2.3 Применить миграции через интеграцию migratus и проверить наличие таблицы `entries` в SQLite

## 3. Слой доступа к данным

- [x] 3.1 Создать namespace `app.domains.entries.db` с `create-entry!` (INSERT через next.jdbc + HoneySQL)
- [x] 3.2 Добавить в `app.domains.entries.db` функцию `get-entries` (SELECT с сортировкой по дате)
- [x] 3.3 Проверить в REPL: `create-entry!` сохраняет запись, `get-entries` её возвращает; mood_score вне 0–10 отклоняется (CHECK)

## 4. Запуск и проверка

- [x] 4.1 Запустить тесты проекта — должны проходить
- [x] 4.2 Запустить приложение (`clj -M -m app.core`) и убедиться, что оно стартует без ошибок, миграции применяются
- [x] 4.3 Проверить health-check `curl http://localhost:3000/`

## 5. Исправить verify

Применить suggestion ниже:
SUGGESTION
- [x] Unit tests not added — design.md:56 ("Unit-тесты на базу") suggests automated DB tests, and tasks.md 3.3/4.1 were satisfied via ad-hoc REPL checks only (no test/ dir, no :test alias). Recommendation: add a test/app/domains/entries/db_test.clj + :test alias covering create/get/CHECK boundaries, or drop the design.md testing decision to match reality.
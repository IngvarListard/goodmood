## Why

Без таблицы и слоя доступа к данным невозможно хранить записи дневника
активности и настроения — это основа всей продуктовой функциональности проекта.

## What Changes

- Добавлена миграция создания таблицы entries (id, date, activity, effect,
  mood_score 0-10, sleep_hours, created_at)
- Добавлен namespace app.domains.entries.db с функциями create-entry! и get-entries
  на основе next.jdbc + HoneySQL
- Breaking changes: нет.

## Capabilities

### New Capabilities
- `entries-data`: схема таблицы entries и слой доступа к данным (CRUD на уровне данных, без HTTP)

### Modified Capabilities
<!-- Нет изменений существующих требований -->

## Impact

- Затрагивает: `resources/migrations` (новая миграция), `app.domains.entries.db` (новый namespace)
- Зависит от: `db-connection`, `db-migrations` (setup-database)

## Scope

- **In scope**: схема таблицы entries, CRUD-функции на уровне данных (без HTTP)
- **Out of scope / Non-goals**: HTTP-роуты, UI-формы, валидация пользовательского ввода,
  агрегация/статистика

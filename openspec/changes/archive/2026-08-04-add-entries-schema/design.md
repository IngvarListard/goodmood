## Context

Проект — трекер настроения при биполярном расстройстве. В настоящее время есть
SQLite подключение (`:db/connection` integrant-компонент на next.jdbc) и прогон
миграций migratus'ом на старте. Существует миграция `001-init` (пустая).
Доменной модели записей пока нет.

## Goals / Non-Goals

**Goals:**
- Таблица `entries` с полями id, date, activity, effect, mood_score (0–10),
  sleep_hours, created_at
- Слой доступа к данным `app.domains.entries.db` с функциями `create-entry!` и `get-entries`
- Ограничение mood_score диапазоном 0–10 на уровне БД

**Non-Goals:**
- HTTP-роуты и UI-формы
- Валидация пользовательского ввода на уровне приложения
- Агрегация/статистика — это отдельные будущие изменения

## Decisions

### Миграция `002-add-entries-schema` через migratus
Новая миграция в `resources/migrations/` (up/down). Следует за пустой
`001-init`. Таблица:
```sql
CREATE TABLE entries (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  date        TEXT NOT NULL,
  activity    TEXT NOT NULL,
  effect      TEXT NOT NULL,
  mood_score  INTEGER NOT NULL CHECK (mood_score BETWEEN 0 AND 10),
  sleep_hours REAL,
  created_at  TEXT NOT NULL DEFAULT (datetime('now'))
);
```
- `CHECK` на уровне БД гарантирует rejection out-of-range mood_score
  (соответствует Acceptance Criteria и сценарию «Out-of-range mood score rejected»).
- Почему `TEXT` для date/created_at: SQLite + простота, без java.time конвертации
  на этом этапе.

### Namespace `app.domains.entries.db`
Функции на next.jdbc с HoneySQL:
- `create-entry!` — `jdbc/execute-one!` с `INSERT INTO entries`
- `get-entries` — `jdbc/execute!` с `SELECT ... ORDER BY date DESC`
- Функции принимают datasource как первый аргумент (no global state,
  согласуется со стилем `app.db.migrate/migrate!`).

### Новая зависимость: HoneySQL
В `deps.edn` добавляется `com.github.seancorfield/honeysql`. Альтернатива —
сырые SQL-строки через next.jdbc; но там, где SQL станет более сложным
(сортировки, фильтры, агрегаты), HoneySQL упростит поддержку. Для базового
INSERT/SELECT можно было бы обойтись без неё, однако план проекта
предполагает расти на этот стек.

### Unit-тесты на базу
Минимальная проверка через nREPL/in-memory или временный файл SQLite:
создание таблицы, `create-entry!` + `get-entries`, rejection при
mood_score = -1 или 11.

## Risks / Trade-offs

- [Новая внешняя зависимость HoneySQL] → добавлена только для аккуратного
  использования в data-слое; API не расползается за пределы `app.domains`.
- [Миграция `001-init` пустая — история миграций начнётся с добавления таблицы] →
  это нормально, мигрatus упорядочивает по имени файла; down-миграция предоставляется.
- [Xerial SQLite не валидирует CHECK для REAL/null строго, но INTEGER CHECK сработает] →
  mood_score объявлен INTEGER NOT NULL с CHECK, что подтверждено поведением SQLite.
- [Хранение дат как TEXT может потребовать пересмотра при статистике] →
  пока достаточно; при необходимости добавят нормализацию в отдельном изменении.

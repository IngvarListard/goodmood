## Context

Проект — трекер настроения. Сейчас есть integrant-система с одним компонентом `:app.core/server` (setup-skeleton, заархивирован). Нужно добавить постоянное хранилище (SQLite) и управляемые миграции как фундамент для будущих доменов (записи дневника, психотерапия).

**Текущее состояние**: integrant-система в `app.system`, health-check роут, deps.edn без БД-зависимостей. SQLite-файла на диске нет.

**Стек** (из AGENTS.md): Clojure, deps.edn, ring + ring-jetty-adapter, reitit, integrant, hiccup2, htmx, hyperscript, Tailwind + DaisyUI, SQLite, next.jdbc, HoneySQL, migratus.

## Goals / Non-Goals

**Goals:**
- Соединение с SQLite через next.jdbc, управляемое как integrant-компонент `:db/connection`
- При старте системы файл БД создаётся на диске без ошибок
- Миграции схемы через migratus с отслеживанием в `schema_migrations`
- Проверка соединения через REPL-запрос

**Non-Goals:**
- Таблицы доменов (entries, therapy)
- Бизнес-логика чтения/записи
- Аутентификация

## Decisions

### 1. Драйвер и доступ к БД: sqlite-jdbc + next.jdbc

**Решение**: `org.xerial/sqlite-jdbc` (драйвер) + `com.github.seancorfield/next.jdbc` (API доступа).

**Почему**: next.jdbc — современный, активно поддерживаемый слоёй доступа к JDBC, единственный «idiomatic Clojure» вариант для нового кода; соответствует стеку проекта (next.jdbc в AGENTS.md).

**Альтернативы**:
- java.jdbc — устаревший, меньше возможностей (transduce, набор строк).
- JDBC напрямую — слишком много boilerplate.

### 2. Компонент соединения: integrant `:db/connection`

**Решение**: новый ключ `:db/connection` в integrant-системе, значение — next.jdbc datasource. Таблица домена будет брать его как зависимость (`:db/connection` в refs).

**Почему**: единый жизненный цикл с сервером, явная зависимость между компонентами, простое переиспользование в REPL.

**Реализация**: в `app.system`:
- `ig/init-key :db/connection` → создание datasource (SqliteDataSource) так, чтобы файл БД создавался при коннекте.
- `ig/halt-key!` → закрытие пула/контекста.

**Альтернативы**:
- Ленивое создание при первом запросе — неявно, сложно тестировать.
- Отдельная библиотека пула (HikariCP) — избыточно для локального SQLite.

### 3. Миграции: migratus

**Решение**: `migratus` с конфигом `{:store :database :migration-dir "migrations" :db datasource}`.

**Почему**: стандарт для Clojure-миграций, тесно связан с next.jdbc, умеет хранить историю в БД (`schema_migrations`).

**Реализация**: 
- Модуль `app.db.migrate` с `migrate!` (применение при старте системы) на основе конфига.
- Директория `resources/migrations/`; первая миграция `001-init` (например, создание служебной пустой структуры/файла БД — «эмпайрская» разминка).

**Альтернативы**:
- flyway-jdbc — мощнее, но не «Clojure-native», больше конфигурации.
- Радиус-скрипты руками — невоспроизводимо.

### 4. Путь к файлу БД и конфигурация

**Решение**: путь к БД задаётся в конфиге системы (например, `"resources/goodmood.db"` via `:db/connection {:path ...}`), директория `resources/` уже используется под миграции.

**Почему**: ресурсная директория удобна для локальной разработки, файл БД не попадает в JAR (или попадает при желании). Гибкость настройки через integrant-конфиг.

## Risks / Trade-offs

| Risk | Mitigation |
|------|------------|
| SQLite не поддерживает конкурентную запись (Locked database) | Для одного локального процесса — приемлемо; включить WAL pragma при инициализации коннекта |
| Настройка migratus может конфликтовать с путями в JAR | В dev используем filesystem-путь; при необходимости перенастроим на classpath |
| Версии библиотек устареют | Использовать последние стабильные на clojars/maven; зафиксировать в deps.edn |

## Migration Plan

1. Обновить deps.edn (sqlite-jdbc, next.jdbc, migratus).
2. Добавить компонент `:db/connection` в `app.system`.
3. Добавить модуль `app.db.migrate` и вызов при старте системы.
4. Создать `resources/migrations/` и первую миграцию.
5. Проверить через REPL: запуск, запрос `SELECT 1`, проверка `schema_migrations`.

Откат: `migratus/rollback` до предыдущей миграции (для пустой миграции — удаление файла при необходимости).

## Open Questions

- Стоит ли включить WAL (write-ahead logging) глобально как pragma при каждом коннекте, или оставить дефолт?
- Нужно ли выносить путь к БД в environment-переменную сразу, или оставить в конфиге (dev-режим)?

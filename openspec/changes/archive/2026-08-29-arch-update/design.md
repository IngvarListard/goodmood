## Context

Сейчас `src/app/` организована вертикальными срезами по фиче: вся логика записей живёт в `src/app/domains/entries/{db,handlers,views}.clj`, а reitit-таблица и middleware — в `src/app/routes.clj`. Это противоречит `openspec/context/03-architecture.md`, который требует четыре глобальных слоя с жёстким направлением зависимостей:

```
routes → (domains, views) → db
   │
   └── замыкается на system.clj / core.clj
```

Ключевые факты из текущего кода:

- `domains/entries/db.clj` — только SQL (`create-entry!`, `get-entries`), уже изолирован.
- `domains/entries/handlers.clj` — смесь: malli-схема `create-entry-schema`, `today` (бизнес), `htmx-request?`/`html-response`/`prefers-html?` (HTTP), и сама оркестрация `create-entry`, `get-entries`, которая вызывает `db/*` напрямую.
- `domains/entries/views.clj` — только hiccup (`form`, `item`, `entries-list`, `error-fragment`, `page`), уже изолирован.
- `routes.clj` — reitit-таблица, coercion-настройки, `coercion-error-middleware`, `health-check`; ссылается на хендлеры и views.
- `system.clj` требует только `app.routes` и `app.db.migrate`.
- Тесты: `test/app/domains/entries/db_test.clj` (тестирует `app.domains.entries.db`), `test/app/routes_test.clj` (тестирует `app.routes/->app`). `deps.edn` в alias `:test` хардкодит имена тестовых ns.

## Goals / Non-Goals

**Goals:**
- Привести структуру `src/app/` к 03-architecture.md: глобальные слои `db/`, `routes/`, `views/`, `domains/`.
- Выдержать направление зависимостей: `routes` вызывает `domains` и `views`, `domains` вызывает `db`; SQL — только в `db/`, hiccup — только в `views/`, схемы/бизнес-логика — только в `domains/`.
- Поведение приложения и HTML-разметка не меняются ни на байт.

**Non-Goals:**
- Изменение бизнес-логики, API-контрактов, разметки.
- Изменение зависимостей и их версий.
- Изменение логики тестов.
- Рефакторинг содержимого `core.clj`, `system.clj`, `icons.clj` (кроме `require` в `system.clj`).
- Новые доманы, вынесение общих layout-компонентов (в доке файлов `views/layout.clj` нет — не выдумываем).

## Decisions

### 1. Целевая раскладка файлов и ns

| Было | Стало |
|---|---|
| `src/app/domains/entries/db.clj` (`app.domains.entries.db`) | `src/app/db/entries.clj` (`app.db.entries`) |
| `src/app/domains/entries/views.clj` (`app.domains.entries.views`) | `src/app/views/entries.clj` (`app.views.entries`) |
| `src/app/domains/entries/handlers.clj` (`app.domains.entries.handlers`) | расформировывается → `routes/entries.clj` + `domains/entries.clj` |
| `src/app/routes.clj` (`app.routes`) | `src/app/routes/app.clj` (`app.routes.app`) |
| `src/app/db/migrate.clj` (`app.db.migrate`) | без изменений |

Имена `db/entries.clj`, `routes/app.clj`, `views/entries.clj` заданы явно, чтобы у исполнителя не было свободы выбора (иначе — бikeshedding). `routes/app.clj` — таблица+middleware, `routes/entries.clj` — HTTP-хендлеры фичи.

### 2. Распределение кода по слоям при переносе

- **`db/entries.clj`**: `create-entry!`, `get-entries` — переносятся байт-в-байт.
- **`domains/entries.clj`** (ns `app.domains.entries`): `create-entry-schema`, `today` + новая тонкая оркестрация:
  - `create-entry` (ds, data) → `db/create-entry!` — маппинг `mood_score` → `:mood-score` и `:sleep-hours`;
  - `list-entries` (ds) → `db/get-entries`.
  
  Обоснование: док даёт `domains` роль оркестрации и явно запрещает `routes` вызывать `db/*` напрямую. Перенос оркестрации из хендлеров — это перемещение кода, а не изменение логики.
- **`routes/entries.clj`** (ns `app.routes.entries`): всё HTTP-лицо — `htmx-request?`, `html-response`, `prefers-html?`, и хендлеры `create-entry` [ds request], `get-entries` [ds request], которые: достают `[:parameters :body]`, зовут `domains/*`, выбирают JSON/fragment/страницу по заголовкам.
- **`routes/app.clj`**: `health-check`, coercion-настройки (`blank->nil-double`, `app-coercion`, ...), `coercion-error-middleware`, `router`, `->app`. Требует `app.routes.entries` и `app.views.entries` (для `error-fragment`).
- **`views/entries.clj`**: все hiccup-функции и `htmx-src` и пр. — байт-в-байт.

Имена функций сохраняются; коллизии исчезают за счёт разных ns. Логика ветвлений (`htmx-request?` → fragment vs JSON; `prefers-html?` → страница vs JSON) остаётся в routes — это HTTP-решения, не бизнес.

### 3. Тесты и deps.edn

- `test/app/domains/entries/db_test.clj` → `test/app/db/entries_test.clj`, ns `app.db.entries-test`, require `app.db.entries`. Логика, fixture-ы, tmp-path — без изменений.
- `test/app/routes_test.clj` → `test/app/routes/app_test.clj`, ns `app.routes.app-test`, require `app.routes.app` (через `->app `). Логика — без изменений.
- **`deps.edn`**: в alias `:test` обновляются только имена тестовых ns (`'app.db.entries-test`, `'app.routes.app-test`). Это не изменение зависимостей — версии и координаты не трогаются.

### 4. Что остаётся нетронутым

`core.clj`, `system.clj` (кроме `:require [app.routes :as routes]` → `[app.routes.app :as routes]`), `icons.clj`, `db/migrate.clj`, `migrations/`.

## Risks / Trade-offs

- [Оркестрация в domains/entries.clj не совпадает буквально с формулировкой «перенести файлы» из промпта] → Это прямое требование 03-architecture.md (routes запрещено вызывать `db/*`); поведение не меняется, тесты это подтверждают.
- [Поведенческий дрейф при переезде (случайная правка строки)] → Критерий готовности: в diff нет изменённых строк с SQL/валидацией/разметкой; полный прогон `clojure -M:test`.
- [Исполнитель забудет обновить `deps.edn` :test] → Явная задача в tasks.md.
- [Коллизия имён `create-entry` в двух ns] → Разные ns, alias `entries` при require; внутри `routes/entries.clj` вызов `entries/create-entry` (domains) из хендлера `create-entry` однозначен.
- [`app.domains.entries` пустой слой-одиночка (один домен в проекте)] → Приемлемо: так и задумано доком; слой наполнится с новыми фичами.
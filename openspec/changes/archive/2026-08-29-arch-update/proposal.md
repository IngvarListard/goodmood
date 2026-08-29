## Why

Текущая структура `src/app/` нарушает документ `openspec/context/03-architecture.md`: слоистую архитектуру с глобальными слоями `db/`, `routes/`, `views/` и `domains/`. Код сгруппирован вертикальными срезами по фиче (`src/app/domains/entries/{db,handlers,views}.clj`), из-за чего SQL живёт не в `db/`, hiccup — не в `views/`, а хендлеры и бизнес-схемы смешаны в одном файле. Это затрудняет соблюдение направления зависимостей и переиспользование кода между фичами.

## What Changes

- Перенос SQL-функций (`create-entry!`, `list-entries`, …) из `domains/entries/db.clj` в `src/app/db/entries.clj`.
- Перенос hiccup-разметки из `domains/entries/views.clj` в `src/app/views/entries.clj`.
- Перенос HTTP-хендлеров (`htmx-request?`, обработчики запросов) из `domains/entries/handlers.clj` в `src/app/routes/entries.clj`.
- Перенос reitit-таблицы и middleware из `src/app/routes.clj` в `src/app/routes/app.clj`.
- Сбор бизнес-логики (malli-схема `create-entry-schema`, `today`, прочие бизнес-хелперы) в `src/app/domains/entries.clj`.
- Обновление `ns`-форм и `require` во всех файлах `src/` и `test/` вслед за перемещением.
- В тестах изменяются только `ns`, `require` и пути — логика тестов не меняется.
- **BREAKING** (внутреннее): публичные ns `app.domains.entries.db`, `app.domains.entries.handlers`, `app.domains.entries.views`, `app.routes` перестают существовать; ссылки на них в коде проекта обновляются.

## Capabilities

### New Capabilities

- `project-structure`: нормативные требования к слоистой структуре `src/app/` (слои `db/`, `routes/`, `views/`, `domains/`, направление зависимостей, сохранение поведения при рефакторинге). Отражает инварианты из `openspec/context/03-architecture.md`.

### Modified Capabilities

Нет. Поведение системы (API, данные, UI) не меняется — меняется только внутренняя структура кода.

## Scope

- Входит: перемещение файлов и функций между слоями, обновление имён namespace, обновление imports в тестах.
- Целевая структура — строго по `openspec/context/03-architecture.md`: слои глобальные, вертикальный срез по фиче только внутри `domains/`.

## Non-goals

- Изменение бизнес-логики и поведения приложения.
- Изменение внешнего вида и HTML-разметки страниц.
- Изменение зависимостей (`deps.edn`).
- Изменение логики тестов.
- Рефакторинг `src/app/core.clj`, `src/app/system.clj`, `src/app/icons.clj` (кроме обновления `require` в `system.clj`).
- Новые домены/фичи.

## Impact

- Код: переезд `src/app/domains/entries/*.clj` и `src/app/routes.clj`; новые файлы `src/app/db/entries.clj`, `src/app/routes/app.clj`, `src/app/routes/entries.clj`, `src/app/views/entries.clj`, `src/app/domains/entries.clj`.
- NS: исчезают `app.domains.entries.db/handlers/views`, `app.routes`; появляются `app.db.entries`, `app.routes.app`, `app.routes.entries`, `app.views.entries`, `app.domains.entries`.
- Тесты: `test/app/routes_test.clj`, `test/app/domains/entries/db_test.clj` — обновляются `ns` и `require`.
- Зависимости: не меняются.
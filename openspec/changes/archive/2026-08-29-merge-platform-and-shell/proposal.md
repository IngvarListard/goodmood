## Why

После `merge-entries-domain` в `openspec/specs/` останется 14 доменов. Семь из них — инфраструктура и UI-оболочка, раздробленные по слоям: `http-server`, `db-connection`, `db-migrations`, `project-structure` (инфраструктура) и `layout`, `navigation`, `placeholder-pages` (UI-оболочка). Целевая карта из `context/00-spec-domains.md` определяет два домена: `platform` (инфраструктура) и `ui-shell` (навигация, layout, заглушки).

## What Changes

- **BREAKING (спецификации)**: 4 инфраструктурных домена (`http-server`, `db-connection`, `db-migrations`, `project-structure`) объединяются в `platform`.
- **BREAKING (спецификации)**: 3 UI-домена (`layout`, `navigation`, `placeholder-pages`) объединяются в `ui-shell`.
- Все требования переносятся дословно (ADDED в новые + REMOVED из старых).
- Старые 7 доменов удаляются после архивации.
- **Код и поведение не меняются.**

## Scope

- `openspec/specs/platform/` — новый домен (4→1)
- `openspec/specs/ui-shell/` — новый домен (3→1)
- `openspec/specs/{http-server,db-connection,db-migrations,project-structure,layout,navigation,placeholder-pages}/` — удаляются

## Non-goals

- Изменение кода, поведения, UI, API, БД
- Объединение других доменов (`entries` — change 1, `users-auth` — change 3)
- Редактирование формулировок требований

## Capabilities

### New Capabilities
- `platform`: Инфраструктура — HTTP-сервер (ring + jetty + integrant), SQLite-соединение (integrant component), миграции (migratus), слоистость исходника (db/routes/views/domains).
- `ui-shell`: UI-оболочка — HTML-layout (CDN, responsive sidebar/bottom-bar, hx-boost), навигация (4 пункта, active highlighting, solid/outline icons), placeholder-страницы.

### Modified Capabilities
(нет)

## Impact

- **Спецификации**: `openspec/specs/` — 7 директорий удаляются, 2 создаются. После change: 14 → 9 доменов.
- **Код/тесты/поведение**: не затрагиваются.

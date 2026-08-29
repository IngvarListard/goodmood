## Context

После `merge-entries-domain` остаётся 14 доменов. Семь — инфраструктура и UI-оболочка. Целевая карта (`00-spec-domains.md`) определяет `platform` и `ui-shell`. Механика идентична change 1: ADDED в новые + REMOVED из старых.

## Goals / Non-Goals

**Goals:**
- `platform` содержит: HTTP-сервер, SQLite-соединение, миграции, слоистость кода.
- `ui-shell` содержит: layout, навигацию, placeholder-страницы.
- Старые 7 доменов удалены.

**Non-Goals:** редактирование формулировок, затрагивание кода, другие домены.

## Decisions

### D1: Механика — та же, что в merge-entries-domain
ADDED в `platform`/`ui-shell` (полное тело) + REMOVED из 7 старых (Reason + Migration). При архивации OpenSpec создаст новые домены и опустошит старые.

### D2: Порядок в `platform/spec.md`
1. HTTP-сервер (из `http-server`)
2. SQLite-соединение (из `db-connection`)
3. Миграции (из `db-migrations`)
4. Слоистость кода (из `project-structure`)

### D3: Порядок в `ui-shell/spec.md`
1. Layout (из `layout`)
2. Навигация (из `navigation`)
3. Placeholder-страницы (из `placeholder-pages`)

### D4: Конфликт имён — «Active nav item matches the current route»
Требование с таким именем есть в `placeholder-pages`. В `navigation` есть «Active item is visually highlighted» — другое имя. Дубликатов нет.

## Risks / Trade-offs

- **[Объём]** ADDED ~300 строк в двух файлах. → Mitigation: требования копируются дословно, ревью = проверка полноты.
- **[Warning «>10 deltas»]** 9 delta-файлов (2 ADDED + 7 REMOVED). → Non-blocking, игнорируем.

## Migration Plan

1. Написать артефакты.
2. `openspec validate --changes merge-platform-and-shell`.
3. `openspec archive merge-platform-and-shell --yes`.
4. Проверить `openspec/specs/{platform,ui-shell}/spec.md` — все требования на месте.
5. `git rm -r openspec/specs/{http-server,db-connection,db-migrations,project-structure,layout,navigation,placeholder-pages}`.
6. `openspec doctor`.
7. Коммит.

## Open Questions

(нет)

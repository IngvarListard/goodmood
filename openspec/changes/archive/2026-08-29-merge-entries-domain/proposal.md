## Why

Главные спеки в `openspec/specs/` разрослись до 20 доменов, многие из которых — слои одной и той же фичи (entries-api, entries-data, entries-ui, entry-granularity, mobile-entry, mood-states). Это противоречит принципу «домен = область поведения, а не слой реализации» из `context/00-spec-domains.md`. Шесть entries-* доменов описывают разные грани одного поведенческого контекста — записей дневника состояния — и должны жить в одном домене `entries`.

## What Changes

- **BREAKING (спецификации)**: шесть доменов (`entries-api`, `entries-data`, `entries-ui`, `entry-granularity`, `mobile-entry`, `mood-states`) объединяются в один новый домен `entries`.
- Все требования из 6 старых доменов переносятся в `entries/spec.md` (ADDED) с сохранением формулировок и сценариев.
- Старые домены помечаются REMOVED (с Reason + Migration), их директории удаляются из `openspec/specs/` после архивации.
- **Код и поведение приложения не меняются** — это чистая реорганизация спецификаций.

## Scope

- `openspec/specs/entries/` — новый домен (объединение 6 старых)
- `openspec/specs/{entries-api,entries-data,entries-ui,entry-granularity,mobile-entry,mood-states}/` — удаляются после архивации
- `openspec/context/00-spec-domains.md` — обновляется маппинг (в отдельном change или после всех трёх merge-changes)

## Non-goals

- Изменение исходного кода приложения (`src/app/**`)
- Изменение поведения, UI, API или БД
- Объединение других доменов (users-auth, platform, ui-shell) — это отдельные changes `merge-platform-and-shell` и `merge-users-auth`
- Создание нового домена `ai-assistant` или `coping-channels` — они уже существуют и остаются как есть

## Capabilities

### New Capabilities
- `entries`: Запись дневника состояния — форма, check-in, feed, роза ветров, поля записи, гранулярность (несколько записей/день, периоды), mobile-first layout, soft mode, SVG-радар, rule-based state label, API endpoints, БД-слой.

### Modified Capabilities
(нет — только удаление из старых и добавление в новый)

## Impact

- **Спецификации**: `openspec/specs/` — 6 директорий удаляются, 1 создаётся. Общее количество доменов: 20 → 15 (после этого change; далее `merge-platform-and-shell` и `merge-users-auth` доведут до ~9).
- **Код**: не затрагивается.
- **Тесты**: не затрагиваются.
- **Git**: ~7 новых spec-файлов в `openspec/changes/merge-entries-domain/specs/`, удаление 6 директорий в `openspec/specs/` после архивации.

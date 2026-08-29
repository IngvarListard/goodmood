## Context

Текущее состояние `openspec/specs/` — 20 доменов. Шесть из них описывают один поведенческий контекст (записи дневника состояния), но разделены по слоям реализации (api/data/ui) и фазам внедрения (mobile-entry, mood-states, entry-granularity). Целевая карта из `context/00-spec-domains.md` определяет домен `entries` как объединение всех шести. Архивация sub-changes в прошлом создавала домены по одному change'у — теперь нужно схлопнуть.

## Goals / Non-Goals

**Goals:**
- Один домен `entries` содержит все требования к записям дневника: API, БД-слой, UI (форма/feed/check-in), гранулярность, mobile-form, розу ветров/state labels.
- Старые 6 доменов удалены из `openspec/specs/`.
- Требования сохранены дословно (формулировки + сценарии) — реорганизация без семантических изменений.

**Non-Goals:**
- Редактирование формулировок требований (чистый relocation).
- Затрагивание кода, тестов, поведения.
- Объединение других доменов.

## Decisions

### D1: Механика — ADDED в новый + REMOVED из старых
**Решение:** В `specs/entries/spec.md` — `## ADDED Requirements` со всеми требованиями из 6 старых доменов (полное тело: description + scenarios). В каждом из 6 старых — `## REMOVED Requirements` с `**Reason**` и `**Migration**` (без полного тела).

**Альтернативы:**
- *RENAMED Requirements (FROM:/TO:)* — не подходит: RENAMED переименовывает требование внутри домена, не перемещает между доменами.
- *--skip-specs + ручной git mv* — грязнее: delta-спеки пустые, реорганизация не отражена в OpenSpec. Не используем.

**Why:** ADDED создаёт новый домен с полным телом; REMOVED зачищает старые. При архивации OpenSpec применит дельту: создаст `entries/spec.md`, удалит требования из 6 старых. Старые директории опустеют — удалим вручную (см. D2).

### D2: Удаление пустых директорий — вручную после архивации
**Решение:** После `openspec archive` проверить, что 6 старых `openspec/specs/<domain>/spec.md` не содержат требований, и удалить директории через `git rm -r`.

**Why:** OpenSpec CLI не поддерживает `retire_capabilities` (идея из `00-spec-domains.md` не реализована в CLI). Удаление пустых spec-директорий — безопасная ручная операция.

### D3: Порядок требований в объединённом `entries/spec.md`
**Решение:** Логическая группировка (не алфавитная):
1. БД-слой и схема (из `entries-data`)
2. API endpoints (из `entries-api`)
3. Форма / check-in / mobile (из `mobile-entry` + `entries-ui`)
4. Feed / лента (из `entries-ui`)
5. Роза ветров / state labels (из `mood-states`)
6. Гранулярность / периоды (из `entry-granularity`)

**Why:** Читатель спеки проходит от данных к API к UI к аналитике — естественный flow.

### D4: Конфликт имён требований
Несколько требований в разных старых доменах имеют одинаковые имена (например, «Optional blocks rendered as DaisyUI collapses» в `entries-ui` и «Optional blocks as DaisyUI collapses» в `mobile-entry` — разные имена, ок; но «Entry data access functions» / «Entry data access handles state_label...» — разные). Проверю: дубликатов полных имён требований нет. При ADDED в новый домен конфликта не будет.

## Risks / Trade-offs

- **[Объём delta-спеки]** ADDED в `entries/spec.md` ~570 строк → тяжело ревьюить. → Mitigation: требования копируются дословно из существующих спек, ревью сводится к проверке что «ничего не потеряли и не изменили».
- **[Потеря требования при копировании]** Можно пропустить требование. → Mitigation: tasks содержит явный чек-лист «сравнить количество требований до и после».
- **[OpenSpec warning «>10 deltas»]** Change содержит 7 delta-файлов. → Mitigation: non-blocking warning, игнорируем.

## Migration Plan

1. Написать артефакты change (proposal, design, specs, tasks).
2. `openspec validate --changes merge-entries-domain` — структура валидна.
3. `openspec archive merge-entries-domain --yes` — дельта применена, `entries/spec.md` создан, 6 старых опустошены.
4. Проверить: `openspec/specs/entries/spec.md` содержит все ~30 требований; 6 старых `spec.md` пусты.
5. `git rm -r openspec/specs/{entries-api,entries-data,entries-ui,entry-granularity,mobile-entry,mood-states}`.
6. `openspec doctor` — рут здоров.
7. Коммит.

**Rollback:** `git revert` коммита архивации + удаления.

## Open Questions

(нет — все решения приняты)

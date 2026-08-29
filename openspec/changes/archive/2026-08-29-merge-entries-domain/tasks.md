## 1. Валидация change

- [ ] 1.1 `openspec validate --changes merge-entries-domain` — структура валидна (ADDED в entries, REMOVED в 6 старых).
- [ ] 1.2 Проверить: количество ADDED-требований в `entries/spec.md` = сумме REMOVED-требований в 6 старых дельта-спеках (ничего не потеряли).
- [ ] 1.3 Проверить: имена всех ADDED-требований уникальны в пределах `entries` (нет дубликатов).

## 2. Архивация

- [ ] 2.1 `openspec archive merge-entries-domain --yes` — дельта применена: `openspec/specs/entries/spec.md` создан со всеми требованиями; 6 старых `spec.md` опустошены (REMOVED применён).
- [ ] 2.2 Проверить `openspec/specs/entries/spec.md` — содержит все ~32 требования, сгруппированные по схеме design.md D3 (БД → API → форма/feed → роза/labels → гранулярность).
- [ ] 2.3 Проверить 6 старых `openspec/specs/{entries-api,entries-data,entries-ui,entry-granularity,mobile-entry,mood-states}/spec.md` — не содержат требований (пустой `## Requirements` или удалены).

## 3. Удаление пустых директорий

- [ ] 3.1 `git rm -r openspec/specs/entries-api openspec/specs/entries-data openspec/specs/entries-ui openspec/specs/entry-granularity openspec/specs/mobile-entry openspec/specs/mood-states`
- [ ] 3.2 `openspec doctor` — рут здоров, нет битых ссылок на удалённые домены.

## 4. Финальная проверка

- [ ] 4.1 `ls openspec/specs/` — 14 доменов (было 20, минус 6, плюс 1 `entries`).
- [ ] 4.2 `openspec list` — active changes пуст (change заархивирован).
- [ ] 4.3 Приложение запускается (`clj -M -m app.core`), health-check проходит — код не затронут.
- [ ] 4.4 Коммит: "openspec: merge 6 entries-* domains into single entries domain".

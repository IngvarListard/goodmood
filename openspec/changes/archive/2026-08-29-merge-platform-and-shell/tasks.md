## 1. Валидация change

- [ ] 1.1 `openspec validate --changes merge-platform-and-shell` — структура валидна.
- [ ] 1.2 Проверить: ADDED в `platform` (12 req) = REMOVED из http-server(3) + db-connection(2) + db-migrations(2) + project-structure(3) = 10. ADDED в `ui-shell` (13 req) = REMOVED из layout(7) + navigation(4) + placeholder-pages(3) = 14. Внимание: layout имеет 7, navigation 4, placeholder 3 — итого 14 для ui-shell; http-server 3 + db-connection 2 + db-migrations 2 + project-structure 3 = 10 для platform. Сверить суммы.
- [ ] 1.3 Проверить уникальность имён требований в `platform` и `ui-shell`.

## 2. Архивация

- [ ] 2.1 `openspec archive merge-platform-and-shell --yes` — дельта применена: `openspec/specs/{platform,ui-shell}/spec.md` созданы; 7 старых опустошены.
- [ ] 2.2 Проверить содержимое `openspec/specs/{platform,ui-shell}/spec.md`.
- [ ] 2.3 Проверить 7 старых `spec.md` — пусты.

## 3. Удаление пустых директорий

- [ ] 3.1 `git rm -r openspec/specs/{http-server,db-connection,db-migrations,project-structure,layout,navigation,placeholder-pages}`.
- [ ] 3.2 `openspec doctor` — рут здоров.

## 4. Финальная проверка

- [ ] 4.1 `ls openspec/specs/` — 9 доменов (после change 1: 14, минус 7, плюс 2).
- [ ] 4.2 Приложение запускается, health-check проходит.
- [ ] 4.3 Коммит: "openspec: merge platform (4→1) and ui-shell (3→1) domains".

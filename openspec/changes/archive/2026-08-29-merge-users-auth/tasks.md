## 1. Валидация change

- [ ] 1.1 `openspec validate --changes merge-users-auth` — структура валидна.
- [ ] 1.2 Проверить: ADDED в `users-auth` (5 req) = REMOVED из `access-control` (5 req).

## 2. Архивация

- [ ] 2.1 `openspec archive merge-users-auth --yes` — 5 требований добавлены в `openspec/specs/users-auth/spec.md`; `access-control/spec.md` опустошен.
- [ ] 2.2 Проверить `openspec/specs/users-auth/spec.md` — 12 требований (7 существующих + 5 новых).
- [ ] 2.3 Проверить `openspec/specs/access-control/spec.md` — пуст.

## 3. Удаление пустой директории

- [ ] 3.1 `git rm -r openspec/specs/access-control`.
- [ ] 3.2 `openspec doctor` — рут здоров.

## 4. Финальная проверка

- [ ] 4.1 `ls openspec/specs/` — 9 доменов (после всех 3 changes).
- [ ] 4.2 Приложение запускается, health-check проходит.
- [ ] 4.3 Коммит: "openspec: merge access-control into users-auth domain".

## 5. Обновление context/00-spec-domains.md (после всех 3 changes)

- [ ] 5.1 Обновить целевую карту доменов (теперь 9 вместо 7): `entries`, `medications`, `insights`, `users-auth`, `i18n`, `ui-shell`, `platform`, `ai-assistant`, `coping-channels`.
- [ ] 5.2 Обновить статус: реструктуризация проведена.

## Why

Аудит over-engineering (ponytail-audit, весь tree) нашёл ~600 строк мёртвого и дублированного кода: мёртвый namespace-заглушка, 10 копий html-response, 17 копий тестовой fixture, опора на транзитивные зависимости, мёртвые функции и спекулятивные ветки. Это шум, который дороже поддерживать, чем удалить, и он маскирует живую логику.

## What Changes

- Удалить мёртвый код: `app.views.placeholder` (+ тест), `db.users/get-user-by-id`, `app.domains.ai/all-findings`, `app.i18n/t-for`, неиспользуемую арность `chat-history`, 4 no-op `ig/halt-key!` в system.clj, startup-warning в core.clj, 2 мусорных e2e-спека (`__screenshot`, `__dbg-locale`)
- Дедуплицировать: `html-response` из 10 route-файлов в один хелпер; тестовую fixture `with-test-db` из 17 файлов в один `app.test-helpers`
- Схлопнуть бойлерплейт AI-настроек: 6 флагов × coerce-enabled повторены трижды; 6 однотипных `*-enabled?` предикатов через один хелпер
- Убрать спекулятивные элементы: ветка `match-by-name :login` в require-auth, `local-redirect`-обёртка, лишние def'ы в icons.clj, боилерплейт validate-malli в 3 доменах через общий хелпер
- Сделать зависимости явными: заменить `clojure.data.json` (транзитив) на cheshire в `db/ai.clj`; `jsonista` в 6 тестах — заменить на cheshire или объявить явно
- Сократить тестовый алиас deps.edn: `clojure.test/run-all-tests` вместо списка из 21 ns

Решения владельца (зафиксированы в explore):
- `register-user` остаётся (даже хотя тест-only)
- иконки (unused SVG) и user-id дедуп — отозваны, не режем
- JSON-ветка create-entry остаётся (тест-контракт, дуальный режим намеренный)

## Capabilities

### New Capabilities

- `codebase-cleanup`: требования к внутренней чистоте кодовой базы — единые хелперы вместо копий, отсутствие мёртвого кода, явные зависимости. Поведенчески: дедуп html-response и fixture не меняет внешнее поведение; удалённые функции не имели вызовов.

### Modified Capabilities

- Нет: все удаления (включая `db.users/get-user-by-id`) — детали имплементации без вызовов, спек-уровень поведения `users-auth` и остальных способностей не меняется.

## Impact

- Затронутый код: `src/app/views/placeholder.clj`, `src/app/routes/*` (html-response), `src/app/system.clj`, `src/app/core.clj`, `src/app/icons.clj`, `src/app/i18n.clj`, `src/app/middleware.clj`, `src/app/db/users.clj`, `src/app/db/ai.clj`, `src/app/domains/ai.clj`, `src/app/domains/{insights,notification_settings,state_periods}.clj`, `test/app/**`, `e2e/tests/`, `deps.edn`
- Зависимости: ничего не удаляется из deps.edn; убирается опора на 2 транзитива (data.json, jsonista)
- Риск: низкий — удаляется только код без вызовов и дедуп без изменения поведения; проверка — полный прогон тестов после каждого батча

## Scope

Внутренняя чистота: мёртвый код, дубли, неявные зависимости. Только то, что нашёл аудит и подтвердил ревью (list см. design.md).

## Non-goals

- Не трогаем иконки (SVG-ассеты), `register-user`, JSON-ветку create-entry, делегирующий слой db→domain, muuntaja/malli-коерцию
- Не рефакторим архитектуру (слои db/domain/routes остаются)
- Не чиним корректность/безопасность — это не тот проход

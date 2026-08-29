## Why

Домен `access-control` был выделен из `add-users-auth-i18n` просто потому что change завёл две spec-директории. По целевой карте (`00-spec-domains.md`) это один домен `users-auth` — аккаунт, вход, сессии, RBAC, route protection, identity в request. Разделение искусственное.

## What Changes

- **BREAKING (спецификации)**: домен `access-control` (5 требований) вливается в `users-auth` (7 требований) → 12 требований в объединённом `users-auth`.
- `access-control` удаляется после архивации.
- **Код и поведение не меняются.**

## Scope

- `openspec/specs/users-auth/` — расширяется (получает 5 требований из access-control)
- `openspec/specs/access-control/` — удаляется

## Non-goals

- Изменение кода, поведения, API
- Другие домены

## Capabilities

### New Capabilities
(нет — `users-auth` уже существует, расширяем его)

### Modified Capabilities
- `users-auth`: +5 требований из access-control (route protection, authenticated access, RBAC, entry data scoping, identity in request)
- `access-control`: REMOVED всех 5 требований (домен удаляется)

## Impact

- **Спецификации**: `openspec/specs/` — 1 директория удаляется. После всех 3 changes: 20 → 9 доменов.
- **Код/тесты/поведение**: не затрагиваются.

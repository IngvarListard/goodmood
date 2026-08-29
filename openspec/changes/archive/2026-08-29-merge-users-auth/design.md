## Context

`access-control` — искусственный домен, выделенный при архивации `add-users-auth-i18n` (change завёл две spec-директории). По целевой карте — это один домен `users-auth`.

## Goals / Non-Goals

**Goals:** `users-auth` содержит таблицу users, password hashing, login/logout, CSRF, session expiration, admin seed, route protection, RBAC, entry data scoping, identity в request. `access-control` удалён.

**Non-Goals:** редактирование формулировок, затрагивание кода.

## Decisions

### D1: Механика — ADDED в users-auth + REMOVED из access-control
В `specs/users-auth/spec.md` — `## ADDED Requirements` с 5 требованиями из access-control (полное тело). В `specs/access-control/spec.md` — `## REMOVED Requirements` (Reason + Migration). Существующие 7 требований в `users-auth` не трогаются (они уже в main spec).

### D2: Порядок в users-auth (после архивации)
Существующие 7 (users table, password hashing, login, logout, CSRF, session expiration, admin seed) + новые 5 (route protection, authenticated access, RBAC, entry data scoping, identity in request). Порядок: аутентификация → авторизация/RBAC → scoping.

### D3: «Entry data scoping» остаётся в users-auth
Требование «Entry data scoping» из access-control описывает scoping при listing — это auth-поведение (identity-based), не entries-домен. Оставляем в users-auth.

## Risks / Trade-offs

- Минимальные: 1 ADDED + 1 REMOVED = 2 delta-файла, самый маленький из 3 changes.

## Migration Plan

1. Написать артефакты.
2. `openspec validate --changes merge-users-auth`.
3. `openspec archive merge-users-auth --yes` — 5 требований добавятся в `users-auth/spec.md`, `access-control/spec.md` опустеет.
4. `git rm -r openspec/specs/access-control`.
5. `openspec doctor`.
6. Коммит.

## Open Questions

(нет)

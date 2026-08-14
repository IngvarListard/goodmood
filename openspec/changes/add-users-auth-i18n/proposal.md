## Why

Приложение на данный момент не защищено: любой может создавать/читать записи,
нет разделения данных между пользователями, все UI-строки захардкожены
по-русски. Это блокирует реальное использование трекера настроения.
Требуется единый архитектурный шаг: пользователи, аутентификация, разграничение
доступа и интернационализация.

## What Changes

- **Пользователи**: таблица `users`, домен создания и аутентификации, bcrypt-хеш паролей
- **Аутентификация**: форма логина, сессия на подписанных куках (ring-session), CSRF-защита (ring-anti-forgery), logout
- **Разграничение доступа**: все роуты закрыты; публичные только `GET /` и `/login*`; записи привязаны к пользователю; middleware ролей (user/admin) заложено с первого дня
- **Интернационализация**: ключи вместо строк, словари ru/en через tongue, определение языка по куке / Accept-Language / дефолт, plural-формы для русского, динамический `<html lang>`
- **UI**: плашка пользователя внизу десктопного сайдбара (dropdown: logout, переключатель языка); мобилка — юзер-панель на странице «Настройки» (по шестерёнке)
- **Seed**: при старте, если нет пользователей — создаётся админ из env-переменных, старые записи привязываются к нему
- Breaking changes: **BREAKING** — `entries.user_id` добавляется как nullable (после seed — не-null для новых); роуты возвращают 302 для неаутентифицированных

## Capabilities

### New Capabilities
- `users-auth`: таблица пользователей, регистрация (заготовка), аутентификация (логин/логоут), bcrypt-хранение паролей, сессия на подписанных куках, CSRF-защита форм
- `access-control`: middleware авторизации: незалогиненные → 302 /login, роли user/admin в identity, защита всех роутов кроме явно публичных, scoping записей по user_id
- `i18n`: словари ru/en через tongue, wrap-locale middleware, динамический `<html lang>`, plural-формы для русского, перевод существующих UI-строк

### Modified Capabilities
- `entries-data`: таблица entries получает nullable-колонку user_id; create-entry домен привязывает запись к текущему пользователю; get-entries фильтрует по user_id
- `entries-api`: POST/GET /entries требуют аутентификации; GET /entries отдаёт только записи текущего пользователя

## Impact

- **Новые зависимости**: `buddy/buddy-hashers`, `tongue/tongue`, `ring/ring-anti-forgery`
- **Новые файлы**: `app/db/users.clj`, `app/domains/users.clj`, `app/routes/auth.clj`, `app/middleware.clj`, `app/views/auth.clj`, `app/views/user-menu.clj`, `app/views/settings.clj`, `resources/i18n/ru.edn`, `resources/i18n/en.edn`, `resources/migrations/003-add-users-auth.up.sql`, `resources/migrations/003-add-users-auth.down.sql`
- **Изменённые файлы**: `deps.edn` (+зависимости), `app/system.clj` (seed, secret, middleware), `app/routes/app.clj` (middleware-цепочка, :public-разметка), `app/views/layout.clj` (динамический lang, пользователь в сайдбаре), `app/views/navigation.clj` (переход на ключи i18n), `app/views/placeholder.clj` (ключи i18n), `app/domains/entries.clj` (+user_id), `app/db/entries.clj` (+user_id)
- **Конфигурация**: env `GOODMOOD_SESSION_SECRET`, `GOODMOOD_ADMIN_EMAIL`, `GOODMOOD_ADMIN_PASSWORD`

## Scope

- **In scope**: users таблица + домен, login/logout роуты, CSRF, сессия, защита всех роутов, scoping записей, ролевая модель (user/admin), i18n-инфраструктура + перевод существующих UI-строк, плашка пользователя в сайдбаре и на странице настроек, seed админа
- **Out of scope / Non-goals**: регистрация новых пользователей через UI (только домен-функция и таблица готовы), админка, OAuth/OIDC, восстановление пароля, email, перевод всех будущих экранов (только существующие строки)

## Acceptance Criteria

- GIVEN набран неверный пароль WHEN отправлен POST /login THEN 200 с сообщением об ошибке (i18n)
- GIVEN верный пароль WHEN отправлен POST /login THEN 302 на / (или next) и установлена сессионная кука
- GIVEN не залогинен WHEN GET /dashboard THEN 302 на /login?next=/dashboard
- GIVEN залогинен WHEN GET /dashboard THEN 200 с плашкой пользователя в сайдбаре
- GIVEN залогинен WHEN POST /logout THEN 302 /login и сессия очищена
- GIVEN залогинен как user1 WHEN создана запись THEN запись привязана к user1
- GIVEN залогинен как user1 WHEN GET /entries THEN в ответе только записи user1
- GIVEN кука locale=en WHEN отрендерена страница THEN <html lang="en"> и строки на английском
- GIVEN нет пользователей при старте WHEN система стартует THEN создан админ из env и старые записи привязаны к нему

## Non-functional requirements

- Пароли хранятся bcrypt-хешами (buddy-hashers)
- Сессионная кука подписана (ring-session cookie-store), не требует БД
- Все формы login/logout защищены CSRF-токеном (ring-anti-forgery)
- Локаль определяется: кука locale > Accept-Language > :ru
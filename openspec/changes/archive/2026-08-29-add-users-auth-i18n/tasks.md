## 1. Фундамент (deps, миграции, seed)

- [x] 1.1 Добавить зависимости в `deps.edn`: `buddy/buddy-hashers {:mvn/version "2.0.167"}`, `tongue/tongue {:mvn/version "0.4.4"}`, `ring/ring-anti-forgery {:mvn/version "1.4.0"}`
- [x] 1.2 Добавить конфиг `:app.core/secret` в `system-config` (из env `GOODMOOD_SESSION_SECRET`; если нет — аварийный выход с ошибкой)
- [x] 1.3 Создать миграцию `resources/migrations/003-add-users-auth.up.sql`: таблица `users` + `ALTER TABLE entries ADD COLUMN user_id INTEGER REFERENCES users(id)`
- [x] 1.4 Создать миграцию `resources/migrations/003-add-users-auth.down.sql`
- [x] 1.5 Реализовать seed-функцию в `app.system`: если `users` пусто — создать админа из `GOODMOOD_ADMIN_EMAIL`/`GOODMOOD_ADMIN_PASSWORD`, выполнить `UPDATE entries SET user_id = <admin-id> WHERE user_id IS NULL`

## 2. i18n-инфраструктура (словари, wrap-locale)

- [x] 2.1 Создать `resources/i18n/ru.edn` — словарь: навигация, auth-строки, общие строки
- [x] 2.2 Создать `resources/i18n/en.edn` — английские переводы тех же ключей
- [x] 2.3 Создать `app/middleware.clj` с `wrap-locale`: определение языка (кука gm-locale > Accept-Language > :ru), биндинг dynamic var `*locale*`
- [x] 2.4 Создать i18n-модуль (в `app/middleware.clj` или `app/i18n.clj`): загрузка словарей через tongue, функция `(t key & args)`, inflection fn для русских plural-форм
- [x] 2.5 Изменить `app/views/layout.clj`: `[:html {:lang (name *locale*)} ...]` — динамический
- [x] 2.6 Изменить `app/views/navigation.clj`: nav-items лейблы на ключи `:nav/dashboard` и т.д., вызов `t`
- [x] 2.7 Изменить `app/views/placeholder.clj`: заголовки и "Раздел в разработке" на ключи
- [x] 2.8 Языковой переключатель: на login-странице и в настройках; сеттинг куки `gm-locale`; роут-редирект на себя после смены

## 3. Пользователи: домен и БД

- [x] 3.1 Создать `app/db/users.clj`: `create-user!`, `get-user-by-email`, `get-user-by-id` через next.jdbc
- [x] 3.2 Создать `app/domains/users.clj`: `create-user` (bcrypt через buddy-hashers/derive), `authenticate` (verify через buddy-hashers/check, возвращает user или nil)
- [x] 3.3 (Заготовка) `register-user` — домен-функция с валидацией email, без HTTP-endpoint; готова для будущей регистрации
- [x] 3.4 Тест `app.domains.users-test`: derive/check round-trip, неверный пароль, несуществующий email

## 4. Auth middleware и роуты

- [x] 4.1 Добавить в `app/middleware.clj` `wrap-identity`: читает `(-> request :session :identity)`, assoc в request
- [x] 4.2 Добавить `require-auth`: для всех запросов парсит reitit-матч, если нет `:auth/public` и нет identity → 302 /login?next=uri; если есть `:auth/roles` → проверяет `(contains? roles role)`
- [x] 4.3 Создать `app/routes/auth.clj`: роуты `GET /login`, `POST /login`, `POST /logout` с `:auth/public true`
  - GET /login: отдаёт форму логина (если уже залогинен — 302 /)
  - POST /login: параметры email+password, вызов `domains/authenticate`, при успехе — assoc-in session, 302 / (или next). При ошибке — 200 с формой и сообщением
  - POST /logout: clear session, 302 /login
- [x] 4.4 Интегрировать middleware в `app/routes/app.clj` (`->app`): wrap-session, wrap-anti-forgery, wrap-locale, wrap-identity, require-auth
- [x] 4.5 Разметить роуты: `GET /` и auth-роуты как `:auth/public true`; все остальные — без метки (защищены по умолчанию)
- [x] 4.6 CSRF: meta-тег в layout для htmx; hidden input в формах login/logout

## 5. UI компоненты

- [x] 5.1 Создать `app/views/auth.clj`: login-страница (daisyui card, email+password inputs, submit, ссылка переключения языка, сообщение об ошибке)
- [x] 5.2 Создать `app/views/user-menu.clj`: плашка пользователя в сайдбаре (dropdown с logout и переключателем языка)
- [x] 5.3 Интегрировать user-menu в `app/views/layout.clj`: внизу десктопного сайдбара (только когда identity есть)
- [x] 5.4 Создать `app/views/settings.clj`: страница «Настройки» (аватарка + имя + email + язык + logout-кнопка)
- [x] 5.5 Обновить роутер: `/settings` — handler с проверкой auth (как и все остальные), отдаёт settings-страницу вместо placeholder

## 6. scoping записей

- [x] 6.1 Изменить `app/db/entries.clj`: `create-entry!` принимает `user-id`, `get-entries` принимает `user-id` и фильтрует `WHERE user_id = ?`
- [x] 6.2 Изменить `app/domains/entries.clj`: `create-entry` принимает `user-id` параметр, `list-entries` принимает `user-id`
- [x] 6.3 Изменить `app/routes/entries.clj`: брать `user-id` из `(:identity request)` и передавать в домен
- [x] 6.4 Обновить тесты `app/db/entries_test` с учётом `user-id`

## 7. Интеграционное тестирование

- [x] 7.1 Тест auth-flow: GET /dashboard без сессии → 302; POST /login неверный → ошибка; POST /login верный → кука + 302; GET /dashboard с кукой → 200; POST /logout → кука очищена
- [x] 7.2 Тест CSRF: POST /login без токена → 403
- [x] 7.3 Тест scoping: два пользователя, записи каждого, GET /entries — каждому своё
- [x] 7.4 Тест i18n: кука gm-locale=en → рендер на английском; без куки → Accept-Language; html lang атрибут
- [x] 7.5 Тест seed: на пустой БД создан админ; повторный старт — дубликата нет
- [x] 7.6 Прогнать полный suite: `clj -M:test` — все тесты зелёные

## 8. Проверка и фиксы

- [x] 8.1 Запустить приложение `clj -M -m app.core` с env GOODMOOD_SESSION_SECRET + GOODMOOD_ADMIN_PASSWORD
- [x] 8.2 Проверить curl: POST /login (valid/invalid), GET /dashboard (с кукой/без), GET /entries (scoping), POST /logout
- [x] 8.3 Проверить в браузере: вход, плашка в сайдбаре, переключение языка, выход
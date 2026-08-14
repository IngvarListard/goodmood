## Context

Проект — трекер настроения при биполярном расстройстве. Стек: Clojure, ring +
ring-jetty-adapter, reitit, integrant, next.jdbc + HoneySQL, SQLite, migratus,
hiccup2, htmx + hyperscript, Tailwind + DaisyUI.

Текущее состояние:
- Приложение без аутентификации, любой может создавать/читать записи
- `entries` без привязки к пользователю
- Все UI-строки захардкожены по-русски: `layout {:lang "ru"}`, nav-items, placeholder
- Инфраструктура: integrant (`:db/connection`, `:app.core/server`), reitit-роутер без middleware на уровне app
- ring-core 1.15.5 уже в зависимостях (включает wrap-session + cookie-store)

Нужно: единый шаг — пользователи, аутентификация, разграничение доступа и интернационализация.

## Goals / Non-Goals

**Goals:**
- Таблица users + домен (создание, authenticate через bcrypt)
- Аутентификация: форма логина, сессия на подписанных куках, CSRF-защита
- Все роуты (кроме / и /login) требуют аутентификации; незалогиненные → 302 /login
- Записи ограничены пользователем (user_id в entries, scoping при создании/чтении)
- Ролевая модель user/admin, middleware проверяет роли
- i18n: словари ru/en через tongue, wrap-locale, динамический lang, plural-формы
- Существующие UI-строки переведены на ключи, готовы к расширению
- Seed-пользователь (админ) из env при первом старте

**Non-Goals:**
- UI регистрации новых пользователей (domain ready, API нет)
- Админка (роль :admin заложена, интерфейса нет)
- OAuth, восстановление пароля, email
- DB-backed sessions (cookie sessions достаточны сейчас, расширяемо позже)

## Decisions

### 1. Аутентификация: ring-session + свой middleware (НЕ buddy-auth)

**Решение**: ring-session (cookie-store из ring-core, уже в deps) + тонкий
middleware wrap-identity (читает `:identity` из сессии) и require-auth
(проверяет наличие identity, иначе 302 /login). Пароли — buddy-hashers (bcrypt).

**Почему**: buddy-auth заброшен (последний коммит 2022, только авто-бампы версий).
Сообщество ушло на ring-session + свой middleware — это консенсус. ring-core
уже в проекте, wrap-session + cookie-store — стабильный код weavejester.

**Альтернативы**:
- buddy-auth 3.0: заморожен, не оправдывает зависимость ради ~50 строк middleware
- JWT через buddy-sign: избыточен для серверного рендеринга (htmx), сессии проще
- DB sessions: избыточно сейчас, можно добавить позже как session-store за интерфейсом ring-session

**Реализация**:
```clojure
;; ring-session с cookie-store, секрет из env
(wrap-session handler {:cookie-name "gm-session"
                       :store (cookie-store {:key session-secret})})
;; wrap-identity — достаёт identity из сессии в :identity ключ request
;; require-auth — если нет :identity → 302 /login?next=uri
;; Роуты помечаются :auth/public для пропуска
```

### 2. CSRF: ring-anti-forgery

**Решение**: ring-anti-forgery 1.4.0 (10M загрузок, weavejester, поддерживается).

**Почему**: стандарт Ring-экосистемы. Совместим с hx-boost и htmx (проверяет
X-CSRF-Token заголовок, который htmx отправляет автоматически из meta-тега).
Формы login/logout рендерят hidden input с токеном.

**Альтернативы**:
- Писать свой CSRF: избыточно, стандартный ring-anti-forgery уже решает всё
- SameSite куки: не заменяет CSRF для форм, только дополняет

**Взаимодействие с htmx**: htmx с `hx-boost` автоматически подхватывает
`<meta name="csrf-token" content="...">` и отправляет в каждом запросе.
Для не-hx-boost запросов (обычный submit формы) — hidden input в форме.

### 3. Хеширование паролей: buddy-hashers (bcrypt)

**Решение**: `buddy-hashers/derive` для хеширования, `buddy-hashers/check` для
проверки. Алгоритм: bcrypt (по умолчанию в buddy-hashers).

**Почему**: де-факто стандарт в Clojure (4M загрузок), активно версионируется.

**Альтернативы**:
- Argon2 через buddy-hashers ({:alg :argon2id}): тоже поддерживается, но bcrypt проще и общепринят.
- Прямой вызов org.mindrot.jbcrypt: работает, но buddy-hashers даёт единый API.

**Схема хранения**: bcrypt hash, buddy-hashers включает соль и параметры в хеш-строку (формат $2b$...).

### 4. Сессия: ring-session cookie-store

**Решение**: подписанная куки-сессия (HMAC), без БД. logout = удалить куку.

**Почему**: ноль новых зависимостей, ring-core уже в deps. Сессия хранит только
identity map {:id :email :role :display-name} — мало данных.

**Расширяемость**: ring-session принимает `:store` — любую реализацию
ring.middleware.session.store.Store протокола. Позже можно заменить
cookie-store на DB-backed store без переделки middleware.

### 5. Мидлварь-цепочка и интеграция с reitit

**Решение**: middleware на уровне ring-handler (ВНЕ reitit router), а не per-route.
Порядок:
```
muuntaja/wrap-format
  → wrap-session (cookie-store, 1 час)
  → ring-anti-forgery/wrap-anti-forgery
  → wrap-locale (определяет язык, биндит *locale*)
  → wrap-identity (из сессии в request)
  → require-auth (проверка :public, ролей)
  → reitit router
```

**Почему**: middleware на уровне handler гарантирует, что НИ ОДИН роут (включая
несуществующие — default-handler) не пропустит запрос. Для публичных роутов —
data-ключ `:auth/public true`, проверяется в require-auth до реитит-роутинга.

**require-auth детали**:
```clojure
;; Перед реитит — проверка для всех запросов
;; 1. Парсим route из request (reitit.core/match-by-path)
;; 2. Если не public И нет identity → 302 /login?next=uri
;; 3. Если есть :auth/roles — проверяем (contains? (set (:roles identity)) ...)
```

### 6. Интернационализация: tongue

**Решение**: `tongue/tongue` 0.4.4. Словари в файлах `resources/i18n/ru.edn`,
`resources/i18n/en.edn`. Функция `t` через dynamic var `*locale*`.

**Почему**: tongue — стандарт Clojure i18n (1.7M загрузок, автор tonsky).
Поддерживает вложенные ключи, плейсхолдеры, кастомные inflection-функции.
Для русского критичны plural-формы: 1 запись / 2 записи / 5 записей;
tongue решает это через inflection fn, получающую счётчик.

**Структура словарей**:
```clojure
;; ru.edn
{:nav {:dashboard "Дашборд" :check-in "Чек-ин" ...}
 :auth {:login "Войти" :logout "Выйти" :email "Email" :password "Пароль" ...}
 ...}

;; en.edn
{:nav {:dashboard "Dashboard" :check-in "Check-in" ...}
 :auth {:login "Log in" :logout "Log out" :email "Email" :password "Password" ...}
 ...}
```

**Определение языка (цепочка приоритетов)**:
1. Кука `gm-locale` (выбор пользователя)
2. Заголовок `Accept-Language` из запроса
3. Дефолт: `:ru`

**Интеграция в views**: views получают `t` через request или замыкание.
dynamic var `*locale*` биндится в wrap-locale middleware на каждый запрос.

**lang атрибут**: `[:html {:lang (name *locale*)} ...]` — динамический.

**Даты/числа**: java.time.format.DateTimeFormatter + java.util.Locale/forLanguageTag
(не через tongue, стандартная Java).

### 7. Seed: создание админа при старте системы

**Решение**: в `app.system` после `db.migrate`, если users пусто — создать админа
из env, привязать к нему старые записи.

**Почему**: не хотим хардкодить bcrypt-хеш в SQL-миграции. Env-переменные —
идиоматичный способ конфигурации. Старые записи (dev-данные) не теряются.

**Env-переменные**:
- `GOODMOOD_ADMIN_EMAIL` (по умолчанию: "admin@goodmood.local")
- `GOODMOOD_ADMIN_PASSWORD` (по умолчанию: случайный, выводится в лог)

**Реализация**: функция `(seed-admin! ds)`, вызывается при старте системы после миграций.

### 8. Пользователь в UI

**Десктоп**: внизу боковой панели (`.hidden.md:flex.fixed.left-0.top-0.h-screen.w-64`).
В текущем layout сайдбар — flex-колонка высотой h-screen. Добавляем внизу
(в навигационный компонент или в layout) плашку с аватаркой (первая буква
display_name, daisyui avatar-placeholder) + имя + dropdown:
- "Выйти" (POST /logout)
- переключатель языка (ru ↔ en)

**Мобилка**: плашка не помещается в bottom-bar (там уже 6 пунктов). Пользователь
нажимает на шестерёнку (Настройки) — открывается страница настроек, где отображается:
- аватарка + display_name + email
- переключатель языка
- кнопка "Выйти" (POST /logout)

**Компонент daisyui**: 
- Десктоп: `dropdown dropdown-top` (раскрывается вверх, так как плашка внизу сайдбара)
- Мобилка: простая секция на странице настроек

### 9. Данные: users и scoping entries

**Таблица users**:
```sql
CREATE TABLE users (
  id            INTEGER PRIMARY KEY AUTOINCREMENT,
  email         TEXT NOT NULL UNIQUE,
  password_hash TEXT NOT NULL,
  display_name  TEXT NOT NULL,
  role          TEXT NOT NULL DEFAULT 'user',
  created_at    TEXT NOT NULL DEFAULT (datetime('now'))
);
```

**Таблица entries (изменение)**:
```sql
ALTER TABLE entries ADD COLUMN user_id INTEGER REFERENCES users(id);
-- nullable на время миграции; после seed — обязателен на уровне приложения
```

**Domain**: `create-entry` принимает `user-id` параметром; `list-entries` фильтрует
`WHERE user_id = ?`. User-id берётся из `(:identity request)`.

### 10. Роли и права доступа

**Модель**: строка в users.role (`"user"` / `"admin"`). В identity — вектор ролей
(сейчас одна роль, расширяемо). Middleware проверяет `(contains? (set roles)
требуемая-роль)`.

**Расширяемость**: когда появится админка, можно добавить `users.role` как массив
(JSON-поле) или отдельную таблицу `user_roles`. Пока одна роль — достаточно строки.

## Middleware chain (visual)

```
Request
  │
  ▼
┌────────────────────────────┐
│ 1. muuntaja/wrap-format    │ JSON encode/decode
├────────────────────────────┤
│ 2. wrap-session            │ cookie-store, 1h expiry
├────────────────────────────┤
│ 3. wrap-anti-forgery       │ CSRF token check
├────────────────────────────┤
│ 4. wrap-locale             │ *locale* binding
├────────────────────────────┤
│ 5. wrap-identity           │ session[:identity] → request[:identity]
├────────────────────────────┤
│ 6. require-auth            │ public? skip : else check identity/roles
├────────────────────────────┤
│ 7. reitit router           │ route match → handler
└────────────────────────────┘
  │
  ▼
Response
```

## File structure (новые и изменённые)

```
src/app/
  middleware.clj          ← wrap-locale, wrap-identity, require-auth
  db/users.clj            ← SQL для пользователей
  domains/users.clj       ← create-user, authenticate, bcrypt
  routes/auth.clj         ← GET/POST /login, POST /logout
  views/auth.clj          ← login page
  views/user-menu.clj     ← плашка юзера в сайдбаре (desktop)
  views/settings.clj      ← страница настроек (mobile user + logout)

  (изменённые)
  system.clj              ← +seed, +secret-config, middleware в ->app
  routes/app.clj          ← middleware-цепочка, :public-разметка роутов
  views/layout.clj        ← динамический lang, юзер в сайдбаре
  views/navigation.clj    ← строки на i18n-ключи
  views/placeholder.clj   ← строки на i18n-ключи
  domains/entries.clj     ← +user-id параметр
  db/entries.clj          ← +user_id в INSERT/SELECT

resources/
  i18n/ru.edn, en.edn
  migrations/003-add-users-auth.{up,down}.sql
```

## Risks / Trade-offs

| Risk | Mitigation |
|------|------------|
| buddy-hashers может обновиться с ломающими изменениями | Фиксируем версию в deps.edn; тесты проверяют derive/check |
| ring-anti-forgery может конфликтовать с htmx json-enc | Проверяем совместимость в тестах; htmx сам подхватывает CSRF meta-тег в hx-boost |
| Cookie-store секрет в коде/конфиге — утечка компрометирует сессии | Секрет ТОЛЬКО из env; отсутствует → аварийное завершение с понятной ошибкой |
| entries.user_id nullable для существующих записей | Seed при старте обновляет запись; в домене create-entry всегда проставляет user_id; nullable — только чтобы миграция прошла на существующих данных |
| tongue plural-функции сложны для русского | Tongue поддерживает кастомные inflection fn; напишем одну и покроем тестами (0, 1, 2, 5, 21...) |

## Migration Plan

1. Добавить зависимости в deps.edn
2. Добавить миграцию 003 (users + entries.user_id)
3. Добавить seed-логику в system start
4. Постепенно наращивать слои: db → domain → middleware → routes → views
5. Финально: изменить существующие views на i18n-ключи

**Rollback**: миграция 003 down (удалить users, удалить user_id из entries),
откат изменений в дефолтном ветке. Сессии не хранятся в БД — rollback чист.

## Open Questions

- Картинка logout UI будет предоставлена при реализации фазы D — дизайн плашки в сайдбаре уточнится
- Нужно ли уведомление о просрочке сессии (1 час)? Пока не добавляем, незалогиненные получают 302
- Стоит ли язык сохранять в сессию пользователя (а не только в куку)? Пока кука + Accept-Language достаточно
## Why

Нужна базовая точка входа в приложение: без работающего HTTP-сервера и управляемого жизненного цикла компонентов невозможно добавлять любую дальнейшую функциональность (БД, роуты, UI).

## What Changes

- Добавлен deps.edn с зависимостями: ring, ring-jetty-adapter, reitit, integrant
- Добавлена integrant-система с одним компонентом :server
- Добавлен reitit-роутер с единственным health-check роутом GET / → 200 OK
- Добавлена main-функция запуска приложения (clj -M -m app.core)

**BREAKING**: нет (новый проект).

## Capabilities

### New Capabilities

- `http-server`: Lifecycle HTTP-сервера (старт/остановка через integrant, health-check endpoint)

### Modified Capabilities

_(нет существующих specs)_

## Impact

- Затрагивает: новые namespace'ы app.core, app.system, app.routes
- Зависимости: ring, ring-jetty-adapter, reitit, integrant (добавляются в deps.edn)
- Пользователи: нет (инфраструктурный слой, ещё не виден конечному пользователю)

## Scope

**In scope**: старт/стоп сервера через integrant, один health-check роут, deps.edn.

**Out of scope**: БД, шаблоны, htmx, любая бизнес-логика, аутентификация.

## Acceptance Criteria

- GIVEN приложение запущено через `clj -M -m app.core`
  WHEN отправлен GET-запрос на "/"
  THEN сервер возвращает HTTP 200

- GIVEN integrant-система запущена
  WHEN вызван `integrant.core/halt!`
  THEN сервер корректно останавливается без исключений

## Non-functional requirements

Время старта приложения — не более 3 секунд на локальной машине.

## Why

Нужен HTTP-интерфейс для создания и получения записей дневника, чтобы
дальнейший UI-слой (htmx) мог с ним взаимодействовать без прямого доступа к БД.

## What Changes

- Добавлены reitit-роуты `POST /entries` и `GET /entries`
- Добавлена malli-схема валидации тела запроса (`activity`, `effect`, `mood_score`, `sleep_hours`)
- Handler'ы вызывают функции из `app.domains.entries.db`
- Breaking changes: нет.

## Capabilities

### New Capabilities
- `entries-api`: HTTP-интерфейс для создания (`POST /entries`) и получения (`GET /entries`) записей дневника с валидацией входных данных

### Modified Capabilities
<!-- Нет изменений требований существующих возможностей -->

## Impact

- Затрагивает: `app.routes` (регистрация новых роутов), `app.domains.entries.handlers` (новый namespace),
  `app.domains.entries.db` (`create-entry!` возвращает созданную строку через `INSERT ... RETURNING *`)
- Зависит от: `add-entries-schema`
- Пользователи: пока только программный доступ (API), UI появится в следующем change

## Scope

- **In scope**: роуты, coercion/валидация входных данных, JSON-ответы
- **Out of scope / Non-goals**: HTML-рендеринг, htmx-фрагменты, аутентификация, rate limiting

## Acceptance Criteria

- GIVEN отправлен POST /entries с валидным телом
  WHEN запрос обработан
  THEN возвращается 201 и созданная запись в JSON
- GIVEN отправлен POST /entries с mood_score = 15 (вне диапазона)
  WHEN запрос обработан
  THEN возвращается 400 с описанием ошибки валидации
- GIVEN в БД есть записи
  WHEN отправлен GET /entries
  THEN возвращается 200 со списком записей в JSON

## Non-functional requirements

Время ответа GET /entries — не более 200ms при до 1000 записей в таблице.

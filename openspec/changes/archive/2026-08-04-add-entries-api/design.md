## Context

Проект — трекер настроения при биполярном расстройстве. Стек: Clojure, ring +
ring-jetty-adapter, reitit, integrant, next.jdbc + HoneySQL, SQLite, migratus.

Текущее состояние:
- `app.routes` содержит один статический роут `GET /` (health-check),
  и `routes/app` — статичный `ring-handler` без доступа к данным.
- `app.domains.entries.db` уже содержит `create-entry!` и `get-entries` (из
  change `add-entries-schema`); функции принимают datasource первым аргументом.
- `:db/connection` (datasource) — integrant-компонент; сервер `:app.core/server`
  пока не ссылается на него.

Нужно: HTTP-интерфейс (`POST /entries`, `GET /entries`) с валидацией тела по
malli-схеме и JSON-ответами. UI (htmx) появится в следующем change и будет
потреблять этот API через `fetch`/htmx без прямого доступа к БД.

## Goals / Non-Goals

**Goals:**
- Роуты `POST /entries` (создание, 201 + JSON созданной записи) и
  `GET /entries` (список, 200 + JSON-массив)
- Валидация тела запроса: `activity` (string), `effect` (string),
  `mood_score` (0–10), `sleep_hours` (number); 400 + описание ошибки при невалидном теле
- Handler'ы в `app.domains.entries.handlers`, вызывающие функции `app.domains.entries.db`
- Стандартизированные JSON-ответы через muuntaja

**Non-Goals:**
- HTML-рендеринг и htmx-фрагменты (следующий change)
- Аутентификация, rate limiting
- Пагинация/фильтры для `GET /entries`

## Decisions

### Доступ handler'ов к datasource через замыкание роутера
`routes/app` превращается из статичного значения в функцию `(->app ds)`,
возвращающую `ring-handler` с захваченным datasource. В `app.system` компонент
`:app.core/server` получает `(ig/ref :db/connection)` и строит handler
через `(routes/->app connection)`.
- **Почему**: данные — это состояние, и, согласуясь с уже принятым стилем
  (`create-entry!` принимает `ds`), мы не заводим глобальные атомы.
- **Альтернатива**: глобальный регистрирующийся datasource — отвергнуто
  (скрытые зависимости, сложнее тестировать).

### Router с malli-coercion
Роуты описываются данными reitit: `["/entries" {:post {:parameters {:body ...}
:responses {...}} :handler create-entry-handler} ...]`. В `:data` роутера
подключается `:coercion malli/coercion`, а в middleware — muuntaja
(`muuntaja.middleware/wrap-format`, формат `application/json`).
- **Почему**: reitit + malli дают декларативную валидацию тела (`:body`) и
  соответствуют стеку проекта (reitit уже используется).
- **Обработка ошибок coercion**: добавляется middleware/`:exception`,
  маппящая исключение `:type :reitit.coercion/request-coercion` в `400`
  с телом `{"errors" {поле [сообщения]}}` (через `malli.error/humanize`) — так
  выполнено требование "400 с описанием ошибки валидации". Формат по
  полям удобнее для UI-слоя, чем плоский массив сообщений.

### Схема тела POST /entries
Malli-схема в `app.domains.entries.handlers` (или отдельно в `app.domains.entries.schema`):
```clojure
[:map
 [:activity :string]
 [:effect :string]
 [:mood_score [:int {:min 0 :max 10}]]
 [:sleep_hours [:maybe :double]]]
```
`sleep_hours` может быть `null` (`:maybe :double`, ключ обязателен), остальные поля
обязательны и не-null — соответствует таблице `entries` (NULL разрешён только для `sleep_hours`).
mood_score 0–10 дублирует CHECK из БД на уровне приложения — это даёт понятную
400-ошибку вместо 500 от SQLite.

### Handler'ы
- `create-entry!` handler: берёт `:body` из параметров, вызывает
  `db/create-entry!`, возвращает `{:status 201 :body <запись>}`.
- `get-entries` handler: вызывает `db/get-entries`, возвращает
  `{:status 200 :body <список>}`.
- Роуты регистрируются поверх health-check `GET /`.
- Чтобы выполнить требование «201 + созданная запись», `db/create-entry!` (из
  `add-entries-schema`) расширен: в SQL добавлен `:returning [:*]`, поэтому функция
  возвращает вставленную строку целиком (id, created_at и полями) вместо
  `update-count`. Благодаря `INSERT ... RETURNING` не нужен повторный SELECT.

### Новые зависимости
- `metosin/malli {:mvn/version "0.20.1"}` — валидация/coercion (последняя в локальном кэше)
- `metosin/muuntaja {:mvn/version "0.6.11"}` — JSON encode/decode middleware
Обе есть в локальном `~/.m2`. Тестовая проверка версий перед установкой: при
недоступности сети использовать эти версии, при доступности — сверить с Clojars.

## Risks / Trade-offs

- [Меняется сигнатура `routes/app` (статичное значение → функция)] →
  правка только в `app.system` и тестах; внешнего API приложения нет.
- [muuntaja + malli добавляют зависимости] → это стандартная связка reitit,
  легковесная, обе уже в локальном кэше.
- [Ошибка валидации: JSON/styles muuntaja возвращает по-разному] →
  фиксируем единый DEBUG-формат через `:exception` handler'а coercion (400 + `:errors`).
- [БД: `GET /entries` на SQLite] → для ≤1000 строк выборка тривиально быстрее 200ms;
  при росте добавится пагинация отдельным change.

## Migration Plan

Deploy: добавить роуты и middleware — hot-deploy через существующий запуск
`clj -M -m app.core`. Down-миграций не требуется, схема БД не меняется.
Rollback: откат к `routes/app` без данных.

## Open Questions

- Формат ошибки валидации: фиксированный `{"errors": {поле [сообщения]}}`
  (утверждается в этом change, т.к. от него зависит UI-слой).

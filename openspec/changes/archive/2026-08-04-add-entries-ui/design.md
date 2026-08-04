## Context

Проект — трекер настроения при биполярном расстройстве. Стек: Clojure, ring +
ring-jetty-adapter, reitit, integrant, hiccup2, htmx, hyperscript, Tailwind +
DaisyUI, SQLite, next.jdbc + HoneySQL, migratus.

Текущее состояние (после change `add-entries-api`):
- `app.routes` содержит роуты `POST /entries` и `GET /entries` на JSON-ответы;
  функция `(->app ds)` строит ring-handler с захваченным datasource.
- `app.domains.entries.handlers` — `create-entry` (201 + JSON) и `get-entries`
  (200 + JSON); malli-схема `create-entry-schema`.
- `app.domains.entries.db` — `create-entry!` (через `INSERT ... RETURNING *`) и
  `get-entries`.
- Ошибки coercion (невалидное тело) обрабатываются в `app.routes` middleware
  `coercion-error-middleware`: 400 + JSON `{:errors {поле [сообщения]}}`.
- Статики (htmx, CSS) в проекте нет — только `resources/goodmood.db` и миграции.

Нужно: первый видимый пользователю слой — HTML-страница со списком записей и
формой добавления, обмен фрагментами через htmx, базовая стилизация
Tailwind + DaisyUI. JSON-API из `add-entries-api` не ломается (см. абсолютный
критерий ниже).

## Goals / Non-Goals

**Goals:**
- `GET /entries`: для браузерных запросов (Accept предпочитает `text/html`) —
  полная HTML-страница с формой и списком; для API-клиентов — прежний JSON
- `POST /entries`: для htmx-запросов (заголовок `HX-Request`) — HTML-фрагмент;
  для API-клиентов — прежний JSON (201 / 400)
- Ошибка валидации формы показывается на странице HTML-фрагментом без reload
- Новая запись добавляется в список без reload
- Стилизация через Tailwind (утилиты) + DaisyUI (компоненты); адаптив 375px
- HTML-шаблоны — простые hiccup2-функции без сложных макросов (конвенция проекта)

**Non-Goals:**
- Клиентская валидация формы (только серверная)
- Редактирование/удаление записей, пагинация, фильтры
- Статистика, психотерапевтический дневник
- Раздельная HTML-раздача `GET /entries` без JSON (JSON остаётся)

## Decisions

### GET /entries — content negotiation по Accept
Handler `get-entries` проверяет заголовок `Accept`: если запрос предпочитает
`text/html` (содержит `text/html`), возвращает полную HTML-страницу
({:status 200, :headers {"Content-Type" "text/html; charset=utf-8"},
 :body (views/page ...)}), иначе — прежний JSON (muuntaja отдаст его по Accept
`application/json`, а для браузера мы отдаём HTML напрямую).
- **Почему**: один роут сохраняет и API, и UI; не дублируем записи в контракте и
  не вводим отдельный префикс `/api` (минимальный дифф; тесты JSON не ломаются,
  т.к. curl шлёт `Accept: */*` без `text/html`).
- **Альтернатива**: отдельный роут `/entries.html` — отвергнуто (новичку проще
  один URL; htmx слабо зависит от пути).

### POST /entries — проверка HX-Request в handler'е и в middleware coercion
- Успех: `create-entry` handler смотрит `HX-Request` (истинно) → возвращает
  HTML-фрагмент нового `<li>` (для вставки в список), иначе прежний JSON 201.
- Ошибка валидации: reitit/malli бросает исключение coercion ДО handler'а, поэтому
  ветвление нужно и в `coercion-error-middleware`: если запрос htmx, отдаём 400 с
  HTML-фрагментом ошибки, иначе прежний JSON 400.
- **Почему**: валидация уже живёт на уровне coercion — не дублируем её в handler'е;
  htmx-клиент и браузер получают единый HTML-поток, а API-клиенты — прежний JSON.
- **Альтернатива**: валидировать заново в handler'е и ветвить там — отвергнуто
  (двойная валидация, рассинхрон схем).

### htmx-разметка формы: основной таргет — область ошибки, вставка в список через OOB
Форма: `hx-post="/entries" hx-target="#form-error" hx-swap="innerHTML"`.
- Успех: ответ — единственный корневой элемент `<li id="entry-{id}"
  hx-swap-oob="beforeend:#entries-list">…</li>`. htmx извлекает OOB-элемент,
  добавляет его в конец списка `#entries-list`; основной таргет `#form-error`
  остаётся пустым (чистая область ошибки).
- Ошибка: ответ — `<div id="form-error" class="alert alert-error">…сообщение…</div>`
  (основной свап в `#form-error`), список не трогается.
- Сброс формы после успеха: hyperscript `_="on htmx:afterRequest if
  event.detail.successful reset() me"` (hyperscript в стеке проекта).
- **Почему**: единый простой паттерн «ошибка — в область под формой, запись — в
  список» без JS-ветвлений в нескольких таргетах формы.
- **Альтернатива**: таргет на `#entries-list` со `swap=beforeend` и перенос ошибки
  OOB — также работоспособно, но при ошибке пришлось бы маркировать ошибку как OOB,
  а основной свап в список «кушать» пустоту; выбранный паттерн симметричнее читается.

### Шаблоны — hiccup2, новый namespace `app.domains.entries.views`
`views/page` (каркас `<html lang="ru">` + `<meta name="viewport">` + подключение
htmx/Tailwind/DaisyUI + `#form-error` + форма + `#entries-list`), `views/form`,
`views/list` (с пустым состоянием), `views/item`, `views/error-fragment`.
Возвращают данные hiccup (не строки); конвертация строкой — `hc/html`
в handler'е/роуте.
- **Почему**: конвенция проекта — «простые hiccup2-функции без сложных макросов»;
  отказ от шаблонных макросов упрощает тестирование (сравниваем структуру hiccup).
- **Альтернатива**: генри-макросы (hicada/hiccup macro) — отвергнуто (избыточно).

### Статика: CDN (htmx + Tailwind Play CDN + DaisyUI)
- htmx — `<script src="https://unpkg.com/htmx.org@2.0.4">`
  (стабильная 2.x, актуальная на момент написания; проверить последнюю при доступе)
- Tailwind — Play CDN `<script src="https://cdn.tailwindcss.com"></script>`
  (без билд-шага, достаточно для утилит-классов на одной странице)
- DaisyUI — `<link href="https://cdn.jsdelivr.net/npm/daisyui@5/dist/full.min.css">`
  + data-theme
- Компоненты формы: DaisyUI (`input`, `select`, `btn btn-primary`, `card`,
  `alert alert-error`).
- **Почему**: нулевой билд-шаг, совпадает с масштабом change (одна страница).
- **Альтернатива**: вендорить файлы в `resources/public/` — оставлено как
  fallback/риск (см. Risks): требует загрузку в момент реализации и серверную
  раздачу статики через `ring.middleware.resource`.

### Обработка форматов ответа (muuntaja)
Всё происходит внутри `->app`; body-строку HTML не нужно кодировать через
muuntaja — для HTML-ответов выставляется явный `Content-Type: text/html`,
а строки проходят через muuntaja без перекодирования (стандартный `wrap-format`).

## Risks / Trade-offs

- [CDN недоступен (нет сети/блокировки) → страница без стилей и без htmx] →
  ошибка будет явной при ручной проверке; fallback — вендорить файлы в
  `resources/public/` и раздавать через `ring.middleware.resource`.
- [Content negotiation по Accept может конфликтовать с прокси/curl (`*/*`)] →
  `*/*` не содержит `text/html` → остаётся JSON; поведение как в `add-entries-api`.
- [hw четверка: OOB-элемент в ответе POST при не-htmx-клиенте] → ветвление строго
  по `HX-Request`; чистый API-клиент получает JSON, OOB-фрагменты ему не отдаём.
- [HTML-фрагмент ошибки и JSON 400 в одном middleware] → проверка `HX-Request`
  до выбора формата; тесты покрывают оба пути.
- [HTML-выход приходит как строка — muuntaja может обернуть в JSON] → явный
  `Content-Type: text/html`; строки muuntaja не перекодирует.

## Migration Plan

Deploy: доработка существующих роутов/handler'ов + статика через CDN —
hot-deploy перезапуском `clj -M -m app.core`. Схема БД не меняется.
Rollback: вернуть `coercion-error-middleware` и handler'ы к JSON-only формату
(функциональность API из `add-entries-api` сохраняется в любой момент).
Дополнительных миграций БД нет.

## Open Questions

- Формат сообщения об ошибке валидации в HTML-фрагменте: выводить
  `malli.error/humanize` как отформатированный список под формой (решение по
  умолчанию) — утверждается в этом change, т.к. влияет на восприятие UX.
- Версия htmx/DaisyUI: пиним известные актуальные; при наличии сети сверить с CDN.

## Notes from Implementation

- **Форма шлёт JSON через расширение `json-enc`** (`hx-ext="json-enc"`):
  reitit/malli по умолчанию для `:body` использует json-transformer (без строковой
  декодировки), а htmx json-enc отдаёт все поля строками (в т.ч. пустые). Чтобы
  `POST /entries` принимал и JSON-API, и форму: для `application/json` подключён
  кастомный body-transformer (`string-transformer` + blank->nil для `:double`).
  Схема `[:maybe :double]` требует присутствия ключа `sleep_hours`, но допускает
  nil — пустое поле формы становится `""` -> nil.
- **Tailwind: v4 browser build** (`@tailwindcss/browser@4`) вместо Play CDN (v3):
  Play CDN несовместим с DaisyUI 5; текущая связка — DaisyUI 5 `daisyui.css` +
  `@tailwindcss/browser@4`.
- **`error-fragment` без собственного `id`**: фрагмент `.alert.alert-error`
  подставляется через `hx-swap="innerHTML"` в контейнер `#form-error`;
  повторение того же id внутри создало бы дубликат id в DOM.
- **Валидация в htmx-пути**: ошибки coercion обрабатываются в
  `coercion-error-middleware` (`app.routes`) — для `HX-Request` отдаётся
  HTML-фрагмент, для остальных прежний JSON.


## 1. Views (hiccup2-шаблоны)

- [x] 1.1 Создать `app.domains.entries.views` с `page` (полноценный HTML-каркас: `<html lang="ru">`, `<meta name="viewport">`, `<title>`, подключение htmx/Tailwind/DaisyUI, `#form-error`, форма, `#entries-list`)
- [x] 1.2 Реализовать `form` (поля `activity`, `effect`, `mood_score`, `sleep_hours`, кнопка submit; атрибуты `hx-post="/entries"`, `hx-target="#form-error"`, `hx-swap="innerHTML"`, hyperscript-сброс после успеха)
- [x] 1.3 Реализовать `list` (со списком `item`) и пустое состояние при отсутствии записей
- [x] 1.4 Реализовать `item` (`<li id="entry-{id}" hx-swap-oob="beforeend:#entries-list">`; вывод activity/effect/mood_score/sleep_hours/date)
- [x] 1.5 Реализовать `error-fragment` — `<div id="form-error" class="alert alert-error">` с сообщением(ями) валидации
- [x] 1.6 Обернуть данные hiccup в строку через `hc/html` там, где это делают роуты (не внутри views)

## 2. Handlers и роуты

- [x] 2.1 В `create-entry` handler добавить ветвление по `HX-Request`: htmx → 201 с HTML-фрагментом нового `item` (Content-Type text/html); иначе прежний JSON
- [x] 2.2 В `get-entries` handler добавить content negotiation по `Accept` (`text/html` → 200 с HTML-страницей `page`, Content-Type text/html; иначе прежний JSON)
- [x] 2.3 В `coercion-error-middleware` (`app.routes`) добавить ветвление: htmx-запрос → 400 с HTML-фрагментом ошибки (Content-Type text/html), иначе прежний JSON 400

## 3. Статика (CDN)

- [x] 3.1 Подключить в `page`: htmx (unpkg), Tailwind (v4 browser build), DaisyUI CSS (+ `data-theme`); при доступности сети сверить последние версии
- [x] 3.2 Проверить применение DaisyUI-классов к форме (input/select/btn/card/alert) и адаптив на viewport 375px

## 4. Тесты и проверка

- [x] 4.1 Интеграционные тесты: GET /entries с `Accept: text/html` → 200 + HTML-страница (содержит форму и список/пустое состояние); GET /entries без HTML-Accept → прежний JSON
- [x] 4.2 Интеграционные тесты: POST /entries с `HX-Request: true` и валидным телом → 201 + HTML-фрагмент `<li>`; без заголовка → прежний JSON 201
- [x] 4.3 Интеграционные тесты: POST /entries с `HX-Request: true` и mood_score=15 → 400 + HTML-фрагмент ошибки; без заголовка → прежний JSON 400 (и no-op для списка)
- [x] 4.4 Прогнать `clojure.test` (alias `:test`) — все тесты зелёные
- [x] 4.5 Запустить приложение `clj -M -m app.core`; curl-проверка: JSON-поведение не сломано (POST/GET без HX-Request), `curl -H "Accept: text/html"` отдаёт страницу, htmx-фрагмент вставляет запись в список

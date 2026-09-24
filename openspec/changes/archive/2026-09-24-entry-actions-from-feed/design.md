# Design: entry-actions-from-feed

## Context

`app.views.feed/entry-card` рендерит `ellipsis-horizontal` как нефункциональную заглушку.
Рабочий паттерн confirm-модалки уже есть — `app.views.entries/delete-modal`: daisyUI `<dialog>` +
кнопка `hx-delete` с CSRF-заголовком в `hx-headers`. `app.routes.entries/delete-entry` для
htmx-запросов возвращает `HX-Redirect /entries` (полная навигация). `app.db.entries/delete-entry!`
делает `DELETE ... RETURNING *` → обработчик получает удалённую строку с `:id` и `:date`.
Карточки ленты живут в `timeline-day` (прошедшие дни) и в `today-section` (остальные записи
сегодня); сейчас ни у карточки, ни у дневной секции нет id, поэтому in-place удаление и удаление
секции требуют стабильных якорей.

## Decisions

### D1. Меню — daisyUI dropdown на трёхточечной кнопке

`entry-card` оборачивает иконку в `div.dropdown` (с `tabindex`, `role="button"`,
`aria-label`) и `ul.dropdown-content.menu` с двумя пунктами: «Править» — `<a href="/entries/:id">`,
«Удалить» — `<button>` с hyperscript `on click call #del-feed-entry-<id>.showModal()`. Только
htmx/hyperscript, без сырого JS — как в `read-view` и `delete-modal`.
- Альтернатива (`<details>`/`<summary>`): тоже нативно и без JS, но daisyUI dropdown — штатный
  компонент проекта, единообразен по стилям и клавиатуре.
- Tradeoff: за корректную клавиатуру/`tabindex` отвечаем классами daisyUI, а не своей логикой.

### D2. Confirm-модалка — отдельная маленькая функция по паттерну `delete-modal`

`entry-card` добавляет вложенный `<dialog id="del-feed-entry-<id>" class="modal ...">` с заголовком
`i18n :entries/delete-confirm`, текстом `:entries/delete-confirm-text`, «Отмена» (`form method=dialog`)
и кнопкой подтверждения: `hx-delete "/entries/<id>?from=feed"`, `hx-headers` с CSRF,
`hx-target "#feed-entry-<id>"`, `hx-swap "delete"`. Нативный `<dialog>` рендерится в top-layer,
поэтому вложенность в `.card` не клипается (в отличие от обычного div-модала).
- Альтернатива (переиспользовать `views.entries/delete-modal`): он параметризован под редирект
  (`HX-Redirect`) и не знает про target/swap ленты; параметризовать его — преждевременная
  абстракция на двух потребителях.
- Tradeoff: небольшое дублирование разметки модалки; при третьем потребителе — вынести общий
  компонент.

### D3. Различение источника удаления — query-параметр `from=feed`

`delete-entry` читает `(get-in request [:query-params "from"])` и ветвит:
- `"feed"` + htmx → `200`, тело — OOB-фрагмент удаления дня (D4) или пустая строка; карточка
  убирается за счёт `hx-swap="delete"` на кнопке.
- иначе + htmx → прежний `{"HX-Redirect" "/entries"}` (поведение `/entries/:id` сохранено).
- non-htmx → `204`.
Владение без изменений: `WHERE id AND user_id`; чужая/несуществующая → `404`.
- Альтернатива (отдельный роут `DELETE /feed/entries/:id`): новый роут ради одного флага —
  query-параметр дешевле и явно читается в хендлере.
- Tradeoff: `from` — слабо типизированный сигнал, но это внутренний флаг рендера, не контракт API.

### D4. Удаление пустого дня — OOB-фрагмент с `hx-swap-oob="delete"`

Дневные секции получают стабильные id: `timeline-day` → `#feed-day-<date>`, контейнер дня в
`today-section` → `#feed-day-<today>`. После удаления обработчик берёт `:date` удалённой строки и
считает остаток записей за этот день (`entries/list-entries`); если остаток пуст — добавляет в тело
ответа `<div id="feed-day-<date>" hx-swap-oob="delete"></div>`, и htmx удаляет секцию целиком
(вместе с заголовком дня и точкой timeline).
- Сегодняшний день через это меню пустым стать не может: последняя запись дня рендерится
  hero-карточкой без меню действий, а удаляемые карточки сегодня — не последние. id ставим всё
  равно, для единообразия и устойчивости.
- Альтернатива (`hx-swap-oob="outerHTML:..."` с пустым телом): менее явно, чем swap-style `delete`.
- Tradeoff: `hx-swap-oob="delete"` требует htmx ≥ 1.9 — проект на htmx 2.x, ок.

### D5. «Править» — навигация на страницу, без inline-редактирования

Пункт «Править» — обычная ссылка на `/entries/:id`; edit-форма в ленте не появляется. Меньше
точек входа, полностью переиспользуется существующий in-place edit страницы записи.

## Risks

- [OOB-удаление дня при неверном подсчёте остатка] → считаем по свежему `list-entries` уже после
  удаления; при любом сомнении секцию не удаляем (безопасный дефолт — оставить).
- [daisyUI dropdown и `<dialog>` на мобильных/тач] → штатные компоненты; проверяем Playwright на
  412px и отсутствие горизонтального скролла.
- [Дублирование разметки модалки] → сознательное дублирование маленькой функции; вынос — при
  третьем потребителе.
- [Регресс удаления со страницы записи] → ветка без `from=feed` не трогается, покрыта e2e-регрессом.

## Verification

- Playwright `/feed`: три точки → меню с «Править»/«Удалить»; «Править» → URL `/entries/:id`;
  «Удалить» + подтверждение → карточка исчезает без перезагрузки (URL остаётся `/feed`), пустой
  день исчезает.
- Playwright `/entries/:id`: «Удалить» + подтверждение → редирект на `/entries` (регресс не сломан).
- `cd e2e && npm run test:fast` зелёный; `./bin/dev` поднимается, `curl http://localhost:3000/` → OK.
- REPL: `(require 'app.views.feed :reload)` → рендер `entry-card` содержит dropdown и dialog;
  прямой вызов `delete-entry` с fake request `from=feed`/`hx-request` → 200 и OOB при пустом дне.

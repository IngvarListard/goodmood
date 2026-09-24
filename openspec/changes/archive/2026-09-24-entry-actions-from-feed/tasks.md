# Tasks: entry-actions-from-feed

## 1. Views: меню и модалка

- [x] 1.1 `src/app/views/feed.clj` — `entry-card`: добавить id карточке `#feed-entry-<id>`, заменить заглушку `ellipsis-horizontal` на daisyUI dropdown (`div.dropdown` + `ul.dropdown-content.menu`) с «Править» (`<a href="/entries/:id">`) и «Удалить» (hyperscript `on click call #del-feed-entry-<id>.showModal()`); приёмка: рендер карточки содержит dropdown с двумя пунктами, сырой JS не добавлен
- [x] 1.2 `src/app/views/feed.clj` — приватная функция confirm-модалки ленты (dialog id `del-feed-entry-<id>`, `hx-delete "/entries/<id>?from=feed"`, CSRF в `hx-headers`, `hx-target "#feed-entry-<id>"`, `hx-swap "delete"`, «Отмена» через `form method=dialog`), встроить в `entry-card`; приёмка: dialog рендерится рядом/внутри карточки, target/swap соответствуют design D2
- [x] 1.3 `src/app/views/feed.clj` — id дневных секций: `timeline-day` (и контейнер `past-day-section`) → `#feed-day-<date>`, контейнер дня в `today-section` → `#feed-day-<today>`; приёмка: в отрендеренной ленте каждый день имеет уникальный id
- [x] 1.4 REPL-проверка: `(require 'app.views.feed :reload)` → `(hiccup2.core/html (app.views.feed/entry-card ...))` рендерит dropdown и dialog без исключений

## 2. Route: различение источника удаления

- [x] 2.1 `src/app/routes/entries.clj` — `delete-entry`: читать `(get-in request [:query-params "from"])`; для `from=feed` + htmx → 200 с телом-фрагментом (пусто или OOB дня, см. 2.2); для прочих htmx → прежний `{"HX-Redirect" "/entries"}`; non-htmx → 204; ветка 404/владение не меняется; приёмка: обе htmx-ветки возвращают ожидаемые заголовки/тело
- [x] 2.2 `src/app/routes/entries.clj` — при `from=feed` после удаления считать остаток записей за `:date` удалённой строки (`entries/list-entries`); если 0 — вернуть OOB `[:div {:id (str "feed-day-" date) :hx-swap-oob "delete"}]`; приёмка: удаление последней записи дня даёт OOB-узел, при остатке > 0 — пустое тело
- [x] 2.3 REPL-проверка: `(require 'app.routes.entries :reload)`; вызов `delete-entry` с fake request (`from=feed`, `hx-request=true`) → 200 и OOB; с `from` отсутствующим → `HX-Redirect /entries`; чужой id → 404

## 3. i18n

- [x] 3.1 `resources/i18n/ru.edn` и `resources/i18n/en.edn` — добавить только недостающие ключи (например aria-label меню действий карточки); переиспользовать существующие `:entries/edit`, `:entries/delete`, `:entries/delete-confirm`, `:entries/delete-confirm-text`, `:entries/delete-action`, `:entries/cancel`; приёмка: ключи есть в обоих файлах, `i18n/t` возвращает строки для обеих локалей

## 4. E2E

- [x] 4.1 `e2e/tests/feed-entry-actions.spec.ts` (новый) — /feed: три точки → меню с «Править»/«Удалить»; «Править» → URL `/entries/:id`; «Удалить» + подтверждение → карточка исчезает без перезагрузки (URL остаётся `/feed`), пустой день исчезает; приёмка: `cd e2e && npx playwright test tests/feed-entry-actions.spec.ts` зелёный
- [x] 4.2 `e2e/tests/entries-crud.spec.ts` — прогнать регресс удаления со страницы `/entries/:id` (по-прежнему редирект на `/entries`); приёмка: спек зелёный без правок сценария
- [x] 4.3 `cd e2e && npm run test:fast` — полный быстрый прогон зелёный

## 5. Верификация

- [x] 5.1 `./bin/dev` поднимается без ошибок, `curl http://localhost:3000/` → `OK`
- [x] 5.2 Playwright MCP на 412px: dropdown и dialog не ломают вёрстку ленты, горизонтального скролла нет

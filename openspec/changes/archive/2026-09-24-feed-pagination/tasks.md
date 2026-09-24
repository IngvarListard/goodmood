# Tasks: feed-pagination

> Порядок: сначала данные и домен (1–2), затем лента (3), затем /entries (4),
> затем проверки (5). Правки view/route подхватываются `:reload` без рестарта;
> `routes/app.clj` требует рестарта системы.

## 1. Слой данных (`app.db.entries`)

- [x] 1.1 `src/app/db/entries.clj`: `get-entries-since ds user-id since-date` — `{:select [:*] :from [:entries] :where [:and [:= :user_id user-id] [:>= :date since-date]] :order-by [[:date :desc] [:created_at :desc]]}`, builder `default-opts`. Приёмка: возвращает только записи с `date >= since-date` в порядке date desc, created_at desc. Проверка: REPL `(app.db.entries/get-entries-since ds uid "2026-09-18")`.
- [x] 1.2 `src/app/db/entries.clj`: `get-entries-before ds user-id before-date` — `:where [:and [:= :user_id user-id] [:< :date before-date]]`, тот же порядок, `:limit older-row-limit` (константа 500). Приёмка: только `date < before-date`, не больше 500 строк. Проверка: REPL `(app.db.entries/get-entries-before ds uid "2026-09-18")`.
- [x] 1.3 `src/app/db/entries.clj`: `count-entries ds user-id` — `{:select [[:%count.* :n]] :from [:entries] :where [:= :user_id user-id]}` → число. Приёмка: равно числу строк пользователя. Проверка: REPL.
- [x] 1.4 `src/app/db/entries.clj`: `get-latest-entry ds user-id` — последняя запись (`order-by [[:date :desc] [:created_at :desc]] :limit 1`) или nil. Приёмка: `(first (get-entries …))` эквивалент. Проверка: REPL.
- [x] 1.5 `test/app/db/entries_test.clj`: кейсы `get-entries-since` (граница включительно), `get-entries-before` (строго раньше, порядок), `count-entries`, `get-latest-entry`. Приёмка: `clojure -M:test` зелёный (или проектная команда тестов).

## 2. Домен (`app.domains.entries`)

- [x] 2.1 `src/app/domains/entries.clj`: константы `initial-window-days 7`, `older-chunk-days 5`; `day-chunks` — чистая функция `rows → [{:date … :entries […]} …]` с сохранением порядка `date desc`. Приёмка: группирует, не сортирует заново, порядок дней и записей сохранён. Проверка: REPL/тест.
- [x] 2.2 `src/app/domains/entries.clj`: `list-entries-initial ds user-id` — окно `initial-window-days` через `get-entries-since` (since = сегодня − 6). Приёмка: только последние 7 календарных дней. Проверка: REPL.
- [x] 2.3 `src/app/domains/entries.clj`: `list-entries-before ds user-id before-date` — `get-entries-before` → `day-chunks` → первые `older-chunk-days` дней; возвращает `{:chunks […] :next-before <самая старая дата чанка или nil>}`. Приёмка: ≤5 различных дней, `next-before` = самая старая дата, nil при пустом результате. Проверка: REPL.
- [x] 2.4 `src/app/domains/entries.clj`: `latest-entry ds user-id` (через `db/get-latest-entry`); `count-entries ds user-id`. Приёмка: эквивалент прежнего `(first (list-entries …))`. Проверка: REPL.
- [x] 2.5 Переключить потребителей «последней записи» на `latest-entry`: `src/app/routes/ai.clj`, `src/app/routes/notifications.clj`, `src/app/routes/check_in.clj`. Приёмка: пользователь без записей за 7 дней всё ещё получает свою последнюю запись. Проверка: `clojure -M:test` + REPL.
- [x] 2.6 `test/app/routes/app_test.clj` (или профильный тест): `day-chunks`, `list-entries-before` (исчерпание/`next-before`), `latest-entry`. Приёмка: тесты зелёные.

## 3. Лента `/feed`

- [x] 3.1 `src/app/views/feed.clj`: `past-day-section` принимает `next-before` и рендерит внизу самозаменяющий sentinel `<div id="feed-older" hx-get (str "/feed/older?before=" next-before) hx-trigger "revealed" hx-swap "outerHTML">`; при `nil` — без sentinel. Приёмка: sentinel есть при непустом окне, атрибуты точные. Проверка: `(hiccup2.core/html …)` в REPL.
- [x] 3.2 `src/app/views/feed.clj`: `page` использует окно 7 дней (`list-entries-initial`) вместо полной выборки; вычисляет `next-before` (самая старая дата окна, иначе сегодня) и передаёт в `past-day-section`. Приёмка: первый paint — только 7 дней + sentinel. Проверка: `curl` + REPL.
- [x] 3.3 `src/app/routes/feed.clj`: хендлер `older ds request` — парсит `before` из `:query-params`, зовёт `entries/list-entries-before`, рендерит `timeline-day` по каждому дню + свежий sentinel, либо пусто при исчерпании. Переиспользует `timeline-day`/`entry-card`. Приёмка: ≤5 дней в ответе; при исчерпании нет `#feed-older`. Проверка: `curl "localhost:3000/feed/older?before=…"`.
- [x] 3.4 `src/app/routes/feed.clj`: `page` больше не зовёт `list-entries` (радар и маршрут `/feed/radar` к этому моменту уже удалены change `replace-radar-with-time-chart`); hero-график `GET /feed/chart` уже использует ограниченный оконный запрос из того change — убедиться, что страница не грузит всю историю. Приёмка: `/feed/chart?period=month` корректен при >7 днях истории, первый paint ограничен окном. Проверка: `curl`/REPL.
- [x] 3.5 `src/app/routes/app.clj`: зарегистрировать `["/feed/older" {:get {:handler (partial feed/older ds)}}]` рядом с `/feed`. Приёмка: роут доступен, требует auth. Проверка: рестарт + `curl`.

## 4. Список `/entries`

- [x] 4.1 `src/app/views/entries.clj`: `list-page` принимает окно 7 дней + `total` (из `count-entries`) + `next-before`; рендерит `day-section` по загруженным дням и внизу sentinel `#entries-older` (`hx-get "/entries/older?before=…"`, `hx-trigger "revealed"`, `hx-swap "outerHTML"`). Приёмка: первый paint — 7 дней, счётчик = вся история. Проверка: `curl` + REPL.
- [x] 4.2 `src/app/routes/entries.clj`: хендлер `older ds request` — `before` из query, `list-entries-before`, рендер `day-section` + свежий sentinel либо пусто. Приёмка: ≤5 дней, исчерпание → без sentinel. Проверка: `curl "localhost:3000/entries/older?before=…"`.
- [x] 4.3 `src/app/routes/entries.clj`: `list-page` использует `list-entries-initial` + `count-entries`. Приёмка: не грузит всю историю. Проверка: REPL/`curl`.
- [x] 4.4 `src/app/routes/app.clj`: зарегистрировать `["/entries/older" {:get {:handler (partial entries/older ds)}}]` **до** `["/entries/:id"]` (приоритет статики). Приёмка: `GET /entries/older` не уходит в `show-page`. Проверка: рестарт + `curl` (ожидаем фрагмент, не 404 карточки).

## 5. Проверки

- [x] 5.1 Обновить `e2e/tests/feed.spec.ts`: сценарий «прокрутка подгружает следующий 5-дневный чанк, на конце `#feed-older` исчезает». Приёмка: `cd e2e && npx playwright test tests/feed.spec.ts` зелёный.
- [x] 5.2 Обновить `e2e/tests/entries-crud.spec.ts`: сценарий прокрутки `/entries` (`#entries-older`, подгрузка, исчерпание). Приёмка: `cd e2e && npx playwright test tests/entries-crud.spec.ts` зелёный.
- [x] 5.3 Ручная проверка Playwright MCP: проскроллить `/feed` и `/entries` до конца, убедиться в htmx-свапах и остановке; проверить обе локали (`gm-locale=ru/en`). Приёмка: чанки подгружаются, в конце новых запросов нет.
- [x] 5.4 Полный прогон: `cd e2e && npm run test:fast` и `clojure -M:test`. Приёмка: всё зелёное. Приложение поднимается с нуля: `rm resources/goodmood.db* && ./bin/dev` (миграций нет, но проверяем старт).

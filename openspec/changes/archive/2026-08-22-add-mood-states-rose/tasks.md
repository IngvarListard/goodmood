## 1. Миграция 006 — state_label + state_period_id

- [x] 1.1 Создать `resources/migrations/006-add-state-label-and-period.up.sql`: `ALTER TABLE entries ADD COLUMN state_label TEXT;` + `ALTER TABLE entries ADD COLUMN state_period_id INTEGER;`
- [x] 1.2 Создать `resources/migrations/006-add-state-label-and-period.down.sql`: `ALTER TABLE entries DROP COLUMN state_period_id;` + `ALTER TABLE entries DROP COLUMN state_label;`
- [x] 1.3 Проверить: применить миграцию, убедиться что колонки nullable, существующие данные сохранены.

## 2. DB-слой — app.db.entries

- [x] 2.1 Обновить `create-entry!` в `src/app/db/entries.clj`: добавить `state_label` и `state_period_id` в `:values` map (nullable).
- [x] 2.2 Обновить `get-entries`: вернуть `state_label`, `state_period_id`, `created_at`; изменить `:order-by` на `[[:date :desc] [:created_at :desc]]` (группировка по дате, внутри дня — по времени).
- [x] 2.3 Тест: `create-entry!` с `state_label` → колонка заполнена; без `state_label` → NULL.
- [x] 2.4 Тест: `get-entries` возвращает записи отсортированными по `created_at` внутри дня.

## 3. Domain-слой — app.domains.entries

- [x] 3.1 Добавить функцию `state-label` в `src/app/domains/entries.clj`: принимает `{:keys [energy anxiety]}`, возвращает keyword (`:state/mixed` и т.д.) по правилам first-match-wins (Decision 12.3). Docstring.
- [x] 3.2 Обновить `create-entry-schema`: добавить `:state_label {:optional true} [:maybe :string]` и `:state_period_id {:optional true} [:maybe :int]`.
- [x] 3.3 Обновить `create-entry`: пробросить `state_label` и `state_period_id` в `db/create-entry!`.
- [x] 3.4 Тест: `state-label` для всех 6 правил (mixed/anxiety/elevated/low/balanced/neutral) — параметризованный тест.
- [x] 3.5 Тест: `state-label` игнорирует `focus`.

## 4. SVG-радар — app.views.rose

- [x] 4.1 Создать `src/app/views/rose.clj`: функция `radar` принимает `{:keys [energy anxiety focus mood_score]}`, возвращает hiccup2-вектор inline SVG 200×200. Hand-rolled, полярные координаты, `currentColor` + `hsl(var(--p))`, grid-кольца, оси, полигон, вершинные круги, метки, значения, center dot. ~55 строк. Опираться на `design/radar.clj`.
- [x] 4.2 Тест: `(radar {:energy 4 :anxiety 7 :focus 3})` рендерит hiccup2 → HTML строка содержит `<polygon>` с корректными координатами.
- [x] 4.3 Тест: 4-осевой радар с `mood_score` рендерит 4-вершинный полигон.
- [x] 4.4 Тест: SVG не содержит JS-интерактивных элементов (только polygon/line/circle/text).

## 5. IA-реорганизация — роуты

- [x] 5.1 В `src/app/routes/app.clj`: удалить роуты `/dashboard`, `/history`, `/statistics`, `/insights`.
- [x] 5.2 Добавить роут `/feed` → `feed-routes/page` (новый handler).
- [x] 5.3 Заменить `/check-in` placeholder на real handler → `check-in-routes/page`.
- [x] 5.4 `GET /entries` → 308 redirect на `/feed` (`ring.util.response/redirect`).
- [x] 5.5 `POST /entries` — без изменений (htmx API endpoint).
- [x] 5.6 Добавить redirect `/` → `/feed` для authenticated запросов (в `wrap-identity` или роуте `/`).
- [x] 5.7 Удалить `placeholder.clj` или пометить как unused (если не используется другими роутами).

## 6. Страница /feed — app.views.feed + app.routes.feed

- [x] 6.1 Создать `src/app/views/feed.clj`: функция `page` рендерит ленту — заголовок «Лента», группировка по дням, hero-карточка последней записи сегодня с SVG-радаром, остальные — компактные карточки с бейджем ярлыка + timestamp + значения осей. FAB. Опираться на `design/feed-page.clj`.
- [x] 6.2 Реализовать группировку: `created_at` парсится → дата для заголовка («Сегодня», «Вчера», «21 августа»), время для карточки («14:15»).
- [x] 6.3 Реализовать auto-derive ярлыка: если `state_label` = NULL → `(domains/state-label entry)` → `i18n/t`. Если non-NULL → `i18n/t` от keyword'а.
- [x] 6.4 Создать `src/app/routes/feed.clj`: handler `page` вызывает `db/get-entries` → `views/feed-page`.
- [x] 6.5 Empty state: нет записей сегодня → онбординг «Как ты? Создай первую запись» + кнопка `/check-in`.
- [x] 6.6 Тест: `/feed` рендерит hero-карточку с радаром для последней записи сегодня.
- [x] 6.7 Тест: `/feed` рендерит компактные карточки для остальных записей (без радара).
- [x] 6.8 Тест: `/feed` группирует прошлые дни под заголовками дат.

## 7. Страница /check-in — app.views.check_in + app.routes.check_in

- [x] 7.1 Создать `src/app/views/check_in.clj`: функция `page` рендерит «← назад» на `/feed` + заголовок «Новая запись» + форму (перенести `form` из `views/entries.clj`, обновить hyperscript: `on htmx:afterRequest if successful set window.location to '/feed'`).
- [x] 7.2 Создать `src/app/routes/check_in.clj`: handler `page` рендерит `views/check-in.page`.
- [x] 7.3 Обновить `views/entries.clj`: удалить `form` (перенесена), `page` (перенесена в feed); оставить `item` если используется htmx-ответом POST /entries, иначе рефакторинг.
- [x] 7.4 Тест: `/check-in` рендерит форму с «← назад» на `/feed`.
- [x] 7.5 Тест: успешный POST /entries (htmx) → hyperscript редирект на `/feed`.

## 8. Навигация — app.views.navigation

- [x] 8.1 Обновить `nav-items` в `src/app/views/navigation.clj`: 4 пункта — `:feed` (icon «squares-2x2» или «list-bullet», route `/feed`), `:check-in` (icon «plus-circle», route `/check-in`), `:medications` (icon «beaker», route `/medications`), `:settings` (icon «cog-6-tooth», route `/settings`).
- [x] 8.2 Удалить `:dashboard`, `:history`, `:statistics`, `:insights` из `nav-items`.
- [x] 8.3 Тест: `(navigation :mobile nav-items)` рендерит ровно 4 `<a>`.
- [x] 8.4 Тест: отсутствуют роуты `/dashboard`, `/history`, `/statistics`, `/insights`.
- [x] 8.5 Проверить иконку для /feed: найти подходящую в `resources/icons/heroicons/` (grep «list» или «squares»).

## 9. i18n — новые ключи

- [x] 9.1 Добавить в `resources/i18n/ru.edn` и `en.edn`: `:nav/feed` («Лента» / «Feed»), `:state/mixed` («смешанное» / «mixed»), `:state/anxiety` («тревога» / «anxiety»), `:state/elevated` («подъём» / «elevated»), `:state/low` («спад» / «low»), `:state/balanced` («норма» / «balanced»), `:state/neutral` («нейтрально» / «neutral»), `:pages/check-in` («Новая запись» / «New entry»), `:feed/title` («Лента» / «Feed»), `:feed/empty` («Как ты? Создай первую запись» / «How are you? Create your first entry»), `:feed/today` («Сегодня» / «Today»), `:feed/yesterday` («Вчера» / «Yesterday»), `:feed/state-label` («состояние» / «state»).
- [x] 9.2 Удалить устаревшие ключи: `:nav/dashboard`, `:nav/history`, `:nav/statistics`, `:nav/insights`, `:pages/dashboard`, `:pages/history`, `:pages/statistics`, `:pages/insights` (если не используются).

## 10. Интеграция + e2e

- [x] 10.1 Запустить приложение: `clj -M -m app.core`. Проверить health-check `curl http://localhost:3000/`.
- [x] 10.2 Ручной прогон: залогиниться → `/feed` (empty state) → `/check-in` → заполнить форму → сохранение → редирект на `/feed` с новой записью (hero-карточка с радаром).
- [x] 10.3 Ручной прогон на мобилке (Playwright MCP, viewport 375px): `/feed` читаем, FAB доступен, радар виден на тёмной теме.
- [x] 10.4 E2E: создать несколько записей в день → `/feed` группирует по дням, timestamp отображается.
- [x] 10.5 `openspec validate --change add-mood-states-rose` — структура валидна.
- [x] 10.6 `openspec status --change add-mood-states-rose` — все задачи выполнены.
- [x] 10.7 `openspec archive --change add-mood-states-rose` → delta-спеки синхронизированы в `openspec/specs/`.
- [x] 10.8 В `openspec/changes/product-vision/tasks.md` отметить Фазу 2 ✅.

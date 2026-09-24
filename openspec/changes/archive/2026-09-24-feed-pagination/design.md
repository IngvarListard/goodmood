# Design: feed-pagination

## Context

`/feed` и `/entries` сейчас рендерят всю историю записей: оба route-хендлера
зовут `app.domains.entries/list-entries`, который делает полную выборку
`app.db.entries/get-entries` (без `LIMIT`). Записи уже приходят в порядке
`date DESC, created_at DESC` и группируются по `:date` в представлениях
(`views/feed/past-day-section`, `views/entries/list-page`).

Единица пагинации — **день**, потому что день уже визуальная единица
(timeline-секция / day-section) и заголовок даты. Резать по строкам нельзя:
день распался бы между чанками.

Change зависит от `replace-radar-with-time-chart` (hero-график с собственным
окном) и `entry-actions-from-feed` (карточка `entry-card`, которую
переиспользует подгрузка). Выполняется после них.

## Goals

- Первый рендер `/feed` и `/entries` — только последние 7 календарных дней.
- Подгрузка старых дней чанками по 5 дней по мере прокрутки, без перезагрузки.
- Не грузить всю историю на рендер страницы.
- Не сломать существующие потребители `get-entries` (AI-анализ, «последняя
  запись») и независимый hero-график.

## Decisions

### D1. Единица — день, чанк — 5 дней, стартовое окно — 7 дней

Пагинация режется по **различным датам**, а не по строкам: иначе день
фрагментируется между чанками. Стартовое окно 7 дней = сегодня + 6 назад
(совпадает с окном недельного спарклайна AI-секции). Чанк 5 дней — компромисс:
достаточно, чтобы свап был заметен, и не нагружает DOM.

Trade-off: «день» не фиксирован по числу строк — в день может быть 1 запись
или 20. Поэтому row LIMIT выборки берётся с запасом (D4).

### D2. Новые DB-функции: `get-entries-since` и `get-entries-before`

- `get-entries-since ds user-id since-date` — `WHERE user_id = ? AND date >= ?`,
  `ORDER BY date DESC, created_at DESC`. Стартовое окно 7 дней (bounded).
- `get-entries-before ds user-id before-date` — `WHERE user_id = ? AND date < ?`,
  тот же порядок, `LIMIT` (row-limit, D4). Старые чанки.

Обе — honeysql-карты, snake-case поля, builder `as-unqualified-kebab-maps`
(как в существующих функциях `app.db.entries`).

### D3. `get-entries` остаётся полной выборкой для AI — НЕ трогаем

`app.domains.ai` вызывает `app.db.entries/get-entries` напрямую (8 мест:
корреляции, советы, тренд эпизода, «последняя запись»), и этим анализам нужна
**вся история**. Поэтому «не грузить всё» применяется только к рендеру
страниц: страницы переключаются на bounded-функции D2, а `get-entries`
остаётся unbounded-аксессором полной истории для AI.

Trade-off: остаётся функция без `LIMIT`. Осознанно: AI-анализ без истории
деградирует. Полную выборку на рендер не зовём.

### D4. Row LIMIT для чанка: 500 строк с запасом

`older-row-limit = 500` — покрывает 5 дней с большим запасом (100+ записей в
день). Домен берёт из результата первые 5 различных дат. `ponytail: row-limit
на чанк; если день превысит ~100 строк, граничный день может обрезаться —
перейти на DISTINCT-date запрос`.

### D5. Доменный хелпер `day-chunks` + `list-entries-before`

`day-chunks` — чистая функция: на вход строки (уже `date DESC`), на выход
упорядоченный вектор `[{:date … :entries […]} …]` (группировка с сохранением
порядка). `list-entries-initial` = `get-entries-since` (сегодня − 6). `list-entries-before ds user-id before-date`:
`get-entries-before` → `day-chunks` → первые `older-chunk-days` (5) дней;
возвращает `{:chunks […] :next-before <самая старая дата чанка или nil>}`.

### D6. Feed: sentinel `#feed-older`, роут `GET /feed/older`

`past-day-section` получает внизу самозаменяющий sentinel:
`<div id="feed-older" hx-get="/feed/older?before=<oldest>" hx-trigger="revealed"
hx-swap="outerHTML">`. `views/feed/page` рендерит стартовое окно 7 дней;
sentinel добавляется, когда в окне есть записи, `before` = самая старая дата
окна (или сегодня, если окно содержит только сегодня). Роут `GET /feed/older`
(`routes/feed.clj`) парсит `before`, зовёт `list-entries-before`, рендерит
`timeline-day` по каждому дню + свежий sentinel с `:next-before`; при
`nil` чанке — пустой ответ без sentinel. Переиспользуются `timeline-day` /
`entry-card`, новые компоненты не пишем.

Trade-off: sentinel рендерится всегда при непустом окне — при <7 днях данных
один «холостой» reveal вернёт пусто и остановится. Дешевле, чем отдельный
EXISTS-запрос на каждый рендер.

### D7. `/entries`: sentinel `#entries-older`, роут `GET /entries/older`

Зеркально D6: `views/entries/list-page` рендерит стартовое окно 7 дней +
sentinel `#entries-older`; `routes/entries.clj/older` возвращает `day-section`
для следующих 5 дней + свежий sentinel, либо пусто. Переиспользуется
`day-section`.

### D8. Счётчик в шапке `/entries` — по всей истории

Шапка показывает `entries/count-plural` по числу записей. При окне 7 дней
`(count entries)` стал бы враньём, поэтому добавляем `count-entries`
(`SELECT COUNT(*)`) и показываем реальное общее число. Один дешёвый
COUNT-запрос на рендер.

### D9. Регрессия «последняя запись» у прочих потребителей

`routes/ai.clj`, `routes/notifications.clj`, `routes/check_in.clj` брали
`(first (entries/list-entries …))`. Если `list-entries` станет окном 7 дней,
пользователь без записей за неделю получит `nil` (регрессия). Добавляем
`db/get-latest-entry` + `domains/entries/latest-entry` и переключаем этих
трёх потребителей на него. Страницы-списки `list-entries` больше не зовут.

### D10. Hero-график независим от пагинации

По решению change `replace-radar-with-time-chart` hero-график берёт своё окно
(3д/неделя/месяц) из БД через собственный endpoint. Стартовые 7 дней ленты
нужны только timeline и недельному спарклайну AI-секции
(`week-chart-section`). К моменту этой правки радар и маршрут `/feed/radar`
уже удалены (change `replace-radar-with-time-chart`), а hero-график
`GET /feed/chart` уже запрашивает окно из БД — пагинация его не ограничивает.

## Risks

- **AI ломается при bound `get-entries`** — снято D3 (оставляем unbounded для
  AI). Это единственное расхождение с формулировкой «больше не грузить всё»;
  сфера сужена до рендера страниц.
- **Обрезка граничного дня row-limit'ом** (D4) — задокументировано, запас
  велик; при патологии перейти на DISTINCT-date запрос.
- **Регрессия latest-entry** у ai/notifications/check_in — закрыта D9.
- **Регистрация роутов**: `/entries/older` должен быть объявлен до
  `/entries/:id`, иначе возможен перехват динамическим сегментом (проверить
  приоритет статики reitit тестом).
- **Пустое стартовое окно при наличии старых записей** — если за 7 дней
  записей нет, но есть старее, показывается empty state без sentinel (старые
  недостижимы). Приемлемо для трекера с частым логированием; отмечено как
  известное ограничение.

## Verification

- **Playwright (ручная, MCP/Test)**: открыть `/feed`, проскроллить вниз —
  подгружается следующий 5-дневный чанк через htmx, sentinel `#feed-older`
  заменяется; на конце (записей больше нет) sentinel исчезает и новых
  запросов нет. То же для `/entries` и `#entries-older`.
- **Первый paint**: в HTML первого ответа `/feed` и `/entries` — только дни
  последних 7 календарных дней; в БД-запросе рендера нет полной выборки.
- **e2e**: обновить `e2e/tests/feed.spec.ts` и `e2e/tests/entries-crud.spec.ts`
  сценариями прокрутки; прогон `cd e2e && npm run test:fast`.
- **REPL**: `(app.domains.entries/list-entries-before ds uid "2026-09-01")` →
  ≤5 дней и корректный `:next-before`; `count-entries` совпадает с числом строк.
- **Тесты**: `clojure -M:test` (или проектная команда) — кейсы `get-entries-since`,
  `get-entries-before`, `day-chunks`, `list-entries-before`, роуты `/feed/older`,
  `/entries/older` (в т.ч. исчерпание и чужой пользователь).

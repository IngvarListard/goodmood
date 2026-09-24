# Proposal: feed-pagination

## Why

Сейчас `/feed` и `/entries` грузят и рендерят **все** записи пользователя
(`app.domains.entries/list-entries` → `app.db.entries/get-entries` без `LIMIT`).
С ростом истории первый рендер отдаёт всё больше HTML и данных, страница
замедляется. Нужен infinite scroll: первый экран — только свежие дни, более
старые дни подгружаются по мере прокрутки.

Единица пагинации — **день** (по уже существующей дневной группировке), а не
сырая строка: чанк = 5 дней. Это сохраняет семантику timeline и «день» как
визуальную единицу.

## What Changes

- **Первый рендер** `/feed` и `/entries` — только последние **7 календарных
  дней** (сегодня + 6 предыдущих), а не вся история.
- **`/feed`**: внизу `past-day-section` — самозаменяющий sentinel
  `<div id="feed-older" hx-get="/feed/older?before=<самая старая загруженная дата>"
  hx-trigger="revealed" hx-swap="outerHTML">`. Новый роут `GET /feed/older`
  отдаёт timeline-секции следующих **5 дней** + свежий sentinel, либо ничего
  при исчерпании (htmx останавливается). Переиспользуются `timeline-day` /
  `entry-card`.
- **`/entries`**: зеркально — `GET /entries/older?before=…` и `#entries-older`,
  переиспользуется `day-section`.
- **Данные**: `app.db.entries` получает `get-entries-before` (date < before,
  ORDER BY date DESC, created_at DESC, с row LIMIT) и `get-entries-since`
  (стартовое окно 7 дней); домен — хелпер нарезки на дневные чанки (первые 5
  различных дат). Страницы больше **не** вызывают полную выборку
  `list-entries`.
- **График** (change `replace-radar-with-time-chart`): hero-график берёт своё
  окно (3д/неделя/месяц) из БД через собственный endpoint, **независимо** от
  пагинации. Стартовые 7 дней нужны только timeline-ленте и недельному
  спарклайну AI-секции.
- **Исчерпание**: когда старых записей не осталось, сервер не отдаёт sentinel
  → htmx прекращает триггерить, бесконечного цикла нет.

## Capabilities

### New Capabilities

_(нет)_

### Modified Capabilities

- `feed-timeline`: требование «Timeline-лента по дням» — первый рендер ограничен
  последними 7 днями; добавлено требование «Подгрузка старых дней ленты
  (infinite scroll)» с sentinel `#feed-older` и роутом `GET /feed/older`.
- `entries-crud`: требование «Список записей (страница /entries)» — первый
  рендер ограничен последними 7 днями; добавлено требование «Подгрузка старых
  записей (infinite scroll /entries)» с sentinel `#entries-older` и роутом
  `GET /entries/older`.

## Impact

- `src/app/db/entries.clj` — `get-entries-since`, `get-entries-before`,
  `count-entries`, `get-latest-entry` (защита от регрессии «latest»).
- `src/app/domains/entries.clj` — константы окна/чанка, `day-chunks`,
  `list-entries-initial`, `list-entries-before`, `latest-entry`.
- `src/app/views/feed.clj` — `past-day-section` (sentinel), `page` (окно 7 дней).
- `src/app/routes/feed.clj` — `older` (5-дневный чанк).
- `src/app/views/entries.clj` — `list-page` (окно 7 дней + sentinel).
- `src/app/routes/entries.clj` — `older`.
- `src/app/routes/app.clj` — регистрация `GET /feed/older`, `GET /entries/older`.
- `e2e/tests/feed.spec.ts`, `e2e/tests/entries-crud.spec.ts` — сценарии прокрутки.
- Тесты: `test/app/db/entries_test.clj`, `test/app/routes/app_test.clj`.
- **Без миграций**: схема БД не меняется.

## Scope

- Обе страницы-списка: `/feed` (timeline прошедших дней) и `/entries`
  (полный список), включая первый рендер и подгрузку старых дней.
- Слой данных: две новые bounded-выборки + доменная нарезка на дневные чанки.
- Границы запроса: `before` — ISO-дата; исчерпание обрабатывается отсутствием
  sentinel.

## Non-goals

1. Нумерованные страницы / ссылки на страницы (`?page=N`).
2. Jump-to-date / произвольный переход к дате.
3. Серверный кэш / CDN.
4. Offline-режим.
5. Изменение семантики дневной группировки (день остаётся единицей, порядок
   `date DESC, created_at DESC` не меняется).

## Dependencies / ordering

Change выполняется **после** `replace-radar-with-time-chart` (финализирует
hero-график с собственным окном) и **после** `entry-actions-from-feed`
(финализирует вид ленты и переиспользуемую карточку записи `entry-card`).
Оба предыдущих изменения должны быть влиты до старта.

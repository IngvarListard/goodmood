## Why

Фаза 1 завершена: медикаменты интегрированы, форма ввода (мобильная, 3 оси + опциональные блоки) работает. Но состояние по-прежнему сводится к трём числам в списке — нет визуализации «розы ветров» (Decision 2 из `product-vision`), нет производного ярлыка состояния (Decision 12.3), форма и список слеплены на одной странице, а несколько записей в день не имеют группировки и отображаются плоско. OQ2 (оси розы ветров) решён в `product-vision/design.md` Decision 12 — пора реализовать. `[ref: A3-q2, A2-q1, A3-q6, A1-q2]`

## What Changes

- **SVG-радар «роза ветров»** — hand-rolled inline SVG в hiccup2 (~55 строк в `app.views.rose`), 3 оси (energy/anxiety/focus) + опциональный `mood_score` = 3–4 точки полигона. Read-only на `/feed`, редактирование через слайдеры на `/check-in`. Без JS-библиотек.
- **Rule-based ярлык состояния** — 6 ярлыков (mixed/anxiety/elevated/low/balanced/neutral), first-match-wins по energy+anxiety, простая функция в `app.domains.entries` (не AI). Пользователь может сменить ярлык вручную.
- **Хранение ярлыка** — колонка `state_label TEXT NULLABLE` (миграция 006). NULL = auto-derived, non-NULL = user override. При правке осей → сброс в NULL.
- **Колонка `state_period_id`** — `INTEGER NULLABLE`, без FK. Добавляется сейчас (Фаза 2), заполняется в Фазе 6 (по mandate `entry-granularity` spec).
- **IA-реорганизация** — **BREAKING**: `/dashboard`, `/history`, `/statistics`, `/insights` удаляются (placeholder'ы без контента); `/feed` (NEW) — лента записей с розой, landing после логина; `/check-in` (placeholder → real) — отдельная страница формы; `GET /entries` → 308 redirect на `/feed`; `POST /entries` остаётся как htmx API endpoint. Итоговая навигация: 4 пункта (`/feed`, `/check-in`, `/medications`, `/settings`).
- **Несколько записей в день** — группировка по дате на `/feed` (сегодня / вчера / …), внутри дня — сортировка по `created_at`, timestamp отображается как «08:30». `created_at` уже есть в схеме (миграция 002), БД поддерживает несколько записей в день.

## Capabilities

### New Capabilities

- `mood-states`: «роза ветров состояний» — read-only SVG-радар, rule-based ярлык состояния, ручная смена ярлыка, 3 оси + опц. mood_score. AI-proposed state и override AI-розы — deferred to Phase 5.

### Modified Capabilities

- `entries-data`: миграция 006 добавляет `state_label TEXT` и `state_period_id INTEGER` (обе nullable, без FK); `create-entry!` принимает `state_label`; `get-entries` возвращает `state_label`, `state_period_id`, `created_at`.
- `entries-ui`: **BREAKING** — `/entries` (form+list) разделяется на `/feed` (лента с розой, landing) и `/check-in` (отдельная форма); `/dashboard`, `/history`, `/statistics`, `/insights` удаляются; hero-карточка последней записи с SVG-радаром 200×200, остальные — компактные карточки с бейджем ярлыка + значениями осей.
- `entry-granularity`: вариант (а) — несколько записей в день с timestamps, группировка по дням на `/feed`; `state_period_id` nullable now (вариант в — deferred to Phase 6).
- `mobile-entry`: требование «Submitting the form swaps result into feed» удалено — форма переехала на отдельную страницу `/check-in`, успех редиректит на `/feed`.
- `navigation`: **BREAKING** — навигация сокращается до 4 пунктов (`/feed`, `/check-in`, `/medications`, `/settings`); пункты `:dashboard`, `:history`, `:statistics`, `:insights` удаляются.

## Scope

- **In scope:** SVG-радар (hand-rolled, inline в hiccup2), rule-based ярлыки (функция в domains), миграция 006 (`state_label` + `state_period_id`), IA-реорганизация (`/feed`, `/check-in`, удаление placeholder-роутов), группировка записей по дням, ручная смена ярлыка (dropdown из 6 keyword'ов).
- **Out of scope:** AI-предложение розы/ярлыка (Фаза 5), инсайт-артефакты (Фаза 3), «период состояния» с `state_periods` таблицей (Фаза 6), статистика/корреляции (Фаза 5), push-уведомления (Фаза 4), мини-роза 64×64 на каждой карточке (hero-роза на последней записи дня + ярлык+текст на остальных — решено в design.md), редактирование существующих записей (только создание новых в этой фазе).

## Non-goals

- **AI-предложение состояния/ярлыка** — отложено в Фазу 5; rule-based достаточно для Фазы 2.
- **«Период состояния» (variant v)** — `state_period_id` колонка добавляется сейчас (schema-prepare), но таблица `state_periods` и UI отметки начала/конца — Фаза 6.
- **Статистика и аналитика** — `/statistics` удалён, вернётся в Фазе 5.
- **Инсайты** — `/insights` удалён, вернётся в Фазе 3.
- **Редактирование записей** — только создание; правка осей/ярлыка существующих записей — будущий change.
- **Hero-роза на каждой карточке** — только на последней записи сегодня; остальные карточки — бейдж ярлыка + значения осей текстом.
- **Новые зависимости** — не добавляются (SVG — hand-rolled, ноль библиотек).

## Impact

- **Миграции:** `006-add-state-label-and-period.up.sql` / `.down.sql` — два `ALTER TABLE entries ADD COLUMN`.
- **Новые файлы:** `src/app/views/rose.clj` (SVG-радар), `src/app/views/feed.clj` (страница ленты), `src/app/routes/feed.clj` (роут /feed), `src/app/views/check_in.clj` (страница формы), `src/app/routes/check_in.clj` (роут /check-in).
- **Изменённые файлы:** `src/app/db/entries.clj` (новые колонки в `create-entry!`/`get-entries`, группировка по `created_at`), `src/app/domains/entries.clj` (функция `state-label`, `state-label` в схеме/`create-entry`), `src/app/routes/app.clj` (роуты: удалить /dashboard, /history, /statistics, /insights; добавить /feed; /check-in → real; `GET /entries` → 308), `src/app/views/entries.clj` (форма перенесена в `check_in.clj`, `item`/`page` рефакторятся), `src/app/views/navigation.clj` (4 пункта), `src/app/views/placeholder.clj` (unused, можно удалить), `src/app/i18n.clj` (новые ключи: `:nav/feed`, `:state/mixed` и т.д.), `resources/i18n/ru.edn` + `en.edn`.
- **Удалённые роуты:** `/dashboard`, `/history`, `/statistics`, `/insights` — redirect/404.
- **Спецификации:** delta-спеки для `mood-states` (new), `entries-data` (modified), `entries-ui` (modified), `entry-granularity` (modified), `mobile-entry` (modified), `navigation` (modified) синхронизируются в `openspec/specs/` при archive.
- **Зависимости:** не добавляются.

## Acceptance Criteria

- GIVEN пользователь залогинен WHEN открывает `/` (или `/dashboard`) THEN редирект на `/feed`
- GIVEN на `/feed` есть записи сегодня WHEN страница рендерится THEN последняя запись показывает SVG-радар 200×200 + бейдж ярлыка + значения осей
- GIVEN на `/feed` есть записи сегодня WHEN страница рендерится THEN остальные записи сегодня — карточки с бейджем ярлыка + timestamp + значения осей текстом, без розы
- GIVEN на `/feed` есть записи за прошлые дни WHEN страница рендерится THEN записи сгруппированы по дате (заголовок «21 августа»), карточки компактнее (bg-base-300)
- GIVEN energy=4, anxiety=7, focus=3 WHEN вычисляется ярлык THEN `:state/anxiety` (правило `anxiety >= 6` срабатывает)
- GIVEN energy=8, anxiety=7 WHEN вычисляется ярлык THEN `:state/mixed` (правило `energy >= 7 AND anxiety >= 6` срабатывает первым)
- GIVEN запись создана WHEN `state_label` = NULL THEN ярлык auto-derived из осей
- GIVEN пользователь сменил ярлык вручную WHEN запись сохранена THEN `state_label` = выбранному keyword'у, показывается как есть
- GIVEN пользователь открывает `/check-in` WHEN страница рендерится THEN форма (template-tabs, range-sliders, optional blocks) + «← назад» на `/feed`
- GIVEN форма отправлена успешно WHEN htmx получает 201 THEN `window.location` → `/feed`
- GIVEN миграция 006 применена WHEN проверяется схема `entries` THEN колонки `state_label TEXT` и `state_period_id INTEGER` существуют и nullable
- GIVEN навигация рендерится WHEN пользователь видит bottom bar / sidebar THEN ровно 4 пункта: /feed, /check-in, /medications, /settings
- GIVEN роут `/entries` WHEN `GET` запрос THEN 308 redirect на `/feed`
- GIVEN на `/feed` нет записей сегодня WHEN страница рендерится THEN онбординг «Как ты? Создай первую запись» + кнопка на `/check-in`

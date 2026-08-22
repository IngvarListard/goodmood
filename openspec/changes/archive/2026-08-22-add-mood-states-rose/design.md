## Context

Фаза 1 завершена (медикаменты, миграция 005). Текущее состояние: `/entries` — форма+список на одной странице (orphan, не в навигации); оси energy/anxiety/focus уже в БД (миграция 004) и форме (range-sliders); `created_at` уже есть (миграция 002) — несколько записей в день работает на уровне БД; навигация — 7 пунктов, из которых 5 placeholder'ы. OQ2 (оси розы ветров) решён в `product-vision/design.md` Decision 12 — 3 обязательные оси (energy/anxiety/focus) + опц. mood_score.

UI-референсы прототипов: `design/feed-page.clj`, `design/check-in-page.clj`, `design/radar.clj` — одобренные hiccup2-векторы трёх экранов.

Стек: Clojure, hiccup2 (`:refer [html raw]`), Tailwind+DaisyUI, htmx, hyperscript, SQLite, next.jdbc, HoneySQL, migratus. Mobile-first, тёмная тема. Архитектура: `routes → domains → db`, views — чистые функции `data → hiccup`.

## Goals / Non-Goals

**Goals:**
- Реализовать SVG-радар «розу ветров» как переиспользуемый hand-rolled компонент (~55 строк, ноль зависимостей).
- Реализовать rule-based ярлык состояния (6 ярлыков, first-match-wins, без AI).
- Добавить `state_label` (nullable, гибрид auto-derive + override) и `state_period_id` (nullable, schema-prepare для Фазы 6) в миграции 006.
- Реорганизовать IA: `/feed` (лента, landing), `/check-in` (форма), удалить placeholder-роуты.
- Группировка записей по дням на `/feed`, timestamp «08:30».

**Non-Goals:**
- AI-предложение розы/ярлыка — Фаза 5.
- Таблица `state_periods` + UI отметки периода — Фаза 6.
- Инсайты, статистика, push-уведомления — Фазы 3/5/4.
- Редактирование существующих записей — будущий change.
- Мини-роза 64×64 на каждой карточке — только hero-роза на последней записи сегодня + бейдж ярлыка на остальных.
- Новые зависимости — не добавляются.

## Decisions

### 1. SVG-радар — hand-rolled inline SVG в hiccup2

Рендерится в `app.views.rose` (~55 строк, включая 2 helper'а и docstring'и):
- Полярные координаты: `cx + r·cos(θ)`, `cy + r·sin(θ)` (SVG y-down, старт с `-π/2`).
- `θ_i = -π/2 + 2π·i/N` — параметрически по N (3 или 4 оси), без хардкода.
- `viewBox` обязателен, `width/height` через CSS (200×200 мобайл, 240×240 десктоп).
- `currentColor` для осей/меток → наследуется от DaisyUI theme; `hsl(var(--p))` для полигона.
- 3 концентрических grid-треугольника (opacity 0.08/0.12/0.2), осевые линии (opacity 0.2), value-полигон (fill-opacity 0.3, stroke 2px), вершинные круги r=3, метки осей + значения рядом с точками.

Read-only на `/feed` (hero-роза 200×200 на последней записи сегодня). Редактирование через слайдеры на `/check-in`.

**Альтернатива (библиотека)** — отвергнута: радар = ~12 строк геометрии; cljs/svg-либа привнесёт больше накладных (сборка, version lock), чем сэкономит. Прототипы в `design/radar.clj` и `design/feed-page.clj` подтверждают: hand-rolled SVG рендерится корректно на тёмной DaisyUI-теме.

### 2. Rule-based ярлыки — 6 состояний, first-match-wins

Только energy + anxiety (focus не участвует в правилах — только визуализация). Биннинг: energy low 0-3 / mid 4-6 / high 7-10; anxiety low 0-3 / mid 4-5 / high 6-10.

Матрица 3×3 → 6 ярлыков:

```
                 anxiety 0-3      anxiety 4-5      anxiety 6-10
  energy 0-3     спад              спад              тревога
  energy 4-6     нейтрально        норма             тревога
  energy 7-10    подъём            подъём            смешанное
```

Порядок правил (specificity descending): `:state/mixed` → `:state/anxiety` → `:state/elevated` → `:state/low` → `:state/balanced` → `:state/neutral`.

Ярлыки — text (keyword → `i18n/t`), не цвет/иконка (простота, i18n). Пользователь может сменить ярлык вручную (dropdown из тех же 6 keyword'ов). AI в Фазе 5 сможет вводить focus-корреляции и дробить «спад» — преждевременно для rule-based.

**Альтернатива (больше ярлыков)** — отвергнута: 6 ярлыков клинически осмысленны, не фрагментируют UI. Деление «спад» на «чистый спад» vs «тревожный спад» — для AI Фазы 5.

### 3. Хранение ярлыка — `state_label TEXT NULLABLE`, гибрид

- `NULL` → рендер вычисляет ярлык на лету (auto-derived из осей, rule changes retroactive для старых записей).
- `non-NULL` → user override, показывается как есть, не перетирается правилами.
- При правке осей (через /check-in editing — будущий change): сброс `state_label` в NULL (override invalidated изменением розы). В этой фазе правки нет, поэтому правило фиксируется в design, но не тестируется.

~95% записей: auto-derived, ~5% override. Удовлетворяет spec `mood-states`: «User can override system-proposed state» + «Derived state label».

**Альтернатива (on-the-fly без колонки)** — отвергнута: невозможно хранить user override (spec requirement). **Альтернатива (всегда хранить)** — отвергнута: rule changes не retroactive, snapshot истории при эволюции правил теряется.

### 4. `state_period_id` — добавляется сейчас (миграция 006)

`INTEGER NULLABLE`, без FK (следуя паттерну medications — Decision 4a.4: loose coupling). Фаза 6 создаст таблицу `state_periods` и заполнит `state_period_id`, не трогая схему `entries`. Spec `entry-granularity` уже содержит mandate: «Data model foresees periods from Phase 2».

### 5. IA — `/feed` как landing, 4 пункта навигации

```
  До Фазы 2                            После Фазы 2
  /dashboard (placeholder)   → redirect → /feed
  /check-in  (placeholder)   → real (форма, отдельная страница)
  /history   (placeholder)   → removed (subsumed в /feed)
  /statistics (placeholder)  → removed (вернётся в Фазе 5)
  /insights  (placeholder)   → removed (вернётся в Фазе 3)
  /entries   (form + list)   → GET 308 → /feed; POST stays (htmx API)
  /medications, /settings    → без изменений
  /feed                      → NEW (лента = «мой день», landing)
```

Итоговая навигация: 4 пункта (`/feed`, `/check-in`, `/medications`, `/settings`). Decision 1 из `product-vision` («основная ценность живёт в ленте») → лента — первый экран после логина. Authenticated запросы на `/` или `/dashboard` редиректят на `/feed`.

### 6. Hero-роза на последней записи + бейдж на остальных

На `/feed`: последняя запись сегодня — hero-карточка с SVG-радаром 200×200 + бейдж ярлыка + значения осей. Остальные записи (сегодня и прошлые дни) — компактные карточки с бейджем ярлыка + timestamp + значения осей текстом («энергия 4 · тревога 7 · фокус 3»), без розы.

Роза — identity продукта (Decision 2); hero-роза на последней записи Reinforces это. Мини-роза 64×64 на каждой карточке — отложена (расхождение resolved в explore: ярлык+текст достаточно, hero-роза показывает визуальный паттерн). 64×64 SVG ≈ 200 bytes DOM — пренебрежимо, но визуальный шум на маленьких карточках избыточен.

## Risks / Trade-offs

- **SVG-сложность** → ~55 строк hand-rolled, прототипы (`design/radar.clj`) валидны и рендерятся. Риск минимален; если превысит 80 строк — рефакторинг.
- **Rule-based ярлыки слишком грубые** → 6 ярлыков не ловят нюансы («тревожный спад» vs «чистый спад»). Mitigation: AI в Фазе 5 уточнит; user override доступен уже сейчас.
- **IA-ломает привычный /entries** → `/entries` bookmark'ов у founder'а нет (orphan-роут). `GET /entries` → 308 redirect на `/feed` — backward compatible.
- **`state_label` drift при эволюции правил** → auto-derived ярлыки retroactive (NULL = re-compute), override'ы заморожены. При правке осей (будущее) — сброс в NULL. Объяснимо пользователю.
- **Удаление placeholder-роутов** → `/dashboard`, `/history`, `/statistics`, `/insights` — placeholder'ы без контента, возвращаются своими change'ами в Фазах 3/5. Bookmark'ов нет.

## Migration Plan

1. Миграция 006 (`resources/migrations/006-add-state-label-and-period.{up,down}.sql`): два `ALTER TABLE entries ADD COLUMN`. Non-blocking, nullable.
2. Код обновляется в порядке: миграция → db → domains → routes → views → i18n → тесты.
3. Rollback: миграция 006-down (DROP COLUMN) — безопасна, колонки nullable.

`[ref: A3-q2, A2-q1, A3-q6, A1-q2]`

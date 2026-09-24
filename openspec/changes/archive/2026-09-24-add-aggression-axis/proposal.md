# Proposal: add-aggression-axis

## Why

В биполярном расстройстве раздражительность/агрессия — отдельная значимая ось
состояния, не сводимая к энергии и тревоге. Сейчас ядро записи знает только
energy/anxiety/focus. Нужна четвёртая ось «агрессия» с **обратной полярностью**
(высокое значение — плохо, в отличие от energy/focus, где высокое — хорошо),
чтобы фиксировать эти состояния и позже строить по ним графики.

## What Changes

- Новая миграция `004-aggression-axis`: nullable-колонка `entries.aggression`
  INTEGER (без CHECK через ALTER — как energy/anxiety в `001-init`); диапазон
  0–10 валидирует домен.
- Домен `app.domains.entries`: `:aggression` в `create-entry-schema` и
  `update-entry-schema` (`[:maybe [:int {:min 0 :max 10}]]`), в
  `updatable-fields` и в проброске `create-entry`.
- БД `app.db.entries/create-entry!`: `:aggression` в values-map.
- Чек-ин `app.views.check_in.clj` (`soft-fields`): range-слайдер агрессии после
  focus с двумя краевыми подписями — слева «Добрячок», справа «Мудак» (i18n).
- Edit-форма и read-view `app.views.entries.clj`: агрессия как range-field и в
  строке осей (`axes-line`).
- Лента `app.views.feed.clj` (`entry-card`): четвёртый чип метрики; grid
  `grid-cols-3` → `grid-cols-2` (2×2, мобильный приоритет).
- `app.views.layout.clj`: CSS-переменная `--gm-metric-aggression` в `:root`.
- i18n ru/en: `:entries/aggression` и подписи краёв слайдера.

## Capabilities

### New Capabilities

_(нет)_

### Modified Capabilities

- `entries`: «Entries table schema» (колонка aggression), «Entry data access
  functions» (проброс aggression), «Create entry via API» и «Entry request
  validation» (aggression — опциональное поле), «Three-axis core with range
  sliders» (четвёртый опциональный слайдер с краевыми подписями).
- `feed-timeline`: «Карточка записи с чипами метрик» — четвёртый чип агрессии,
  сетка 2×2.
- `entries-crud`: «Карточка записи с редактированием» — агрессия в read-view и
  edit-форме.

## Impact

- `resources/migrations/004-aggression-axis.up.sql` / `.down.sql` — новые файлы
- `src/app/domains/entries.clj` — схемы и проброс
- `src/app/db/entries.clj` — `create-entry!`
- `src/app/views/check_in.clj`, `src/app/views/entries.clj`,
  `src/app/views/feed.clj`, `src/app/views/layout.clj` — UI
- `resources/i18n/{ru,en}.edn` — тексты
- Тесты: `test/app/routes/app_test.clj`, `test/app/db/entries_test.clj`
- Схема БД меняется (одна nullable-колонка), миграция append-only в паре
  up/down

## Scope

- Полный вертикальный срез оси: миграция → домен → БД → чек-ин → edit/read →
  чип ленты → i18n.
- Слайдер 0–10 с краевыми якорями, хранение целым числом (как energy/anxiety/
  focus — числа пойдут в графики).

## Non-goals

- Изменение rule-based `state-label` — по-прежнему считается только по
  energy/anxiety; агрессия в него не входит.
- AI-корреляции/ярлыки/советы с агрессией (будущее изменение).
- Сводная линия «общее настроение» и переход с радара на time-chart — это
  отдельное изменение `replace-radar-with-time-chart`, которое **зависит** от
  этого (композит использует агрессию с учётом обратной полярности).
- Изменение контракта радара розы ветров (агрессия в радар не добавляется).

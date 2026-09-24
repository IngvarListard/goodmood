# Design: replace-radar-with-time-chart

## Context

Сейчас hero-карточка `/feed` рендерит `app.views.rose/period-radar` — `canvas[data-gm-radar]` с JSON `{labels, values}`, который клиентский `resources/public/js/radar.js` рисует как Chart.js `radar` (200×200). Данные для радара считает `app.domains.entries/period-axes` (per-day mean → recency-weighted mean). Переключение периода — `GET /feed/radar?period=day|week|month` с `hx-target="#radar-period"`, `hx-swap="outerHTML"`. Chart.js 4.5.1 (UMD) уже подключён в `app.views.layout/head` с `defer`, следом за ним `radar.js`.

Рядом живёт мини-график недели (`week-chart` / `week-chart-section` в `app.views.feed`): SVG-полилиния за 7 дней, точки с классами `fill-success` / `fill-warning` / `fill-error`, линия `stroke-primary`. Эти daisyUI-named `fill-*` утилиты Tailwind browser CDN не генерирует, поэтому точки рендерятся чёрными.

Владелец решил убрать радар полностью и заменить его линейным графиком по времени (оси `mood_score`, `energy`, `anxiety`, `focus`, `aggression` + составная «общее настроение»), переключатель 3 дня / неделя / месяц, дефолт — неделя.

## Decisions

### 1. Удаление радара целиком

Удаляются `src/app/views/rose.clj` и `resources/public/js/radar.js`, require `rose` и все вызовы `period-radar` в `app.views.feed`, handler `radar` и require в `app.routes.feed`, маршрут `/feed/radar` в `app.routes.app`. Radar-only доменные хелперы (`period-axes`, `axis-value`, `period-sizes`, `period-dates` в текущем виде) удаляются; `mean-non-nil` сохраняется как переиспользуемый per-day-mean.

**Rationale:** прямое решение владельца; мёртвый код не оставляем. **Tradeoff:** теряется многоугольная форма и recency-weighted агрегат; radar-спеки, unit- и e2e-тесты переписываются.

### 2. Тип графика и переиспользование Chart.js

Новый график — Chart.js `line`; библиотека не добавляется (pinned 4.5.1 уже в head). Новый рендерер — `resources/public/js/feed-chart.js`.

**Rationale:** тренд читается как изменение во времени, что и нужно; нулевая новая зависимость. **Tradeoff:** Chart.js остаётся единственным canvas-рендерером, контракт `data-gm-chart` нужно поддерживать вручную.

### 3. Контракт canvas `data-gm-chart`

Сервер рендерит `<canvas role="img" data-gm-chart="...">` с JSON `{labels:[ISO dates], datasets:[{key, label, values, colorVar}]}`. `values` — числа или `null` (день без значения = разрыв). `label` — i18n; `colorVar` — имя CSS-переменной. `aria-label` — i18n-строка. Составной датасет идёт последним.

**Rationale:** симметрично прежнему `data-gm-radar` — сервер владеет данными и текстами, клиент — пикселями. **Tradeoff:** смена контракта требует нового рендерера и переписывания `radar.spec.ts`.

### 4. Набор датасетов и формула составной линии

Оси: `mood_score`, `energy`, `anxiety`, `focus`, `aggression`. Составная = среднее по доступным осям из `[mood_score, energy, focus, 10−anxiety, 10−aggression]` (energy и focus «хорошо = высоко», anxiety и aggression инвертируются). День без значений оси → `null`.

**Rationale:** «общее настроение» — одна читаемая линия поверх пяти осей; инверсия приводит все оси к одной полярности. **Tradeoff:** при разном числе доступных осей среднее по доступным (не по нулям) делает дни не полностью сопоставимыми — сознательно, чтобы отсутствие данных не тянуло вниз.

### 5. Окна периодов и дефолт

`3d` = 3 дня, `week` = 7 дней, `month` = 30 дней; окно скользящее, заканчивается сегодня, фильтр по `entries.date`. Дефолт — `week`. Маршрут `GET /feed/chart?period=3d|week|month`; невалидное значение трактуется как `week`.

**Rationale:** неделя — баланс между «не одна точка» и «не рябь». **Tradeoff:** дневной срез, который был у радара, убран; 3 дня — минимальная осмысленная линия.

### 6. Серверный фрагмент и htmx-свап

Переключатель повторяет прежний паттерн радара: кнопки `hx-get="/feed/chart?period=…"`, `hx-target="#feed-chart"`, `hx-swap="outerHTML"`; сервер возвращает фрагмент через `html-response`. Активная кнопка подсвечена, `aria-pressed`.

**Rationale:** установленный htmx-паттерн, без нового JS для переключения; никакого JSON API для фронтенда. **Tradeoff:** выбранный период не персистится (перезагрузка → неделя) — принято сознательно.

### 7. Доменная агрегация и ограниченный запрос

Новая функция в `app.domains.entries` принимает период и `ds`/`user-id`, возвращает `{:dates [...] :axes {:mood-score [...] :energy [...] :anxiety [...] :focus [...] :aggression [...]} :composite [...]}`. Per-day mean = среднее непустых значений оси за день (`mean-non-nil`), день без значений → `nil`. Запрос к БД ограничен окном (`date >= today − (n−1)`), а не `list-entries` (все записи).

**Rationale:** окно из 30 дней не должно тянуть всю историю пользователя. **Tradeoff:** нужна новая db-функция и, для производительности, индекс/условие по `(user_id, date)`; `list-entries` остаётся для ленты и других потребителей.

### 8. Цвета только из CSS-переменных

`colorVar` по датасетам: `energy` → `--gm-metric-energy`, `anxiety` → `--gm-metric-anxiety`, `focus` → `--gm-metric-focus`, `aggression` → `--gm-metric-aggression`, `mood_score` → `--color-secondary`, составной → `--color-primary`. JS читает значение через `getComputedStyle(document.documentElement)`; хардкод цветов запрещён.

**Rationale:** график читаем в тёмной и светлой темах. **Tradeoff:** `--gm-metric-aggression` появляется только вместе с `add-aggression-axis` — жёсткая зависимость (см. Risks).

### 9. Фикс цвета мини-графика недели

В `app.views.feed` классы `fill-success` / `fill-warning` / `fill-error` заменяются на inline `:style {:fill "var(--color-success)"}` (и аналогично warning/error), `stroke-primary` — на `:style {:stroke "var(--color-primary)"}`. Сам SVG, точки, пороги и fallback не меняются.

**Rationale:** Tailwind browser CDN не генерирует daisyUI `fill-*`, поэтому точки чёрные; inline-`var(...)` рендерится всегда. **Tradeoff:** inline-стиль вместо класса — минимальный diff; при желании позже можно вынести в `gm-` класс в layout-стилях.

### 10. Пустые и недостаточные данные

День без значения оси → `null` в `values`; рендерер использует `spanGaps: false` — линия честно рвётся на пропущенном дне. Если во всём окне меньше 2 дней с любыми данными — вместо графика показывается приглушённый fallback-текст (паттерн `:ai/no-week-data`).

**Rationale:** разрыв не выдаёт отсутствие данных за данные. **Tradeoff:** одиночный пропуск делит линию надвое; смягчено точками и fallback.

### 11. i18n

Добавляются ключи: подписи переключателя (`:period-3d` «3 дня» / «3 days», существующие `:period-week` / `:period-month` переиспользуются), метка составного ряда «общее настроение» / «overall mood», текст fallback при < 2 днях, `aria-label` графика. Удаляются `:radar-weighted`, `:radar-no-data`. Обе локали — `resources/i18n/{ru,en}.edn`.

**Rationale:** весь видимый текст — через i18n. **Tradeoff:** `:period-day` заменяется на `:period-3d` — проверить отсутствие других потребителей.

### 12. Зависимость от add-aggression-axis

Ось `aggression` приходит из изменения `add-aggression-axis`. Реализацию `replace-radar-with-time-chart` начинать после него; до этого составная формула и датасеты неполны.

**Rationale:** заявленная зависимость владельца. **Tradeoff:** последовательная, а не параллельная реализация.

## Risks

- **`add-aggression-axis` ещё не реализован** (в `openspec/changes/add-aggression-axis` пока только `.openspec.yaml`): без колонки `aggression` и её CSS-переменной график и составная линия неполны. Митигация — блокировать группы 2–4 tasks до мержа зависимости.
- **`grep -ri "radar\|rose" src resources/public` не станет пустым сам собой**: `src/app/domains/ai.clj` содержит `rose-line` («Роза ветров последней записи»), `resources/public/js/push.js` — комментарий про `radar.js`, `layout.clj` — комментарии. Нужен явный переименованный/очищенный текст (tasks 1.6–1.7, 5.1).
- **Смена темы не перерисовывает график**: цвета читаются один раз при отрисовке; после переключения темы до перезагрузки/свапа линии остаются старыми. Приемлемо для текущего дизайна; при необходимости — перерисовка по событию.
- **Ограниченный запрос** требует условия по `(user_id, date)`; при отсутствии индекса возможен полный скан на больших объёмах.
- **E2E-проверки canvas** (пиксели/инстанс) хрупкие; переписать `radar.spec.ts` под `data-gm-chart` аккуратно.

## Verification

- `/feed` рендерит `canvas[data-gm-chart]`; Chart.js рисует линейный график; читаем в тёмной и светлой темах.
- Переключение 3 дня / неделя / месяц htmx-свапает фрагмент без полной перезагрузки; дефолт — неделя.
- `grep -ri "radar\|rose" src resources/public` — пусто.
- Доменные unit-тесты на per-day mean, разрывы и составную линию: `clojure -M:test`.
- E2E `feed.spec.ts` обновлён и зелёный: `cd e2e && npx playwright test tests/feed.spec.ts`; `radar.spec.ts` удалён/переписан.
- Приложение поднимается и `/feed` отвечает 200: `./bin/dev` + `curl http://localhost:3000/`.

# Design: add-js-radar-chart

## Context

`app.views.rose/radar` рисует SVG-радар 200×200 hand-rolled hiccup-ом: ручная полярная геометрия, grid-кольца, value-полигон, i18n-метки. Внешний вид плоский; развитие визуала усложняет Clojure-код. Локальных статиков в проекте нет: все скрипты через CDN (`layout.clj` держит `def htmx-src` и т.п.), middleware `->app` оборачивает всё в `require-auth` (всё, что не `:auth/public`, редиректит на /login). Прототипы V1/V2/V3 отрисованы в браузере (скриншоты в сессии), выбран V2: радиальный градиент + свечение точек.

Инварианты 03-architecture.md: изменения в слое `views/` (+ middleware в routes), домены и db не затронуты.

## Goals / Non-Goals

**Goals:**
- Красивый анимированный радар (Chart.js) без роста hiccup-геометрии
- Чистый контракт Clojure→JS: сервер владеет данными/текстами, JS — тупой рендерер
- Минимальная инфраструктура локальных статиков (один файл, без npm/bundler)

**Non-Goals:**
- npm/node_modules/bundler-пайплайн (esbuild и пр.) — появится, когда локальных JS станет несколько и им понадобятся импорты
- Перевод `week-chart` (график настроения) и empty-state SVG на JS — только радар
- Локальный хостинг самого Chart.js (остаётся CDN, как htmx/daisyui)
- Fallback-рендер SVG в `<noscript>` — принят отказ: значения дублируются чипами метрик и axes-line

## Decisions

### 1. Chart.js 4.5.1 через CDN (последняя 4.x)

Pin `https://cdn.jsdelivr.net/npm/chart.js@4.5.1/dist/chart.umd.min.js`. UMD-сборка даёт глобальный `Chart` без модульной системы — согласуется с остальными CDN-скриптами layout. Альтернативы: ApexCharts/ECharts — тяжелее при тех же возможностях для одного графика; hand-rolled canvas — тот же труд, что текущий SVG, без выигрыша.

### 2. Контракт: JSON в `data-gm-radar`, JS — тупой рендерер

`app.views.rose/radar` рендерит:

```clojure
[:canvas {:class "gm-radar" :width 200 :height 200
          :role "img" :aria-label (str "Роза ветров: энергия " …)
          :data-gm-radar (json/generate-string
                          {:labels [(i18n/t :entries/energy) …]
                           :values [energy anxiety focus mood-score]})}]
```

- **Почему data-атрибут, а не inline-скрипт/глобальная переменная**: данные едут вместе с разметкой — работает при любой навигации (hx-boost) и в htmx-фрагментах без дополнительных событий; проект уже пишет JSON в `hx-vals` — паттерн знакомый.
- **Почему JSON генерирует Clojure**: i18n-метки и формат значений остаются серверными; в JS нет локалей и словарей. cheshire уже в зависимостях (domains/ai).
- **aria-label остаётся серверным** — accessibility и e2e-якорь не переезжают в JS.
- Раскладка осей (порядок и углы как в текущем `axis-specs`: energy top → anxiety → focus → mood при наличии) сохраняется через порядок в `:labels`/`:values`.

### 3. Инициализация: DOMContentLoaded + htmx:afterSettle, защита от повторов

```js
const draw = () => document
  .querySelectorAll('canvas[data-gm-radar]:not([data-gm-drawn])')
  .forEach(drawRadar);
document.addEventListener('DOMContentLoaded', draw);
document.body.addEventListener('htmx:afterSettle', draw);
```

`data-gm-drawn` — маркер «уже нарисовано»; после свапа htmx новый canvas рисуется, старые не трогаются. Скрипт подключается глобально (`<script defer>`) — на страницах без радара это no-op (как hyperscript). Слушатель вешается на `document.body` один раз — при body-свапе hx-boost htmx переносит слушатели body (документация htmx; слушатели на body не дублируются, т.к. скрипт исполняется один раз за загрузку страницы).

### 4. Визуал V2: gradient + glow, цвета из CSS-переменных

- Fill: `createRadialGradient` от `--color-secondary` (alpha ~0.33 в центре) к `--color-primary` (alpha ~0.13 к краям).
- Точки: белые, радиус 3.5, обводка primary 2px, glow через мини-плагин (`beforeDatasetsDraw`: `ctx.shadowBlur=12; shadowColor=primary`) — как у `.gm-range`.
- Цвета читаются из `getComputedStyle(document.documentElement).getPropertyValue('--color-primary')` — работают обе темы (dark/light) и смена темы через data-theme.
- Сетка: круглые кольца (`grid.circular: true`), `rgba(230,232,240,0.10)`; ticks скрыты, 0–10; pointLabels — 11px Inter, цвет base-content.
- `aria-label` дублирует значения текстом — при отсутствии JS (canvas пуст) скринридер и e2e получают данные из атрибута.

### 5. Статики: wrap-resource снаружи require-auth

В `->app` после `mw/wrap-request-log` (внешне его) добавить:

```clojure
(ring.middleware.resource/wrap-resource "public")
ring.middleware.content-type/wrap-content-type
ring.middleware.not-modified/wrap-not-modified
```

Внешнее размещение = статики не требуют сессии (как и CDN-скрипты; секретов в JS нет) и не логинят при прямом заходе. Путь файла: `resources/public/js/radar.js` → URL `/js/radar.js`. Бонус: локальный статик грузится даже в браузерах с адблоком, режущим CDN.

### 6. Порядок скриптов в layout

Chart.js (`defer`) → `/js/radar.js` (`defer`) — defer сохраняет порядок исполнения, radar.js видит `window.Chart`. Оба до `</body>`-логики не нужны: layout уже грузит скрипты в head.

## Risks / Trade-offs

- [Chart.js заблокирован адблоком у части пользователей] → тот же класс риска, что у уже используемых CDN; деградация — пустой canvas при живых чипах/axes-line. Опция смягчения позже — локальная копия UMD в resources/public.
- [Тема переключается — canvas не перерисовывается сам] → цвета читаются на момент отрисовки; смена темы POST-ом перезагружает страницу (wrap-theme редирект) → перерисовка происходит. Приёмлемо.
- [htmx:afterSettle на body при hx-boost] → слушатель выживает boost-свапы; повторная подписка исключена однократным исполнением скрипта. Проверяется e2e-навигацией feed→check-in→feed.
- [canvas без JS] → принято (non-goal); чипы метрик дублируют значения.

## Open Questions

_(нет — вид V2 выбран по прототипам, версии pinned, пути согласованы)_

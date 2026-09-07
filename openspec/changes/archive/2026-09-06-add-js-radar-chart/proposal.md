# Proposal: add-js-radar-chart

## Why

Радар «роза ветров» на hero-карточке ленты рисуется hand-rolled SVG в `app.views.rose` — плоско и без анимации, а каждое улучшение вида раздувает hiccup-код с ручной геометрией. Отрисовку стоит вынести в JS (Chart.js), оставив Clojure владельцем данных и текстов. Проект при этом не имеет инфраструктуры локальных статиков (всё через CDN) — она появится как часть этого изменения.

## What Changes

- **Chart.js 4.5.1 через CDN** — первая JS-библиотека визуализации; подключается в `layout.clj` рядом с htmx/hyperscript (глобально, идемпотентно).
- **`+resources/public/js/radar.js`** — первый локальный статик проекта: vanilla-JS рендерер радара по контракту `data-gm-radar` (JSON в data-атрибуте); стилистика V2: радиальный градиент primary→secondary, белые точки с primary-ободком и свечением; инициализация на DOMContentLoaded + htmx:afterSettle (покрывает hx-boost и свапы).
- **Раздача статиков**: `wrap-resource "public"` + `wrap-content-type` + `wrap-not-modified` в `->app` снаружи `require-auth` (JS не содержит секретов; CDN-скрипты тоже публичны).
- **`app.views.rose/radar`** — вместо SVG-геометрии рендерит `<canvas role="img" aria-label="…" data-gm-radar="…JSON…">`; метки осей через i18n, значения и раскладка осей (energy — верх, anxiety, focus, mood) сохраняются 1:1.
- **e2e**: локатор в `feed.spec.ts` меняется с `svg[role="img"]` на `canvas[role="img"]`.
- Fallback без JS принимается: значения метрик дублируются чипами и текстом `axes-line`.

## Capabilities

### New Capabilities

_(нет)_

### Modified Capabilities

- `ui-shell`: новые требования — раздача локальных статиков из `resources/public` (вне auth), подключение Chart.js + `/js/radar.js` в layout.
- `entries`: требование «Feed page renders entries with rose-of-winds» меняется (SVG-радар → canvas/Chart.js); требование «Read-only SVG radar renders rose-of-winds» заменяется на canvas-версию с контрактом `data-gm-radar` (запрет JS-библиотек снимается).

## Impact

- `src/app/routes/app.clj` — middleware-цепочка
- `src/app/views/layout.clj` — `chart-js-src`, `<script defer src="/js/radar.js">`
- `src/app/views/rose.clj` — перезапись `radar` (контракт вместо SVG), удаление геометрических хелперов
- `+resources/public/js/radar.js`
- `e2e/tests/feed.spec.ts` — локатор
- Риски: CDN-блокировка Chart.js в браузерах с адблоком (как у существующих CDN; локальный radar.js от этого не зависит)

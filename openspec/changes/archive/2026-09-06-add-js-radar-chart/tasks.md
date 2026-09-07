# Tasks: add-js-radar-chart

## 1. Инфраструктура статиков

- [x] 1.1 `routes/app.clj`: добавить `wrap-resource "public"` + `wrap-content-type` + `wrap-not-modified` в `->app` снаружи `require-auth`; проверить `GET /js/radar.js` (пока 404 — путь появится в задаче 2.1)

## 2. Рендерер и данные

- [x] 2.1 Создать `resources/public/js/radar.js`: контракт `canvas[data-gm-radar]` (JSON `{labels, values}`), V2-стиль — радиальный градиент secondary→primary, белые точки с primary-ободком и glow-плагином, круглая сетка, ticks скрыты 0–10, pointLabels 11px; цвета из CSS-переменных; инициализация DOMContentLoaded + `htmx:afterSettle`, маркер `data-gm-drawn`
- [x] 2.2 `views/rose.clj`: переписать `radar` — рендер `<canvas role="img" width 200 height 200 :aria-label (str "Роза ветров: энергия " …) :data-gm-radar (json/generate-string {:labels [...] :values [...]})>`; порядок осей и i18n-метки как в текущем `axis-specs`; удалить геометрические хелперы (`axis-angle`, `polar-point`, `points->str`, `grid-scales` и т.п.); обновить docstring (данные/контракт — Clojure, отрисовка — js/radar.js)
- [x] 2.3 `views/layout.clj`: `def chart-js-src` (CDN chart.js@4.5.1 UMD) + `<script defer>` для обоих (Chart.js перед radar.js)

## 3. Проверка

- [x] 3.1 REPL: `(app.views.rose/radar {:energy 4 :anxiety 7 :focus 3})` и с `:mood-score 5` — валидный hiccup, JSON в атрибуте корректен, 3/4 оси
- [x] 3.2 Перезапустить сервер; curl: `/js/radar.js` → 200 без сессии; `/feed` содержит canvas с `data-gm-radar`
- [x] 3.3 Playwright: радар отрисован на /feed (canvas непуст, Chart.js инициализирован), вид V2 (градиент/glow) в тёмной и светлой темах; навигация hx-boost feed→check-in→feed — радар перерисовался единожды
- [x] 3.4 e2e: обновить локатор в `feed.spec.ts` (`svg[role="img"]` → `canvas[role="img"]`); прогнать smoke + feed + page-shell наборы — зелёные
- [x] 3.5 Grep-контроль: в `src/` не осталось SVG-геометрии радара (`polar-point`, `points->str`); в JS нет i18n-словарей и хардкода русских текстов

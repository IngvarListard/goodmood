# Tasks: replace-radar-with-time-chart

> **Зависимость:** `add-aggression-axis` должен быть реализован до задач групп 2–4 (колонка `aggression` в БД, ось в домене и CSS-переменная `--gm-metric-aggression`).

## 1. Удаление радара

- [x] 1.1 Удалить файлы `src/app/views/rose.clj` и `resources/public/js/radar.js`.
  Приёмка: файлов нет.
  Проверка: `test ! -e src/app/views/rose.clj && test ! -e resources/public/js/radar.js`.
- [x] 1.2 В `src/app/views/feed.clj` убрать `[app.views.rose :as rose]` из `ns`, аргумент `radar` и вызовы `rose/period-radar` в `hero-card`, `today-section`, `page`, а также radar-комментарии.
  Приёмка: неймспейс компилируется, `/feed` рендерится.
  Проверка: `clojure -M:test`.
- [x] 1.3 В `src/app/routes/feed.clj` удалить require `[app.views.rose :as rose]` и функцию `radar`.
  Приёмка: неймспейс компилируется.
  Проверка: `clojure -M:test`.
- [x] 1.4 В `src/app/routes/app.clj` удалить маршрут `["/feed/radar" {:get {:handler (partial feed/radar ds)}}]`.
  Приёмка: маршрут недоступен.
  Проверка: `curl -s -o /dev/null -w "%{http_code}" http://localhost:3000/feed/radar` → не 200.
- [x] 1.5 В `src/app/domains/entries.clj` удалить radar-only хелперы `period-axes`, `axis-value` и связанный recency-код; `period-sizes`/`period-dates` заменить оконным хелпером (см. 2.2); сохранить `mean-non-nil` как переиспользуемый per-day-mean.
  Приёмка: мёртвого radar-кода нет.
  Проверка: `clojure -M:test`.
- [x] 1.6 В `src/app/domains/ai.clj` переименовать `rose-line` и убрать текст «Роза ветров последней записи» из контекста чата (нейтральное имя и формулировка без `rose`/`radar`).
  Приёмка: `grep -ri "radar\|rose" src` — пусто.
  Проверка: `grep -ri "radar\|rose" src`.
- [x] 1.7 В `resources/public/js/push.js` убрать упоминание `radar.js` из комментария.
  Приёмка: `grep -ri "radar\|rose" resources/public` — пусто.
  Проверка: `grep -ri "radar\|rose" resources/public`.
- [x] 1.8 В `resources/i18n/{ru,en}.edn` удалить `:radar-weighted` и `:radar-no-data`.
  Приёмка: radar-ключей нет ни в одной локали.
  Проверка: `grep -ri "radar\|rose" resources/i18n`.

## 2. Доменная агрегация

- [x] 2.1 В `src/app/db/entries.clj` добавить выборку, ограниченную окном (например `get-entries-since` с `:where [:and [:= :user_id user-id] [:>= :date since]]` и `:order-by`).
  Приёмка: возвращаются только записи окна.
  Проверка: REPL/unit-тест на `get-entries-since`.
- [x] 2.2 В `src/app/domains/entries.clj` добавить оконный хелпер `chart-windows` (`{:3d 3 :week 7 :month 30}`) и `window-dates`, затем функцию `daily-axis-series` → `{:dates [...] :axes {:mood-score [...] :energy [...] :anxiety [...] :focus [...] :aggression [...]} :composite [...]}` (per-day mean непустых значений; composite = среднее `[mood_score, energy, focus, 10−anxiety, 10−aggression]` по доступным осям; окно ограничено запросом из 2.1).
  Приёмка: значения, разрывы и инверсия корректны.
  Проверка: `clojure -M:test`.
- [x] 2.3 В `test/app/domains/entries_test.clj` заменить radar-тесты `test-period-axes-*` на тесты `daily-axis-series`: per-day mean, nil-разрыв, composite-инверсия, границы окна, future-excluded.
  Приёмка: тесты проходят.
  Проверка: `clojure -M:test`.

## 3. Рендерер и маршрут графика

- [x] 3.1 Создать `resources/public/js/feed-chart.js`: Chart.js `line`, X = `labels`, Y 0–10, цвет каждого датасета из `colorVar` через `getComputedStyle(document.documentElement)`, `spanGaps: false`; идемпотентность (DOMContentLoaded + `htmx:afterSettle`, маркер `data-gm-drawn`).
  Приёмка: рисует в тёмной и светлой темах, без хардкода цветов.
  Проверка: визуально/Playwright; `grep -n "#[0-9a-fA-F]\{3,6\}" resources/public/js/feed-chart.js` — пусто.
- [x] 3.2 В `src/app/views/feed.clj` добавить view-функцию hero-графика: `<canvas role="img" data-gm-chart=... aria-label=...>` (JSON `{labels, datasets:[{key,label,values,colorVar}]}`, composite последним), кнопки 3 дня/неделя/месяц (`hx-get="/feed/chart?period=..."`, `hx-target="#feed-chart"`, `hx-swap="outerHTML"`, `aria-pressed`), fallback-текст при < 2 днях данных.
  Приёмка: `/feed` содержит `canvas[data-gm-chart]`.
  Проверка: `curl -s http://localhost:3000/feed | grep -o 'data-gm-chart'`.
- [x] 3.3 В `src/app/routes/feed.clj` добавить handler `chart` (period `3d|week|month`, невалидное → `week`; bounded query + `daily-axis-series`; `html-response`). В `src/app/routes/app.clj` добавить `["/feed/chart" {:get {:handler (partial feed/chart ds)}}]`.
  Приёмка: фрагмент отдаётся по всем периодам.
  Проверка: `curl -s "http://localhost:3000/feed/chart?period=week" | grep -o 'data-gm-chart'`.

## 4. Фикс цвета мини-графика недели

- [x] 4.1 В `src/app/views/feed.clj` заменить классы `fill-success`/`fill-warning`/`fill-error` на inline `:style {:fill "var(--color-success)"}` / `var(--color-warning)` / `var(--color-error)`, а `stroke-primary` — на `:style {:stroke "var(--color-primary)"}` (функция `mood-fill-class` и polyline в `week-chart`). Сам мини-график иначе не менять.
  Приёмка: точки и линия цветные в обеих темах, чёрного нет.
  Проверка: Playwright/визуально; `grep -n "fill-success\|fill-warning\|fill-error\|stroke-primary" src/app/views/feed.clj` — пусто.

## 5. layout head

- [x] 5.1 В `src/app/views/layout.clj` заменить `/js/radar.js` на `/js/feed-chart.js` и обновить комментарии (включая CSS-комментарий про `radar.js`). Chart.js CDN оставить.
  Приёмка: head содержит `/js/feed-chart.js`.
  Проверка: `grep -ri "radar\|rose" src` — пусто.

## 6. i18n

- [x] 6.1 В `resources/i18n/{ru,en}.edn` добавить ключи: подписи переключателя (`:period-3d` «3 дня»/«3 days»; `:period-week`/`:period-month` переиспользовать), метка составного ряда («общее настроение»/«overall mood»), текст fallback при < 2 днях, `aria-label` графика. Убрать неиспользуемый `:period-day`.
  Приёмка: обе локали рендерят тексты.
  Проверка: `curl -s -H 'Cookie: gm-locale=en' http://localhost:3000/feed | grep -o 'overall mood'`.

## 7. Тесты и e2e

- [x] 7.1 В `test/app/routes/app_test.clj` заменить `feed-page-hero-card-with-radar` на проверку `data-gm-chart`/`<canvas>` (без «Роза ветров»).
  Приёмка: тест проходит.
  Проверка: `clojure -M:test`.
- [x] 7.2 Обновить `e2e/tests/feed.spec.ts` (hero = `canvas[data-gm-chart]`, переключение 3 дня/неделя/месяц). Переписать или удалить `e2e/tests/radar.spec.ts` под новый контракт.
  Приёмка: спеки зелёные.
  Проверка: `cd e2e && npx playwright test tests/feed.spec.ts`.

## 8. Финальная верификация

- [x] 8.1 `grep -ri "radar\|rose" src resources/public` — пусто.
- [x] 8.2 `clojure -M:test` — зелёный.
- [x] 8.3 `./bin/dev`; `curl http://localhost:3000/` → 200; `/feed` рендерит `canvas[data-gm-chart]`; переключение 3 дня/неделя/месяц свапает фрагмент без перезагрузки; график читаем в тёмной и светлой темах.
- [x] 8.4 `cd e2e && npm run test:fast` — зелёный.

# Tasks: add-aggression-axis

## 1. Миграция

- [x] 1.1 Создать `resources/migrations/004-aggression-axis.up.sql` (`ALTER TABLE entries ADD COLUMN aggression INTEGER;`, комментарий: nullable, без CHECK — как energy/anxiety в 001-init) и `resources/migrations/004-aggression-axis.down.sql` (drop колонки; в комментарии — fallback через пересоздание таблицы для SQLite < 3.35). Acceptance: колонка появляется/исчезает. Проверка: `rm resources/goodmood.db* && ./bin/dev`, затем `PRAGMA table_info(entries)` содержит `aggression`.

## 2. Домен

- [x] 2.1 `src/app/domains/entries.clj`: `:aggression {:optional true} [:maybe [:int {:min 0 :max 10}]]` в `create-entry-schema` и `update-entry-schema`; `:aggression` в `updatable-fields`; проброс в `create-entry` (destructuring + `:aggression` в map). Acceptance: create с aggression=7 проходит, 15 — отклоняется. Проверка (REPL): `(m/validate app.domains.entries/create-entry-schema {:mood_score 5 :energy 5 :anxiety 5 :aggression 15})` → ошибки.
- [x] 2.2 Тесты `test/app/routes/app_test.clj` и `test/app/db/entries_test.clj`: create с aggression, create без aggression (nil), out-of-range 15 → 400/отказ. Проверка: `clojure -M:test` (или принятая в проекте команда тестов).

## 3. БД

- [x] 3.1 `src/app/db/entries.clj/create-entry!`: добавить `:aggression` в destructuring и в values-map (`:aggression aggression`). Acceptance: `create-entry!` с aggression персистит значение, `get-entries` его возвращает. Проверка (REPL): создать запись, прочитать обратно.

## 4. UI

- [x] 4.1 `src/app/views/layout.clj`: в `:root` добавить `--gm-metric-aggression: #c0407f;` рядом с `--gm-metric-energy/anxiety/focus`. Acceptance: переменная присутствует в отрендеренном `<style>`. Проверка: открыть любую страницу, найти переменную в исходнике.
- [x] 4.2 `src/app/views/check_in.clj`: в `soft-fields` после focus добавить range-слайдер aggression с краевыми подписями (i18n `:entries/aggression-left`/`:entries/aggression-right`); расширить `range-field` опциональным map для якорных подписей (по умолчанию «0»/«10»). Acceptance: слайдер виден, подписи «Добрячок»/«Мудак». Проверка: Playwright/`browser_navigate` на `/check-in`.
- [x] 4.3 `src/app/views/entries.clj`: в `edit-form` добавить range-field aggression (после focus, предзаполнение `:aggression`); в `axes-line` включить агрессию. Acceptance: read-строка осей и edit-слайдер показывают агрессию. Проверка: открыть `/entries/:id`.
- [x] 4.4 `src/app/views/feed.clj/entry-card`: добавить четвёртый чип `(metric-chip :entries/aggression "hand-raised" "--gm-metric-aggression" (:aggression entry))`, сетку `grid-cols-3` → `grid-cols-2`. Acceptance: в ленте 4 чипа в 2×2, отсутствующая агрессия — «–». Проверка: открыть `/feed`.

## 5. i18n

- [x] 5.1 `resources/i18n/ru.edn` и `resources/i18n/en.edn`: `:entries/aggression` («Агрессия»/«Aggression»), `:entries/aggression-left` («Добрячок»/«Sweetheart»), `:entries/aggression-right` («Мудак»/«Jerk»). Acceptance: оба файла в синхроне. Проверка: REPL `(binding [app.i18n/*locale* :en] (app.i18n/t :entries/aggression))`.

## 6. Проверки

- [x] 6.1 REPL-гипотезы на живом system: `create-entry` с aggression, out-of-range, `state-label` не зависит от aggression. Проверка: `app.domains.entries/state-label` с aggression=10 даёт прежний результат.
- [x] 6.2 e2e `cd e2e && npm run test:fast` — существующие спеки проходят (лента, check-in, entries-crud). Acceptance: зелёный прогон.
- [x] 6.3 Ручная проверка Playwright: слайдер с якорями на /check-in, чип в /feed (2×2), поле/строка в /entries/:id. Acceptance: UI совпадает со спеками.

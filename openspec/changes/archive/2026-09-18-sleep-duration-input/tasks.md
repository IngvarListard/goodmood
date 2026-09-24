# Tasks: sleep-duration-input

## 1. Домен и конверсия

- [x] 1.1 `domains/entries.clj`: `sleep_minutes` в `create-entry-schema` и `update-entry-schema` (`[:maybe [:int {:min 0 :max 59}]]`), конверсия h+m → десятичные часы в `create-entry` и `update-entry` (D3)
- [x] 1.2 Тесты `test/app/routes/app_test.clj` + `test/app/db/entries_test.clj`: кейсы 7+30→7.5; одиночный 8.0→8.0; оба пусты→nil; 0+30→0.5

## 2. Дисплей

- [x] 2.1 `app.views.layout`: публичный `format-sleep` (D4); i18n-ключи `:entries/h-unit`/`:entries/m-unit` в `resources/i18n/{ru,en}.edn`, label «Сон (часы)» → «Сон»
- [x] 2.2 Свап дисплея: `views/feed.clj` (карточка ленты), `views/entries.clj` item-фрагмент + read-view — «7ч30м» вместо `(format "%.1f ч")`

## 3. Ввод

- [x] 3.1 `views/check_in.clj`: sleep-блок — join из `input[name=sleep_hours]` (number 0–24) + `select[name=sleep_minutes]` (0/15/30/45); расширить hyperscript-селектор тоггла до `<input, select, textarea/>` (D6)
- [x] 3.2 `views/entries.clj` edit-форма: те же join-поля + prefill через split с округлением минут к 15 (D5)

## 4. Проверки

- [x] 4.1 REPL-проверка: `(format-sleep 7.5)` → «7ч30м», `(format-sleep 7.2)` → «7ч12м», en-локаль
- [x] 4.2 e2e `helpers.ts`: submitEntry заполняет ч+мин (снять disabled с обоих); прогон `npm run test:fast`
- [x] 4.3 Ручная проверка в Playwright MCP: чек-ин (раскрыть блок, 7ч30м → сохранить → в feed «7ч30м»), edit-карточка (prefill, очистка), мягкий режим не задет

> 4.3 выполнена прогоном Playwright Test (Chromium): feed-спека «sleep 7h30m → 7ч30м»,
> entries-crud (edit-карточка с новыми join-полями), soft-mode в test:fast.
> MCP-браузер в сессии недоступен — заменено эквивалентным e2e-прогоном.

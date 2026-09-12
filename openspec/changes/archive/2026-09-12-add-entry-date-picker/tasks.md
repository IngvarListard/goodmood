## 1. Домен и БД

- [x] 1.1 `app.db.state-periods`: добавить `get-period-covering-date` (`date(started_at) <= ? AND (ended_at IS NULL OR date(ended_at) >= ?)`, ограничение user_id, LIMIT 1)
- [x] 1.2 `app.domains.entries`: `:date` в `create-entry-schema` (regex + границы [today−30; today], константа `max-backfill-days` 30); в `create-entry` — `(or date (today))` и выбор периода через `get-period-covering-date` с fallback на `get-active-period-id`
- [x] 1.3 Проверить в REPL: валидация границ (будущее / старше 30 / malformed), привязка периода по дате на dev-БД

## 2. Route и форма

- [x] 2.1 `app.routes.entries/create-entry`: пробросить `:date` из body-параметров в домен
- [x] 2.2 `app.views.check-in`: collapse-блок «Дата» с нативным `<input type="date">` (min/max/default = серверная today, unchecked → disabled), реюз `optional-block`; i18n-ключи `entries/date-*` в ru/en
- [x] 2.3 Ошибка валидации даты отображается в `#form-error` (как остальные ошибки формы)

## 3. Проверка

- [x] 3.1 Playwright: бэкфилл вчерашней записи — свап, редирект на /feed, запись под вчерашним днём; дефолтный флоу без блока не сломан; future/min/max в пикере
- [x] 3.2 Глазами: away-баннер и soft-mode при бэкфилле ведут себя приемлемо (по `date DESC, created_at DESC`)
- [x] 3.3 e2e: расширить smoke-тест чек-ина сценарием бэкфилла; `npm run test:fast` зелёный

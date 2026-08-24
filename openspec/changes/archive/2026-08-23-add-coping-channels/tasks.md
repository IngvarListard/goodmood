## 1. Миграция 008 — таблица user_notification_settings

- [x] 1.1 Создать `resources/migrations/008-add-notification-settings.up.sql`: `CREATE TABLE user_notification_settings` (user_id FK users, slot CHECK IN morning/midday/evening, enabled, time, last_summary_date, last_slot_shown, PRIMARY KEY (user_id, slot))
- [x] 1.2 Создать `resources/migrations/008-add-notification-settings.down.sql`: `DROP TABLE user_notification_settings`
- [x] 1.3 Применить миграцию, проверить схему через `sqlite3` (таблица + CHECK + PK существуют)
- [x] 1.4 Откатить миграцию (down), проверить что остальные таблицы сохранены; применить обратно

## 2. БД-слой — app.db.notification-settings

- [x] 2.1 Создать `src/app/db/notification_settings.clj`
- [x] 2.2 Реализовать `get-or-default` (upsert-on-read: создаёт 3 строки по умолчанию при первом обращении)
- [x] 2.3 Реализовать `upsert!` (обновление трёх слотов: enabled, time)
- [x] 2.4 Реализовать `set-summary-shown!` (обновляет last_summary_date для слота evening)
- [x] 2.5 Реализовать `set-slot-shown!` (обновляет last_slot_shown для конкретного слота)
- [x] 2.6 Тесты БД-слоя (default-seed, upsert, sentinel-поля)

## 3. Домен — app.domains.notification-settings

- [x] 3.1 Создать `src/app/domains/notification_settings.clj`
- [x] 3.2 Реализовать malli-схему параметров (три слота: enabled + time)
- [x] 3.3 Реализовать `get-settings` (возвращает 3 слота с enabled/time)
- [x] 3.4 Реализовать `update-settings` (валидация + upsert)
- [x] 3.5 Реализовать `mark-summary-shown` и `mark-slot-shown` (обёртки над БД-слоем)
- [x] 3.6 Тесты домена (валидация времени, дефолтные значения)

## 4. Роуты — app.routes.notifications + интеграция

- [x] 4.1 Создать `src/app/routes/notifications.clj`: handlers `pending-insight` (GET /feed/pending-insight, фрагмент инсайта + баннер «пока тебя не было»), `summary-dismiss` (POST /notifications/summary-dismiss), `settings-update` (POST /settings/notifications)
- [x] 4.2 Подключить `app.routes.notifications` в `src/app/routes/app.clj`, добавить роуты `/feed/pending-insight`, `/notifications/summary-dismiss`, `/settings/notifications`
- [x] 4.3 В `src/app/routes/check_in.clj`: после успешного создания записи вычислять `state_label` (entries/state-label) и подбирать `insights/matching-insight`; при наличии — включить OOB-toast в ответ
- [x] 4.4 В `src/app/routes/feed.clj`: вычислять контекст вечерней сводки (≥18:00 + записи сегодня + last_summary_date != today) и передавать во вьюху
- [x] 4.5 Тесты роутов (pending-insight, summary-dismiss ставит sentinel, settings-update валидирует)

## 5. Вьюхи — компоненты Фазы 4

- [x] 5.1 Создать `src/app/views/notifications.clj` с фрагментами: OOB-toast «В таком состоянии тебе помогало», баннер вечерней сводки (свёрнутый/раскрытый), баннер «пока тебя не было», секция настроек слотов, статус «Сохранено»
- [x] 5.2 Реализовать OOB-toast: `alert-info`, заголовок + context (1 строка) + первый advice + ссылка `/insights/:id` + dismissible `×`
- [x] 5.3 Реализовать баннер вечерней сводки: `alert alert-info`, строка «Сводка на завтра готова», кнопки «посмотреть»/«×», раскрытие по тапу, облегчённый вариант без инсайта
- [x] 5.4 Реализовать секцию настроек в `src/app/views/settings.clj`: три слота (Утро/День/Вечер), time-input + toggle, кнопка «Сохранить», статус-фрагмент
- [x] 5.5 Реализовать soft mode в `src/app/views/check_in.clj`: баннер предложения + toggle «мягкий режим»/«полная форма» через hyperscript (скрыть #soft-targets, показать #mood-only)
- [x] 5.6 Реализовать подъём виджета выше hero в `src/app/views/feed.clj` для `low`/`mixed`
- [x] 5.7 Проверить daisyui-классы и htmx-атрибуты в мапе параметров (Hiccup v2)

## 6. Notification API через hyperscript

- [x] 6.1 В `src/app/views/notifications.clj` (или feed.clj): hyperscript `js` для `Notification.requestPermission` при первом слоте
- [x] 6.2 Если разрешение не получено — показать только in-app toast (graceful fallback), без падения
- [x] 6.3 Слушать слоты на /feed: hyperscript проверяет `LocalTime.now` против настроек и показывает toast
- [x] 6.4 Убедиться что нет сырого JS-файла — только hyperscript `_`-атрибуты (js-interop)

## 7. i18n

- [x] 7.1 Добавить ключи: `:notifications/title`, `:notifications/subtitle`, `:notifications/morning`, `:notifications/midday`, `:notifications/evening`, `:notifications/save`, `:notifications/saved`, `:notifications/time`, `:notifications/enabled`, `:notifications/disabled`, `:feed/evening-summary-title`, `:feed/evening-summary-see`, `:feed/evening-summary-closing`, `:feed/away-banner`, `:checkin/soft-mode-banner`, `:checkin/soft-mode-accept`, `:checkin/soft-mode-decline`, `:toast/hint-title`
- [x] 7.2 ru и en переводы для всех ключей (поддерживающий тон, без вины/стрика)
- [x] 7.3 Проверить что новые строки локализуются через `i18n/t`

## 8. Тесты

- [x] 8.1 Юнит-тесты db/notification-settings (default-seed 3 слота, upsert, sentinel)
- [x] 8.2 Юнит-тесты domains/notification-settings (malli-валидация: невалидное время, слот вне enum)
- [x] 8.3 Интеграционные тесты роутов (pending-insight возвращает фрагмент, summary-dismiss ставит sentinel, settings-update валидирует и сохраняет, персональность)
- [x] 8.4 Тест soft mode (low → баннер; balanced → нет баннера; включение скрывает слайдеры)
- [x] 8.5 Тест вечерней сводки (≥18:00 + записи → баннер; <18:00 → нет; после dismiss → не показывать сегодня)
- [x] 8.6 Тест OOB-toast (есть инсайт → toast; нет → не рендерится)
- [x] 8.7 Тест миграции 008 (up создаёт таблицу, down удаляет, остальные таблицы сохранены)
- [x] 8.8 Все тесты проходят: `clojure -M:test` (или команда из README/AGENTS.md)

## 9. Ручной прогон и валидация

- [x] 9.1 Запустить приложение (`clj -M -m app.core`), health-check проходит
- [x] 9.2 Playwright MCP: /feed со сводкой после 18:00 → баннер «Сводка на завтра готова», раскрытие, dismiss
- [x] 9.3 Playwright: /check-in в low/mixed → баннер мягкого режима; toggle скрывает энергия/тревога/optional
- [x] 9.4 Playwright: сохранение записи → редирект на /feed + OOB-toast (если есть инсайт)
- [x] 9.5 Playwright: /settings → три слота, смена времени, сохранение, alert «Сохранено»
- [x] 9.6 Playwright: Notification API — запрос разрешения; при отказе — in-app toast
- [x] 9.7 `openspec validate --change add-coping-channels` — структура валидна
- [x] 9.8 `openspec status --change add-coping-channels` — все задачи выполнены
- [x] 9.9 `openspec archive --change add-coping-channels` → delta-спеки синхронизированы в `openspec/specs/`
- [x] 9.10 Отметить Фазу 4 ✅ в `product-vision/tasks.md`; обновить `product-vision/design.md` Decisions 14–16 если гипотезы уточнились
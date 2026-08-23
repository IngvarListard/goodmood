## 1. Миграция 007 — таблица insights

- [x] 1.1 Создать `resources/migrations/007-add-insights.up.sql`: `CREATE TABLE insights` (id, user_id FK users, context NOT NULL, category NOT NULL CHECK IN, advice_to_self NOT NULL, identity, state_label, entry_id, created_at, updated_at) + `CREATE INDEX idx_insights_user_state`
- [x] 1.2 Создать `resources/migrations/007-add-insights.down.sql`: `DROP TABLE insights`
- [x] 1.3 Применить миграцию (`clj -M -m app.core` или migratus напрямую), проверить схему через `sqlite3` (таблица + индекс существуют)
- [x] 1.4 Откатить миграцию (down), проверить что `entries` и другие таблицы сохранены; применить обратно

## 2. БД-слой — app.db.insights

- [x] 2.1 Создать `src/app/db/insights.clj`
- [x] 2.2 Реализовать `create-insight!`
- [x] 2.3 Реализовать `get-insight`
- [x] 2.4 Реализовать `get-insights`
- [x] 2.5 Реализовать `get-matching-insights`
- [x] 2.6 Реализовать `update-insight-field!`
- [x] 2.7 Реализовать `delete-insight!`
- [x] 2.8 Тесты БД-слоя

## 3. Домен — app.domains.insights

- [x] 3.1 Создать `src/app/domains/insights.clj`
- [x] 3.2 Реализовать `create-insight`
- [x] 3.3 Реализовать `list-insights`
- [x] 3.4 Реализовать `matching-insights`
- [x] 3.5 Реализовать `update-field`
- [x] 3.6 Реализовать `delete-insight`
- [x] 3.7 Тесты домена

## 4. Роуты — app.routes.insights

- [x] 4.1 Создать `src/app/routes/insights.clj` с handlers: `page` (GET /insights, список + фильтр), `new-page` (GET /insights/new), `show` (GET /insights/:id), `create` (POST /insights, hx-post json-enc), `edit-context` (GET /insights/:id/edit-context), `edit-advice`, `edit-identity`, `edit-category`, `update-field` (POST /insights/:id/<field>), `delete` (DELETE /insights/:id), `advice-item` (GET /insights/advice-item, фрагмент)
- [x] 4.2 Подключить `app.routes.insights` в `src/app/routes/app.clj`, добавить роуты `/insights*` в `router`
- [x] 4.3 Реализовать `page` handler: список инсайтов с htmx-фильтром по category (query param), empty-states (онбординг / per-category)
- [x] 4.4 Реализовать `new-page`: предзаполнение из query params (entry_id, state_label), context pre-seed из последней записи
- [x] 4.5 Реализовать `create`: валидация, сохранение, при успехе — HX-Redirect на /insights, при ошибке — validation-error-fragment в #form-error
- [x] 4.6 Реализовать `show`: полный вид инсайта (4 карточки)
- [x] 4.7 Реализовать edit-фрагменты (context, advice, identity, category): GET возвращает form-фрагмент, POST сохраняет и свапает обратно на read-блок
- [x] 4.8 Реализовать `delete`: DELETE, редирект на /insights
- [x] 4.9 Реализовать `advice-item`: GET возвращает фрагмент нового пункта (htmx beforeend)
- [x] 4.10 Тесты роутов (создание из /feed, standalone, in-place edit, удаление, персональность — чужой user не видит)

## 5. Вьюхи — app.views.insights

- [x] 5.1 Создать `src/app/views/insights.clj`
- [x] 5.2 Реализовать `list-page`
- [x] 5.3 Реализовать `new-page`
- [x] 5.4 Реализовать `show-page`
- [x] 5.5 Реализовать фрагменты
- [x] 5.6 Реализовать `feed-widget`
- [x] 5.7 Реализовать `onboarding-fragment`

## 6. Интеграция в /feed

- [x] 6.1 В `src/app/views/feed.clj` добавить вызов виджета инсайтов в `today-section` после `hero-card`, до compact-cards
- [x] 6.2 В `src/app/routes/feed.clj` (или handler) добавить загрузку matching-insights по state_label последней записи сегодня
- [x] 6.3 Передача `state_label` и `entry_id` в виджет; если нет записей сегодня — виджет не рендерится
- [x] 6.4 Ручной прогон: /feed с инсайтом → виджет; /feed без инсайта → онбординг; /feed без записей → нет виджета

## 7. Навигация

- [x] 7.1 В `src/app/views/navigation.clj` добавить пункт `:insights` в `nav-items` (label-key `:nav/insights`, иконка из `app.icons`, route `/insights`)
- [x] 7.2 Проверить что 5 пунктов корректно рендерятся в mobile bottom-nav и desktop sidebar
- [x] 7.3 Активный пункт на /insights* подсвечивается (учитать `:active :insights` в layout)

## 8. i18n

- [x] 8.1 Добавить ключи `:nav/insights`, `:insights/title`, `:insights/new`, `:insights/subtitle`, `:insights/context-label`, `:insights/advice-label`, `:insights/identity-label`, `:insights/category-productivity`, `:insights/category-coping`, `:insights/category-identity`, `:insights/category-general`, `:insights/state-mixed`, `:insights/state-anxiety`, `:insights/state-elevated`, `:insights/state-low`, `:insights/state-balanced`, `:insights/state-neutral`, `:insights/save`, `:insights/cancel`, `:insights/edit`, `:insights/delete`, `:insights/delete-confirm`, `:insights/delete-confirm-text`, `:insights/add-advice`, `:insights/empty-global`, `:insights/empty-category`, `:insights/onboarding-prefix`, `:insights/onboarding-create`, `:insights/widget-title`, `:insights/widget-record`, `:insights/more-advice`
- [x] 8.2 ru и en переводы для всех ключей (поддерживающий тон, без клинического языка)
- [x] 8.3 Проверить что бейджи категорий и state_label локализуются через `i18n/t`

## 9. Тесты

- [x] 9.1 Юнит-тесты db/insights (create, get, matching, update-field, delete, dangling entry_id)
- [x] 9.2 Юнит-тесты domains/insights (malli-валидация: пустой advice, невалидный category, нормализация state_label)
- [x] 9.3 Интеграционные тесты роутов (создание из /feed со state_label+entry_id, standalone, in-place edit context/advice/identity, удаление с подтверждением, персональность — user A не видит инсайты user B)
- [x] 9.4 Тест виджета на /feed (есть match → карточка, нет match → онбординг, нет записей → нет виджета)
- [x] 9.5 Тест миграции 007 (up создаёт таблицу+индекс, down удаляет, entries сохранены)
- [x] 9.6 Все тесты проходят: `clojure -M:test` (или команда из README/AGENTS.md)

## 10. Ручной прогон и валидация

- [x] 10.1 Запустить приложение (`clj -M -m app.core`), health-check проходит
- [x] 10.2 Playwright MCP: /feed с записью anxiety + исторический insight → виджет показывает инсайт
- [x] 10.3 Playwright: /feed без инсайта для текущего state → онбординг с кнопкой
- [x] 10.4 Playwright: /insights → список, фильтр-табы работают (htmx swap)
- [x] 10.5 Playwright: /insights/new → форма, добавление advice-пунктов, сохранение, редирект
- [x] 10.6 Playwright: /insights/:id → in-place edit context/advice/identity, state_label не редактируется
- [x] 10.7 Playwright: удаление → modal confirm → редирект на /insights
- [x] 10.8 `openspec validate --change add-insights-artefact` — структура валидна
- [x] 10.9 `openspec status --change add-insights-artefact` — все задачи выполнены
- [x] 10.10 `openspec archive --change add-insights-artefact` → delta-спеки синхронизированы в `openspec/specs/`
- [x] 10.11 Отметить Фазу 3 ✅ в `product-vision/tasks.md`; обновить `product-vision/design.md` Decision 13 если гипотезы уточнились

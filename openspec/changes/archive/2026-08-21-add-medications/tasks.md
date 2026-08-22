## 1. Миграция БД

- [x] 1.1 Создать `resources/migrations/005-add-medications.up.sql`: CREATE TABLE medications, medication_logs, medication_dose_changes + INDEX на medication_logs(user_id, log_date), `--;;` разделители
- [x] 1.2 Создать `resources/migrations/005-add-medications.down.sql`: DROP TABLE в обратном порядке (dose_changes → logs → medications), `--;;` разделители
- [x] 1.3 Применить миграцию, проверить что 3 таблицы существуют через `SELECT sql FROM sqlite_master WHERE name IN ('medications','medication_logs','medication_dose_changes')`

## 2. DB-слой

- [x] 2.1 Создать `src/app/db/medications.clj`: `create-medication!`, `get-medication`, `get-medications-by-user`, `update-medication!`, `deactivate-medication!`, `activate-medication!` (HoneySQL, next.jdbc, kebab↔snake)
- [x] 2.2 Создать `medication-log` функции: `upsert-log!` (INSERT ON CONFLICT UPDATE или delete-then-insert для UNIQUE constraint), `get-logs-by-date`, `delete-log!`
- [x] 2.3 Создать `dose-change` функции: `create-dose-change!`, `get-dose-changes`
- [x] 2.4 Расписание: helper `parse-schedule` (JSON-строка → seq строк) и `serialize-schedule` (seq → JSON-строка)

## 3. Domain-слой

- [x] 3.1 Создать `src/app/domains/medications.clj`: malli-схема `medication-schema` (name str required, dose number required, dose_unit str required, schedule str required, sensitive int optional, notes str optional), `log-schema` (medication_id int, log_date str, scheduled_time str, status str)
- [x] 3.2 Реализовать `create-medication`, `update-medication` (с dose-change tracking: если доза изменилась → `create-dose-change!`), `list-medications` (active + inactive), `deactivate`, `activate`
- [x] 3.3 Реализовать `log-intake` (upsert лога), `cancel-intake` (delete), `get-today-slots` (активные медикаменты + их schedule → слоты на сегодня)
- [x] 3.4 `updated_at` устанавливается в domain при каждом UPDATE

## 4. Routes

- [x] 4.1 Создать `src/app/routes/medications.clj`: `GET /medications` (HTML страница), `GET /medications/new` (модалка), `POST /medications` (создание), `GET /medications/:id/edit` (модалка), `POST /medications/:id` (обновление), `POST /medications/:id/deactivate`, `POST /medications/:id/activate`, `POST /medications/:id/log` (отметка приёма)
- [x] 4.2 Подключить роуты в `src/app/routes/app.clj` (router)
- [x] 4.3 Проверить CSRF: все POST-формы содержат скрытое поле `__anti-forgery-token`, `read-csrf-token` читает `:body-params`

## 5. Views

- [x] 5.1 Создать `src/app/views/medications.clj`: `page` (реестр + виджет приёма + collapse неактивных + empty state + модалка)
- [x] 5.2 Реализовать `med-card` (карточка медикамента: name, dose+unit, schedule, sensitive badge, кнопки edit/deactivate)
- [x] 5.3 Реализовать `intake-widget` (слоты на сегодня: кнопки «Принял»/«Пропустил», бейджи статусов, «Отменить»)
- [x] 5.4 Реализовать `medication-form` (модалка: name, dose+dose_unit, schedule text, sensitive toggle, notes textarea)
- [x] 5.5 Реализовать `inactive-section` (collapse неактивных медикаментов)
- [x] 5.6 Реализовать `empty-state` (онбординг-сообщение + кнопка)
- [x] 5.7 HTMX: `hx-post` на формы, `hx-target` на `#med-list` или слот, `hx-swap="outerHTML"`, `hx-confirm` на деактивацию, `hx-get` на модалку
- [x] 5.8 Все touch-targets ≥44px (h-11 min-h-11 на кнопках)

## 6. Навигация

- [x] 6.1 Добавить пункт «Медикаменты» в `src/app/views/navigation.clj` (nav-items), иконку из `resources/icons/` (pill/medicine)
- [x] 6.2 Проверить что пункт виден в desktop sidebar и mobile bottom bar

## 7. I18n

- [x] 7.1 Добавить в `resources/i18n/ru.edn` ключи: `:medications/title` → "Медикаменты", `:medications/add` → "Добавить", `:medications/description` → "Препараты, которые вы принимаете", `:medications/today` → "Сегодня", `:medications/taken` → "принял", `:medications/skipped` → "пропустил", `:medications/cancel-log` → "Отменить", `:medications/deactivate` → "Деактивировать", `:medications/activate` → "Активировать", `:medications/sensitive` → "sensitive", `:medications/sensitive-label` → "Особо чувствительный", `:medications/inactive` → "Неактивные", `:medications/name` → "Название", `:medications/dose` → "Доза", `:medications/dose-unit` → "Единица", `:medications/schedule` → "Расписание", `:medications/schedule-hint` → "Время приёма через запятую", `:medications/notes` → "Заметки", `:medications/save` → "Сохранить", `:medications/cancel` → "Отмена", `:medications/new` → "Новый медикамент", `:medications/edit` → "Редактировать", `:medications/empty-title` → "Здесь будут ваши медикаменты", `:medications/empty-desc` → "Добавьте препараты, которые вы принимаете, чтобы отслеживать приём", `:medications/empty-add` → "Добавить медикамент", `:medications/deactivate-confirm` → "Деактивировать {name}? История приёма сохранится."
- [x] 7.2 Добавить в `resources/i18n/en.edn` английские переводы

## 8. Тесты

- [x] 8.1 Создать `test/app/db/medications_test.clj`: тест создания медикамента, получения по пользователю, обновления (с dose-change), деактивации/активации, лога приёма (upsert, delete), UNIQUE constraint
- [x] 8.2 Создать `test/app/routes/medications_test.clj` (или обновить app_test): GET /medications (200 HTML), POST /medications (201), POST /medications/:id/log (201), деактивация, форма валидация (missing name → 400 alert-warning)
- [x] 8.3 Запустить все тесты: `clj -M:test` → все проходят

## 9. Ручная проверка

- [x] 9.1 Запустить приложение, открыть `/medications` на мобилке — empty state отображается
- [x] 9.2 Добавить медикамент через модалку — появляется в списке
- [x] 9.3 Отметить приём «Принял» — бейдж «принял», повторный тап — обновление, не дубль
- [x] 9.4 Отметить «Пропустил» — бейдж «пропустил» (жёлтый, не красный)
- [x] 9.5 «Отменить» — слот возвращается в «не отмечен»
- [x] 9.6 Изменить дозу медикамента — проверить `medication_dose_changes` через SQL
- [x] 9.7 Деактивировать медикамент — перемещается в collapse «Неактивные»
- [x] 9.8 Активировать обратно — возвращается в активные
- [x] 9.9 Sensitive toggle — бейдж «sensitive» на карточке
- [x] 9.10 Проверить тёмную тему на `/medications`
- [x] 9.11 Проверить пункт «Медикаменты» в навигации (desktop + mobile)
- [x] 9.12 Нет стриков/геймификации

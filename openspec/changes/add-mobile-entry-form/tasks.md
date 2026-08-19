## 1. Миграция БД

- [x] 1.1 Создать `resources/migrations/004-add-mobile-form-fields.up.sql`: ALTER TABLE entries ADD COLUMN energy INTEGER, ADD COLUMN anxiety INTEGER, ADD COLUMN focus INTEGER, ADD COLUMN note TEXT, ADD COLUMN template TEXT
- [x] 1.2 Создать `resources/migrations/004-add-mobile-form-fields.down.sql`: обратные ALTER TABLE DROP COLUMN
- [x] 1.3 Применить миграцию: `clj -M -m migratus.core migrate` (или эквивалент), проверить что колонки появились: `sqlite3 resources/goodmood.db ".schema entries"`

## 2. DB-слой

- [x] 2.1 Обновить `create-entry!` в `src/app/db/entries.clj`: принимать новые поля `energy`, `anxiety`, `focus`, `note`, `activity`, `effect`, `template`; маппить snake_case ↔ kebab-case
- [x] 2.2 Убедиться что `get-entries` возвращает все новые колонки (SELECT * покрывает)

## 3. Domain-слой

- [x] 3.1 Обновить `create-entry-schema` в `src/app/domains/entries.clj`: mandatory `mood_score` [:int {:min 0 :max 10}], `energy` [:int {:min 0 :max 10}], `anxiety` [:int {:min 0 :max 10}]; optional `focus` [:maybe [:int {:min 0 :max 10}]], `sleep_hours` [:maybe :double], `note` [:maybe :string], `activity` [:maybe :string], `effect` [:maybe :string], `template` [:maybe :string]
- [x] 3.2 Обновить `create-entry` в `src/app/domains/entries.clj`: деструктурировать все новые поля, передавать в `db/create-entry!`
- [x] 3.3 Проверить через REPL: `(require '[app.domains.entries :as d] :reload)`, `(mc/validate d/create-entry-schema {:mood_score 5 :energy 5 :anxiety 5})` → true

## 4. Routes

- [x] 4.1 Обновить `create-entry` handler в `src/app/routes/entries.clj`: деструктурировать `energy`, `anxiety`, `focus`, `note`, `template` из `(:parameters :body request)`, передавать в `entries/create-entry`
- [x] 4.2 Проверить что `POST /entries` с valid core fields возвращает 201 и HTML-фрагмент (для htmx)
- [x] 4.3 Проверить что `POST /entries` без `energy` возвращает 400 с alert-warning (через coercion-error-middleware)

## 5. Views — форма

- [x] 5.1 Заменить `form` в `src/app/views/entries.clj`: новая Hiccup-структура с тремя range-слайдерами (mood_score range-primary, energy range-success, anxiety range-warning), template-табами (DaisyUI tabs-boxed), опциональными collapse-блоками
- [x] 5.2 Добавить Hyperscript на range-слайдерах для отображения текущего значения: `_ "on input put my value into #<field>-value"`
- [x] 5.3 Добавить Hyperscript на template-табах: `_ "on click remove .tab-active from .tab then add .tab-active to me"` + установка значения скрытого поля `template`
- [x] 5.4 Опциональные блоки: каждый — `collapse collapse-arrow bg-base-300/50`, внутри input/textarea/range по типу поля
- [x] 5.5 Форма отправляет через `hx-post="/entries"`, `hx-ext="json-enc"`, `hx-target="#entries-list"`, `hx-swap="afterbegin"`, сброс через `_ "on htmx:afterRequest if event.detail.successful me.reset()"`
- [x] 5.6 Секция «Дополнительно (необязательно)» с разделителем `border-t border-base-300`

## 6. Views — карточка записи (item)

- [x] 6.1 Обновить `item` в `src/app/views/entries.clj`: отображать mood_score как число/10, energy и anxiety в одной строке; опционально sleep_hours, note, activity если не nil
- [x] 6.2 Карточка использует `card bg-base-200 p-4` и `hx-swap-oob` для вставки в список

## 7. Views — error fragment

- [x] 7.1 Заменить `alert-error` на `alert-warning` в `error-fragment`
- [x] 7.2 Проверить что ошибка валидации рендерится с жёлтым/янтарным фоном, не красным

## 8. Layout — тёмная тема

- [x] 8.1 В `src/app/views/layout.clj` заменить `data-theme "light"` на `data-theme "dark"`
- [ ] 8.2 Визуально проверить: все страницы (entries, placeholder, settings, login) корректно выглядят в тёмной теме

## 9. I18n

- [x] 9.1 Добавить в `resources/i18n/ru.edn` ключи: `:entries/mood` → "Настроение", `:entries/energy` → "Энергия", `:entries/anxiety` → "Тревога", `:entries/focus` → "Фокус", `:entries/note` → "Заметка", `:entries/optional` → "Дополнительно (необязательно)", `:entries/save` → "Сохранить запись", `:template/morning` → "Утро", `:template/day` → "День", `:template/evening` → "Вечер", `:template/event` → "Событие"
- [x] 9.2 Добавить в `resources/i18n/en.edn` английские переводы
- [x] 9.3 Удалить неиспользуемые ключи: `:entries/activity-label`, `:entries/effect-label` (если больше не используются в item)

## 10. Тесты

- [x] 10.1 Обновить `test/app/db/entries_test.clj`: тест создания записи с новыми полями (core only, core + optional), тест что `get-entries` возвращает новые поля
- [x] 10.2 Добавить тест валидации: обязательные поля отсутствуют → ошибка; out-of-range → ошибка
- [x] 10.3 Добавить тест: запись с только core полями создаётся, опциональные nil
- [x] 10.4 Запустить все тесты: `clj -M:test` → все проходят

## 11. Ручная проверка

- [x] 11.1 Запустить приложение: `clj -M -m app.core`
- [ ] 11.2 Открыть на мобилке (или в Chrome DevTools mobile view 375px): проверить что форма не требует горизонтального скролла
- [x] 11.3 Заполнить ядро (3 range) → отправить → запись появилась в списке
- [x] 11.4 Раскрыть опциональный блок «Сон», ввести значение, отправить → запись содержит sleep_hours
- [x] 11.5 Переключить шаблоны: значения ядра сохраняются
- [x] 11.6 Отправить без energy → alert-warning с сообщением об ошибке
- [x] 11.7 Проверить тёмную тему на всех страницах
- [x] 11.8 Проверить что нет стриков/геймификации в UI
- [ ] 11.9 Целевое время заполнения ядра ≤ 5 секунд (три движения пальца)
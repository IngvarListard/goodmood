# Tasks: add-entries-crud

## 1. Бэкенд: данные и домен

- [x] 1.1 `app.db.entries`: `update-entry!` (SET только переданных ключей, `WHERE id AND user_id`), `delete-entry!`, `get-entry` (одна запись по id+user_id)
- [x] 1.2 `app.domains.entries`: Malli-схема update (все поля опциональны, mood 0–10), `update-entry`/`delete-entry`/`get-entry` с обработкой пустых строк → NULL/очистка
- [x] 1.3 REPL-проверка: update/delete/get на dev-БД, чужой user_id → 0 строк

## 2. Роуты

- [x] 2.1 `routes/entries.clj`: `list-page` (GET /entries — полный список вместо redirect), `show-page` (GET /entries/:id), `update-entry` (POST /entries/:id), `delete-entry` (DELETE /entries/:id); регистрация в `routes/app.clj`
- [x] 2.2 Владение: unauthenticated → login, чужая запись → 404

## 3. Views

- [x] 3.1 `views/entries.clj`: страница списка — группировка по дням (переиспользовать паттерн day-header ленты), карточки с state-бейджем/временем/ядром → ссылка на карточку
- [x] 3.2 Карточка записи: read-блоки всех полей + «Править» (edit-фрагменты: ядро-слайдеры, сон, заметка, активность, state_label-select, дата) + кнопка удаления
- [x] 3.3 Confirm-модалка удаления (паттерн инсайтов: dialog, hx-delete + CSRF, редирект на /entries)
- [x] 3.4 Иконка-ссылка `/entries` в шапке ленты (`views/feed.clj`, рядом с заголовком)

## 4. i18n и мелочи

- [x] 4.1 `resources/i18n/{ru,en}.edn`: ключи страницы списка/карточки/подтверждения удаления

## 5. E2E и верификация

- [x] 5.1 `e2e/tests/entries-crud.spec.ts`: создать запись → открыть из списка → отредактировать заметку → удалить с подтверждением; чужая запись → 404 (проверка API-уровня)
- [x] 5.2 `npm run test:fast` зелёный; смоук ленты не сломан иконкой в шапке
- [x] 5.3 Мобильная проверка 412px: список/карточка в PageShell без горизонтального скролла

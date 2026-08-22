## Why

Медикаменты — объективная переменная, которую пользователь может изменить (дозу, расписание). В отличие от субъективных шкал настроения, это first-class фактор с весом выше остальных в будущем анализе. Discovery (A3-q5): «Размер дозы — это то, что можно хоть как-то объективно изменить». Без сбора данных о медикаментах Фаза 5 (AI-корреляции «доза→состояние») невозможна.

Модель данных зафиксирована в `product-vision/design.md` Decision 4а (2026-08-19): три таблицы — `medications` (реестр), `medication_logs` (ежедневный лог), `medication_dose_changes` (история доз).

## What Changes

- **Миграция 005**: три новые таблицы (`medications`, `medication_logs`, `medication_dose_changes`) с индексом на `medication_logs(user_id, log_date)`.
- **Новый домен `medications`**: реестр медикаментов (CRUD), ежедневный лог приёма (taken/skipped), отслеживание изменений дозы.
- **Новые роуты**: `/medications` (GET — список + виджет приёма), `/medications/new` (GET — форма), `/medications` (POST — создание), `/medications/:id/edit` (GET — форма), `/medications/:id` (POST — обновление), `/medications/:id/deactivate` (POST), `/medications/:id/activate` (POST), `/medications/:id/log` (POST — отметка приёма).
- **Новый view `medications`**: страница реестра с виджетом сегодняшнего приёма, модалка формы, empty state, collapse неактивных.
- **Навигация**: пункт «Медикаменты» в sidebar (desktop) и bottom bar (mobile).
- **UI-скетчи**: зафиксированы в `product-vision/ui-sketches/medications-registry.clj` и `medications-intake-widget.clj`.
- **CSRF**: скрытое поле `__anti-forgery-token` в формах (как в Фазе 0, `read-csrf-token` в `app.middleware`).
- **Тёмная тема**: `data-theme="dark"` (унаследовано от layout).

## Capabilities

### New Capabilities

- `medications`: реестр медикаментов (CRUD), ежедневный лог приёма (taken/skipped), отслеживание изменений дозы, sensitive-флаг, mobile-first UI с DaisyUI collapses/modals.

### Modified Capabilities

- `entries-data`: без изменений схемы `entries`. Связь `medication_logs`↔`entries` — по дате (`log_date = entries.date`), без FK. Аналитический join — в Фазе 5.
- `navigation`: новый пункт «Медикаменты» в обоих вариантах навигации (desktop sidebar + mobile bottom bar).

## Scope

- **In scope:** миграция 005 (3 таблицы + индекс), db-layer (HoneySQL/next.jdbc), domain-layer (malli-схемы), routes (CRUD + лог), views (реестр + виджет приёма + модалка + empty state), навигация, i18n (ru/en), тесты.
- **Out of scope:** AI-корреляции «доза→состояние» (Фаза 5), medication-aware insight matching (Фаза 5), reminders/push-уведомления (Фаза 4), авто-определение пропусков (Фаза 4), `delayed` статус авто-определение (Фаза 2), time-window join logs↔entries (Фаза 2), sensitive enforcement в экспорте (future, OQ4), сложные расписания через день/weekly (future — JSON-структура позволяет без миграции).

## Non-goals

- **AI-аналитика** — данные собираются в Фазе 1, корреляции в Фазе 5.
- **Reminders / push** — Фаза 4 (coping-channels).
- **Экспорт / отчёт врачу** — Non-goal из umbrella-change (OQ4 — граница вмешательства).
- **Стрики / геймификация** — запрещены.

## Impact

- **Миграция:** `resources/migrations/005-add-medications.{up,down}.sql` — 3 таблицы, индекс, `--;;` разделители.
- **Новые файлы:** `src/app/db/medications.clj`, `src/app/domains/medications.clj`, `src/app/routes/medications.clj`, `src/app/views/medications.clj`.
- **Обновляемые файлы:** `src/app/routes/app.clj` (новые роуты), `src/app/views/navigation.clj` (новый пункт), `resources/i18n/{ru,en}.edn` (ключи медикаментов).
- **Спецификации:** delta `specs/medications/spec.md` (ADDED + MODIFIED), delta `specs/entries-data/spec.md` (без изменений схемы — связь по дате описана в design.md).

## Acceptance Criteria

- GIVEN миграция 005 применена WHEN `SELECT sql FROM sqlite_master WHERE name='medications'` THEN таблица существует с колонками `id, user_id, name, dose, dose_unit, schedule, active, sensitive, notes, created_at, updated_at`
- GIVEN у пользователя нет медикаментов WHEN он открывает `/medications` THEN отображается empty-state с кнопкой «Добавить» `[ref: A3-q5]`
- GIVEN пользователь заполняет форму (name, dose, dose_unit, schedule) WHEN отправляет THEN медикамент сохраняется и появляется в списке `[ref: A3-q5]`
- GIVEN у медикамента расписание "08:00, 20:00" WHEN открывается виджет приёма THEN отображаются два слота с кнопками «Принял» / «Пропустил» `[ref: A3-q5]`
- GIVEN пользователь нажимает «Принял» WHEN сервер обрабатывает POST THEN в `medication_logs` создаётся запись со `status='taken'` и `taken_at=now()` `[ref: A3-q5]`
- GIVEN пользователь меняет дозу с 300 на 450 WHEN обновляет медикамент THEN в `medication_dose_changes` создаётся запись с `previous_dose=300, new_dose=450` `[ref: A3-q5, A1-q2]`
- GIVEN медикамент деактивирован WHEN открывается `/medications` THEN он свёрнут в collapse «Неактивные» `[ref: A3-q5]`
- GIVEN все тесты запущены WHEN `clj -M:test` THEN все тесты проходят

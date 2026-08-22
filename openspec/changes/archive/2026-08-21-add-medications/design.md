## Context

Продукт-вижн (Decision 4, Decision 4а) зафиксировал медикаменты как first-class фактор с моделью из трёх таблиц. UI-скетчи сохранены в `product-vision/ui-sketches/medications-registry.clj` и `medications-intake-widget.clj`. Стек: Clojure, hiccup2, htmx, hyperscript, DaisyUI, SQLite, next.jdbc, HoneySQL, migratus. CSRF — через скрытое поле `__anti-forgery-token` + `read-csrf-token` из `app.middleware` (как в Фазе 0).

## Goals / Non-Goals

**Goals:**
- Создать модель данных медикаментов (3 таблицы, миграция 005)
- Реализовать CRUD реестра медикаментов
- Реализовать ежедневный лог приёма (taken/skipped)
- Реализовать отслеживание изменений дозы
- Mobile-first UI с DaisyUI modals/collapses, тёмная тема
- Интегрировать в навигацию

**Non-Goals:**
- AI-корреляции, insight matching (Фаза 5)
- Reminders/push (Фаза 4)
- Авто-определение пропусков (Фаза 4)
- `delayed` авто-статус (Фаза 2)
- Time-window join с entries (Фаза 2)
- Sensitive enforcement в экспорте (future, OQ4)

## Decisions

### 1. Три таблицы (миграция 005)

`medications` (реестр): `id, user_id (FK users), name, dose REAL, dose_unit TEXT, schedule TEXT (JSON), active INTEGER DEFAULT 1, sensitive INTEGER DEFAULT 0, notes TEXT, created_at, updated_at`.

`medication_logs` (лог приёма): `id, user_id (FK users), medication_id (FK medications), log_date TEXT (YYYY-MM-DD), scheduled_time TEXT (HH:MM), status TEXT CHECK IN ('taken','skipped','delayed'), taken_at TEXT (nullable), actual_dose REAL (nullable), notes TEXT, created_at`. `UNIQUE(medication_id, log_date, scheduled_time)`. `INDEX(user_id, log_date)`.

`medication_dose_changes` (история доз): `id, medication_id (FK), user_id (FK), previous_dose REAL, new_dose REAL, changed_at, reason TEXT (nullable)`.

`[ref: A3-q5, A1-q2]`

### 2. Расписание: JSON-массив

`schedule` — TEXT, хранит JSON `["08:00","20:00"]`. Парсится через `json_each` (SQLite) или `clojure.data.json`/`cheson` (Clojure). Выбран JSON, а не comma-separated — приложение для разных людей, расписания разнообразны; JSON даёт структуру и совместимость со сложными форматами (через день, weekly) без миграции. UI-инпут — текст "08:00, 20:00", сервер конвертирует в JSON.

### 3. Доза: два столбца

`dose REAL` + `dose_unit TEXT`. Числовая доза нужна для корреляций Фазы 5. Единица — свободный текст (мг/мл/таб/кап).

### 4. Связь logs↔entries: по дате, без FK

`medication_logs.log_date = entries.date`. Развязка: лог и запись существуют независимо. В Фазе 2 (несколько записей в день) добавится time-window join по `taken_at`.

### 5. Dose change tracking: отдельная таблица

При изменении дозы: `UPDATE medications.dose` + `INSERT INTO medication_dose_changes` (одна транзакция). `previous_dose` и `new_dose` хранятся оба — событие самодостаточно для Фазы 5 без JOIN к `medications`.

### 6. Незалогированный слот ≠ skipped

Нет строки в `medication_logs` = «неизвестно, принял или нет». `skipped` = осознанный выбор. Авто-определение пропусков — Фаза 4.

### 7. `status` — TEXT с CHECK

`CHECK (status IN ('taken','skipped','delayed'))`. В Фазе 1 UI экспонирует только `taken` и `skipped`. `delayed` зарезервирован для Фазы 2.

### 8. `actual_dose` — nullable REAL

Хранится, когда пользователь принял дозу, отличную от назначенной (тритирование, половина таблетки). В Фазе 1 — опциональное поле в форме лога, не в виджете быстрой отметки.

### 9. UI: отдельная страница `/medications`

Виджет сегодняшнего приёма (слоты с кнопками «Принял»/«Пропустил») + реестр карточками + collapse неактивных + модалка формы. На `/entries` — read-only badge (future, Фаза 2). Ввод медикаментов НЕ в форме настроения.

### 10. `active` (0/1) — деактивация без удаления

Деактивированные медикаменты сохраняются для истории (Фаза 5). В UI — свёрнуты в collapse «Неактивные».

### 11. `sensitive` (0/1) — хранится, не используется

Future-proofing для OQ4. В Clojure-логах: `name` медикаментов с `sensitive=1` не логировать.

### 12. CSRF — скрытое поле

Как в Фазе 0: `<input type="hidden" name="__anti-forgery-token">` в каждой форме. `read-csrf-token` из `app.middleware` читает `:body-params :__anti-forgery-token`. Ноль JS.

## Risks / Trade-offs

- **Расширение schedule позже** → JSON-структура позволяет добавить поля объектов (`{"time":"08:00","days":["mon","wed"]}`) без миграции. Риск: парсинг усложнится. Митигация: в Фазе 1 парсер простой (`json/read-str` → seq строк), расширение — отдельный change.
- **Sensitive-флаг пока no-op** → пользователь может отметить, но это ни на что не влияет. Митигация: задокументировано как future-proofing для OQ4; не вводит в заблуждение (UI показывает «особо чувствительный»).
- **Нет FK к entries** → нет ссылочной целостности. Митигация: персональное приложение, данные не критичны; join по дате тривиален; в Фазе 2 добавится time-window.
- **`updated_at` ручное управление** → SQLite не умеет `ON UPDATE`. Митигация: domain-layer устанавливает `updated_at` при каждом UPDATE.
- **UNIQUE constraint на логи** → повторный тап по «Принял» не создаёт дубль, а обновляет существующую запись. Митигация: handler использует `INSERT ... ON CONFLICT DO UPDATE` (SQLite upsert) или delete-then-insert.

## Migration Plan

1. Создать `005-add-medications.up.sql` с 3 CREATE TABLE + INDEX, `--;;` разделители
2. Создать `005-add-medications.down.sql` с 3 DROP TABLE, `--;;` разделители
3. Применить через migratus (автоматически при запуске приложения)
4. Rollback: DROP TABLE в обратном порядке (сначала dose_changes, потом logs, потом medications)

## Open Questions

Нет блокирующих. Модель данных зафиксирована (Decision 4а).

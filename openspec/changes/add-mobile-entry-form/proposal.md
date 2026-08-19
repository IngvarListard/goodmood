## Why

Текущая форма создания записи (`mood_score 0–10`, `activity`, `effect`, `sleep_hours`) не соответствует MVP: она не mobile-first, содержит 3 текстовых обязательных поля, требует вдумчивого ввода, и даже основатель её не заполняет. Без формы, которую пользователь реально открывает каждый день, продукт не запускается.

Решение OQ1 (product-vision, 2026-08-18) зафиксировало минимальный состав: ядро из трёх range-полей (mood_score, energy, anxiety) и опциональные блоки (focus, sleep_hours, note, activity). Шаблоны (утро/день/вечер/событие) управляют видимостью опциональных блоков. Целевое время заполнения ядра ≤ 5 секунд (три движения пальца).

## What Changes

- **BREAKING**: `activity` и `effect` исключены из обязательного ядра формы; становятся nullable в БД. `mood_score` сохраняется как обязательное range-поле 0–10.
- Добавляются обязательные поля ядра: `energy` (int 0–10), `anxiety` (int 0–10) — range-инпуты.
- Добавляются опциональные поля: `focus` (int 0–10, range), `note` (text), `sleep_hours` (уже существует, становится part of optional blocks).
- Добавляется переключатель шаблонов (утро/день/вечер/событие) над формой. Шаблон определяет видимый набор опциональных блоков.
- Форма переводится на mobile-first: крупные touch-targets (≥44px), range-слайдеры, collapse-блоки для опциональных полей.
- Тёмная тема как дефолт (`data-theme="dark"`).
- Мягкий режим: шаблон «день» содержит только ядро, все опциональные блоки скрыты.
- Валидация: alert-warning вместо alert-error («прощающие ошибки»).
- Миграция 004: добавляет колонки `energy`, `anxiety`, `focus`, `note`; снимает `NOT NULL` с `activity` и `effect`.

## Capabilities

### New Capabilities

- `mobile-entry`: минимальная мобильная форма с range-ядром (mood_score, energy, anxiety), переключателем шаблонов, collapse-опциональными блоками (focus, sleep_hours, note, activity), тёмной темой и мягким режимом. Шаблоны: утро/день/вечер/событие.

### Modified Capabilities

- `entries-data`: таблица `entries` — новые колонки `energy`, `anxiety`, `focus`, `note` (все nullable); `activity` и `effect` становятся nullable. Миграция 004.
- `entries-api`: `POST /entries` принимает новый набор полей (ядро обязательно, опциональные — nullable).
- `entries-ui`: форма переводится на range-слайдеры, template-табы, collapse-блоки; mobile-first layout с нижней навигацией; тёмная тема.

## Impact

- **Миграция:** `resources/migrations/004-add-mobile-form-fields.up.sql` — ALTER TABLE entries ADD COLUMN …
- **Domain:** `src/app/domains/entries.clj` — новый malli-схема (mood_score, energy, anxiety обязательны; sleep_hours, focus, note, activity опциональны)
- **DB:** `src/app/db/entries.clj` — `create-entry!` принимает новые поля
- **Routes:** `src/app/routes/entries.clj` — деструктуризация новых полей из `:parameters`
- **Views:** `src/app/views/entries.clj` — новая форма (range, collapse, tabs, dark theme, soft alert)
- **Views:** `src/app/views/layout.clj` — `data-theme="dark"`
- **I18n:** `resources/i18n/{ru,en}.edn` — новые ключи для шаблонов и полей
- **Specs:** дельты в `specs/mobile-entry/spec.md`, `specs/entries-data/spec.md`, `specs/entries-ui/spec.md`, `specs/entries-api/spec.md`
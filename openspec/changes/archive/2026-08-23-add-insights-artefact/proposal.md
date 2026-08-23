## Why

Фаза 3 продуктового roadmap'а (`product-vision`). У людей с аффективными расстройствами регулярно появляются работающие инсайты о том, как прожить конкретное состояние, но доступ к ним теряется — в плохом состоянии они не вспоминаются. Фазы 0–2 заложили фундамент: мобильная форма, медикаменты, роза ветров состояний с rule-based ярлыками и /feed как landing. Сейчас нет механики хранения инсайтов и их подбора по текущему состоянию — ядро продуктовой ценности («доступ к инсайтам в нужный момент») не реализовано.

Фаза 3 вводит инсайт-артефакт как структурированный тип данных и rule-based подбор по `state_label` без AI. Решения зафиксированы в `product-vision/design.md` Decision 13 (2026-08-23): схема таблицы, exact-match по ярлыку, fallback = молчать + мягкий онбординг (resolve OQ3), in-place редактирование с `updated_at`-only. UI-скетчи четырёх экранов в `openspec/changes/product-vision/ui-sketches/insights-*.clj`.

`[ref: A2-q2, A2-q4, A4-q5, A3-q3]`

## What Changes

- **Новая таблица `insights`** (миграция 007): структурированный артефакт `context / category / advice_to_self (JSON-массив в TEXT) / identity (nullable) / state_label (ключ подбора) / entry_id (soft reference на `entries.id`, без FK)`.
- **Новый домен `app.domains.insights`**: malli-схема создания, валидация (context + category + min 1 advice), нормализация `state_label`, JSON-сериализация `advice_to_self`.
- **Новый слой доступа `app.db.insights`**: CRUD, `get-matching-insights` (exact match по `state_label`, `ORDER BY updated_at DESC`).
- **Новые роуты `/insights*`** в `app.routes.app`: список (с htmx-фильтром по category), создание, просмотр, in-place edit (per-поле htmx-swap), удаление с подтверждением, фрагмент advice-item для динамического списка.
- **Виджет на `/feed`**: секция под hero-карточкой, показывает 1 релевантный инсайт (Decision 13.4) или мягкий онбординг (OQ3 resolved).
- **Навигация**: `/insights` возвращается в `nav-items` (5 пунктов — потолок мобильной bottom-nav).
- **i18n**: ключи для категорий, ярлыков, заголовков, кнопок, онбординга.
- **Обезличено**: структурный шаблон, дословные raw-заметки не копируются (см. `product-vision/design.md` Decision 10).

## Capabilities

### New Capabilities
- `insights`: структурированный инсайт-артефакт (context / category / advice_to_self / identity / state_label / entry_id), хранение, создание (3 entry point: из /feed, post-save из /check-in, standalone), просмотр, in-place редактирование, подбор по `state_label` (rule-based, exact match), fallback при отсутствии, персональность (только свои).

### Modified Capabilities
- `entries-data`: добавляется опциональная soft-ссылка `entry_id` из `insights` на `entries.id` (без FK; инсайт переживает удаление записи). Спецификация `entries` не меняется — связь односторонняя, на стороне `insights`.

## Scope

- **In scope:** миграция 007 (таблица `insights` + индекс), домен, БД-слой, роуты, 4 экрана (список / создание / просмотр / виджет на /feed), навигация, i18n, rule-based подбор по `state_label`, fallback (онбординг), in-place edit, удаление с подтверждением.
- **Out of scope:** AI-генерация советов (Фаза 5), euclidean-distance matching (Фаза 5), medication-aware matching (Фаза 5), каналы доставки / push (Фаза 4), полная версия истории изменений `insight_versions` (Фаза 6), редактирование `state_label` post-creation (Фаза 6+), shared insight library (future opt-in), вечерние инсайты на «завтра» (Фаза 4, OQ6).

## Non-goals

- **AI-генерация fallback'а** — Фаза 5. Фаза 3: молчать + онбординг.
- **Чужие советы / shared library** — Non-goal (proposal `product-vision`).
- **Euclidean distance по осям розы** — Фаза 5. Exact match по 6-значному `state_label` достаточен для Фазы 3.
- **Полная версия инсайтов** — Фаза 6. Фаза 3: `updated_at`-only.
- **Push-доставка** — Фаза 4. Фаза 3: только in-app surfacing на /feed.

## Impact

- **Миграция:** `007-add-insights.up.sql` / `.down.sql` (CREATE TABLE `insights` + индекс `idx_insights_user_state`).
- **Новые файлы:**
  - `src/app/db/insights.clj` — CRUD + `get-matching-insights`.
  - `src/app/domains/insights.clj` — malli-схема, валидация, нормализация, JSON-сериализация advice.
  - `src/app/routes/insights.clj` — handlers: page (список), new, show, create, edit-фрагменты, delete, advice-item.
  - `src/app/views/insights.clj` — рендер: list-page, new-page, show-page, fragments (compact-card, edit-forms, onboarding, validation-error).
- **Изменяемые файлы:**
  - `src/app/routes/app.clj` — подключить `app.routes.insights`, добавить роуты `/insights*`.
  - `src/app/views/feed.clj` — вставить `feed-insight-widget` в `today-section` после hero-card.
  - `src/app/views/navigation.clj` — добавить пункт `:insights` в `nav-items` (5 пунктов).
  - `src/app/i18n.clj` (или ресурс) — ключи для категорий, ярлыков, заголовков, онбординга.
- **Зависимости:** новые библиотеки не добавляются (JSON через `clojure.data.json` или `cheshire`, если уже в deps — иначе спросить; `clojure.core/read-string` рискован для user-данных).
- **Спецификации:** `openspec/specs/insights/spec.md` будет создан при archive; до этого — delta в `openspec/changes/add-insights-artefact/specs/`.

## Acceptance Criteria

- GIVEN пользователь с записью сегодня в состоянии `anxiety` и историческим инсайтом `state_label='anxiety'` WHEN открывает `/feed` THEN видит виджет «Что ты сам говорил в таком состоянии» с 1 релевантным инсайтом под hero-карточкой `[ref: A2-q4, A3-q3]`
- GIVEN пользователь без инсайтов для текущего `state_label` WHEN открывает `/feed` THEN видит мягкий онбординг «Инсайтов для состояния "X" ещё нет» + кнопку «Создать» `[ref: OQ3]`
- GIVEN пользователь WHEN открывает `/insights/new?state_label=anxiety` THEN `state_label` предзаполнен, `context` пуст, форма валидирует min 1 advice `[ref: A2-q2]`
- GIVEN пользователь WHEN создаёт инсайт из /feed (entry_id=42, state_label=anxiety) THEN артефакт сохраняется с `entry_id=42`, `state_label='anxiety'`, `advice_to_self` как JSON-массив `[ref: A2-q2, A2-q4]`
- GIVEN пользователь WHEN открывает `/insights` THEN видит список всех своих инсайтов, фильтр-табы по category, кнопку «Новый» `[ref: A2-q4]`
- GIVEN пользователь WHEN редактирует поле инсайта (in-place htmx) THEN сохранение свапает read-блок, `updated_at` обновляется, `state_label` не редактируется `[ref: A2-q2]`
- GIVEN пользователь WHEN удаляет инсайт (modal confirm) THEN артефакт удаляется (hard delete), связанные записи сохраняются `[ref: A4-q5]`
- GIVEN два разных пользователя WHEN один создаёт инсайт THEN второй не видит его ни в списке, ни в виджете `[ref: A3-q4]`
- GIVEN выполнена миграция 007 THEN таблица `insights` существует с индексом `(user_id, state_label)`, rollback удаляет таблицу без потери `entries`
- GIVEN `openspec validate --change add-insights-artefact` THEN структура валидна

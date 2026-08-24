# Insights Specification

## Purpose

Инсайт-артефакт — структурированная единица «совета себе» с привязкой к контексту состояния. Хранится отдельно от дневниковых записей и подбирается системой по текущему `state_label` (rule-based exact match, Фаза 3) для отображения в /feed. Ядро продуктовой ценности: доступ к собственным инсайтам именно в тот момент, когда они нужны.

`[ref: A2-q2, A2-q4, A4-q5, A3-q3, OQ3]`
## Requirements
### Requirement: Migration 007 creates insights table
The system SHALL provide migration 007 that creates the `insights` table with columns `id`, `user_id` (FK to `users`), `context` (TEXT NOT NULL), `category` (TEXT NOT NULL, CHECK IN `productivity/coping/identity/general`), `advice_to_self` (TEXT NOT NULL, JSON-array of strings), `identity` (TEXT, nullable), `state_label` (TEXT, nullable), `entry_id` (INTEGER, nullable, NO foreign key to `entries`), `created_at`, `updated_at`. An index `idx_insights_user_state` on `(user_id, state_label)` SHALL be created.

#### Scenario: Migration creates table and index
- **WHEN** migration `007-add-insights.up.sql` is applied
- **THEN** table `insights` exists with all specified columns
- **AND** column `category` has a CHECK constraint restricting values to `productivity`, `coping`, `identity`, `general`
- **AND** column `advice_to_self` is NOT NULL
- **AND** column `entry_id` is nullable and has NO foreign key constraint to `entries`
- **AND** index `idx_insights_user_state` on `(user_id, state_label)` exists
- **AND** existing data in `entries` and other tables is preserved

#### Scenario: Migration rollback drops table
- **WHEN** migration `007-add-insights.down.sql` is applied
- **THEN** table `insights` and index `idx_insights_user_state` are dropped
- **AND** all other tables remain unchanged `[ref: A4-q5]`

### Requirement: Insight artefact structure
The system SHALL store insights as structured artefacts with fields: `context` (description of state when the insight arrived, non-empty), `category` (`productivity` / `coping` / `identity` / `general`), `advice_to_self` (non-empty JSON-array of strings), optional `identity` (statement about self relevant to the state), `state_label` (one of `mixed/anxiety/elevated/low/balanced/neutral`, nullable for standalone), `entry_id` (nullable soft reference to `entries.id`, no FK).

#### Scenario: User creates an insight with full fields
- **GIVEN** пользователь в состоянии `anxiety` получил инсайт
- **WHEN** создаёт инсайт с `context`, `category=coping`, `advice_to_self=["дыхание 4-7-8"]`, `identity="я не своя тревога"`, `state_label=anxiety`, `entry_id=42`
- **THEN** артефакт сохраняется со всеми полями
- **AND** `advice_to_self` хранится как JSON-массив строк в TEXT
- **AND** `created_at` и `updated_at` равны текущему времени `[ref: A2-q2, A2-q4]`

#### Scenario: User creates a standalone insight without entry link
- **GIVEN** пользователь хочет записать общий инсайт без привязки к записи
- **WHEN** создаёт инсайт через `/insights/new` без `entry_id`, `state_label=balanced`
- **THEN** артефакт сохраняется с `entry_id=NULL`, `state_label='balanced'`
- **AND** `identity` может быть NULL `[ref: A2-q2]`

#### Scenario: Insight with empty advice rejected
- **WHEN** `create-insight!` вызывается с `advice_to_self=[]` (пустой массив)
- **THEN** доменный слой отклоняет создание через malli-валидацию
- **AND** минимум 1 non-empty строка требуется
- **AND** строка в `entries` не создаётся

#### Scenario: Insight with invalid category rejected
- **WHEN** `create-insight!` вызывается с `category="foo"` (не из допустимого множества)
- **THEN** доменный слой отклоняет создание через malli-валидацию

### Requirement: Insight creation from three entry points
The system SHALL support three creation entry points: (A) from `/feed` with `state_label` and `entry_id` auto-filled from the latest entry of today, context pre-seeded with a state description; (B) a soft post-save link from `/check-in` offering to record an insight (not a form field); (C) standalone `/insights/new` with `state_label` chosen from a dropdown and no `entry_id`.

#### Scenario: Create from /feed with auto-filled context
- **GIVEN** у пользователя есть запись сегодня с `state_label=anxiety`
- **WHEN** нажимает «записать инсайт» в виджете на /feed
- **THEN** открывается `/insights/new?entry_id=42&state_label=anxiety`
- **AND** `state_label` предзаполнен как `anxiety`
- **AND** `entry_id=42` передан как hidden field
- **AND** `context` предзаполнен описанием состояния (редактируемый) `[ref: A2-q4, A3-q3]`

#### Scenario: Create standalone without state binding
- **GIVEN** пользователь на /insights
- **WHEN** нажимает «Новый» и выбирает `state_label` из dropdown
- **THEN** инсайт сохраняется с выбранным `state_label`, `entry_id=NULL` `[ref: A2-q2]`

#### Scenario: Post-save link from /check-in is optional
- **GIVEN** пользователь сохранил запись на /check-in
- **WHEN** видит мягкую ссылку «записать инсайт для этого состояния?»
- **THEN** ссылка ведёт на `/insights/new?entry_id=…&state_label=…`
- **AND** это НЕ поле формы /check-in (минимализм формы сохранён) `[ref: Decision 9]`

### Requirement: Insight matching by current state_label
The system SHALL surface historically relevant insights by exact match on `state_label` between the current entry's label and the insight's `state_label`. Matching is rule-based (no AI). The query SHALL order by `updated_at DESC` and limit to 1 insight on /feed. `:neutral` is a valid 6th label, handled identically to others.

#### Scenario: User in anxious state sees past anxious-state insight
- **GIVEN** у пользователя есть исторический инсайт с `state_label='anxiety'`
- **AND** последняя запись сегодня имеет `state_label='anxiety'`
- **WHEN** открывает `/feed`
- **THEN** видит виджет «Что ты сам говорил в таком состоянии» с 1 релевантным инсайтом под hero-карточкой
- **AND** инсайт отсортирован по `updated_at DESC` `[ref: A2-q4, A3-q3]`

#### Scenario: User in neutral state sees neutral-state insight
- **GIVEN** у пользователя есть инсайт с `state_label='neutral'`
- **AND** последняя запись сегодня имеет `state_label='neutral'`
- **WHEN** открывает `/feed`
- **THEN** видит виджет с этим инсайтом (neutral — равноправный ярлык)

#### Scenario: No entries today hides widget
- **GIVEN** у пользователя нет записей сегодня
- **WHEN** открывает `/feed`
- **THEN** виджет инсайтов не показывается (нет current state для матчинга)

#### Scenario: Euclidean distance matching is deferred
- **GIVEN** Фаза 3 реализована с exact match по `state_label`
- **WHEN** рассматривается matching по осям розы (energy/anxiety/focus)
- **THEN** это Фаза 5 (AI), не Фаза 3
- **AND** снапшот осей не хранится в `insights` (backfill из `entry_id` в Фазе 5)

### Requirement: No insight loss across state changes and entry deletion
The system SHALL preserve all insights across state changes and time, including insights created during depressive or mixed states. An insight SHALL survive deletion of its linked entry (`entry_id` has no FK; a dangling `entry_id` is acceptable and display omits the link).

#### Scenario: User in depression creates insight, later returns to depression
- **GIVEN** пользователь в состоянии `low` создал инсайт «как пережить спад»
- **WHEN** спустя месяцы возвращается в `low`
- **THEN** инсайт доступен и предлагается системой в виджете на /feed
- **AND** пользователь не должен помнить об инсайте, чтобы его увидеть `[ref: A4-q5, A2-q4]`

#### Scenario: Insight survives linked entry deletion
- **GIVEN** инсайт с `entry_id=42`
- **WHEN** запись `entries.id=42` удаляется
- **THEN** инсайт сохраняется (нет FK CASCADE)
- **AND** `entry_id=42` остаётся (dangling), display опускает ссылку на запись `[ref: A4-q5]`

### Requirement: Fallback when no matching insight exists
The system SHALL apply a fallback strategy of «silence + soft per-state onboarding» when no historical insight matches the current `state_label`. The onboarding prompt SHALL be factual («Инсайтов для состояния "X" ещё нет»), dismissible by nature (empty-state in section, not modal), and offer a link to `/insights/new?state_label=X`. AI-generation is deferred to Phase 5; sharing others' insights is a Non-goal.

#### Scenario: User in a novel state with no past insights
- **GIVEN** пользователь в состоянии `elevated`, для которого ещё нет инсайтов
- **WHEN** открывает `/feed`
- **THEN** видит мягкий онбординг «Инсайтов для состояния "подъём" ещё нет» + кнопку «Создать»
- **AND** кнопка ведёт на `/insights/new?state_label=elevated`
- **AND** тон фактический, без strik/self-blame языка `[ref: OQ3]`

#### Scenario: User has insights but not for current state
- **GIVEN** у пользователя есть инсайты для `anxiety`, но текущее состояние `low`
- **WHEN** открывает `/feed`
- **THEN** видит онбординг для `low` (не хвастается наличием других)
- **AND** система не предлагает чужие инсайты (Non-goal) `[ref: OQ3, proposal Non-goals]`

### Requirement: Insight editability via in-place htmx with updated_at-only versioning
The system SHALL allow the user to edit `context`, `category`, `advice_to_self`, and `identity` of an insight via in-place htmx swaps (per-field edit form replaces read-block, save swaps back). `state_label` SHALL NOT be editable in Phase 3 (bound to creation context; delete+recreate to fix). Versioning is simplified: only `updated_at` is tracked (full `insight_versions` table is deferred to Phase 6). The spec requirement «история изменений сохраняется» is interpreted as «the artefact survives state changes and time, `context` is not silently overwritten», not byte-level diffs of advice items.

#### Scenario: User edits context in-place
- **GIVEN** у пользователя есть инсайт
- **WHEN** нажимает «Править» возле поля `context` на `/insights/:id`
- **THEN** read-блок свапается на edit-form (textarea + «Сохранить» + «Отмена»)
- **AND** при сохранении `hx-post` свапает форму обратно на read-блок
- **AND** `updated_at` обновляется `[ref: A2-q2]`

#### Scenario: User extends an old insight with new advice
- **GIVEN** у пользователя есть инсайт с 2 советами
- **WHEN** в edit-mode добавляет 3-й совет (кнопка «Добавить пункт»)
- **THEN** `advice_to_self` перезаписывается как JSON-массив из 3 строк
- **AND** `updated_at` обновляется
- **AND** `context` оригинала не теряется (не обнуляется) `[ref: A2-q2]`

#### Scenario: state_label is not editable in Phase 3
- **GIVEN** у пользователя есть инсайт с `state_label='anxiety'`
- **WHEN** открывает `/insights/:id`
- **THEN** рядом с `state_label` нет кнопки «Править»
- **AND** чтобы изменить состояние, нужно удалить и пересоздать инсайт

#### Scenario: Full version history is deferred
- **GIVEN** Фаза 3 реализована с `updated_at`-only
- **WHEN** пользователь удаляет пункт из `advice_to_self`
- **THEN** текст этого пункта теряется (нет `insight_versions`)
- **AND** полная версия — Фаза 6 `[ref: A2-q2]`

### Requirement: Insight deletion with confirmation
The system SHALL allow the user to delete an insight via a confirmation modal (hard delete, `DELETE FROM insights WHERE id=? AND user_id=?`). The modal SHALL use `modal-bottom sm:modal-middle` for mobile ergonomics. A soft-delete (`deleted_at`) is NOT used (no consumers, no recycle bin). Linked entries are preserved (no FK).

#### Scenario: User deletes an insight
- **GIVEN** у пользователя есть инсайт
- **WHEN** нажимает «Удалить» и подтверждает в modal
- **THEN** `DELETE FROM insights WHERE id=? AND user_id=?` выполняется
- **AND** пользователь редиректится на `/insights`
- **AND** связанные записи в `entries` сохраняются `[ref: A4-q5]`

#### Scenario: User cancels deletion
- **GIVEN** у пользователя есть инсайт
- **WHEN** нажимает «Удалить» и затем «Отмена» в modal
- **THEN** инсайт сохраняется, modal закрывается

### Requirement: Insights are personal
The system SHALL NOT share or expose a user's insights to other users. All queries SHALL filter by `user_id` from the authenticated identity. AI operates only on the user's own insights (deferred to Phase 5; explicit future opt-in for a shared library is a Non-goal in this change).

#### Scenario: User sees only own insights on /insights
- **GIVEN** два пользователя, у каждого есть инсайты
- **WHEN** один открывает `/insights`
- **THEN** видит только свои инсайты
- **AND** не видит инсайтов другого пользователя `[ref: A3-q4]`

#### Scenario: User sees only own insights in /feed widget
- **GIVEN** два пользователя, у одного есть инсайт для `anxiety`
- **WHEN** второй (в состоянии `anxiety`, без своих инсайтов) открывает `/feed`
- **THEN** видит онбординг (не чужой инсайт) `[ref: A3-q4, proposal Non-goals]`

#### Scenario: AI advice generation is deferred
- **GIVEN** Фаза 3 реализована без AI
- **WHEN** рассматривается AI-генерация советов из своих инсайтов
- **THEN** это Фаза 5, не Фаза 3
- **AND** чужие инсайты не используются (Non-goal) `[ref: A3-q4]`

### Requirement: Insights list page with category filter
The system SHALL provide a `/insights` page listing all of the user's insights (ordered by `updated_at DESC`), with htmx-powered filter tabs by category (`all`, `productivity`, `coping`, `identity`, `general`) and a «Новый» button. Empty states: global onboarding when no insights exist, per-category message when filter yields no results.

#### Scenario: User opens /insights with existing insights
- **GIVEN** у пользователя есть 3 инсайта в разных категориях
- **WHEN** открывает `/insights`
- **THEN** видит список из 3 карточек, отсортированных по `updated_at DESC`
- **AND** видит фильтр-табы по категории с «Все» активным
- **AND** видит кнопку «Новый» `[ref: A2-q4]`

#### Scenario: User filters by category
- **GIVEN** у пользователя есть инсайты в `coping` и `productivity`
- **WHEN** нажимает таб «Копинг»
- **THEN** список свапается (htmx) на инсайты только категории `coping` `[ref: A2-q4]`

#### Scenario: Global onboarding when no insights
- **GIVEN** у пользователя нет ни одного инсайта
- **WHEN** открывает `/insights`
- **THEN** видит онбординг «У тебя ещё нет инсайтов» + кнопку «Создать первый»
- **AND** фильтр-табы НЕ показываются (нечего фильтровать)

#### Scenario: Per-category empty state
- **GIVEN** у пользователя есть инсайты, но не в `identity`
- **WHEN** выбирает таб «Идентичность»
- **THEN** видит «В этой категории пока пусто» + подсказку
- **AND** кнопка «Новый» сверху остаётся доступной

### Requirement: Insights navigation entry
The system SHALL include `/insights` in the bottom navigation (`nav-items`), making it the 5th item (`/feed`, `/check-in`, `/insights`, `/medications`, `/settings`). The icon SHALL be sourced from `app.icons`.

#### Scenario: Navigation shows 5 items
- **WHEN** пользователь открывает любую страницу с навигацией
- **THEN** видит 5 пунктов: /feed, /check-in, /insights, /medications, /settings
- **AND** `/insights` отмечен активным при нахождении на ней

### Requirement: AI advice widget under insights
The system SHALL display an AI-synthesised advice (`ai-advice`) in the /feed insights area, generated from the user's own historical insights with context similar to the current state. The widget SHALL show a human-readable explanation («почему этот совет?»), a link to the originating insight («из твоего инсайта от …»), and a «пожаловаться» button. If the user has no own insights or AI is disabled, the widget SHALL NOT appear.

#### Scenario: AI advice shown with source link
- **WHEN** у пользователя есть исторические инсайты с близким к текущему контекстом и AI-советы включены
- **THEN** под виджетом инсайтов на /feed показывается секция `ai-advice`
- **AND** совет содержит ссылку «из твоего инсайта от …» на исходный инсайт
- **AND** совет содержит кнопку «почему этот совет?» с объяснением
- **AND** совет содержит кнопку «пожаловаться»

#### Scenario: No own insights → no AI advice
- **WHEN** у пользователя нет ни одного инсайта (или AI-советы отключены)
- **THEN** секция `ai-advice` не показывается на /feed


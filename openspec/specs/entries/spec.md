# Entries Specification

## Purpose

Запись дневника состояния: форма (check-in), лента (feed), роза ветров (mood-states), гранулярность (несколько записей/день, периоды), mobile-first layout, soft mode, API endpoints, БД-слой.

## Requirements


### Requirement: Migration 004 adds mobile form fields
The system SHALL provide a migration that adds new columns to the `entries` table without data loss.

#### Scenario: Migration adds columns
- **WHEN** migration `004-add-mobile-form-fields.up.sql` is applied
- **THEN** columns `energy`, `anxiety`, `focus`, `note`, `template` are added to `entries`
- **AND** all new columns are nullable
- **AND** existing data in `entries` is preserved

#### Scenario: Migration rollback removes columns
- **WHEN** migration `004-add-mobile-form-fields.down.sql` is applied
- **THEN** columns `energy`, `anxiety`, `focus`, `note`, `template` are dropped
- **AND** columns `activity` and `effect` remain unchanged

### Requirement: Entries table schema
The system SHALL provide a database table `entries` with columns `id`, `date`, `mood_score` (0–10), `energy` (0–10), `anxiety` (0–10), `focus` (0–10, nullable), `sleep_hours` (nullable), `note` (nullable), `activity` (nullable), `effect` (nullable), `template` (nullable), `created_at`.

#### Scenario: Table exists after migration
- **WHEN** migrations 002, 003, and 004 are applied
- **THEN** the `entries` table exists
- **AND** it contains columns `id`, `date`, `mood_score`, `energy`, `anxiety`, `sleep_hours`, `created_at`, `user_id`
- **AND** it contains nullable columns `focus`, `note`, `activity`, `effect`, `template`

#### Scenario: New required columns have default values
- **WHEN** migration 004 is applied
- **THEN** existing rows have `energy` and `anxiety` set to NULL (columns are nullable for backward compatibility)
- **AND** domain logic enforces non-null for new entries

### Requirement: Entry data access functions
The system SHALL update `create-entry!` and `get-entries` in `app.db.entries` to handle the new field set.

#### Scenario: Create entry with new core fields
- **WHEN** `create-entry!` is called with `mood_score`, `energy`, `anxiety`
- **THEN** the entry is persisted with all three core values
- **AND** optional fields (`focus`, `sleep_hours`, `note`, `activity`, `effect`, `template`) are stored as provided or NULL

#### Scenario: Create entry with optional fields
- **WHEN** `create-entry!` is called with core fields plus `sleep_hours` = 7.5, `note` = "текст заметки", `template` = "morning"
- **THEN** the entry is persisted with all provided values
- **AND** non-provided optional fields are NULL

#### Scenario: Existing entries still retrievable
- **WHEN** `get-entries` is called after migration 004
- **THEN** entries created before migration are returned with new columns as NULL
- **AND** entries created after migration include new column values

### Requirement: Entry mood score range enforced
The system SHALL reject an entry whose `mood_score`, `energy`, or `anxiety` is outside the 0–10 range.

#### Scenario: Out-of-range core field rejected
- **WHEN** `create-entry!` is called with `energy` = 15 (outside 0–10)
- **THEN** the domain layer rejects the entry via malli schema validation
- **AND** no row is inserted into the database

### Requirement: Medication logs link to entries by date
The system SHALL allow analytical joins between `medication_logs` and `entries` by matching `medication_logs.log_date` to `entries.date` for the same `user_id`. No foreign key constraint exists between the tables; the join is implicit by date.

#### Scenario: Join logs to entries for a day
- **WHEN** a query joins `medication_logs` and `entries` on `user_id` and `log_date = date`
- **THEN** all medication logs for a given day are associated with the mood entry for that day
- **AND** if no mood entry exists for a day, medication logs remain queryable independently `[ref: A3-q5]`

### Requirement: Migration 006 adds state_label and state_period_id
The system SHALL provide migration 006 that adds two nullable columns to the `entries` table: `state_label TEXT` and `state_period_id INTEGER`. Both columns SHALL be nullable and SHALL NOT have a foreign key constraint.

#### Scenario: Migration adds columns
- **WHEN** migration `006-add-state-label-and-period.up.sql` is applied
- **THEN** column `state_label TEXT` is added to `entries`
- **AND** column `state_period_id INTEGER` is added to `entries`
- **AND** both columns are nullable
- **AND** no foreign key constraint is created
- **AND** existing data in `entries` is preserved

#### Scenario: Migration rollback removes columns
- **WHEN** migration `006-add-state-label-and-period.down.sql` is applied
- **THEN** columns `state_label` and `state_period_id` are dropped from `entries`
- **AND** all other columns remain unchanged

### Requirement: Entry data access handles state_label and state_period_id
The system SHALL update `create-entry!` and `get-entries` in `app.db.entries` to handle the `state_label` and `state_period_id` columns. `get-entries` SHALL return entries ordered by `created_at` descending within each date group.

#### Scenario: Create entry with state_label
- **WHEN** `create-entry!` is called with `state_label` = `:state/anxiety`
- **THEN** the entry is persisted with `state_label` stored as text
- **AND** `state_period_id` is stored as NULL

#### Scenario: Create entry without state_label
- **WHEN** `create-entry!` is called without `state_label` (nil)
- **THEN** the entry is persisted with `state_label` = NULL
- **AND** the label will be auto-derived on render

#### Scenario: Get entries ordered by created_at within day
- **WHEN** `get-entries` is called and the user has 3 entries on the same date
- **THEN** entries are returned with the most recent `created_at` first within that date
- **AND** entries are grouped by date (most recent date first)

### Requirement: Insights link to entries by optional soft entry_id
The system SHALL allow an optional soft reference from `insights.entry_id` to `entries.id` for the same `user_id`. No foreign key constraint SHALL exist between `insights` and `entries`. Deleting an entry SHALL NOT cascade to linked insights; a dangling `entry_id` is acceptable and display SHALL omit the entry link when the referenced entry no longer exists. This follows the loose-coupling pattern established by `medication_logs` (link by date, no FK) and `entries.state_period_id` (no FK).

#### Scenario: Insight references an existing entry
- **WHEN** an insight is created with `entry_id=42` and entry `entries.id=42` exists for the same `user_id`
- **THEN** the insight is persisted with `entry_id=42`
- **AND** no foreign key constraint is enforced
- **AND** the insight's `state_label` matches the referenced entry's `state_label` (set at creation) `[ref: A2-q4, A4-q5]`

#### Scenario: Deleting an entry leaves dangling entry_id
- **GIVEN** an insight with `entry_id=42`
- **WHEN** entry `entries.id=42` is deleted
- **THEN** the insight is preserved (no FK CASCADE)
- **AND** `insights.entry_id` remains `42` (dangling)
- **AND** the insight remains queryable and matchable by `state_label`
- **AND** display of the insight omits the link to the deleted entry `[ref: A4-q5]`

#### Scenario: Standalone insight has null entry_id
- **WHEN** an insight is created without `entry_id` (standalone via `/insights/new`)
- **THEN** `insights.entry_id` is NULL
- **AND** the insight is matchable by `state_label` identically to linked insights `[ref: A2-q2]`

### Requirement: AI findings table
The system SHALL provide migration 009 that creates the `ai_findings` table to cache AI analysis results (correlations, proposed state labels, advice from own insights). The table SHALL have columns `id`, `user_id` (FK to `users`), `type` (TEXT, CHECK IN `correlation/label/advice`), `content` (TEXT, JSON), `confidence` (TEXT, CHECK IN `high/medium/low`, nullable for labels/advice), `source_refs` (TEXT, JSON-array of references to source data/insights), `feedback` (TEXT, nullable — «не релевантно» / «уже знал» / «пожаловаться»), `hidden` (INTEGER DEFAULT 0), `created_at`. Findings SHALL NOT be hard-deleted once created (history is preserved).

#### Scenario: Migration creates ai_findings table
- **WHEN** migration `009-add-ai-findings.up.sql` is applied
- **THEN** table `ai_findings` exists with all specified columns
- **AND** column `type` has a CHECK constraint restricting to `correlation`, `label`, `advice`
- **AND** column `content` stores JSON in TEXT
- **AND** column `confidence` is nullable
- **AND** column `hidden` defaults to 0
- **AND** existing data in `entries` and other tables is preserved

#### Scenario: Migration rollback drops table
- **WHEN** migration `009-add-ai-findings.down.sql` is applied
- **THEN** table `ai_findings` is dropped
- **AND** all other tables remain unchanged

### Requirement: AI findings store feedback
The system SHALL update `ai_findings` rows with user feedback and soft-hide them via the `hidden` flag rather than deleting, preserving the analysis history.

#### Scenario: Feedback hides finding without deletion
- **WHEN** a user marks a correlation as «не релевантно» (or «уже знал», or reports advice)
- **THEN** the corresponding `ai_findings` row is updated with `feedback` set and `hidden` = 1
- **AND** the row remains in the table (no hard delete)

### Requirement: Admin seed assignment
The system SHALL assign all existing entries without `user_id` to the seeded admin user during the startup seed process.

#### Scenario: Orphan entries assigned to admin
- **WHEN** the system starts with existing entries having `user_id IS NULL`
- **THEN** those entries are updated to belong to the admin user created during seed

### Requirement: Create entry via API
The system SHALL require authentication for `POST /entries`. The entry is automatically scoped to the authenticated user. For authenticated requests without an `HX-Request` header the endpoint returns the created entry as JSON with HTTP status 201; for htmx requests it returns an HTML fragment.

#### Scenario: Authenticated entry creation
- **WHEN** a `POST /entries` request is sent with a valid session and valid body
- **THEN** the server responds with HTTP status 201
- **AND** the created entry has `user_id` matching the authenticated user

#### Scenario: Unauthenticated POST /entries redirected
- **WHEN** a `POST /entries` request is sent without a session and without an `HX-Request` header
- **THEN** the server responds with HTTP 302 redirect to `/login?next=/entries`

### Requirement: Entry request validation
The system SHALL validate the `POST /entries` request body. Mandatory fields `mood_score`, `energy`, `anxiety` are required. Optional fields (`focus`, `sleep_hours`, `note`, `activity`, `effect`, `template`) are nullable. For requests without an `HX-Request` header an invalid body produces HTTP status 400 with a JSON validation error description; for htmx requests an invalid body produces HTTP status 400 with an HTML fragment containing the validation error message using alert-warning class.

#### Scenario: Missing required field rejected via API
- **WHEN** a `POST /entries` request is sent without an `HX-Request` header and without the required `energy` field
- **THEN** the server responds with HTTP status 400
- **AND** the response body contains a description of the validation error mentioning `energy`

#### Scenario: Missing required field rejected via htmx
- **WHEN** a `POST /entries` request is sent with an `HX-Request: true` header and without the required `energy` field
- **THEN** the server responds with HTTP status 400
- **AND** the response body is an HTML fragment with `alert-warning` class containing the validation error message

#### Scenario: Out-of-range core field rejected
- **WHEN** a `POST /entries` request is sent with `anxiety` = 15 (outside 0–10)
- **THEN** the server responds with HTTP status 400
- **AND** the response body contains a description of the validation error

#### Scenario: Empty optional fields accepted
- **WHEN** a `POST /entries` request is sent with valid core fields but `sleep_hours` is empty string
- **THEN** the server responds with HTTP status 201
- **AND** `sleep_hours` is coerced to nil in the created entry

### Requirement: List entries via API
The system SHALL require authentication for `GET /entries` and return only entries where `user_id` matches the authenticated user.

#### Scenario: Authenticated listing returns user's entries
- **WHEN** a `GET /entries` request is sent with a valid session
- **THEN** the server responds with HTTP status 200
- **AND** the response contains only entries belonging to the authenticated user

#### Scenario: Unauthenticated GET /entries redirected
- **WHEN** a `GET /entries` request is sent without a session
- **THEN** the server responds with HTTP 302 redirect to `/login?next=/entries`

### Requirement: Three-axis core with range sliders
The system SHALL provide a mobile-first entry form with exactly three mandatory core fields: mood_score, energy, and anxiety — each as a range slider 0–10.

#### Scenario: Core fields always visible
- **GIVEN** форма открыта с любым шаблоном
- **WHEN** пользователь видит форму
- **THEN** поля mood_score (range 0–10), energy (range 0–10) и anxiety (range 0–10) отображаются всегда
- **AND** все три поля обязательны для отправки формы
- **AND** каждое поле показывает текущее значение рядом со слайдером `[ref: A3-q1, A2-q1]`

#### Scenario: Core fill under 5 seconds
- **GIVEN** пользователь открыл форму
- **WHEN** совершает три движения пальца (mood_score + energy + anxiety)
- **THEN** ядро заполнено
- **AND** целевое время ≤ 5 секунд `[ref: A3-q1, A2-q3]`

#### Scenario: Range sliders visually distinct
- **GIVEN** форма с тремя range-слайдерами
- **WHEN** форма рендерится
- **THEN** mood_score использует range-primary, energy — range-success, anxiety — range-warning
- **AND** каждый слайдер имеет высоту не менее 2rem (32px)
- **AND** общая touch-зона (label + слайдер) ≥ 44px `[ref: A3-q1]`

### Requirement: Optional blocks as DaisyUI collapses
The system SHALL provide optional entry fields as collapsible blocks using DaisyUI `collapse collapse-arrow`.

#### Scenario: Optional blocks collapsed by default
- **GIVEN** форма открыта
- **WHEN** пользователь видит секцию «Дополнительно (необязательно)»
- **THEN** все опциональные блоки (focus, sleep_hours, note, activity) отображаются в свёрнутом состоянии
- **AND** каждый блок раскрывается по клику на заголовок `[ref: A3-q1]`

#### Scenario: User submits form with only core fields
- **GIVEN** форма с ядром и опциональными блоками
- **WHEN** пользователь заполняет только ядро (mood_score, energy, anxiety) и отправляет
- **THEN** запись создаётся успешно
- **AND** опциональные поля передаются как nil или не включаются в запрос `[ref: A3-q1]`

#### Scenario: Focus block
- **GIVEN** пользователь раскрывает блок «Фокус»
- **WHEN** выбирает значение на range-слайдере
- **THEN** поле focus — int 0–10, range-info
- **AND** поле не обязательно

#### Scenario: Sleep block
- **GIVEN** пользователь раскрывает блок «Сон (часы)»
- **WHEN** вводит значение
- **THEN** поле sleep_hours — number, step 0.1, min 0, max 24
- **AND** поле не обязательно

#### Scenario: Note block
- **GIVEN** пользователь раскрывает блок «Заметка»
- **WHEN** вводит текст
- **THEN** поле note — textarea, maxlength 500
- **AND** поле не обязательно

#### Scenario: Activity block
- **GIVEN** пользователь раскрывает блок «Активность»
- **WHEN** вводит текст
- **THEN** поле activity — text input
- **AND** поле не обязательно

### Requirement: Template selector
The system SHALL provide a template selector with four tabs: morning, day, evening, event.

#### Scenario: Template tabs rendered
- **GIVEN** форма открыта
- **WHEN** форма рендерится
- **THEN** отображаются четыре таба: «Утро», «День», «Вечер», «Событие»
- **AND** табы реализованы как DaisyUI `tabs tabs-boxed`
- **AND** активный таб сохраняется в скрытое поле `template`

#### Scenario: Template switch preserves entered data
- **GIVEN** пользователь ввёл mood_score = 7 в активном шаблоне «День»
- **WHEN** переключается на шаблон «Вечер»
- **THEN** значение mood_score = 7 сохраняется
- **AND** значения уже раскрытых опциональных блоков сохраняются `[ref: A3-q1]`

### Requirement: Soft mode via day template
The system SHALL provide a minimal mode where only core fields are visible without expanding optional blocks.

#### Scenario: Day template shows only core
- **GIVEN** пользователь в плохом состоянии
- **WHEN** открывает форму с шаблоном «День»
- **THEN** форма показывает только ядро (mood_score, energy, anxiety)
- **AND** секция «Дополнительно» видна, но все collapses свёрнуты
- **AND** целевое время заполнения ≤ 5 секунд `[ref: A2-q3]`

### Requirement: Dark theme as default
The system SHALL use dark theme as the default visual theme.

#### Scenario: Form renders in dark theme
- **GIVEN** пользователь открывает приложение
- **WHEN** любая страница рендерится
- **THEN** body имеет атрибут `data-theme="dark"`
- **AND** форма использует цвета DaisyUI dark theme (bg-base-200, bg-base-300 и т.д.)

### Requirement: Soft validation alerts
The system SHALL display validation errors using alert-warning instead of alert-error.

#### Scenario: Validation error shown softly
- **GIVEN** пользователь отправляет форму с невалидным значением
- **WHEN** сервер возвращает ошибку валидации
- **THEN** ошибка отображается в `alert alert-warning`
- **AND** ошибка не использует красный цвет (alert-error)
- **AND** сообщение об ошибке не обвиняет пользователя

### Requirement: No streak or guilt mechanics
The system SHALL NOT display any streak, consecutive day counter, or guilt-inducing messaging.

#### Scenario: No streak counter
- **GIVEN** пользователь пользуется приложением
- **WHEN** любая страница рендерится
- **THEN** отсутствуют счётчики «дней подряд», «цепочка», «стрик»
- **AND** отсутствуют сообщения «вы пропустили день» или «цепочка разорвана» `[ref: A2-q3]`

### Requirement: Template selector rendered as tabs
The system SHALL render a template selector as DaisyUI `tabs tabs-boxed` with four tabs: morning, day, evening, event. The selected template SHALL be submitted as a hidden field `template`.

#### Scenario: Template tabs visible
- **WHEN** the form renders
- **THEN** four tabs are visible: «Утро», «День», «Вечер», «Событие»
- **AND** the first tab («Утро») is active by default

#### Scenario: Template value submitted with entry
- **WHEN** a user submits the form with «Вечер» tab selected
- **THEN** the POST request includes `template` = "evening"

### Requirement: Optional blocks rendered as DaisyUI collapses
The system SHALL render optional fields (focus, sleep_hours, note, activity) as DaisyUI `collapse collapse-arrow` components, all collapsed by default.

#### Scenario: All optional blocks collapsed on load
- **WHEN** the form first renders
- **THEN** sections for «Фокус», «Сон (часы)», «Заметка», «Активность» are collapsed
- **AND** clicking a section title expands that section
- **AND** clicking again collapses it

#### Scenario: Collapse does not require JavaScript
- **WHEN** a user clicks a collapse title
- **THEN** the section expands using pure CSS (DaisyUI collapse with hidden checkbox)
- **AND** no JavaScript or Hyperscript is needed for the collapse behavior

### Requirement: Render entries page
The system SHALL render an HTML page containing an entry creation form with a template selector (morning/day/evening/event tabs), three mandatory range sliders (mood_score, energy, anxiety each 0–10), optional collapsible blocks (focus, sleep_hours, note, activity), and the list of stored diary entries when the entries page is requested by a browser. The page SHALL use dark theme as default (`data-theme="dark"`). When the latest entry's `state_label` is `low` or `mixed`, the /check-in page SHALL render a non-blocking soft-mode banner above the form offering «мягкий режим» (form reduced to only `mood_score`) with an always-available «полная форма» escape; the form SHALL remain full by default (not forced).

**Note:** The form+list combined page at `GET /entries` is replaced by separate `/feed` (list) and `/check-in` (form) pages. `GET /entries` now redirects to `/feed`.

#### Scenario: Page displays new form and list
- **WHEN** a user opens `GET /entries` in a browser
- **THEN** the server responds with HTTP 308 redirect to `/feed`
- **AND** the form is available at `/check-in`
- **AND** the list is available at `/feed`

#### Scenario: Empty list
- **WHEN** a user opens `GET /feed` in a browser and there are no stored entries
- **THEN** the page shows the onboarding message with a link to `/check-in`
- **AND** no empty-list guilt message is shown

#### Scenario: Soft-mode banner shown in low state
- **GIVEN** the latest entry has `state_label='low'`
- **WHEN** the user opens `/check-in`
- **THEN** a non-blocking banner «Тебе сейчас может быть непросто. Включить мягкий режим?» is shown above the form
- **AND** two buttons are present: «мягкий режим» and «полная форма»
- **AND** the full form is the default (no state is forced)

#### Scenario: Soft mode reduces form to mood only
- **GIVEN** the soft-mode banner is shown
- **WHEN** the user clicks «мягкий режим»
- **THEN** only the `mood_score` slider and «Сохранить» button are visible
- **AND** energy/anxiety sliders and optional blocks are hidden
- **AND** clicking «полная форма» restores the full form

#### Scenario: No soft-mode banner in non-low states
- **GIVEN** the latest entry has `state_label='balanced'`
- **WHEN** the user opens `/check-in`
- **THEN** no soft-mode banner is shown

### Requirement: Mobile-friendly layout
The system SHALL keep the entries page readable and functional on a mobile viewport of 375px with touch targets at least 44px in the primary interaction zones.

#### Scenario: Form and list usable at mobile width
- **WHEN** the entries page is rendered in a 375px viewport
- **THEN** the form and the list remain readable and usable without horizontal scrolling
- **AND** all form controls (range sliders, template tabs, submit button) are operable by touch
- **AND** submit button has `h-12` class (48px height)

#### Scenario: Range sliders have adequate touch targets
- **WHEN** the form is rendered on a mobile device
- **THEN** each range slider track has height ≥ 2rem (32px)
- **AND** the label + slider combination creates a touch target ≥ 44px tall

### Requirement: Feed page renders entries with rose-of-winds

The system SHALL render a `/feed` page as the landing page after login, displaying the user's entries grouped by date. The latest entry of the current day SHALL be rendered as a hero card with a 200×200px radar chart rendered client-side (canvas + Chart.js, see "Read-only radar chart renders rose-of-winds"). Other entries SHALL be rendered as compact cards with a state-label badge, timestamp, and axis values as text. In low/mixed states the insights widget SHALL be rendered above the hero card (emphasis on advice over state fixation). The page SHALL render an evening summary banner at the top (before the «Лента» heading) when local time is ≥ 18:00, today has entries, and the summary has not been shown today: тонкий однострочный баннер с тёмным фоном, левой акцентной полосой и крестиком закрытия — без сплошной цветной заливки (`alert-info` не используется) и без встроенной карточки инсайта (совет для состояния живёт только в отдельной карточке совета, см. feed-timeline). Страница не содержит инлайн-чата.

#### Scenario: Feed shows hero card with radar
- **GIVEN** the user is logged in and has entries today
- **WHEN** they open `/feed`
- **THEN** the latest entry today is rendered as a hero card
- **AND** the hero card contains a 200×200px canvas radar
- **AND** the hero card shows the state-label badge and timestamp
- **AND** the radar polygon reflects the entry's axis values

#### Scenario: Feed shows compact cards for other entries
- **GIVEN** the user has 3 entries today
- **WHEN** they open `/feed`
- **THEN** the latest entry is the hero card with radar
- **AND** the other 2 entries are compact cards (no radar)
- **AND** each compact card shows: «состояние» label, state badge, timestamp, and «энергия X · тревога Y · фокус Z» text

#### Scenario: Feed groups past days under date headers
- **GIVEN** the user has entries on 2026-08-21 and 2026-08-20
- **WHEN** they open `/feed`
- **THEN** past-day entries appear under date headers («21 августа», «20 августа»)
- **AND** past-day cards use `bg-base-300` (more compact than today's `bg-base-200`)

#### Scenario: Feed empty state
- **GIVEN** the user has no entries today
- **WHEN** they open `/feed`
- **THEN** an onboarding message «Как ты? Создай первую запись» is shown
- **AND** a button linking to `/check-in` is displayed `[ref: A3-q1]`

#### Scenario: Insights widget raised above hero in low state
- **GIVEN** the latest entry has `state_label='low'` or `mixed`
- **WHEN** they open `/feed`
- **THEN** the insights widget is rendered above the hero card (not below it)
- **AND** the hero card remains visible below the widget `[ref: design.md Decision 14.4]`

#### Scenario: Evening summary banner at top of feed
- **GIVEN** local time is ≥ 18:00, today has entries, and the summary was not shown today
- **WHEN** they open `/feed`
- **THEN** a thin one-line banner «Сводка на завтра готова» is rendered before the «Лента» heading
- **AND** the banner uses dark surface with a left accent stripe, no solid `alert-info` fill
- **AND** the banner has a «посмотреть» link and a dismiss cross, dismisses for the day `[ref: A4-q1, OQ6]`
- **AND** the banner does not embed the advice/insight content (no duplication with the advice card)

#### Scenario: No summary banner before 18:00
- **GIVEN** local time is before 18:00
- **WHEN** they open `/feed`
- **THEN** no evening summary banner is rendered
- **AND** no empty placeholder is shown

#### Scenario: No inline chat on feed
- **GIVEN** пользователь на `/feed`
- **WHEN** страница отрендерена
- **THEN** в теле ленты нет кнопки «Чат» и инлайн-панели `#ai-chat-open`

### Requirement: Feed is the landing page after login
The system SHALL redirect authenticated users from `/` and `/dashboard` to `/feed`.

#### Scenario: Root redirects to feed
- **GIVEN** an authenticated user navigates to `/`
- **WHEN** the server processes the request
- **THEN** the user is redirected to `/feed`

#### Scenario: Dashboard redirects to feed
- **GIVEN** an authenticated user navigates to `/dashboard`
- **WHEN** the server processes the request
- **THEN** the user is redirected to `/feed`

### Requirement: Read-only radar chart renders rose-of-winds
The system SHALL render a read-only radar chart («роза ветров») on the feed page for the latest entry of the current day as a 200×200px `<canvas role="img">` drawn client-side by Chart.js (pinned 4.x via CDN). The server SHALL render the canvas element with: a server-generated `aria-label` containing the axis values («Роза ветров: энергия X, тревога Y, фокус Z»), and a `data-gm-radar` attribute with JSON payload `{labels, values}` where labels are i18n-generated axis names and values are the axis values (0–10, nil allowed for absent). The chart SHALL display 3 mandatory axes (energy, anxiety, focus) and optionally a 4th axis (mood_score), ordered so that energy points up and axes proceed clockwise (θ_i = −π/2 + 2π·i/n). Visual style: radial gradient fill from secondary to primary color, white point markers with primary-color border and soft glow, circular grid rings in low-opacity base-content color, hidden radial ticks (scale 0–10), i18n point labels.

#### Scenario: Radar renders 3-axis polygon
- **GIVEN** the latest entry today has energy=4, anxiety=7, focus=3, mood_score=nil
- **WHEN** the feed page renders the hero card
- **THEN** a canvas of 200×200px is rendered with `role="img"` and server-generated aria-label «Роза ветров: энергия 4, тревога 7, фокус 3»
- **AND** the `data-gm-radar` attribute contains JSON with 3 i18n labels and values [4,7,3]
- **AND** Chart.js draws a 3-vertex polygon after page load

#### Scenario: Radar renders 4-axis polygon with mood_score
- **GIVEN** the latest entry today has energy=4, anxiety=7, focus=3, mood_score=5
- **WHEN** the feed page renders the hero card
- **THEN** the `data-gm-radar` payload has 4 labels/values
- **AND** the polygon has 4 vertices (energy, anxiety, focus, mood_score)

#### Scenario: Radar redraws after htmx navigation
- **GIVEN** the user navigates via hx-boost links (feed → check-in → feed)
- **WHEN** the feed body is swapped by htmx
- **THEN** the new canvas is drawn (initializer listens to DOMContentLoaded and htmx:afterSettle)
- **AND** canvases are drawn exactly once (`data-gm-drawn` marker prevents redraw loops)

#### Scenario: Radar is read-only
- **GIVEN** the radar canvas is rendered on the feed page
- **WHEN** the user interacts with it
- **THEN** no interactive elements (sliders, inputs) are present within the chart
- **AND** editing is only possible via the `/check-in` form sliders

#### Scenario: Radar adapts to theme colors
- **GIVEN** the app uses DaisyUI dark theme (`data-theme="dark"`)
- **WHEN** the radar is drawn
- **THEN** fill gradient, point borders and glow use colors read from CSS custom properties (`--color-primary`, `--color-secondary`)
- **AND** grid and labels use base-content-derived colors
- **AND** the radar is visible on dark background without hardcoded black/white colors

#### Scenario: Degradation without JavaScript
- **GIVEN** JavaScript is disabled or the Chart.js CDN is blocked
- **WHEN** the feed page renders
- **THEN** the canvas remains empty without runtime errors breaking the page
- **AND** the axis values remain accessible via the hero card's `axes-line` text and metric chips

### Requirement: Rule-based state label derivation
The system SHALL derive a human-readable state label from the energy and anxiety axis values using a deterministic rule-based function (no AI). The function SHALL return one of 6 keywords: `:state/mixed`, `:state/anxiety`, `:state/elevated`, `:state/low`, `:state/balanced`, `:state/neutral`. Rules SHALL be evaluated first-match-wins in specificity-descending order.

#### Scenario: Mixed state detected
- **GIVEN** energy=8, anxiety=7
- **WHEN** the state label is derived
- **THEN** the result is `:state/mixed` (rule: `energy >= 7 AND anxiety >= 6`)

#### Scenario: Anxiety state detected
- **GIVEN** energy=4, anxiety=7
- **WHEN** the state label is derived
- **THEN** the result is `:state/anxiety` (rule: `anxiety >= 6`, after mixed rule fails)

#### Scenario: Elevated state detected
- **GIVEN** energy=9, anxiety=2
- **WHEN** the state label is derived
- **THEN** the result is `:state/elevated` (rule: `energy >= 7`)

#### Scenario: Low state detected
- **GIVEN** energy=2, anxiety=3
- **WHEN** the state label is derived
- **THEN** the result is `:state/low` (rule: `energy <= 3`)

#### Scenario: Balanced state detected
- **GIVEN** energy=5, anxiety=4
- **WHEN** the state label is derived
- **THEN** the result is `:state/balanced` (rule: `energy 4-6 AND anxiety 4-5`)

#### Scenario: Neutral state detected
- **GIVEN** energy=5, anxiety=2
- **WHEN** the state label is derived
- **THEN** the result is `:state/neutral` (default/else branch)

#### Scenario: Focus axis does not affect label
- **GIVEN** energy=4, anxiety=7, focus=10
- **WHEN** the state label is derived
- **THEN** the result is `:state/anxiety` (focus is not used in rule evaluation) `[ref: A3-q2, A1-q2]`

### Requirement: User can override state label
The system SHALL allow the user to manually change the state label on an entry by selecting from the same 6 keyword options. The override SHALL be persisted in the `state_label` column; when `state_label` is non-NULL it SHALL be displayed as-is and SHALL NOT be recomputed from rules.

#### Scenario: User overrides auto-derived label
- **GIVEN** an entry has energy=4, anxiety=7, state_label=NULL (auto-derived as `:state/anxiety`)
- **WHEN** the user selects `:state/low` from the label dropdown
- **THEN** `state_label` is persisted as `:state/low`
- **AND** the feed displays «спад» (the override), not «тревога» (auto-derived)

#### Scenario: Null state label auto-derives on render
- **GIVEN** an entry has energy=9, anxiety=2, state_label=NULL
- **WHEN** the feed renders the entry card
- **THEN** the label is auto-derived as `:state/elevated` and displayed as «подъём»
- **AND** no value is read from `state_label` (it is NULL) `[ref: A3-q2, A1-q2]`

### Requirement: State label is text, not color or icon
The system SHALL render state labels as localized text (keyword → `i18n/t`) using a DaisyUI badge (`badge-secondary`), not as a color swatch, icon, or emoji.

#### Scenario: Label rendered as text badge
- **GIVEN** an entry with state label `:state/mixed`
- **WHEN** the feed renders the entry card
- **THEN** a `<span class="badge badge-secondary">` contains the localized text «смешанное»
- **AND** no emoji, icon, or color-only indicator is used for the label

### Requirement: State is not bipolar good/bad
The system SHALL NOT reduce state to a binary «good mood» vs «bad mood» dimension; the multi-axis radar and the 6-label set reflect the spectral nature of affective instability.

#### Scenario: Mixed state logged without forced binary choice
- **GIVEN** a user feels simultaneously high energy and high anxiety
- **WHEN** they create an entry with energy=8, anxiety=7
- **THEN** the state label `:state/mixed` is derived
- **AND** the system does not force the user to choose «good» or «bad» `[ref: A1-q2, A2-q7]`

### Requirement: AI-proposed state deferred to Phase 5
The system SHALL NOT propose a state distribution or label via AI in Phase 2. AI-proposed rose and AI-override of user-corrected rose are deferred to Phase 5 (`add-ai-correlations`).

#### Scenario: No AI proposal in Phase 2
- **GIVEN** a user creates an entry in Phase 2
- **WHEN** the entry is saved and rendered on the feed
- **THEN** the state label is derived purely from the rule-based function
- **AND** no AI model is invoked for state proposal

### Requirement: Multiple entries per day with timestamps
The system SHALL allow the user to create multiple entries per day, each with its own `created_at` timestamp, without an enforced schedule. The feed SHALL display entries grouped by date (today, yesterday, date headers) and sorted by `created_at` descending within each day.

#### Scenario: Two entries same day displayed with timestamps
- **GIVEN** the user created an entry at 07:10 and another at 14:15 today
- **WHEN** the feed renders
- **THEN** both entries appear under the «Сегодня» header
- **AND** the 14:15 entry appears first (most recent first within the day)
- **AND** the 07:10 entry appears second
- **AND** each card displays its timestamp as «14:15» and «07:10» `[ref: A3-q6, A1-q2]`

#### Scenario: Past days grouped under date headers
- **GIVEN** the user has entries on 2026-08-21 and 2026-08-20
- **WHEN** the feed renders
- **THEN** entries for 2026-08-21 appear under a «21 августа» header
- **AND** entries for 2026-08-20 appear under a separate «20 августа» header
- **AND** past-day cards use a more compact style (`bg-base-300`) than today's cards (`bg-base-200`)

### Requirement: No mandatory entry cadence
The system SHALL NOT enforce a fixed cadence; entries are created on demand. Days with no entries SHALL NOT be marked as «incomplete» or trigger guilt messaging.

#### Scenario: Sparse entries not flagged
- **GIVEN** the user logged once yesterday and three times today
- **WHEN** the feed renders
- **THEN** both histories are correct and not flagged as incomplete
- **AND** no «you missed a day» or streak-break message is shown `[ref: A2-q3]`

### Requirement: Nullable state_period_id foresees Phase 6
The system SHALL include a nullable `state_period_id` column on `entries` from Phase 2, so Phase 6 can add state periods without a breaking schema change. The column SHALL remain NULL in Phase 2 (no `state_periods` table exists yet).

#### Scenario: Entry created without state period
- **GIVEN** migration 006 is applied
- **WHEN** a new entry is created without an active state period
- **THEN** `state_period_id` is NULL
- **AND** no error occurs `[ref: OQ7]`

### Requirement: State periods deferred to Phase 6
The system SHALL NOT auto-create state periods or require the user to mark period start/end in Phase 2. State periods with explicit begin/end are deferred to Phase 6 (`add-entry-granularity-periods`).

#### Scenario: No period UI in Phase 2
- **GIVEN** a user has 3 consecutive low-energy days in Phase 2
- **WHEN** they view the feed
- **THEN** no «start period» or «end period» button is shown
- **AND** no period is auto-created `[ref: A3-q6]`

### Requirement: State period with explicit start/end (Phase 6)
The system SHALL support «state periods» — explicitly marked begin and end of a sustained state (e.g. «depressive episode», «hypomanic period», «anxious week») with arbitrary length. Periods SHALL be user-defined (not auto-created); auto-detection of episode onset is a separate Phase 7 AI feature with explicit guardrails.

#### Scenario: User marks start of a depressive period
- **WHEN** пользователь чувствует начало спада и нажимает «начать период: спад»
- **THEN** создаётся `state_period` с `started_at`, без `ended_at`
- **AND** последующие записи автоматически привязываются к этому периоду

#### Scenario: User closes a period
- **WHEN** у пользователя открыт период «спад» и он нажимает «закрыть период»
- **THEN** `state_period.ended_at` заполняется
- **AND** период доступен для ретроспективного анализа

#### Scenario: Periods are user-defined, not auto-detected
- **GIVEN** пользователь 3 дня в плохом состоянии, но не отметил период
- **WHEN** смотрит ленту
- **THEN** система не создаёт период автоматически
- **AND** может предложить (soft hint) отметить период, но не настаивает

### Requirement: Entries can be linked to a state period
The system SHALL allow entries to be optionally linked to an active state period, so retrospective analysis can group entries by episode. Entry created during an active period SHALL be automatically linked via `entries.state_period_id`.

#### Scenario: Entry created during an active period
- **WHEN** у пользователя активен период «подъём» и он создаёт новую запись
- **THEN** запись сохраняется с `state_period_id` = id активного периода
- **AND** фильтр «показать записи из периода X» доступен в ретроспективе

## MODIFIED Requirements

### Requirement: Entries table schema
The system SHALL provide a database table `entries` with columns `id`, `date`, `mood_score` (0–10), `energy` (0–10), `anxiety` (0–10), `focus` (0–10, nullable), `aggression` (0–10, nullable), `sleep_hours` (nullable), `note` (nullable), `activity` (nullable), `effect` (nullable), `template` (nullable), `created_at`.

#### Scenario: Table exists after migration
- **WHEN** migrations 002, 003, and 004 are applied
- **THEN** the `entries` table exists
- **AND** it contains columns `id`, `date`, `mood_score`, `energy`, `anxiety`, `sleep_hours`, `created_at`, `user_id`
- **AND** it contains nullable columns `focus`, `aggression`, `note`, `activity`, `effect`, `template`

#### Scenario: New required columns have default values
- **WHEN** migration 004 is applied
- **THEN** existing rows have `energy` and `anxiety` set to NULL (columns are nullable for backward compatibility)
- **AND** domain logic enforces non-null for new entries

#### Scenario: Aggression column is nullable and has no DB CHECK
- **WHEN** migration `004-aggression-axis.up.sql` is applied
- **THEN** column `aggression INTEGER` is added to `entries`
- **AND** the column is nullable
- **AND** no CHECK constraint is created (0–10 is enforced by the domain layer)
- **AND** existing data in `entries` is preserved

### Requirement: Entry data access functions
The system SHALL update `create-entry!` and `get-entries` in `app.db.entries` to handle the new field set, including the optional `aggression` axis.

#### Scenario: Create entry with new core fields
- **WHEN** `create-entry!` is called with `mood_score`, `energy`, `anxiety`
- **THEN** the entry is persisted with all three core values
- **AND** optional fields (`focus`, `aggression`, `sleep_hours`, `note`, `activity`, `effect`, `template`) are stored as provided or NULL

#### Scenario: Create entry with optional fields
- **WHEN** `create-entry!` is called with core fields plus `aggression` = 6, `sleep_hours` = 7.5, `note` = "текст заметки", `template` = "morning"
- **THEN** the entry is persisted with all provided values
- **AND** non-provided optional fields are NULL

#### Scenario: Create entry without aggression
- **WHEN** `create-entry!` is called without `aggression` (nil)
- **THEN** the entry is persisted with `aggression` = NULL
- **AND** no error occurs

#### Scenario: Existing entries still retrievable
- **WHEN** `get-entries` is called after migration 004
- **THEN** entries created before migration are returned with new columns as NULL
- **AND** entries created after migration include new column values

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
The system SHALL validate the `POST /entries` request body. Mandatory fields `mood_score`, `energy`, `anxiety` are required. Optional fields (`focus`, `aggression`, `sleep_hours`, `note`, `activity`, `effect`, `template`) are nullable. For requests without an `HX-Request` header an invalid body produces HTTP status 400 with a JSON validation error description; for htmx requests an invalid body produces HTTP status 400 with an HTML fragment containing the validation error message using alert-warning class.

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

#### Scenario: Out-of-range aggression rejected
- **WHEN** a `POST /entries` request is sent with `aggression` = 15 (outside 0–10)
- **THEN** the server responds with HTTP status 400
- **AND** the response body contains a description of the validation error
- **AND** no row is inserted

#### Scenario: Empty optional fields accepted
- **WHEN** a `POST /entries` request is sent with valid core fields but `sleep_hours` is empty string
- **THEN** the server responds with HTTP status 201
- **AND** `sleep_hours` is coerced to nil in the created entry

### Requirement: Three-axis core with range sliders
The system SHALL provide a mobile-first entry form with three mandatory core fields: mood_score, energy, and anxiety — each as a range slider 0–10 — plus an additional optional `aggression` axis rendered as a range slider 0–10 with two end anchor labels (left «Добрячок», right «Мудак», localized).

#### Scenario: Core fields always visible
- **GIVEN** форма открыта с любым шаблоном
- **WHEN** пользователь видит форму
- **THEN** поля mood_score (range 0–10), energy (range 0–10) и anxiety (range 0–10) отображаются всегда
- **AND** все три поля обязательны для отправки формы
- **AND** каждое поле показывает текущее значение рядом со слайдером `[ref: A3-q1, A2-q1]`

#### Scenario: Aggression slider visible with end anchors
- **GIVEN** форма открыта
- **WHEN** пользователь видит форму
- **THEN** слайдер aggression (range 0–10) отображается после focus
- **AND** слева от слайдера подпись «Добрячок», справа — «Мудак» (локализованы)
- **AND** поле не обязательно для отправки формы

#### Scenario: Core fill under 5 seconds
- **GIVEN** пользователь открыл форму
- **WHEN** совершает три движения пальца (mood_score + energy + anxiety)
- **THEN** ядро заполнено
- **AND** целевое время ≤ 5 секунд `[ref: A3-q1, A2-q3]`

#### Scenario: Range sliders visually distinct
- **GIVEN** форма с range-слайдерами
- **WHEN** форма рендерится
- **THEN** mood_score использует range-primary, energy — range-success, anxiety — range-warning
- **AND** каждый слайдер имеет высоту не менее 2rem (32px)
- **AND** общая touch-зона (label + слайдер) ≥ 44px `[ref: A3-q1]`

## ADDED Requirements

### Requirement: Aggression axis with inverted polarity
The system SHALL support an optional `aggression` axis alongside energy, anxiety and focus, stored as an integer 0–10 in `entries.aggression` and validated by the domain layer. High aggression SHALL mean a worse state (inverted polarity, unlike energy/focus where high is good). The rule-based `state-label` SHALL NOT use `aggression`.

#### Scenario: Aggression accepted in range
- **WHEN** `create-entry` is called with `aggression` = 7
- **THEN** the entry is persisted with `aggression` = 7
- **AND** `aggression` is stored as an integer

#### Scenario: Out-of-range aggression rejected by domain
- **WHEN** `create-entry` is called with `aggression` = 15 (outside 0–10)
- **THEN** the domain layer rejects the entry via malli schema validation
- **AND** no row is inserted into the database

#### Scenario: State label ignores aggression
- **GIVEN** energy=4, anxiety=7, aggression=10
- **WHEN** the state label is derived
- **THEN** the result is `:state/anxiety` (aggression is not used in rule evaluation)

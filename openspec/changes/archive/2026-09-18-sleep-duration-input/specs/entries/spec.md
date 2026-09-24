## MODIFIED Requirements

### Requirement: Optional blocks as DaisyUI collapses
The system SHALL provide optional entry fields as collapsible blocks using DaisyUI `collapse collapse-arrow`.

#### Scenario: Optional blocks collapsed by default
- **GIVEN** форма открыта
- **WHEN** пользователь видит секцию «Дополнительно (необязательно)»
- **THEN** все опциональные блоки (focus, sleep, note, activity) отображаются в свёрнутом состоянии
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
- **GIVEN** пользователь раскрывает блок «Сон» (без «(часы)» в заголовке)
- **WHEN** вводит часы и выбирает минуты
- **THEN** длительность сна — два поля: часы (number, min 0, max 24) и минуты (select с шагом 15: 0/15/30/45), визуально объединены (daisyUI join)
- **AND** блок не обязателен: оба поля пусты → sleep_hours = nil

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

## ADDED Requirements

### Requirement: Sleep duration accepted as hours and minutes
The system SHALL accept sleep duration as decimal hours in `sleep_hours` plus an optional `sleep_minutes` (integer, step 15) and convert the pair to decimal hours (`sleep_hours + sleep_minutes/60`) server-side. A single numeric or decimal-string `sleep_hours` without `sleep_minutes` SHALL remain accepted (API backward compatibility). No sleep SHALL be recorded (nil) when both are absent/blank, or when hours are blank and minutes = 0 (form default).

#### Scenario: Hours plus minutes converted to decimal hours
- **WHEN** форма/API передаёт `sleep_hours` = 7 и `sleep_minutes` = 30
- **THEN** в БД сохраняется 7.5

#### Scenario: Single numeric sleep_hours without minutes (backward compat)
- **WHEN** API передаёт только `sleep_hours` = 8.0
- **THEN** в БД сохраняется 8.0 (`sleep_minutes` отсутствует — не ошибка)

#### Scenario: Both empty means no sleep
- **WHEN** оба поля пусты / отсутствуют / пустые строки
- **THEN** `sleep_hours` записи = nil (существующая blank→nil коерция сохраняется)

#### Scenario: Zero minutes without hours means no sleep (edit-form default)
- **GIVEN** edit-форма всегда шлёт select минут (дефолт 0)
- **WHEN** передано `sleep_hours` пусто и `sleep_minutes` = 0
- **THEN** `sleep_hours` записи = nil (сон 0ч0м у записи без сна не «создаётся»)

#### Scenario: Minutes without hours
- **WHEN** передано `sleep_minutes` = 30 без `sleep_hours`
- **THEN** в БД сохраняется 0.5

### Requirement: Sleep displayed as hours and minutes
The system SHALL display stored sleep duration everywhere it is shown (feed-карточка, item-фрагмент POST /entries, карточка /entries/:id) in «7ч30м» format with localized units, computed as целые часы + округлённые до целого минуты (round((hours − trunc(hours)) × 60)).

#### Scenario: Half-hour value displayed as 7ч30м
- **GIVEN** запись с sleep_hours = 7.5
- **WHEN** рендерится карточка
- **THEN** отображается «7ч30м» (ru) / «7h30m» (en)

#### Scenario: Legacy decimal value displayed honestly
- **GIVEN** запись с sleep_hours = 7.2 (шаг 0.1 из старой формы)
- **WHEN** рендерится карточка
- **THEN** отображается «7ч12м» (7.2 × 60 = 432 мин → 7 ч 12 м)

#### Scenario: Entry without sleep shows no sleep line
- **GIVEN** запись с sleep_hours = nil
- **WHEN** рендерится карточка
- **THEN** строка сна не отображается

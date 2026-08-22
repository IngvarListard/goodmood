# Mobile Entry Specification

## Purpose
Provide a mobile-first entry form for recording mood state: a three-axis core (mood_score, energy, anxiety) as range sliders, optional collapsible blocks (focus, sleep_hours, note, activity), a template selector (morning/day/evening/event), dark theme as default, soft validation alerts, and htmx-based insertion of new entries into the feed.
## Requirements
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


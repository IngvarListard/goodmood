# Mood States Specification (Hypothesis)

## Purpose

«Роза ветров состояний» — многомерное распределение состояния по нескольким осям (энергия / тревога / фокус / аффект / …). Заменяет одну шкалу `mood_score` как основную модель состояния. AI может предлагать распределение, человек всегда vetoит/перераспределяет. Производная «роль/ярлык» состояния легко сменяема пользователем.

**Статус:** гипотеза. Оси и их количество — open question OQ2 (см. `product-vision/design.md`). Финализируется в имплементационном change `add-mood-states-rose` (Фаза 2).

## ADDED Requirements

### Requirement: Multi-axis state representation
The system SHALL represent a user's state at a point in time as a distribution across multiple axes (energy, anxiety, focus, affect, …), not as a single scalar.

#### Scenario: Entry captures multi-axis state
- **GIVEN** пользователь создаёт запись
- **WHEN** заполняет оси состояния
- **THEN** в БД сохраняется распределение по всем осям
- **AND** одна цифра `mood_score` остаётся опциональной, не основной `[ref: A3-q2, A2-q1]`

### Requirement: User can override system-proposed state
The system MAY propose a state distribution based on form inputs or AI, but the user SHALL always be able to manually redistribute the axes.

#### Scenario: User corrects AI-proposed rose
- **GIVEN** на странице «лента/мой день» система автоматически определила розу ветров
- **WHEN** пользователь вручную перераспределяет оси
- **THEN** новое распределение сохраняется как актуальное
- **AND** инсайты/рекомендации пересчитываются под новое распределение `[ref: A3-q2]`

### Requirement: Derived state label
The system SHALL derive a human-readable state label («роль» / «ярлык») from the rose-of-winds distribution, and the user SHALL be able to change it easily.

#### Scenario: User changes state label
- **GIVEN** система предложила ярлык «тревожник» для текущей розы
- **WHEN** пользователь меняет ярлык на «романтик»
- **THEN** ярлык сохраняется, привязан к текущей записи/периоду
- **AND** роза ветров не обязательно меняется (ярлык — производная, но редактируемая) `[ref: A3-q2, A1-q2]`

### Requirement: Multiple states per day
The system SHALL support multiple state entries per day, since intraday mood shifts are common.

#### Scenario: User logs morning and evening states
- **GIVEN** пользователь утром создал запись с розой A
- **WHEN** вечером создаёт новую запись с розой B
- **THEN** обе записи сохраняются с разными временными метками
- **AND** день отображается с двумя точками состояния `[ref: A3-q6, A1-q2]`

### Requirement: State is not bipolar «good/bad»
The system SHALL NOT reduce state to a binary «good mood» vs «bad mood» dimension; the multi-axis representation is mandatory and reflects the spectral nature of affective instability.

#### Scenario: User logs a mixed state
- **GIVEN** пользователь чувствует одновременно высокую энергию и высокую тревогу
- **WHEN** заполняет розу ветров
- **THEN** обе оси могут быть высокими одновременно
- **AND** система не заставляет выбирать «хорошо» или «плохо» `[ref: A1-q2, A2-q7]`

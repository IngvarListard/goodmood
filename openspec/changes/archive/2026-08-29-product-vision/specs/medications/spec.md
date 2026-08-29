# Medications Specification (Hypothesis)

## Purpose

Медикаменты — first-class фактор в трекере. Расписание приёма, дозировка, пропуски учитываются наравне (и с более высоким весом) с другими факторами при анализе состояния и подборе инсайтов. Это объективная переменная, которую пользователь может изменить, в отличие от субъективных шкал.

**Статус:** гипотеза. Финализируется в имплементационном change `add-medications` (Фаза 1).
**Модель данных зафиксирована (2026-08-19):** три таблицы (medications, medication_logs, medication_dose_changes). См. `product-vision/design.md` Decision 4а.

## ADDED Requirements

### Requirement: Medication registry
The system SHALL allow the user to maintain a registry of medications they take, including name, dose, schedule, and optional notes.

#### Scenario: User adds a medication
- **GIVEN** пользователь начинает приём нового медикамента
- **WHEN** добавляет его в реестр с дозой и расписанием
- **THEN** медикамент сохраняется
- **AND** доступен для отметки приёма по расписанию `[ref: A3-q5]`

### Requirement: Daily medication logging
The system SHALL allow the user to log each medication intake (taken / skipped / delayed) with timestamp and actual dose if different from scheduled.

#### Scenario: User logs a missed dose
- **GIVEN** у пользователя медикамент с расписанием «утро 8:00»
- **WHEN** отмечает пропуск на сегодня
- **THEN** в `medication_logs` сохраняется запись о пропуске с timestamp
- **AND** это учитывается в анализе состояния на этот день `[ref: A3-q5]`

### Requirement: Medications integrated into state analysis
The system SHALL include medication factors (dose, changes, skips) in correlation analysis alongside sleep, activity, and mood axes. Weight of medication factors SHALL be higher than subjective axes due to their objective nature.

#### Scenario: Correlation between dose change and mood shift
- **GIVEN** пользователь изменил дозу медикамента на неделе N
- **WHEN** система ищет корреляции
- **THEN** изменение дозы рассматривается как потенциальный фактор сдвига состояния
- **AND** вес этого фактора выше, чем у субъективных осей `[ref: A3-q5, A1-q2]`

### Requirement: Medication-aware insight matching
The system SHALL consider medication context when matching insights to the current state.

#### Scenario: Same rose-of-winds, different meds → different insights
- **GIVEN** у пользователя два исторических периода с близкой розой ветров, но разными медикаментами
- **WHEN** текущее состояние близко к обоим
- **THEN** приоритет отдаётся инсайтам из периода с тем же медикаментозным контекстом
- **AND** система может явно отметить: «в прошлый раз на этой дозе тебе помогло…» `[ref: A3-q5]`

### Requirement: Dose change tracking
The system SHALL track dose changes over time as first-class events (not just updates to current dose), so correlations with state shifts can be analysed.

#### Scenario: User changes dose and later correlates
- **GIVEN** пользователь увеличил дозу 2 недели назад
- **WHEN** смотрит статистику за месяц
- **THEN** видит событие изменения дозы на таймлайне
- **AND** может сравнить состояние до и после изменения `[ref: A3-q5, A1-q2]`

### Requirement: Optional medication privacy flag
The system SHALL allow the user to mark medications as especially sensitive, restricting their visibility in any future export or shared view (future-proofing for OQ4 — boundary of intervention).

#### Scenario: User marks medication A as sensitive
- **GIVEN** пользователь добавляет медикамент и отмечает «sensitive»
- **WHEN** в будущем появляется функция экспорта
- **THEN** этот медикамент исключается из экспорта по умолчанию
- **AND** пользователь должен явно включить его при экспорте `[ref: OQ4, proposal Non-goals — отчёт врачу drop]`

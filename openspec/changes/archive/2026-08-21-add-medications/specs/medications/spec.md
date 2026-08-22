# Medications Specification

## ADDED Requirements

### Requirement: Medication registry CRUD
The system SHALL allow the user to maintain a registry of medications with name, dose, dose_unit, schedule (JSON array of HH:MM strings), active flag, sensitive flag, and optional notes.

#### Scenario: User adds a medication
- **GIVEN** пользователь начинает приём нового медикамента
- **WHEN** открывает `/medications/new`, заполняет name, dose, dose_unit, schedule, нажимает «Сохранить»
- **THEN** медикамент сохраняется в `medications` с `active=1`
- **AND** появляется в списке активных `[ref: A3-q5]`

#### Scenario: User edits a medication
- **GIVEN** медикамент существует в реестре
- **WHEN** пользователь меняет дозу или расписание и сохраняет
- **THEN** `medications` обновляется (включая `updated_at`)
- **AND** если доза изменилась — создаётся запись в `medication_dose_changes` с `previous_dose` и `new_dose` `[ref: A3-q5, A1-q2]`

#### Scenario: User deactivates a medication
- **GIVEN** медикамент активен
- **WHEN** пользователь нажимает «Деактивировать» и подтверждает
- **THEN** `medications.active` устанавливается в 0
- **AND** медикамент не удаляется
- **AND** медикамент перемещается в collapse «Неактивные» `[ref: A3-q5]`

#### Scenario: User reactivates a medication
- **GIVEN** медикамент деактивирован
- **WHEN** пользователь нажимает «Активировать»
- **THEN** `medications.active` устанавливается в 1
- **AND** медикамент возвращается в список активных `[ref: A3-q5]`

#### Scenario: Empty registry shows onboarding
- **GIVEN** у пользователя нет медикаментов
- **WHEN** открывает `/medications`
- **THEN** отображается empty-state с сообщением и кнопкой «Добавить» `[ref: A3-q5]`

### Requirement: Daily medication logging
The system SHALL allow the user to log each scheduled medication intake as taken or skipped, with timestamp and optional actual dose.

#### Scenario: User logs taken dose
- **GIVEN** у медикамента расписание содержит слот "08:00"
- **WHEN** пользователь нажимает «Принял»
- **THEN** в `medication_logs` создаётся (или обновляется) запись со `status='taken'`, `taken_at=now()`
- **AND** слот отображает бейдж «принял» (badge-success) `[ref: A3-q5]`

#### Scenario: User logs skipped dose
- **GIVEN** у медикамента расписание содержит слот "08:00"
- **WHEN** пользователь нажимает «Пропустил»
- **THEN** в `medication_logs` создаётся запись со `status='skipped'`, `taken_at=NULL`
- **AND** слот отображает бейдж «пропустил» (badge-warning, не alert-error) `[ref: A3-q5]`

#### Scenario: User cancels a logged slot
- **GIVEN** слот уже отмечен (taken или skipped)
- **WHEN** пользователь нажимает «Отменить»
- **THEN** запись из `medication_logs` удаляется
- **AND** слот возвращается в состояние «не отмечен» `[ref: A3-q5]`

#### Scenario: Unlogged slot is not skipped
- **GIVEN** слот не отмечен пользователем
- **WHEN** пользователь смотрит виджет приёма
- **THEN** слот не имеет бейджа статуса
- **AND** это означает «неизвестно», а не «пропущено» (отсутствие записи ≠ status='skipped') `[ref: A3-q5]`

#### Scenario: Duplicate log prevented
- **GIVEN** слот "08:00" уже отмечен как taken
- **WHEN** пользователь снова нажимает «Принял»
- **THEN** запись обновляется (не дублируется) благодаря UNIQUE(medication_id, log_date, scheduled_time) `[ref: A3-q5]`

### Requirement: Dose change tracking
The system SHALL track dose changes as first-class events in a separate `medication_dose_changes` table, so correlations with state shifts can be analysed in future phases.

#### Scenario: Dose change creates history entry
- **GIVEN** медикамент с dose=300
- **WHEN** пользователь меняет дозу на 450 и сохраняет
- **THEN** `medications.dose` обновляется до 450
- **AND** в `medication_dose_changes` создаётся запись: `previous_dose=300, new_dose=450, changed_at=now()`
- **AND** если дозу не меняли — запись не создаётся `[ref: A3-q5, A1-q2]`

### Requirement: Schedule stored as JSON
The system SHALL store medication schedule as a JSON array of HH:MM strings in the `schedule` column.

#### Scenario: Schedule parsed from comma-separated input
- **GIVEN** пользователь вводит "08:00, 20:00" в поле расписания
- **WHEN** форма отправляется
- **THEN** сервер конвертирует в JSON `["08:00","20:00"]` и сохраняет в `schedule`
- **AND** при отображении сервер парсит JSON обратно в список слотов `[ref: A3-q5]`

### Requirement: Medication page with intake widget
The system SHALL render a `/medications` page with a today's intake widget (slots with taken/skipped buttons), active medication cards, and collapsed inactive medications.

#### Scenario: Page shows intake widget and active cards
- **GIVEN** у пользователя есть активные медикаменты с расписанием
- **WHEN** открывает `/medications`
- **THEN** сверху виджет сегодняшнего приёма со слотами
- **AND** ниже — карточки активных медикаментов
- **AND** неактивные свёрнуты в collapse `[ref: A3-q5]`

#### Scenario: Intake widget slot has touch-friendly buttons
- **GIVEN** виджет приёма рендерится на мобилке
- **WHEN** пользователь видит слот
- **THEN** кнопки «Принял» и «Пропустил» имеют touch-target ≥ 44px (h-11 min-h-11) `[ref: A3-q5]`

### Requirement: Sensitive flag stored
The system SHALL allow the user to mark medications as sensitive. The flag is stored but has no functional effect in Phase 1 (future-proofing for OQ4).

#### Scenario: User marks medication as sensitive
- **GIVEN** пользователь добавляет медикамент
- **WHEN** переключает toggle «Особо чувствительный»
- **THEN** `medications.sensitive` устанавливается в 1
- **AND** на карточке отображается бейдж «sensitive» (badge-neutral, ненавязчивый) `[ref: OQ4]`

## MODIFIED Requirements

### Requirement: Medications integrated into state analysis
The system SHALL include medication factors (dose, changes, skips) in correlation analysis alongside sleep, activity, and mood axes. Weight of medication factors SHALL be higher than subjective axes due to their objective nature.

**Phase 1 status: data capture only.** Correlation analysis is deferred to Phase 5 (AI). In Phase 1, medication data is captured and stored in `medications`, `medication_logs`, and `medication_dose_changes` tables, ready for future analysis. No correlation or weighting is computed.

#### Scenario: Data captured for future correlation
- **GIVEN** пользователь логирует приём медикаментов и записи настроения
- **WHEN** данные накапливаются в БД
- **THEN** `medication_logs` и `medication_dose_changes` содержат полные данные для будущего join с `entries` по дате
- **AND** корреляционный анализ не выполняется в Фазе 1 `[ref: A3-q5, A1-q2]`

### Requirement: Medication-aware insight matching
The system SHALL consider medication context when matching insights to the current state.

**Deferred to Phase 5.** Insight matching requires insight artifacts (Phase 3) and AI (Phase 5). In Phase 1, no insight matching is performed. Medication data is captured for future use.

#### Scenario: No insight matching in Phase 1
- **GIVEN** у пользователя есть медикаменты и записи настроения
- **WHEN** система работает в Фазе 1
- **THEN** medication-aware insight matching не выполняется
- **AND** данные готовы для Фазы 5 `[ref: A3-q5]`

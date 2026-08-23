# Entries Data Specification (Delta — Phase 3)

## Purpose

Уточнение `entries-data` для Фазы 3: добавляется односторонняя soft-ссылка из новой таблицы `insights` на `entries.id` через `insights.entry_id` (без FK). Схема таблицы `entries` НЕ меняется — связь хранится на стороне `insights`. При удалении записи `entry_id` остаётся dangling, что приемлемо по spec «No insight loss».

`[ref: A4-q5, A2-q4]`

## ADDED Requirements

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

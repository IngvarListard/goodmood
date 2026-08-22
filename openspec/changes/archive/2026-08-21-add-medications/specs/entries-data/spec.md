# Entries Data Specification

## ADDED Requirements

### Requirement: Medication logs link to entries by date
The system SHALL allow analytical joins between `medication_logs` and `entries` by matching `medication_logs.log_date` to `entries.date` for the same `user_id`. No foreign key constraint exists between the tables; the join is implicit by date.

#### Scenario: Join logs to entries for a day
- **WHEN** a query joins `medication_logs` and `entries` on `user_id` and `log_date = date`
- **THEN** all medication logs for a given day are associated with the mood entry for that day
- **AND** if no mood entry exists for a day, medication logs remain queryable independently `[ref: A3-q5]`

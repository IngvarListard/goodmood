## ADDED Requirements

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
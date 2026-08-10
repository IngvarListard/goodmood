## MODIFIED Requirements

### Requirement: Render entries page
The system SHALL render an HTML page containing an entry creation form with fields `activity`, `effect`, `mood_score`, `sleep_hours` and the list of stored diary entries when the entries page is requested by a browser. The page SHALL be wrapped in the common layout (html/head/body, navigation, CDN scripts).

#### Scenario: Page displays form and list
- **WHEN** a user opens `GET /entries` in a browser and the diary contains entries
- **THEN** the page shows the entry creation form
- **AND** the page shows the list of existing entries
- **AND** the page includes the desktop sidebar navigation
- **AND** the page includes the mobile bottom bar navigation

#### Scenario: Empty list
- **WHEN** a user opens `GET /entries` in a browser and there are no stored entries
- **THEN** the page shows the entry creation form
- **AND** the page shows an empty-state message in place of the list
- **AND** the page includes the desktop sidebar navigation
- **AND** the page includes the mobile bottom bar navigation

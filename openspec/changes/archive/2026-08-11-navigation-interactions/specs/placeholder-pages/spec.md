## ADDED Requirements

### Requirement: Every navigation route renders a placeholder page
The system SHALL render an HTML page for each route declared in the navigation items
(/dashboard, /check-in, /history, /statistics, /insights, /settings). Each page SHALL use the common
layout (html/head/body, navigation, CDN scripts) and SHALL contain the item's label as a heading.

#### Scenario: Dashboard page exists
- **WHEN** a user opens `GET /dashboard` in a browser
- **THEN** the response is 200 with a full HTML page
- **AND** the page includes the desktop sidebar and mobile bottom bar navigation
- **AND** the page heading contains the label "Дашборд"

#### Scenario: Every nav route is served
- **WHEN** a user opens `GET /check-in`, `GET /history`, `GET /statistics`, `GET /insights`, `GET /settings`
- **THEN** each request returns 200 with a full HTML page

### Requirement: Placeholder pages are empty of business logic
Each navigation placeholder SHALL contain no entry form, no data fetching, and no entry list.

#### Scenario: Placeholder has stub content only
- **WHEN** the dashboard page is rendered
- **THEN** the main content contains a stub message and no diary entry form
- **AND** no database queries are executed for the page

### Requirement: Active nav item matches the current route
The page SHALL mark as active the navigation item whose `:route` equals the request path.
The active item SHALL render with the active styling (solid icon variant and active menu classes);
all other items SHALL render inactive (outline icons).

#### Scenario: Statistics page highlights statistics item
- **WHEN** a user opens `GET /statistics`
- **THEN** the navigation marks the "Статистика" item as active (solid icon variant)
- **AND** all other items render with outline icons

#### Scenario: Each route highlights its own item
- **WHEN** a user opens any navigation route
- **THEN** the item with the matching `:route` is the only active item on the page
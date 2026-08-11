## MODIFIED Requirements

### Requirement: Mobile navigation is a fixed bottom bar
The mobile navigation SHALL be positioned as a fixed bar at the bottom, full width, stacked above
other content (z-index), with a safe-area bottom inset on devices with home indicators.

#### Scenario: Mobile bottom bar positioning
- **WHEN** layout is rendered
- **THEN** the mobile navigation wrapper has classes `md:hidden fixed bottom-0 inset-x-0`
- **AND** the wrapper has a z-index class (`z-50`)

#### Scenario: Safe-area inset on the bottom bar
- **WHEN** layout is rendered
- **THEN** the mobile navigation includes `pb-[env(safe-area-inset-bottom)]` on its bar or wrapper

## ADDED Requirements

### Requirement: Layout enables hx-boost navigation
The layout SHALL render an `hx-boost` attribute on `<body>` so navigation links are fetched via
AJAX and swapped without a full page reload. When JavaScript is unavailable, links SHALL still
navigate via normal full-page requests.

#### Scenario: Body has hx-boost
- **WHEN** layout is rendered
- **THEN** the `<body>` element has `hx-boost="true"`

#### Scenario: Links work without JavaScript
- **WHEN** the page is opened without JavaScript enabled and a navigation link is clicked
- **THEN** the browser performs a normal full-page navigation to the link's `href`
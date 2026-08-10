## ADDED Requirements

### Requirement: Layout renders a complete HTML page
The `layout` function SHALL produce a full HTML document with `html`, `head`, and `body` elements, including all required CDN scripts and stylesheets.

#### Scenario: Layout includes all CDN resources
- **WHEN** `(layout {:title "Test"} nav-items content)` is called
- **THEN** the result contains a `<script>` tag with the htmx CDN URL
- **AND** the result contains a `<script>` tag with the hyperscript CDN URL
- **AND** the result contains a `<script>` tag with the Tailwind browser CDN URL
- **AND** the result contains a `<link>` tag with the DaisyUI stylesheet URL

#### Scenario: Layout sets page title
- **WHEN** `(layout {:title "My Page"} nav-items content)` is called
- **THEN** the `<title>` element contains "My Page"

### Requirement: Layout embeds navigation in both mobile and desktop modes
The layout SHALL include both mobile and desktop navigation elements. The desktop variant is hidden on small screens and the mobile variant is hidden on large screens.

#### Scenario: Both navigation variants present in DOM
- **WHEN** layout is rendered
- **THEN** the DOM contains a desktop navigation element with class `hidden md:flex`
- **AND** the DOM contains a mobile navigation element with class `md:hidden`

### Requirement: Desktop navigation is a fixed sidebar
The desktop navigation SHALL be positioned as a fixed sidebar on the left, full height, 16rem wide.

#### Scenario: Desktop sidebar positioning
- **WHEN** layout is rendered
- **THEN** the desktop navigation wrapper has classes `hidden md:flex fixed left-0 top-0 h-screen w-64`

### Requirement: Mobile navigation is a fixed bottom bar
The mobile navigation SHALL be positioned as a fixed bar at the bottom, full width.

#### Scenario: Mobile bottom bar positioning
- **WHEN** layout is rendered
- **THEN** the mobile navigation wrapper has classes `md:hidden fixed bottom-0 inset-x-0`

### Requirement: Main content compensates for navigation
The `<main>` element SHALL have left padding to compensate for the desktop sidebar and bottom padding to compensate for the mobile bottom bar.

#### Scenario: Content padding
- **WHEN** layout is rendered
- **THEN** the `<main>` element has class `md:pl-64`
- **AND** the `<main>` element has class `pb-16`

### Requirement: Layout uses CSS media query for responsive switching
The mobile/desktop switch SHALL use only CSS media query classes (hidden/md:flex/md:hidden), not JavaScript width detection.

#### Scenario: No JavaScript width detection
- **WHEN** layout is rendered
- **THEN** no JavaScript-based viewport detection is present
- **AND** responsive behavior is achieved through CSS classes only

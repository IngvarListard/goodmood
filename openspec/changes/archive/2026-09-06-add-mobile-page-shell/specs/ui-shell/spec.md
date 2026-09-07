# ui-shell Delta: add-mobile-page-shell

## ADDED Requirements

### Requirement: Layout provides a single content column shell
The `layout` function SHALL wrap page content in a shell `div` inside `<main>` that centers content, constrains its width to `max-w-lg`, applies horizontal padding `px-4` and top padding `pt-4`, and applies a bottom padding that covers the mobile bottom bar height plus the device safe-area inset (`pb-[calc(env(safe-area-inset-bottom)+5rem)]`).

Page views SHALL NOT render their own top-level width or outer padding wrappers (`max-w-*`, `mx-auto`, `p-4`, `pb-24`); the shell is the single source of page width. The auth page (`app.views.auth`) is exempt.

#### Scenario: Shell classes present
- **WHEN** `(layout {:title "T"} nav-items content)` is called
- **THEN** the result contains a wrapper with classes `mx-auto`, `w-full`, `max-w-lg`, `px-4`, `pt-4`
- **AND** the wrapper has class `pb-[calc(env(safe-area-inset-bottom)+5rem)]`

#### Scenario: Pages render without own width wrappers
- **WHEN** any page (`/feed`, `/check-in`, `/medications`, `/settings`, `/insights`, `/insights/new`, `/insights/:id`) is rendered
- **THEN** its content does not contain top-level `max-w-md` or `max-w-2xl` wrappers
- **AND** its content does not contain `pb-24` outer padding

#### Scenario: Content is wider-constrained on desktop too
- **WHEN** the medications page is opened on a viewport wider than 512px (desktop with sidebar)
- **THEN** the content column is centered within the area right of the sidebar and does not exceed 512px

### Requirement: Viewport meta enables safe-area insets
The `head` SHALL include a viewport meta tag with `viewport-fit=cover` so that `env(safe-area-inset-*)` values are non-zero on devices with notches/home indicators, making the bottom bar safe-area padding and shell bottom padding effective.

#### Scenario: Viewport-fit cover present
- **WHEN** any page is rendered
- **THEN** the viewport meta tag content includes `viewport-fit=cover`

### Requirement: Body height uses dynamic viewport units
The `<body>` SHALL use `min-h-dvh` (dynamic viewport height) instead of `min-h-screen`, so mobile browser address bars do not cause height jumps.

#### Scenario: Body uses dvh
- **WHEN** layout is rendered
- **THEN** the `<body>` element has class `min-h-dvh`
- **AND** the `<body>` element does not have class `min-h-screen`

### Requirement: Shared form inputs do not trigger iOS focus zoom
The shared input style `.gm-input` SHALL use font-size of at least 16px, because iOS Safari auto-zooms focused inputs with font-size below 16px.

#### Scenario: gm-input font size
- **WHEN** any page is rendered
- **THEN** the inline stylesheet defines `.gm-input { ... font-size: 16px; ... }`

## MODIFIED Requirements

### Requirement: Main content compensates for navigation
The `<main>` element SHALL have left padding to compensate for the desktop sidebar. The bottom compensation for the mobile bottom bar SHALL live in the content shell (see "Layout provides a single content column shell"), not on `<main>`; `<main>` SHALL NOT carry a bottom padding class.

#### Scenario: Content padding
- **WHEN** layout is rendered
- **THEN** the `<main>` element has class `md:pl-64`
- **AND** the `<main>` element does not have class `pb-16`

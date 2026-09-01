## ADDED Requirements

### Requirement: Custom DaisyUI theme for contrast

The system SHALL apply custom CSS variables to override DaisyUI dark theme defaults, increasing text contrast for readability.

#### Scenario: Base content color override
- **WHEN** the application renders with `data-theme="dark"`
- **THEN** `--color-base-content` SHALL be `#e8e5df` (not DaisyUI default `#d8d5ce`)

#### Scenario: Secondary text colors
- **WHEN** text uses `text-base-content/60` class
- **THEN** the rendered color SHALL be `#b8b5af` or higher contrast

### Requirement: Consistent opacity-to-color mapping

All view files SHALL use `text-base-content/N` Tailwind classes instead of raw `opacity-N` for text elements.

#### Scenario: Feed page subheadings
- **WHEN** rendering "Состояние" label in `feed.clj`
- **THEN** the element SHALL use `text-base-content/60` class, not `opacity-50`

#### Scenario: Medication dose text
- **WHEN** rendering "600 мг · 08:00" in `medications.clj`
- **THEN** the element SHALL use `text-base-content/70` class, not `opacity-60`

### Requirement: Minimum tap target size

All interactive elements (buttons, links, toggles) SHALL have minimum height of 44px.

#### Scenario: Medication cancel button
- **WHEN** rendering "Отменить" button in `medications.clj`
- **THEN** the button SHALL have `min-h-[44px]` class

#### Scenario: Bottom nav items
- **WHEN** rendering mobile bottom navigation
- **THEN** each nav item SHALL have `min-h-[44px]` on the clickable area

### Requirement: Unified badge system

The system SHALL use consistent badge classes for medication statuses.

#### Scenario: Taken status
- **WHEN** a medication dose is marked as taken
- **THEN** the badge SHALL use `badge badge-success badge-sm` (filled)

#### Scenario: Skipped status
- **WHEN** a medication dose is marked as skipped
- **THEN** the badge SHALL use `badge badge-warning badge-outline badge-sm` (outlined)

#### Scenario: Sensitive medication
- **WHEN** a medication has `sensitive=1`
- **THEN** the badge SHALL use `badge badge-ghost badge-sm`

### Requirement: Active tab indicator

Bottom navigation SHALL visually distinguish the active tab with background color and dot indicator.

#### Scenario: Active tab rendering
- **WHEN** a bottom nav tab is active
- **THEN** the tab SHALL have `bg-primary/10` background class
- **AND** a dot indicator SHALL be rendered below the label

### Requirement: Uniform slider colors

All check-in sliders SHALL use the same color class.

#### Scenario: Mood, energy, anxiety sliders
- **WHEN** rendering range inputs in `check_in.clj`
- **THEN** all three sliders SHALL use `range-primary` class

### Requirement: Settings dividers replaced with CSS

Settings sections SHALL use `divide-y` instead of separate divider elements.

#### Scenario: AI settings toggles
- **WHEN** rendering toggle list in `ai.clj` settings section
- **THEN** the container SHALL use `divide-y divide-base-300` class
- **AND** no `divider` elements SHALL appear between toggle rows

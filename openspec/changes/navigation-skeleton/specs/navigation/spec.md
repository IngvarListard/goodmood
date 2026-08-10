## ADDED Requirements

### Requirement: Navigation component renders menu items from a single data source
The `navigation` component SHALL accept a vector of menu items and render each item as a clickable element with icon and label. The component SHALL support two variants: `:mobile` and `:desktop`.

#### Scenario: Mobile variant renders all items
- **WHEN** `(navigation :mobile nav-items)` is called with `nav-items` containing N items
- **THEN** the result contains exactly N `<button type="button">` elements
- **AND** each button displays the item's label text

#### Scenario: Desktop variant renders all items
- **WHEN** `(navigation :desktop nav-items)` is called with `nav-items` containing N items
- **THEN** the result contains exactly N `<button type="button">` elements
- **AND** each button displays the item's label text
- **AND** the result contains a heading element with the text "Good Mood"

### Requirement: Active item is visually highlighted
The navigation component SHALL highlight the currently active item when an `:active` key is passed. Comparison is by `:id`.

#### Scenario: Active item marked in mobile
- **WHEN** `(navigation :mobile nav-items {:active :dashboard})` is called
- **THEN** the button for the item with `:id :dashboard` has distinct styling (solid icon variant)
- **AND** other buttons do not have that styling

#### Scenario: Active item marked in desktop
- **WHEN** `(navigation :desktop nav-items {:active :check-in})` is called
- **THEN** the button for the item with `:id :check-in` has distinct styling (solid icon variant)
- **AND** other buttons do not have that styling

#### Scenario: No active item when :active is nil
- **WHEN** `(navigation :mobile nav-items)` is called without `:active`
- **THEN** no button has the active styling

### Requirement: Navigation uses buttons, not links
Each menu item SHALL be rendered as `<button type="button">`, not as `<a>` with `href`. No click handlers are attached.

#### Scenario: Items are buttons
- **WHEN** `(navigation :mobile nav-items)` is called
- **THEN** every menu item is a `<button type="button">` element
- **AND** no `<a>` elements are present in the menu items

### Requirement: Active item uses solid icon, inactive use outline
The navigation component SHALL render the solid variant of the icon for the active item and the outline variant for inactive items.

#### Scenario: Active item has solid icon
- **WHEN** `(navigation :desktop nav-items {:active :dashboard})` is called
- **THEN** the button for `:dashboard` renders a solid-variant icon

#### Scenario: Inactive items have outline icons
- **WHEN** `(navigation :desktop nav-items {:active :dashboard})` is called
- **THEN** buttons for items other than `:dashboard` render outline-variant icons

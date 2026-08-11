# Navigation Specification

## Purpose
Render the application navigation with two responsive variants (mobile bottom bar and desktop sidebar) from a single data source.

## Requirements

### Requirement: Navigation component renders menu items from a single data source
The `navigation` component SHALL accept a vector of menu items and render each item as a menu
link with icon and label. The component SHALL support two variants: `:mobile` (horizontal bottom
bar) and `:desktop` (vertical sidebar), both rendered as daisyUI `menu`. Items SHALL stretch to
equal width within their container so icons align on a common axis; the icon SHALL be placed in a
fixed-size box. On `:mobile` the icon SHALL stack above the label (compact bottom bar layout);
on `:desktop` the icon SHALL sit next to the label.

#### Scenario: Mobile variant renders all items
- **WHEN** `(navigation :mobile nav-items)` is called with `nav-items` containing N items
- **THEN** the result contains exactly N `<a>` elements with `href` from each item's `:route`
- **AND** each link displays the item's label text
- **AND** the result contains a `menu menu-horizontal` container

#### Scenario: Desktop variant renders all items
- **WHEN** `(navigation :desktop nav-items)` is called with `nav-items` containing N items
- **THEN** the result contains exactly N `<a>` elements with `href` from each item's `:route`
- **AND** each link displays the item's label text
- **AND** the result contains a `menu menu-vertical` container
- **AND** the result contains a heading element with the text "Good Mood"

#### Scenario: Icons are in fixed-size boxes
- **WHEN** `(navigation :desktop nav-items)` is called
- **THEN** every link contains an icon wrapper with classes `w-6` and `h-6`
- **AND** every link stretches to the full width of its list item

### Requirement: Active item is visually highlighted
The navigation component SHALL highlight the currently active item when an `:active` key is passed,
with comparison by `:id`. The active item SHALL use the solid icon variant and active menu classes;
inactive items SHALL use outline icons.

#### Scenario: Active item marked in mobile
- **WHEN** `(navigation :mobile nav-items {:active :dashboard})` is called
- **THEN** the link for `:dashboard` has the active styling (solid icon variant)
- **AND** other links do not have that styling

#### Scenario: Active item marked in desktop
- **WHEN** `(navigation :desktop nav-items {:active :check-in})` is called
- **THEN** the link for `:check-in` has the active styling (solid icon variant)
- **AND** other links do not have that styling

#### Scenario: No active item when :active is nil
- **WHEN** `(navigation :mobile nav-items)` is called without `:active`
- **THEN** no link has the active styling

### Requirement: Navigation items are links to routes
Each menu item SHALL render as an `<a>` element with an `href` taken from the item's `:route`.
The component SHALL NOT render `<button>` elements for menu items.

**Reason**: Активность определяется серверно по пути роута (hx-boost-навигация); ссылки —
семантически корректный элемент и ожидание daisyUI `menu` (li > a).

#### Scenario: Items are links
- **WHEN** `(navigation :mobile nav-items)` is called
- **THEN** every menu item is an `<a>` element with `href` matching its `:route`
- **AND** no `<button>` elements are present in the menu items

### Requirement: Active item uses solid icon, inactive use outline
The navigation component SHALL render the solid variant of the icon for the active item and the
outline variant for inactive items.

#### Scenario: Active item has solid icon
- **WHEN** `(navigation :desktop nav-items {:active :dashboard})` is called
- **THEN** the link for `:dashboard` renders a solid-variant icon

#### Scenario: Inactive items have outline icons
- **WHEN** `(navigation :desktop nav-items {:active :dashboard})` is called
- **THEN** links for items other than `:dashboard` render outline-variant icons
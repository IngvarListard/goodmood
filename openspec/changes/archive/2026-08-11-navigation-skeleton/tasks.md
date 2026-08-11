## 1. SVG Icon Loader (app.icons)

- [x] 1.1 Implement `app.icons/svg` function: accepts icon name and options map (`:variant`, `:class`), loads from `resources/icons/heroicons/{outline|solid}/<name>.svg`, wraps in `<span>` with `hiccup2.core/raw`
- [x] 1.2 Add memoization for `slurp` calls to avoid re-reading SVG files from disk
- [x] 1.3 Add validation: unknown `:variant` throws `ex-info`, missing icon file throws `ex-info` with name and path
- [x] 1.4 Write tests for `app.icons/svg`: correct paths, default `:outline`, memoization, ex-info on unknown icon/variant, raw SVG in span

## 2. Navigation Component (app.views.navigation)

- [x] 2.1 Create `app.views.navigation` namespace with `nav-items` vector (6 items: dashboard, check-in, history, statistics, insights, settings) using icons verified in `resources/icons/heroicons/`
- [x] 2.2 Implement `navigation` function: accepts `variant` (`:mobile` | `:desktop`), `items` vector, and optional opts map with `:active` key
- [x] 2.3 Implement `:mobile` rendering: horizontal row of `<button type="button">` elements with icon + label
- [x] 2.4 Implement `:desktop` rendering: vertical list of `<button type="button">` elements with icon + label, "Good Mood" heading at top
- [x] 2.5 Implement active item highlighting: active = solid icon variant, inactive = outline; `:active` compared by `:id`
- [x] 2.6 Write tests for navigation: renders all N items in both variants, active item marked, no active when nil, buttons not links

## 3. Layout Component (app.views.layout)

- [x] 3.1 Create `app.views.layout` namespace with `layout` function accepting `{:title ...}`, `nav-items`, and `content`
- [x] 3.2 Implement full HTML page structure: `html[lang="ru"]` > `head` (meta, title, CDN scripts/stylesheets) > `body[data-theme="light"]`
- [x] 3.3 Move CDN URL constants from `app.views.entries` to `app.views.layout` (htmx, hyperscript, Tailwind, DaisyUI)
- [x] 3.4 Embed desktop navigation: wrapper with `hidden md:flex fixed left-0 top-0 h-screen w-64`, calls `(navigation :desktop ...)`
- [x] 3.5 Embed mobile navigation: wrapper with `md:hidden fixed bottom-0 inset-x-0`, calls `(navigation :mobile ...)`
- [x] 3.6 Add `<main>` with `md:pl-64 pb-16` padding classes for content area
- [x] 3.7 Write tests for layout: includes all CDN resources, page title, both navigation variants present, correct positioning classes, content padding

## 4. Refactor Existing Page

- [x] 4.1 Refactor `app.views.entries/page`: remove html/head/body, CDN constants, return only content hiccup
- [x] 4.2 Update route handler in `app.views.entries/page` to use `app.views.layout/layout` for wrapping content
- [x] 4.3 Run existing tests (`clj -M:test`) to verify no regressions

## 5. Integration Verification

- [x] 5.1 Start application (`clj -M -m app.core`) and verify mobile bottom bar renders at narrow viewport
- [x] 5.2 Verify desktop sidebar renders at wide viewport
- [x] 5.3 Verify CSS media query switches between mobile/desktop at 768px breakpoint
- [x] 5.4 Verify active item highlights in both mobile and desktop views
- [x] 5.5 Run full test suite (`clj -M:test`) — all tests pass

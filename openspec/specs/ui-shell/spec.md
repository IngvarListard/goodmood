# UI Shell Specification

## Purpose

UI-оболочка: HTML-layout (CDN, responsive sidebar/bottom-bar, hx-boost), навигация (4 пункта, active highlighting, solid/outline icons), placeholder-страницы.
## Requirements
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

The mobile navigation SHALL be positioned as a fixed bar at the bottom, full width, stacked above other content (z-index), with a safe-area bottom inset on devices with home indicators. Высота бара SHALL быть фиксированной (один ряд пунктов) и выставляться CSS-переменной `--gm-nav-h` на обёртке панели; переменная SHALL быть доступна для позиционирования плавающих элементов (FAB) и расчёта отступов контента. Панель SHALL иметь z-index 50.

#### Scenario: Mobile bottom bar positioning

- **WHEN** layout is rendered
- **THEN** the mobile navigation wrapper has classes `md:hidden fixed bottom-0 inset-x-0`
- **AND** the wrapper has a z-index class (`z-50`)

#### Scenario: Safe-area inset on the bottom bar

- **WHEN** layout is rendered
- **THEN** the mobile navigation includes `pb-[env(safe-area-inset-bottom)]` on its bar or wrapper

#### Scenario: Высота панели доступна как переменная

- **WHEN** layout is rendered
- **THEN** обёртка мобильной панели (или `:root`) содержит `--gm-nav-h` с высотой панели в пикселях (один ряд пунктов, без safe-area)
- **AND** высота панели не превышает ~96px при viewport 412px

### Requirement: Main content compensates for navigation
The `<main>` element SHALL have left padding to compensate for the desktop sidebar. The bottom compensation for the mobile bottom bar SHALL live in the content shell (see "Layout provides a single content column shell"), not on `<main>`; `<main>` SHALL NOT carry a bottom padding class.

#### Scenario: Content padding
- **WHEN** layout is rendered
- **THEN** the `<main>` element has class `md:pl-64`
- **AND** the `<main>` element does not have class `pb-16`

### Requirement: Layout uses CSS media query for responsive switching
The mobile/desktop switch SHALL use only CSS media query classes (hidden/md:flex/md:hidden), not JavaScript width detection.

#### Scenario: No JavaScript width detection
- **WHEN** layout is rendered
- **THEN** no JavaScript-based viewport detection is present
- **AND** responsive behavior is achieved through CSS classes only

### Requirement: Layout enables hx-boost navigation
The layout SHALL render an `hx-boost` attribute on `<body>` so navigation links are fetched via AJAX and swapped without a full page reload. When JavaScript is unavailable, links SHALL still navigate via normal full-page requests.

#### Scenario: Body has hx-boost
- **WHEN** layout is rendered
- **THEN** the `<body>` element has `hx-boost="true"`

#### Scenario: Links work without JavaScript
- **WHEN** the page is opened without JavaScript enabled and a navigation link is clicked
- **THEN** the browser performs a normal full-page navigation to the link's `href`

### Requirement: Navigation component renders menu items from a single data source
The `navigation` component SHALL accept a vector of menu items and render each item as a menu link with icon and label. The component SHALL support two variants: `:mobile` (horizontal bottom bar) and `:desktop` (vertical sidebar), both rendered as daisyUI `menu`. Items SHALL stretch to equal width within their container so icons align on a common axis; the icon SHALL be placed in a fixed-size box. On `:mobile` the icon SHALL stack above the label (compact bottom bar layout); on `:desktop` the icon SHALL sit next to the label.

The navigation SHALL contain exactly 4 items: `/feed` (Лента), `/check-in` (Отметка), `/medications` (Медикаменты), `/settings` (Настройки). The previous items `:dashboard`, `:history`, `:statistics`, `:insights` SHALL be removed.

#### Scenario: Mobile variant renders all items
- **WHEN** `(navigation :mobile nav-items)` is called with `nav-items` containing 4 items
- **THEN** the result contains exactly 4 `<a>` elements with `href` from each item's `:route`
- **AND** each link displays the item's label text
- **AND** the result contains a `menu menu-horizontal` container
- **AND** routes `/dashboard`, `/history`, `/statistics`, `/insights` are NOT present

#### Scenario: Desktop variant renders all items
- **WHEN** `(navigation :desktop nav-items)` is called with `nav-items` containing 4 items
- **THEN** the result contains exactly 4 `<a>` elements
- **AND** the result contains a `menu menu-vertical` container
- **AND** the result contains a heading element with the text "Good Mood"
- **AND** routes `/dashboard`, `/history`, `/statistics`, `/insights` are NOT present

#### Scenario: Icons are in fixed-size boxes
- **WHEN** `(navigation :desktop nav-items)` is called
- **THEN** every link contains an icon wrapper with classes `w-6` and `h-6`
- **AND** every link stretches to the full width of its list item

### Requirement: Active item is visually highlighted
The navigation component SHALL highlight the currently active item when an `:active` key is passed, with comparison by `:id`. The active item SHALL use the solid icon variant and active menu classes; inactive items SHALL use outline icons.

#### Scenario: Active item marked in mobile
- **WHEN** `(navigation :mobile nav-items {:active :feed})` is called
- **THEN** the link for `:feed` has the active styling (solid icon variant)
- **AND** other links do not have that styling

#### Scenario: Active item marked in desktop
- **WHEN** `(navigation :desktop nav-items {:active :check-in})` is called
- **THEN** the link for `:check-in` has the active styling (solid icon variant)
- **AND** other links do not have that styling

#### Scenario: No active item when :active is nil
- **WHEN** `(navigation :mobile nav-items)` is called without `:active`
- **THEN** no link has the active styling

### Requirement: Navigation items are links to routes
Each menu item SHALL render as an `<a>` element with an `href` taken from the item's `:route`. The component SHALL NOT render `<button>` elements for menu items.

**Reason**: Активность определяется серверно по пути роута (hx-boost-навигация); ссылки — семантически корректный элемент и ожидание daisyUI `menu` (li > a).

#### Scenario: Items are links
- **WHEN** `(navigation :mobile nav-items)` is called
- **THEN** every menu item is an `<a>` element with `href` matching its `:route`
- **AND** no `<button>` elements are present in the menu items

### Requirement: Active item uses solid icon, inactive use outline
The navigation component SHALL render the solid variant of the icon for the active item and the outline variant for inactive items.

#### Scenario: Active item has solid icon
- **WHEN** `(navigation :desktop nav-items {:active :feed})` is called
- **THEN** the link for `:feed` renders a solid-variant icon

#### Scenario: Inactive items have outline icons
- **WHEN** `(navigation :desktop nav-items {:active :feed})` is called
- **THEN** links for items other than `:feed` render outline-variant icons

### Requirement: Every navigation route renders a placeholder page
The system SHALL render an HTML page for each route declared in the navigation items (/dashboard, /check-in, /history, /statistics, /insights, /settings). Each page SHALL use the common layout (html/head/body, navigation, CDN scripts) and SHALL contain the item's label as a heading.

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
The page SHALL mark as active the navigation item whose `:route` equals the request path. The active item SHALL render with the active styling (solid icon variant and active menu classes); all other items SHALL render inactive (outline icons).

#### Scenario: Statistics page highlights statistics item
- **WHEN** a user opens `GET /statistics`
- **THEN** the navigation marks the "Статистика" item as active (solid icon variant)
- **AND** all other items render with outline icons

#### Scenario: Each route highlights its own item
- **WHEN** a user opens any navigation route
- **THEN** the item with the matching `:route` is the only active item on the page

### Requirement: Layout provides a single content column shell

The `layout` function SHALL wrap page content in a shell `div` inside `<main>` that centers content, constrains its width to `max-w-lg`, applies horizontal padding `px-4` and top padding `pt-4`, and applies a bottom padding that covers the mobile bottom bar height plus the device safe-area inset, вычисленный от переменной `--gm-nav-h` (`pb-[calc(env(safe-area-inset-bottom)+var(--gm-nav-h)+0.75rem)]`). Хардкод высоты панели в пикселях (например `5rem`) в паддинге контента не допускается.

Page views SHALL NOT render their own top-level width or outer padding wrappers (`max-w-*`, `mx-auto`, `p-4`, `pb-24`); the shell is the single source of page width. The auth page (`app.views.auth`) is exempt.

#### Scenario: Shell classes present

- **WHEN** `(layout {:title "T"} nav-items content)` is called
- **THEN** the result contains a wrapper with classes `mx-auto`, `w-full`, `max-w-lg`, `px-4`, `pt-4`
- **AND** the wrapper has class `pb-[calc(env(safe-area-inset-bottom)+var(--gm-nav-h)+0.75rem)]`

#### Scenario: Pages render without own width wrappers

- **WHEN** any page (`/feed`, `/check-in`, `/medications`, `/settings`, `/insights`, `/insights/new`, `/insights/:id`) is rendered
- **THEN** its content does not contain top-level `max-w-md` or `max-w-2xl` wrappers
- **AND** its content does not contain `pb-24` outer padding

#### Scenario: Content is wider-constrained on desktop too

- **WHEN** the medications page is opened on a viewport wider than 512px (desktop with sidebar)
- **THEN** the content column is centered within the area right of the sidebar and does not exceed 512px

#### Scenario: Нижний контент не перекрывается панелью при двух рядах не бывает

- **GIVEN** viewport 412×915 и RU-локаль
- **WHEN** пользователь открывает `/check-in` и прокручивает до кнопки «Сохранить запись»
- **THEN** кнопка полностью видима выше мобильной панели навигации после полной прокрутки

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

### Requirement: Local static assets are served from resources/public

The application SHALL serve static assets from `resources/public` via `ring.middleware.resource/wrap-resource "public"` combined with `wrap-content-type` and `wrap-not-modified`, placed OUTSIDE the auth middleware (static requests SHALL NOT require a session and SHALL NOT redirect to /login). The URL path maps directly: `resources/public/js/feed-chart.js` → `/js/feed-chart.js`.

#### Scenario: Feed chart script is served without authentication

- **WHEN** an unauthenticated client requests `GET /js/feed-chart.js`
- **THEN** the response is 200 with `Content-Type` `application/javascript` (or `text/javascript`)
- **AND** no redirect to `/login` occurs

#### Scenario: Unknown asset is not served as content

- **WHEN** a client requests `GET /js/missing.js`
- **THEN** no asset content is served with 200: an unauthenticated client is redirected to /login (consistent with unknown routes), an authenticated client gets 404 from the router

### Requirement: Global layout head

The layout `<head>` SHALL include Chart.js 4.x (pinned version, UMD build via CDN) and the local `/js/feed-chart.js` renderer, both with the `defer` attribute so that execution order is preserved (`feed-chart.js` sees `window.Chart`). Both scripts SHALL be loaded on every page; `feed-chart.js` SHALL be idempotent — on pages without `canvas[data-gm-chart]` it performs no work. The head SHALL also include: `<link rel="manifest" href="/manifest.webmanifest">`, `<meta name="theme-color" content="#5b5bea">`, `<meta name="vapid-public-key">` (when VAPID keys are configured on the server) and the local `/js/push.js` renderer (defer), which registers the service worker on every page and drives push subscription from user gestures (see `pwa` capability).

#### Scenario: Scripts present in head

- **WHEN** any page is rendered
- **THEN** the head contains `<script defer src="https://cdn.jsdelivr.net/npm/chart.js@4…">` and `<script defer src="/js/feed-chart.js">`

#### Scenario: Page without chart canvas

- **GIVEN** a page without `canvas[data-gm-chart]` (e.g. /settings)
- **WHEN** the page loads
- **THEN** feed-chart.js performs no work
- **AND** the service worker is registered and manifest is linked

### Requirement: Output escaping
All user-supplied and AI-generated content SHALL be rendered as text nodes through `hiccup2.core/html`, which escapes it. `hiccup2.core/raw` SHALL be used only for static content loaded from the classpath (inline SVG icons, constant inline scripts) and SHALL NOT be used with any value that originates from the database, a request or an AI response. The deprecated `hiccup.core` namespace, which does not escape, SHALL NOT be used.

#### Scenario: User content is escaped
- **GIVEN** an entry whose note is `<img src=x onerror=alert(1)>`
- **WHEN** the feed renders that entry
- **THEN** the HTML contains the escaped text `&lt;img src=x onerror=alert(1)&gt;`
- **AND** the DOM contains no live `img` element

#### Scenario: AI content is escaped
- **GIVEN** an AI response whose message is `<script>alert(1)</script>`
- **WHEN** the AI section is rendered
- **THEN** the HTML contains the escaped text
- **AND** no `script` element with that content exists

#### Scenario: raw is used only for static assets
- **WHEN** `hiccup2.core/raw` is used in `src/`
- **THEN** its argument is a classpath resource or a constant string
- **AND** contains no value from the database, the request or an AI response

#### Scenario: Attribute contexts are escaped
- **WHEN** a user-supplied string is rendered into `href`, `hx-vals` or another attribute
- **THEN** the value is escaped or JSON-encoded
- **AND** a `javascript:` URL supplied by the user does not execute

### Requirement: Подписи и порядок пунктов мобильной навигации

Мобильная навигация SHALL содержать ровно 5 пунктов в порядке: Лента, Инсайты, Запись, Мед, Настройки. Подписи SHALL быть локализованы: RU — «Лента», «Инсайты», «Запись», «Мед», «Настройки»; EN — «Feed», «Insights», «Entry», «Meds», «Settings». Подписи SHALL быть достаточно короткими, чтобы все 5 пунктов помещались в один ряд на viewport шириной 412px без переноса строк.

#### Scenario: Порядок и подписи при рендере

- **WHEN** мобильная навигация отрендерена в RU-локали
- **THEN** пункты идут в порядке: Лента, Инсайты, Запись, Мед, Настройки
- **AND** пункт «Запись» ведёт на `/check-in`

#### Scenario: Один ряд на Nothing Phone 1

- **GIVEN** viewport 412×915 и RU-локаль
- **WHEN** страница открыта на мобильной ширине
- **THEN** все подписи пунктов отрисованы без переноса на вторую строку
- **AND** высота панели навигации соответствует одному ряду пунктов


# ui-shell Delta: add-js-radar-chart

## ADDED Requirements

### Requirement: Local static assets are served from resources/public
The application SHALL serve static assets from `resources/public` via `ring.middleware.resource/wrap-resource "public"` combined with `wrap-content-type` and `wrap-not-modified`, placed OUTSIDE the auth middleware (static requests SHALL NOT require a session and SHALL NOT redirect to /login). The URL path maps directly: `resources/public/js/radar.js` → `/js/radar.js`.

#### Scenario: Radar script is served without authentication
- **WHEN** an unauthenticated client requests `GET /js/radar.js`
- **THEN** the response is 200 with `Content-Type` `application/javascript` (or `text/javascript`)
- **AND** no redirect to `/login` occurs

#### Scenario: Unknown asset is not served as content
- **WHEN** a client requests `GET /js/missing.js`
- **THEN** no asset content is served with 200: an unauthenticated client is redirected to /login (consistent with unknown routes), an authenticated client gets 404 from the router

### Requirement: Chart.js and radar renderer are loaded globally
The layout `<head>` SHALL include Chart.js 4.x (pinned version, UMD build via CDN) and the local `/js/radar.js` renderer, both with the `defer` attribute so that execution order is preserved (radar.js sees `window.Chart`). Both scripts SHALL be loaded on every page; `radar.js` SHALL be idempotent — on pages without `canvas[data-gm-radar]` it performs no work.

#### Scenario: Scripts present in head
- **WHEN** any page is rendered
- **THEN** the head contains `<script defer src="https://cdn.jsdelivr.net/npm/chart.js@4…">` and `<script defer src="/js/radar.js">`

#### Scenario: No-op on pages without radar
- **GIVEN** a page without `canvas[data-gm-radar]` (e.g. /settings)
- **WHEN** radar.js executes
- **THEN** no canvas is drawn and no error is thrown

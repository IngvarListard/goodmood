# UI Shell Specification

## MODIFIED Requirements

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

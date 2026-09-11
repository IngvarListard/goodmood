## MODIFIED Requirements

### Requirement: Global layout head

The layout `<head>` SHALL include Chart.js 4.x (pinned version, UMD build via CDN) and the local `/js/radar.js` renderer, both with the `defer` attribute so that execution order is preserved (radar.js sees `window.Chart`). Both scripts SHALL be loaded on every page; `radar.js` SHALL be idempotent — on pages without `canvas[data-gm-radar]` it performs no work. The head SHALL also include: `<link rel="manifest" href="/manifest.webmanifest">`, `<meta name="theme-color" content="#5b5bea">`, `<meta name="vapid-public-key">` (when VAPID keys are configured on the server) and the local `/js/push.js` renderer (defer), which registers the service worker on every page and drives push subscription from user gestures (see `pwa` capability).

#### Scenario: Page without radar canvas

- **GIVEN** a page without `canvas[data-gm-radar]` (e.g. /settings)
- **WHEN** the page loads
- **THEN** radar.js performs no work
- **AND** the service worker is registered and manifest is linked

## MODIFIED Requirements

### Requirement: In-app scheduled notifications via polling (variant C)

The system SHALL surface scheduled insights at configurable times (default: morning / midday / evening) as in-app notifications while the app is open, using htmx polling every 60 seconds. For users subscribed to push (`pwa` capability), slot delivery SHALL be done by the server-side push scheduler at slot time and the shared sentinel `last_slot_shown` SHALL prevent duplicate in-app banners; users without a subscription SHALL keep the in-app behavior unchanged. A missed notification while the tab is closed SHALL be recoverable as a «since you were away» banner on next /feed open (computed from existing data, not a stored notification event) — only for users without push delivery of that slot.

#### Scenario: Subscribed user gets push, not the away banner

- **GIVEN** юзер подписан на push и включил утренний слот
- **WHEN** слот наступает, а потом юзер открывает /feed
- **THEN** утренний пуш доставлен шедулером в момент слота
- **AND** away-баннер за утренний слот не показывается (sentinel общий)

#### Scenario: Unsubscribed user keeps polling behavior

- **GIVEN** юзер не подписан на push
- **WHEN** слоты наступают при открытой или закрытой вкладке
- **THEN** in-app поведение прежнее: polling-баннер при открытой вкладке, away-баннер при открытой позже

### Scenario: Full PWA push is deferred

- Removed: push delivery is implemented by the `pwa` capability (this statement was superseded).

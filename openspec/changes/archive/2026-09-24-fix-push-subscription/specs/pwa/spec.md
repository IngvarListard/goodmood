## MODIFIED Requirements

### Requirement: Push subscription lifecycle

The system SHALL let the user enable push notifications from the notifications settings section on /settings («Push-уведомления»). Enabling SHALL require a user gesture: `Notification.requestPermission()`, then obtain a `PushSubscription` — reusing an existing one via `pushManager.getSubscription()` when present, otherwise creating it via `pushManager.subscribe` with the server's VAPID public key (delivered via `<meta name="vapid-public-key">`) — then `POST /push/subscribe` with the subscription (endpoint, keys p256dh/auth), authenticated and CSRF-protected. The subscription SHALL be validated against its meaningful subset (endpoint, keys p256dh/auth) only: unknown harmless browser keys such as `expirationTime` SHALL be ignored rather than rejected. Subscriptions SHALL be stored per user in `push_subscriptions` (endpoint unique). The user SHALL be able to disable: `unsubscribe()` + `POST /push/unsubscribe` removes the row. The user SHALL be able to trigger a test notification from the settings section (button visible only while subscribed): the authenticated, CSRF-protected `POST /push/test` SHALL send a notification to the current user's subscriptions through the same delivery path and report the delivered count or an error. The server SHALL delete a subscription row when the push service reports it gone (404/410). When VAPID env keys are absent the section SHALL show that notifications are unavailable and the server SHALL not send pushes (graceful degradation).

#### Scenario: User enables push in settings

- **GIVEN** VAPID-ключи настроены на сервере
- **WHEN** пользователь в /settings нажимает «Включить уведомления» и подтверждает разрешение
- **THEN** подписка сохранена в push_subscriptions с user_id владельца
- **AND** секция показывает статус «уведомления включены» с кнопкой выключить

#### Scenario: Enabling reuses an existing subscription

- **GIVEN** в браузере уже существует подписка (например, предыдущий POST не сохранил её)
- **WHEN** пользователь повторно нажимает «Включить уведомления»
- **THEN** push.js переиспользует существующую подписку и не вызывает pushManager.subscribe повторно
- **AND** POST /push/subscribe сохраняет строку в push_subscriptions
- **AND** пользователь не видит ошибки InvalidStateError

#### Scenario: Browser payload with extra keys is accepted

- **GIVEN** push.js отправляет `PushSubscription.toJSON()`, содержащий дополнительный ключ `expirationTime`
- **WHEN** приходит POST /push/subscribe
- **THEN** валидация проходит (лишний ключ игнорируется) и отвечает 200
- **AND** строка подписки появляется в push_subscriptions

#### Scenario: User sends a test notification

- **GIVEN** пользователь подписан на push и VAPID-ключи настроены
- **WHEN** он нажимает «Отправить тестовое уведомление» в /settings
- **THEN** POST /push/test отправляет пуш по всем его подпискам через тот же путь доставки
- **AND** секция показывает результат (число доставок или ошибку)

#### Scenario: Push is unavailable without VAPID keys

- **GIVEN** GOODMOOD_VAPID_* env-переменные не заданы
- **WHEN** пользователь открывает /settings
- **THEN** секция уведомлений сообщает о недоступности и не показывает кнопку включения
- **AND** шедулер не отправляет пуши и не падает

#### Scenario: Stale subscription is cleaned

- **GIVEN** подписка юзера удалена с телефона (push-сервис отвечает 410)
- **WHEN** шедулер пытается отправить пуш
- **THEN** строка подписки удаляется из БД
- **AND** юзеру не отправляются пуши по мёртвому endpoint
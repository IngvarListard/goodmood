# pwa Specification

## Purpose

PWA-возможности: установка на домашний экран (web app manifest + service worker без кэширования данных, offline-заглушка «нет сети»), подписка на web push (VAPID, user gesture, CSRF) и серверный шедулер доставки слот-пушей с общим sentinel `last_slot_shown`.
## Requirements
### Requirement: Installable standalone app

The system SHALL be installable to the Android home screen: `GET /manifest.webmanifest` (linked from every page) SHALL declare `display: "standalone"`, `start_url: "/feed"`, `scope: "/"`, `theme_color` (#5b5bea), `background_color`, and icons of 192×192 and 512×512 (plus a maskable variant). Every page SHALL include `theme-color` meta (#5b5bea) and a minimal service worker (`GET /sw.js`) with a fetch handler. When launched from the home screen, the app SHALL open in standalone mode without browser chrome (no address bar). The service worker SHALL NOT cache application responses: navigation and API requests SHALL go to the network, and when the network is unavailable the worker SHALL serve a minimal static «нет сети» page instead of the browser error. The service worker SHALL handle `push` and `notificationclick` events (see Push delivery).

#### Scenario: Install on Android

- **GIVEN** Android Chrome открывает приложение по HTTPS
- **WHEN** пользователь выбирает «Установить приложение» / «На главный экран»
- **THEN** иконка появляется на рабочем столе
- **AND** запуск открывает standalone-окно без адресной строки на /feed

#### Scenario: Manifest and worker are public

- **GIVEN** неаутентифицированный браузер
- **WHEN** запрашивает /manifest.webmanifest и /sw.js
- **THEN** оба отдаются 200 без редиректа на /login

#### Scenario: Offline shows the plain fallback

- **GIVEN** приложение установлено и сеть недоступна
- **WHEN** пользователь открывает приложение
- **THEN** показывается минимальная страница «нет сети»
- **AND** данные ленты не кэшируются и не подменяются устаревшими

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

### Requirement: Scheduled push delivery

The system SHALL deliver push notifications for enabled notification slots: a server-side scheduler (background component) SHALL tick at least once per minute and, for each user with an enabled slot whose local time has arrived and whose slot was not shown today (sentinel `last_slot_shown`), SHALL send a push with a per-slot text (i18n, без чувства вины — value, not nagging) to all of the user's subscriptions and mark the slot shown. Notification click SHALL open (or focus) the app on /feed. Delivery SHALL use server local time, the same clock as in-app banners. A user without subscriptions SHALL NOT receive pushes and SHALL keep the existing in-app banner behavior.

#### Scenario: Push at slot time

- **GIVEN** юзер подписан и включён утренний слот 08:00
- **WHEN** наступает 08:00
- **THEN** в течение минуты на телефон приходит пуш с утренним текстом
- **AND** sentinel `last_slot_shown` = сегодня

#### Scenario: No duplicate in-app banner after push

- **GIVEN** юзеру доставлен утренний пуш
- **WHEN** он открывает /feed позже в тот же день
- **THEN** баннер «пока тебя не было» за утренний слот не показывается

#### Scenario: Unsubscribed user keeps in-app behavior

- **GIVEN** юзер не подписан на push, слот включён
- **WHEN** наступает время слота и юзер открывает /feed позже
- **THEN** ведёт себя как прежде: баннер «пока тебя не было»
- **AND** пуш не отправляется

#### Scenario: No guilt texts

- **GIVEN** юзер не заполнял записи сегодня
- **WHEN** приходит вечерний пуш
- **THEN** текст не упрекает («вы не заполнили дневник» отсутствует)
- **AND** содержит нейтральный/поддерживающий текст слота


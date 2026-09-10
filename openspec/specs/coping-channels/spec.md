# coping-channels Specification

## Purpose
TBD - created by archiving change add-coping-channels. Update Purpose after archive.
## Requirements
### Requirement: In-app scheduled notifications via polling (variant C)
The system SHALL surface scheduled insights at configurable times (default: morning / midday / evening) as in-app notifications while the app is open, using htmx polling every 60 seconds. Full PWA push (service worker + Push API + server-side web-push) SHALL NOT be implemented in this phase (variant C); it is a future change. A missed notification while the tab is closed SHALL be recoverable as a «since you were away» banner on next /feed open (computed from existing data, not a stored notification event).

#### Scenario: Morning notification delivered in-app
- **GIVEN** у пользователя есть инсайт с контекстом, близким к утреннему состоянию
- **AND** утренний слот включён в настройках
- **WHEN** наступает утреннее время и вкладка /feed открыта
- **THEN** появляется in-app toast с этим инсайтом или краткой сводкой
- **AND** Notification API вызывается через hyperscript (js-interop), если разрешение получено `[ref: A3-q3]`

#### Scenario: User disables a notification slot
- **GIVEN** пользователь не хочет вечерних уведомлений
- **WHEN** отключает вечерний слот в настройках
- **THEN** вечерние in-app баннеры не приходят
- **AND** утренние и дневные продолжают работать `[ref: A3-q3]`

#### Scenario: Tab was closed during a slot
- **GIVEN** утренний слот наступил, а вкладка была закрыта
- **WHEN** пользователь открывает /feed позже
- **THEN** видит баннер «пока тебя не было, был утренний инсайт»
- **AND** контент вычисляется из существующих данных (последняя запись → state_label → инсайт), не из таблицы событий `[ref: design.md Decision 14.2]`

#### Scenario: Full PWA push is deferred
- **GIVEN** Фаза 4 реализована с in-app polling
- **WHEN** рассматривается доставка уведомлений при закрытом приложении
- **THEN** это отдельный future change, не Фаза 4
- **AND** никаких серверных web-push зависимостей в этом change `[ref: design.md Decision 14]`

### In-form toast hint after save
The system SHALL surface a relevant hint after the user saves an entry on /check-in, based on the resulting `state_label`. The hint SHALL be delivered as a dismissible `alert-info` toast on /feed via hx-swap-oob (the existing redirect /check-in → /feed is preserved). It SHALL be non-blocking and SHALL NOT block entry submission.

#### Scenario: User enters high anxiety and has a relevant insight
- **GIVEN** пользователь заполнил ось тревоги = 7 (state_label `anxiety`)
- **AND** существует инсайт с `state_label='anxiety'`
- **WHEN** отправляет запись и редиректится на /feed
- **THEN** видит toast «В таком состоянии тебе помогало: …» (context + первый advice)
- **AND** toast dismissible, не modal, не блокирует отправку записи `[ref: A3-q3]`

#### Scenario: No relevant insight — no toast
- **GIVEN** пользователь сохранил запись, но инсайта для этого `state_label` нет
- **WHEN** переходит на /feed
- **THEN** toast НЕ показывается
- **AND** нет пустого места и нет онбординга в тосте (онбординг живёт в виджете /feed) `[ref: OQ3, design.md Decision 13.5]`

### Evening «insights for tomorrow» summary
The system SHALL produce an evening summary that prepares the user for the next day based on the current state. Format (resolve OQ6): a collapsible `alert alert-info` banner at the top of /feed, shown after 18:00 local time when there are entries today and the summary has not been shown today. It SHALL contain the current `state_label`, one relevant insight (context + first advice), and a supportive closing line. It SHALL be dismissible for the day (server-side `last_summary_date` sentinel).

#### Scenario: Evening summary shown
- **GIVEN** пользователь заполнял записи в течение дня
- **AND** локальное время ≥ 18:00
- **AND** сводка ещё не показана сегодня
- **WHEN** открывает /feed
- **THEN** видит баннер «Сводка на завтра готова» вверху /feed
- **AND** при раскрытии видит текущий state_label, релевантный инсайт и напутствие «Завтра — новый день. То, что помогало раньше, поможет снова»
- **AND** закрытие скрывает баннер до завтра `[ref: A4-q1, OQ6]`

#### Scenario: Evening summary not shown before 18:00
- **GIVEN** локальное время до 18:00
- **WHEN** пользователь открывает /feed
- **THEN** баннер сводки не показывается
- **AND** нет пустого места

#### Scenario: Evening summary suppressed after dismiss
- **GIVEN** пользователь закрыл баннер сегодня
- **WHEN** снова открывает /feed сегодня
- **THEN** баннер не показывается до следующего дня
- **AND** `last_summary_date` остаётся сегодня

### Soft mode in low states
The system SHALL offer a «soft mode» that minimises input burden and emphasises coping over recording pain when the user is in a `low` or `mixed` state (mitigation of rumination risk). When the latest entry's `state_label` is `low` or `mixed`, /check-in SHALL show a non-blocking banner offering «мягкий режим» (a form reduced to only `mood_score`) with an always-available «полная форма» escape. On /feed in these states, the insights widget SHALL be raised above the hero card to emphasise advice over state fixation.

#### Scenario: User in depression opens /check-in
- **GIVEN** последняя запись пользователя имеет `state_label='low'`
- **WHEN** открывает /check-in
- **THEN** видит баннер «Тебе сейчас может быть непросто. Включить мягкий режим?» с кнопками «мягкий режим» и «полная форма»
- **AND** полная форма остаётся доступной
- **AND** не принуждает: без выбора форма остаётся полной `[ref: design.md Decision 14.4]`

#### Scenario: User enables soft mode
- **GIVEN** пользователь видит предложение мягкого режима
- **WHEN** нажимает «мягкий режим»
- **THEN** форма показывает только слайдер `mood_score` и кнопку «Сохранить»
- **AND** энергия/тревога и опциональные блоки скрыты
- **AND** toggle работает через hyperscript, без сырого JS `[ref: design.md Decision 14.4]`

#### Scenario: Insight widget raised in low state
- **GIVEN** последняя запись пользователя имеет `state_label='low'` or `mixed`
- **WHEN** открывает /feed
- **THEN** виджет инсайтов отображается выше hero-карточки
- **AND** акцент на совете, не на фиксации боли `[ref: design.md Risks — rumination]`

### Notification settings per slot
The system SHALL provide a notification settings section in /settings with three slots (morning, midday, evening), each with an enabled toggle and a time input (HH:MM). Settings SHALL be persisted per user in `user_notification_settings`. A single «Сохранить» button SHALL submit all three slots via htmx; on success a dismissible «Сохранено» alert is shown.

#### Scenario: User sees three notification slots
- **GIVEN** пользователь открывает /settings
- **WHEN** видит секцию уведомлений
- **THEN** три строки: «Утро» (08:00), «День» (13:00), «Вечер» (19:00)
- **AND** каждая строка содержит toggle (вкл/выкл) и time input
- **AND** у выключенного слота time input затемнён

#### Scenario: User saves notification settings
- **GIVEN** пользователь изменил время или выключил слот
- **WHEN** нажимает «Сохранить»
- **THEN** POST /settings/notifications обновляет настройки всех трёх слотов
- **AND** появляется dismissible alert «Сохранено»

### No pressure channels
The system SHALL NOT use notification frequency or content to pressure the user into filling entries; notifications carry value (insights/advice), not reminders-to-fill.

#### Scenario: User skips a day — no guilt notifications
- **GIVEN** пользователь не заполнял записи сегодня
- **WHEN** наступает вечерний слот
- **THEN** уведомление содержит инсайт/совет, а не «вы не заполнили дневник» `[ref: A2-q3]`

### Requirement: Notification settings data scoping
Per-user notification settings (time slots, summary flags, "already shown" markers) SHALL be stored and read with the `user_id` taken from the authenticated identity. No operation SHALL read or modify the settings of another user, and a missing row SHALL fall back to that user's own defaults.

#### Scenario: Settings belong to one user
- **WHEN** an authenticated user saves notification slot settings
- **THEN** the row is stored with the authenticated user's id
- **AND** is returned only to that user

#### Scenario: Another user's settings are not visible
- **GIVEN** user A has custom slot settings
- **WHEN** user B requests `GET /settings`
- **THEN** user B sees their own settings or the defaults
- **AND** does not see any value saved by user A

#### Scenario: Shown markers are per user
- **WHEN** a notification slot is marked as shown for user A
- **THEN** the marker does not affect the banner state for user B


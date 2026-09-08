## MODIFIED Requirements

### Requirement: Feed page renders entries with rose-of-winds

The system SHALL render a `/feed` page as the landing page after login, displaying the user's entries grouped by date. The latest entry of the current day SHALL be rendered as a hero card with a 200×200px radar chart rendered client-side (canvas + Chart.js, see "Read-only radar chart renders rose-of-winds"). Other entries SHALL be rendered as compact cards with a state-label badge, timestamp, and axis values as text. In low/mixed states the insights widget SHALL be rendered above the hero card (emphasis on advice over state fixation). The page SHALL render an evening summary banner at the top (before the «Лента» heading) when local time is ≥ 18:00, today has entries, and the summary has not been shown today: тонкий однострочный баннер с тёмным фоном, левой акцентной полосой и крестиком закрытия — без сплошной цветной заливки (`alert-info` не используется) и без встроенной карточки инсайта (совет для состояния живёт только в отдельной карточке совета, см. feed-timeline). Страница не содержит инлайн-чата.

#### Scenario: Feed shows hero card with radar

- **GIVEN** the user is logged in and has entries today
- **WHEN** they open `/feed`
- **THEN** the latest entry today is rendered as a hero card
- **AND** the hero card contains a 200×200px canvas radar
- **AND** the hero card shows the state-label badge and timestamp
- **AND** the radar polygon reflects the entry's axis values

#### Scenario: Feed shows compact cards for other entries

- **GIVEN** the user has 3 entries today
- **WHEN** they open `/feed`
- **THEN** the latest entry is the hero card with radar
- **AND** the other 2 entries are compact cards (no radar)
- **AND** each compact card shows: «состояние» label, state badge, timestamp, and «энергия X · тревога Y · фокус Z» text

#### Scenario: Feed groups past days under date headers

- **GIVEN** the user has entries on 2026-08-21 and 2026-08-20
- **WHEN** they open `/feed`
- **THEN** past-day entries appear under date headers («21 августа», «20 августа»)
- **AND** past-day cards use `bg-base-300` (more compact than today's `bg-base-200`)

#### Scenario: Feed empty state

- **GIVEN** the user has no entries today
- **WHEN** they open `/feed`
- **THEN** an onboarding message «Как ты? Создай первую запись» is shown
- **AND** a button linking to `/check-in` is displayed `[ref: A3-q1]`

#### Scenario: Insights widget raised above hero in low state

- **GIVEN** the latest entry has `state_label='low'` or `mixed`
- **WHEN** they open `/feed`
- **THEN** the insights widget is rendered above the hero card (not below it)
- **AND** the hero card remains visible below the widget `[ref: design.md Decision 14.4]`

#### Scenario: Evening summary banner at top of feed

- **GIVEN** local time is ≥ 18:00, today has entries, and the summary was not shown today
- **WHEN** they open `/feed`
- **THEN** a thin one-line banner «Сводка на завтра готова» is rendered before the «Лента» heading
- **AND** the banner uses dark surface with a left accent stripe, no solid `alert-info` fill
- **AND** the banner has a «посмотреть» link and a dismiss cross, dismisses for the day `[ref: A4-q1, OQ6]`
- **AND** the banner does not embed the advice/insight content (no duplication with the advice card)

#### Scenario: No summary banner before 18:00

- **GIVEN** local time is before 18:00
- **WHEN** they open `/feed`
- **THEN** no evening summary banner is rendered
- **AND** no empty placeholder is shown

#### Scenario: No inline chat on feed

- **GIVEN** пользователь на `/feed`
- **WHEN** страница отрендерена
- **THEN** в теле ленты нет кнопки «Чат» и инлайн-панели `#ai-chat-open`

## REMOVED Requirements

### Requirement: Floating action button on feed

**Reason**: FAB «+» дублирует вкладку «Отметка» в нижней навигации; по макету `design/lentagem.html` единственная плавающая кнопка на ленте — кнопка ассистента.
**Migration**: создание записи — через вкладку «Отметка» (`/check-in`); плавающая кнопка на ленте теперь открывает модалку ассистента (capability `assistant-modal`).

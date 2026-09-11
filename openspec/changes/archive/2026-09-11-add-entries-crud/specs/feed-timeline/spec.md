## MODIFIED Requirements

### Requirement: Feed page renders entries with rose-of-winds

The system SHALL render a `/feed` page as the landing page after login, displaying the user's entries grouped by date. The latest entry of the current day SHALL be rendered as a hero card with a 200×200px radar chart rendered client-side (canvas + Chart.js, see "Read-only radar chart renders rose-of-winds"). Other entries SHALL be rendered as compact cards with a state-label badge, timestamp, and axis values as text. In low/mixed states the insights widget SHALL be rendered above the hero card (emphasis on advice over state fixation). The page SHALL render an evening summary banner at the top (before the «Лента» heading) when local time is ≥ 18:00, today has entries, and the summary has not been shown today: тонкий однострочный баннер с тёмным фоном, левой акцентной полосой и крестиком закрытия — без сплошной цветной заливки (`alert-info` не используется) и без встроенной карточки инсайта (совет для состояния живёт только в отдельной карточке совета, см. feed-timeline). Страница не содержит инлайн-чата. В шапке страницы (рядом с заголовком «Лента») SHALL быть иконка-ссылка на полный список записей `/entries`.

#### Scenario: Feed shows hero card with radar

- **WHEN** у пользователя есть запись сегодня
- **THEN** последняя запись дня отрисована hero-карточкой с радаром

#### Scenario: Иконка списка записей в шапке

- **GIVEN** страница /feed отрендерена
- **WHEN** пользователь ищет ссылку на все записи
- **THEN** в шапке рядом с заголовком есть иконка-ссылка на /entries
- **AND** нижняя навигация по-прежнему содержит ровно 5 пунктов

# feed-timeline Specification

## MODIFIED Requirements

### Requirement: Feed page renders entries with rose-of-winds

The system SHALL render a `/feed` page as the landing page after login, displaying the user's entries grouped by date. The latest entry of the current day SHALL be rendered as a hero card containing a 200×200px time-series line chart rendered client-side (canvas + Chart.js, contract `data-gm-chart` — see `entries` → «Time-series chart canvas contract»). The chart SHALL plot X = days, Y = 0–10, with one line per tracked axis (`mood_score`, `energy`, `anxiety`, `focus`, `aggression`) and one composite line «общее настроение» (last). Крошечные кнопки-переключатель (3 дня / неделя / месяц, дефолт — неделя) SHALL переключать период через `GET /feed/chart?period=3d|week|month` с OOB-свапом контейнера графика без перезагрузки страницы. Per-day values are means of non-nil axis values for the day (see `entries` → «Daily axis means for the feed time-series chart»). Если во всём окне меньше 2 дней с данными, вместо графика SHALL отображаться приглушённый fallback-текст. Other entries SHALL be rendered as compact cards with a state-label badge, timestamp, and axis values as text. In low/mixed states the insights widget SHALL be rendered above the hero card (emphasis on advice over state fixation). The page SHALL render an evening summary banner at the top (before the «Лента» heading) when local time is ≥ 18:00, today has entries, and the summary has not been shown today: тонкий однострочный баннер с тёмным фоном, левой акцентной полосой и крестиком закрытия — без сплошной цветной заливки (`alert-info` не используется) и без встроенной карточки инсайта (совет для состояния живёт только в отдельной карточке совета, см. feed-timeline). Страница не содержит инлайн-чата. В шапке страницы (рядом с заголовком «Лента») SHALL быть иконка-ссылка на полный список записей `/entries`.

#### Scenario: Hero card renders the time-series chart (default week)

- **GIVEN** пользователь с записями за последнюю неделю
- **WHEN** открывает `/feed`
- **THEN** hero-карточка содержит `canvas[data-gm-chart]` с 7 ISO-датами по X и осями + составной линией
- **AND** активна кнопка «Неделя»
- **AND** Chart.js рисует линейный график после загрузки

#### Scenario: Toggle to 3 days

- **WHEN** пользователь кликает кнопку «3 дня»
- **THEN** контейнер графика свапается фрагментом за последние 3 дня
- **AND** кнопка «3 дня» подсвечена как активная, страница не перезагружалась

#### Scenario: Toggle to rolling month

- **WHEN** пользователь кликает кнопку «Месяц»
- **THEN** график показывает последние 30 дней (не календарный месяц)

#### Scenario: Insufficient data fallback

- **GIVEN** во всём окне меньше 2 дней с данными
- **WHEN** открывает `/feed`
- **THEN** вместо графика отображается приглушённый fallback-текст
- **AND** страница не падает с ошибкой

#### Scenario: Composite line last

- **GIVEN** записи по всем осям за неделю
- **WHEN** рендерится график
- **THEN** составной датасет «общее настроение» идёт последним
- **AND** его значения учитывают инверсию тревоги и агрессии

#### Scenario: Feed shows compact cards for other entries

- **GIVEN** the user has 3 entries today
- **WHEN** they open `/feed`
- **THEN** the latest entry is the hero card with the chart
- **AND** the other 2 entries are compact cards (no chart)
- **AND** each compact card shows: «состояние» label, state badge, timestamp, and «энергия X · тревога Y · фокус Z» text

#### Scenario: Feed groups past days under date headers

- **GIVEN** the user has entries on 2026-08-21 and 2026-08-20
- **WHEN** they open `/feed`
- **THEN** past-day entries appear under date headers («21 августа», «20 августа»)
- **AND** past-day cards use `bg-base-300` (more compact than today's `bg-base-200`)

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

#### Scenario: No inline chat on feed

- **GIVEN** пользователь на `/feed`
- **WHEN** страница отрендерена
- **THEN** в теле ленты нет кнопки «Чат» и инлайн-панели `#ai-chat-open`

#### Scenario: Иконка списка записей в шапке

- **GIVEN** страница /feed отрендерена
- **WHEN** пользователь ищет ссылку на все записи
- **THEN** в шапке рядом с заголовком есть иконка-ссылка на /entries
- **AND** нижняя навигация по-прежнему содержит ровно 5 пунктов

### Requirement: AI-инсайт-карточка с графиком недели

AI-советы на ленте SHALL рендериться карточкой совета для состояния: обычная поверхность карточек ленты (`bg-base-200`, тонкая граница, скругление), левая вертикальная акцентная полоса (градиент), иконка-лампочка в круглом аватаре, крестик dismiss, заголовок «Совет для состояния «Х»», текст совета, «посмотреть полностью» — collapse для длинного текста. Бейдж уверенности и SVG-график последних 7 дней сохраняются: полилиния + точки, окрашенные по значению (≤3 зелёный, 4–6 янтарный, ≥7 красный; день без данных — контурная точка). Цвета полилинии и точек SHALL задаваться явными CSS-переменными (`var(--color-primary)` для линии, `var(--color-success)` / `var(--color-warning)` / `var(--color-error)` для заливки точек) — НЕ через daisyUI-утилиты `fill-*` / `stroke-primary`, которые Tailwind browser CDN не генерирует и которые рендерятся чёрными. Сплошная цветная заливка карточки не используется.

#### Scenario: Достаточно данных

- **WHEN** есть записи минимум за 2 из 7 дней
- **THEN** рендерится карточка с акцентной полосой, лампочкой, dismiss и графиком с точками по доступным дням, подписи дней недели по локали

#### Scenario: Недостаточно данных

- **WHEN** записей за неделю меньше 2
- **THEN** график не рендерится, отображается текст-fallback, карточка остаётся

#### Scenario: Точки графика окрашены переменными

- **GIVEN** мини-график недели отрендерен
- **WHEN** браузер рисует точки
- **THEN** заливка точки берётся из `var(--color-success)` / `var(--color-warning)` / `var(--color-error)` по порогам
- **AND** линия использует `var(--color-primary)`
- **AND** точки не рендерятся чёрными

#### Scenario: Длинный текст совета сворачивается

- **GIVEN** текст совета длиннее трёх строк
- **WHEN** карточка отрендерена
- **THEN** текст свёрнут (collapse), ссылка «посмотреть полностью» раскрывает его

#### Scenario: Dismiss карточки

- **WHEN** пользователь кликает крестик карточки
- **THEN** карточка скрывается (hyperscript) до следующей перезагрузки ленты

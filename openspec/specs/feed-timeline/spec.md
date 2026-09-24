# feed-timeline Specification

## Purpose
TBD - created by archiving change redesign-feed. Update Purpose after archive.
## Requirements
### Requirement: Timeline-лента по дням

Лента SHALL группировать записи по дням в вертикальный timeline: линия слева, на каждый день точка, окрашенная по state_label дня, заголовок даты («Сегодня»/«Вчера»/дата), карточки записей дня смещены вправо от линии. Первый рендер ленты SHALL включать только последние 7 календарных дней (сегодня и 6 предыдущих); более старые дни SHALL подгружаться чанками по 5 дней при прокрутке (infinite scroll), без перезагрузки страницы.

#### Scenario: Несколько дней с записями

- **WHEN** есть записи за сегодня, вчера и позавчера с разными state_label
- **THEN** рендерится одна вертикальная линия, три заголовка дней, у каждого дня точка на линии: `low`/`anxiety` — красная, `ok`/`calm` — зелёная, прочие — нейтральная

#### Scenario: Пустая лента

- **WHEN** записей нет вообще
- **THEN** timeline не рендерится, вместо него — empty state (см. ниже)

#### Scenario: Стартовое окно — 7 дней

- **GIVEN** у пользователя есть записи за последние 30 дней
- **WHEN** открывает /feed
- **THEN** первый рендер содержит дни только за последние 7 календарных дней (сегодня + 6 предыдущих)
- **AND** более старые дни в первом ответе не отрендерены

#### Scenario: Единица пагинации — день, а не строка

- **GIVEN** в один день несколько записей
- **WHEN** этот день попадает в подгружаемый чанк
- **THEN** все записи дня приходят вместе, день не разрывается между чанками

### Requirement: Карточка записи с чипами метрик

Карточка записи в timeline SHALL содержать state-бейдж (`.gm-badge-danger`/`.gm-badge-ok`/нейтральный по state_label), время создания и иконку меню (`ellipsis-horizontal`) справа, ниже — grid из чипов `.gm-chip` (энергия/тревога/фокус) с иконками, окрашенными переменными `--gm-metric-*`, и значениями; отсутствующее значение — «–». Иконка меню SHALL открывать daisyUI dropdown с пунктами «Править» (ссылка на `/entries/:id`) и «Удалить» (открывает confirm-`<dialog>`). Подтверждение удаления SHALL отправлять htmx `DELETE /entries/:id?from=feed` и убирать карточку из ленты in-place, без перезагрузки страницы; если записей за день больше не остаётся, секция дня SHALL исчезать из timeline. Интерактивность SHALL быть реализована только через htmx/hyperscript — без сырого JavaScript.

#### Scenario: Полная запись

- **WHEN** запись имеет energy=7, anxiety=3, focus=6 и state_label=ok
- **THEN** бейдж зелёный «норма», три чипа со значениями, иконка энергии — янтарная, тревоги — красная, фокуса — синяя

#### Scenario: Частичная запись

- **WHEN** focus отсутствует
- **THEN** чип фокуса отображает «–» и рендерится без ошибок

#### Scenario: Меню действий карточки

- **GIVEN** пользователь на /feed с хотя бы одной карточкой записи в timeline
- **WHEN** кликает трёхточечную иконку карточки
- **THEN** открывается dropdown с пунктами «Править» и «Удалить»
- **AND** никакой JavaScript-файл для этого не подключается (htmx/hyperscript)

#### Scenario: Переход к правке из меню

- **GIVEN** открыто меню карточки записи с id
- **WHEN** пользователь выбирает «Править»
- **THEN** открывается страница `/entries/:id` этой записи

#### Scenario: Удаление карточки in-place

- **GIVEN** пользователь на /feed, открыто меню карточки
- **WHEN** выбирает «Удалить» и подтверждает в модалке
- **THEN** запись удалена из БД
- **AND** карточка исчезает из ленты без перезагрузки страницы
- **AND** URL остаётся /feed

#### Scenario: Пустой день исчезает

- **GIVEN** в прошедшем дне ленты ровно одна запись
- **WHEN** пользователь удаляет её из меню карточки и подтверждает
- **THEN** карточка исчезает, а вместе с ней — секция дня (заголовок даты и точка timeline)

#### Scenario: Отмена удаления из ленты

- **GIVEN** открыта confirm-модалка удаления карточки на /feed
- **WHEN** пользователь закрывает модалку без подтверждения (Esc, backdrop или «Отмена»)
- **THEN** запись и карточка остаются на месте

### Requirement: Empty state с иллюстрацией и CTA

При отсутствии записей лента SHALL показывать карточку с SVG-иллюстрацией (контурная улыбка, stroke цвета primary), заголовком «Как ты сегодня?» (i18n), подсказкой и full-width primary-кнопкой создания записи с круглым белым плюсом-бейджем.

#### Scenario: Новый пользователь

- **WHEN** у пользователя нет ни одной записи
- **THEN** отображается иллюстрированная CTA-карточка, кнопка ведёт на `/check-in`, тексты локализованы по текущей локали

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

### Requirement: Пилюля активного периода

Активный период SHALL отображаться карточкой статуса периода (верх ленты): внутри карточки — градиентный pill состояния (`gm-gradient`) с иконкой и названием состояния, дата начала под pill, кнопка «Закрыть период» (outline) справа в один ряд с pill. Карточка — обычная поверхность карточек ленты (`bg-base-200`, тонкая граница), без hero-заливки. Существующие hx-атрибуты и csrf сохраняются.

#### Scenario: Активный период

- **WHEN** период активен
- **THEN** карточка показывает градиентный pill с state-меткой периода и дату под ним
- **AND** кнопка «Закрыть период» отправляет существующий POST без изменения контракта

#### Scenario: Период не активен

- **WHEN** активного периода нет
- **THEN** рендерится существующий start-banner (тоже карточкой ленты, без сплошной заливки)

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

### Requirement: Подгрузка старых дней ленты (infinite scroll)

Внизу timeline прошедших дней система SHALL рендерить самозаменяющий sentinel `<div id="feed-older" hx-get="/feed/older?before=<самая старая загруженная дата>" hx-trigger="revealed" hx-swap="outerHTML">`. `GET /feed/older` SHALL возвращать timeline-секции следующих 5 различных дней строго старше `before` плюс свежий sentinel, у которого `before` — самая старая дата возвращённого чанка. Когда старших записей не осталось, ответ SHALL не содержать sentinel, чтобы htmx прекратил триггерить. Секции SHALL переиспользовать существующие `timeline-day` и `entry-card`; новые компоненты не вводятся. Доступ SHALL иметь только владелец (как на остальных роутах: unauthenticated → redirect на /login).

#### Scenario: Прокрутка подгружает следующие 5 дней

- **GIVEN** первый рендер /feed показал 7 дней и sentinel `#feed-older`
- **WHEN** пользователь прокручивает до sentinel
- **THEN** htmx делает `GET /feed/older?before=<самая старая дата окна>`
- **AND** в DOM добавляются timeline-секции следующих 5 дней
- **AND** sentinel заменяется свежим с `before` = самая старая дата чанка

#### Scenario: Исчерпание останавливает подгрузку

- **GIVEN** старых записей больше нет
- **WHEN** sentinel попадает в область видимости и уходит запрос `GET /feed/older`
- **THEN** ответ не содержит sentinel
- **AND** htmx больше не триггерит запросы (бесконечного цикла нет)

#### Scenario: Неавторизованный запрос

- **GIVEN** unauthenticated-запрос
- **WHEN** GET /feed/older
- **THEN** redirect на /login


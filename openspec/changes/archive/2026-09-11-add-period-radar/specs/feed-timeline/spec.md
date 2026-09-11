## MODIFIED Requirements

### Requirement: Feed page renders entries with rose-of-winds

The system SHALL render a `/feed` page as the landing page after login, displaying the user's entries grouped by date. The latest entry of the current day SHALL be rendered as a hero card. The hero card SHALL contain a 200×200px radar chart rendered client-side (canvas + Chart.js, contract `{labels, values}` unchanged) showing **period aggregates** instead of a single-entry snapshot: по умолчанию — текущий день; крошечные кнопки-переключатель (день / неделя / скользящие 30 дней) SHALL переключать период через `GET /feed/radar?period=…` с OOB-свапом контейнера радара без перезагрузки страницы. Значение каждой оси SHALL вычисляться как «среднее с учётом свежести»: сначала среднее непустых значений оси за каждый день периода (день с одной и с пятью записями весит одинаково), затем среднее дней периода с экспоненциальными весами свежести `w(d) = 0.5^(возраст_дня / полураспад)`, полураспад = период/3 (день — равные веса). Пустой период SHALL рендерить радар с нулевыми значениями и aria-label «нет данных». Под радаром SHALL быть подпись «среднее с учётом свежести» (не «среднее»). Other entries SHALL be rendered as compact cards with a state-label badge, timestamp, and axis values as text. In low/mixed states the insights widget SHALL be rendered above the hero card (emphasis on advice over state fixation). The page SHALL render an evening summary banner at the top (before the «Лента» heading) when local time is ≥ 18:00, today has entries, and the summary has not been shown today: тонкий однострочный баннер с тёмным фоном, левой акцентной полосой и крестиком закрытия — без сплошной цветной заливки (`alert-info` не используется) и без встроенной карточки инсайта (совет для состояния живёт только в отдельной карточке совета, см. feed-timeline). Страница не содержит инлайн-чата. В шапке страницы (рядом с заголовком «Лента») SHALL быть иконка-ссылка на полный список записей `/entries`.

#### Scenario: Радар по умолчанию — текущий день

- **GIVEN** пользователь с записями за сегодня (одна или несколько)
- **WHEN** открывает /feed
- **THEN** радар показывает средние значения осей за сегодня
- **AND** при единственной записи за день значения совпадают со значениями записи

#### Scenario: Переключение на неделю

- **WHEN** кликает кнопку «Неделя»
- **THEN** радар свапается на средние значения осей за последние 7 дней
- **AND** кнопка «Неделя» подсвечена как активная, страница не перезагружалась

#### Scenario: Скользящий месяц

- **WHEN** кликает кнопку «Месяц»
- **THEN** радар показывает средние значения осей за последние 30 дней (не календарный месяц)

#### Scenario: Среднее учитывает свежесть и частоту логирования

- **GIVEN** за неделю записей: 3 дня назад энергия 9, сегодня энергия 3, три записи сегодня
- **WHEN** переключает на неделю
- **THEN** значение энергии ближе к свежим дням, чем простое среднее всех записей
- **AND** пять записей одного дня не весят больше одной записи другого дня

#### Scenario: Пустой период

- **GIVEN** за последние 30 дней записей нет
- **WHEN** переключает на месяц
- **THEN** радар отрисован с нулевыми значениями
- **AND** aria-label сообщает «нет данных»

#### Scenario: Иконка списка записей в шапке

- **GIVEN** страница /feed отрендерена
- **WHEN** пользователь ищет ссылку на все записи
- **THEN** в шапке рядом с заголовком есть иконка-ссылка на /entries
- **AND** нижняя навигация по-прежнему содержит ровно 5 пунктов

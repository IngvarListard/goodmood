## ADDED Requirements

### Requirement: Entry creation with user-selected date

The check-in form SHALL allow the user to optionally pick the entry date (`entries.date`) via a collapsed optional «Дата» block with a native `<input type="date">` on /check-in, placed with the template (утро/день/вечер/событие) selector. The input SHALL default to the server's current date and SHALL be constrained client-side with `min` (today − 30 days) and `max` (today). When the block is collapsed, the input SHALL be disabled and not submitted, and the server SHALL store the current date (unchanged behavior). The server SHALL validate the submitted date as an ISO date within `[today − 30; today]` (server clock, same clock as slot pushes); out-of-range or malformed values SHALL be rejected with a validation error and no row inserted. `created_at` SHALL always be the actual creation time.

#### Scenario: Default flow unchanged

- **GIVEN** юзер не открывает блок «Дата» (или не меняет дату)
- **WHEN** отправляет чек-ин
- **THEN** запись сохраняется с датой «сегодня» (серверное время)
- **AND** `created_at` = момент создания

#### Scenario: Backfill yesterday

- **GIVEN** юзер открыл блок «Дата» и выбрал вчерашнюю дату
- **WHEN** отправляет чек-ин
- **THEN** запись сохраняется с датой «вчера»
- **AND** в ленте запись отображается под вчерашним днём

#### Scenario: Future date rejected

- **GIVEN** клиент отправил `date` из будущего (в обход HTML-ограничений)
- **WHEN** сервер валидирует форму
- **THEN** создание отклоняется с ошибкой валидации, строка не вставляется

#### Scenario: Backfill beyond 30 days rejected

- **GIVEN** клиент отправил `date` старше 30 дней от сегодня
- **WHEN** сервер валидирует форму
- **THEN** создание отклоняется с ошибкой валидации, строка не вставляется

## MODIFIED Requirements

### Requirement: Entries can be linked to a state period

The system SHALL allow entries to be optionally linked to a state period, so retrospective analysis can group entries by episode. Entry created during an active period SHALL be automatically linked via `entries.state_period_id`. When an explicit entry date is provided, the link SHALL target the period covering that date (`date(started_at) <= entry date AND (ended_at IS NULL OR date(ended_at) >= entry date)`); when no period covers the provided date, the entry SHALL fall back to the currently active period, and when no period is active the field SHALL stay NULL.

#### Scenario: Entry created during an active period

- **WHEN** у пользователя активен период «подъём» и он создаёт новую запись
- **THEN** запись сохраняется с `state_period_id` = id активного периода
- **AND** фильтр «показать записи из периода X» доступен в ретроспективе

#### Scenario: Backfilled date inside a covered period

- **GIVEN** у юзера закрытый период с `started_at` = 01.09 и `ended_at` = 05.09, и нет активного периода
- **WHEN** он создаёт запись с датой 03.09
- **THEN** запись сохраняется с `state_period_id` = id того периода (накрывающего дату)

#### Scenario: Backfilled date outside any period falls back to active

- **GIVEN** бэкфилл-дата не накрыта ни одним периодом, активный период существует
- **WHEN** запись создаётся с этой датой
- **THEN** запись привязывается к активному периоду (прежнее поведение)

#### Scenario: No covering period and no active period

- **GIVEN** нет ни накрывающего дату, ни активного периода
- **WHEN** запись создаётся с бэкфилл-датой
- **THEN** `state_period_id` остаётся NULL

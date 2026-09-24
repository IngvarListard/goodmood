## MODIFIED Requirements

### Requirement: Список записей (страница /entries)

Система SHALL предоставлять страницу `/entries` — список записей пользователя, сгруппированных по дате (свежие сверху, как в ленте). Первый рендер SHALL включать только последние 7 календарных дней (сегодня и 6 предыдущих); более старые дни SHALL подгружаться чанками по 5 дней при прокрутке (infinite scroll). Каждая запись в списке SHALL показывать: state-бейдж, время, значения ядра (mood/energy/anxiety/focus) и ведёт на карточку записи `/entries/:id`. Доступ SHALL иметь только владелец (как на остальных роутах: unauthenticated → redirect на /login, чужая запись → 404/403). Страница SHALL использовать общий PageShell (layout, навигация, max-w-lg). Счётчик в шапке SHALL отражать общее число записей пользователя по всей истории, а не только загруженное окно.

#### Scenario: Список показывает записи по дням

- **GIVEN** пользователь с записями за несколько дней
- **WHEN** открывает /entries
- **THEN** записи сгруппированы по датам по убыванию, внутри дня — по created_at по убыванию
- **AND** каждая карточка ведёт на /entries/:id

#### Scenario: Владение списком

- **GIVEN** unauthenticated-запрос
- **WHEN** GET /entries
- **THEN** redirect на /login

#### Scenario: Переход из шапки ленты

- **GIVEN** пользователь на /feed
- **WHEN** кликает иконку «все записи» в шапке страницы
- **THEN** открывается /entries

#### Scenario: Стартовое окно — 7 дней

- **GIVEN** у пользователя есть записи за последние 30 дней
- **WHEN** открывает /entries
- **THEN** первый рендер содержит дни только за последние 7 календарных дней
- **AND** счётчик в шапке показывает общее число записей за всю историю

## ADDED Requirements

### Requirement: Подгрузка старых записей (infinite scroll /entries)

Внизу списка система SHALL рендерить самозаменяющий sentinel `<div id="entries-older" hx-get="/entries/older?before=<самая старая загруженная дата>" hx-trigger="revealed" hx-swap="outerHTML">`. `GET /entries/older` SHALL возвращать `day-section` для следующих 5 различных дней строго старше `before` плюс свежий sentinel с `before` = самая старая дата чанка; когда старших записей нет — ответ без sentinel. Секции SHALL переиспользовать существующий `day-section`. Доступ SHALL иметь только владелец.

#### Scenario: Прокрутка подгружает следующие 5 дней

- **GIVEN** первый рендер /entries показал 7 дней и sentinel `#entries-older`
- **WHEN** пользователь прокручивает до sentinel
- **THEN** htmx делает `GET /entries/older?before=<самая старая дата окна>`
- **AND** в DOM добавляются day-section следующих 5 дней
- **AND** sentinel заменяется свежим

#### Scenario: Исчерпание останавливает подгрузку

- **GIVEN** старых записей больше нет
- **WHEN** sentinel уходит в запрос `GET /entries/older`
- **THEN** ответ не содержит sentinel
- **AND** htmx больше не триггерит запросы

#### Scenario: Неавторизованный запрос

- **GIVEN** unauthenticated-запрос
- **WHEN** GET /entries/older
- **THEN** redirect на /login

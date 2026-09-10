## ADDED Requirements

### Requirement: Подписи и порядок пунктов мобильной навигации

Мобильная навигация SHALL содержать ровно 5 пунктов в порядке: Лента, Инсайты, Запись, Мед, Настройки. Подписи SHALL быть локализованы: RU — «Лента», «Инсайты», «Запись», «Мед», «Настройки»; EN — «Feed», «Insights», «Entry», «Meds», «Settings». Подписи SHALL быть достаточно короткими, чтобы все 5 пунктов помещались в один ряд на viewport шириной 412px без переноса строк.

#### Scenario: Порядок и подписи при рендере

- **WHEN** мобильная навигация отрендерена в RU-локали
- **THEN** пункты идут в порядке: Лента, Инсайты, Запись, Мед, Настройки
- **AND** пункт «Запись» ведёт на `/check-in`

#### Scenario: Один ряд на Nothing Phone 1

- **GIVEN** viewport 412×915 и RU-локаль
- **WHEN** страница открыта на мобильной ширине
- **THEN** все подписи пунктов отрисованы без переноса на вторую строку
- **AND** высота панели навигации соответствует одному ряду пунктов

## MODIFIED Requirements

### Requirement: Mobile navigation is a fixed bottom bar

The mobile navigation SHALL be positioned as a fixed bar at the bottom, full width, stacked above other content (z-index), with a safe-area bottom inset on devices with home indicators. Высота бара SHALL быть фиксированной (один ряд пунктов) и выставляться CSS-переменной `--gm-nav-h` на обёртке панели; переменная SHALL быть доступна для позиционирования плавающих элементов (FAB) и расчёта отступов контента. Панель SHALL иметь z-index 50.

#### Scenario: Mobile bottom bar positioning

- **WHEN** layout is rendered
- **THEN** the mobile navigation wrapper has classes `md:hidden fixed bottom-0 inset-x-0`
- **AND** the wrapper has a z-index class (`z-50`)

#### Scenario: Safe-area inset on the bottom bar

- **WHEN** layout is rendered
- **THEN** the mobile navigation includes `pb-[env(safe-area-inset-bottom)]` on its bar or wrapper

#### Scenario: Высота панели доступна как переменная

- **WHEN** layout is rendered
- **THEN** обёртка мобильной панели (или `:root`) содержит `--gm-nav-h` с высотой панели в пикселях (один ряд пунктов, без safe-area)
- **AND** высота панели не превышает ~96px при viewport 412px

### Requirement: Layout provides a single content column shell

The `layout` function SHALL wrap page content in a shell `div` inside `<main>` that centers content, constrains its width to `max-w-lg`, applies horizontal padding `px-4` and top padding `pt-4`, and applies a bottom padding that covers the mobile bottom bar height plus the device safe-area inset, вычисленный от переменной `--gm-nav-h` (`pb-[calc(env(safe-area-inset-bottom)+var(--gm-nav-h)+0.75rem)]`). Хардкод высоты панели в пикселях (например `5rem`) в паддинге контента не допускается.

Page views SHALL NOT render their own top-level width or outer padding wrappers (`max-w-*`, `mx-auto`, `p-4`, `pb-24`); the shell is the single source of page width. The auth page (`app.views.auth`) is exempt.

#### Scenario: Shell classes present

- **WHEN** `(layout {:title "T"} nav-items content)` is called
- **THEN** the result contains a wrapper with classes `mx-auto`, `w-full`, `max-w-lg`, `px-4`, `pt-4`
- **AND** the wrapper has class `pb-[calc(env(safe-area-inset-bottom)+var(--gm-nav-h)+0.75rem)]`

#### Scenario: Pages render without own width wrappers

- **WHEN** any page (`/feed`, `/check-in`, `/medications`, `/settings`, `/insights`, `/insights/new`, `/insights/:id`) is rendered
- **THEN** its content does not contain top-level `max-w-md` or `max-w-2xl` wrappers
- **AND** its content does not contain `pb-24` outer padding

#### Scenario: Content is wider-constrained on desktop too

- **WHEN** the medications page is opened on a viewport wider than 512px (desktop with sidebar)
- **THEN** the content column is centered within the area right of the sidebar and does not exceed 512px

#### Scenario: Нижний контент не перекрывается панелью при двух рядах не бывает

- **GIVEN** viewport 412×915 и RU-локаль
- **WHEN** пользователь открывает `/check-in` и прокручивает до кнопки «Сохранить запись»
- **THEN** кнопка полностью видима выше мобильной панели навигации после полной прокрутки

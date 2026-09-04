# design-tokens Specification

## Purpose
TBD - created by archiving change redesign-tokens. Update Purpose after archive.
## Requirements
### Requirement: Тёмная палитра дизайн-токенов

Тёмная тема (`data-theme=dark`) SHALL отображаться усреднённой дизайн-палитрой через переопределение daisyUI CSS-переменных в скоупленном CSS-блоке layout, чтобы все daisyUI-компоненты перекрашивались без правки вьюх.

#### Scenario: Базовые поверхности тёмной темы

- **WHEN** страница открыта с темой `dark`
- **THEN** фон страницы (`base-100`) — `#0c0f17`, карточки на `bg-base-200` — `#151827`, вложенные поверхности на `bg-base-300` — `#1c2032`, основной текст — `#e6e8f0`

#### Scenario: Акцентные элементы

- **WHEN** отрисованы primary-кнопки и бейджи (`btn-primary`, `badge-secondary`)
- **THEN** акцентный цвет — `#5b5bea`, текст на акценте — белый

#### Scenario: Светлая тема не изменена

- **WHEN** страница открыта с темой `light`
- **THEN** используется прежняя тёплая палитра (`#faf8f4`/`#f1ede5`/`#e6e0d5`, текст `#4a4238`) без визуальных изменений

### Requirement: Шрифт Inter

Интерфейс SHALL использовать шрифт Inter (веса 400–700) с деградацией на system-ui, подключённый через Google Fonts `<link>`.

#### Scenario: Загрузка страницы

- **WHEN** отрендерен `<head>` любой страницы
- **THEN** содержит `<link>` на `fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&display=swap` и body наследует `font-family` с `'Inter'` первым в стеке

### Requirement: Семантические state-цвета

Система SHALL предоставлять CSS-переменные `--gm-state-*` и хелпер-классы `.gm-badge-danger`/`.gm-badge-ok`/`.gm-chip` для state-бейджей и чипов метрик, доступные всем вьюхам в обеих темах (определены вне темизированных блоков).

#### Scenario: Бейдж тревожного состояния

- **WHEN** элемент использует класс `gm-badge-danger`
- **THEN** фон `#4a1f22`, текст `#ef8a86`, радиус 12px

#### Scenario: Бейдж нормального состояния

- **WHEN** элемент использует класс `gm-badge-ok`
- **THEN** фон `#1d3d28`, текст `#7fd493`, радиус 12px

#### Scenario: Цвета метрик

- **WHEN** вьюха ссылается на `--gm-metric-energy`, `--gm-metric-anxiety`, `--gm-metric-focus`
- **THEN** значения соответственно `#f0c22e`, `#e05545`, `#3b82f6`

### Requirement: Градиентный хелпер

Система SHALL предоставлять класс `.gm-gradient` (linear-gradient 135°, `#6366f1` → `#7c5ce0`) для точечного применения на hero-элементах; остальные кнопки используют solid `bg-primary`.

#### Scenario: Применение градиента

- **WHEN** элемент использует класс `gm-gradient`
- **THEN** фон — диагональный градиент indigo→violet из макета new-insight

### Requirement: Единый активный паттерн bottom nav

Мобильная нижняя навигация SHALL использовать один паттерн активного пункта: иконка в контейнере `rounded-xl` с фоном `primary/10`, текст и точка-индикатор снизу — цвета primary.

#### Scenario: Активный пункт навигации

- **WHEN** пользователь находится на `/feed` в мобильной вёрстке
- **THEN** пункт «Лента» выделен контейнером `bg-primary/10` с точкой снизу, остальные пункты — приглушённым цветом без контейнера


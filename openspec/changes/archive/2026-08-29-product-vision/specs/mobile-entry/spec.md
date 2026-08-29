# Mobile Entry Specification

## Purpose

Минимальная настраиваемая форма ввода записи состояния для мобильного и веб. Шаблоны и опциональные блоки обеспечивают минимализм и гибкость одновременно. Это капабилити Фазы 0 — без неё не заполняется даже основатель.

**OQ1 решён (2026-08-18):** состав ядра и опциональных блоков зафиксирован. См. `product-vision/design.md` Decision 9.

## ADDED Requirements

### Requirement: Mobile-first layout
The system SHALL provide an entry form that is usable on a mobile viewport as the primary target.

#### Scenario: Form opens on mobile
- **GIVEN** пользователь открывает форму на устройстве с шириной вьюпорта ≤ 480px
- **WHEN** форма рендерится
- **THEN** все поля доступны без горизонтального скролла
- **AND** навигация — нижняя панель (bottom nav), не сайдбар `[ref: A3-q1]`

### Requirement: Two-axis core
The system SHALL have exactly two mandatory core fields: mood and energy.

#### Scenario: Core fields always visible
- **GIVEN** форма открыта с любым шаблоном
- **WHEN** пользователь видит форму
- **THEN** поля mood (emoji-селектор 😢 😐 🙂 😄 🥳, 1-5) и energy (иконочный селектор «батарейка», 1-5) отображаются всегда
- **AND** оба поля обязательны

#### Scenario: Core fill under 3 seconds
- **GIVEN** пользователь открыл форму
- **WHEN** совершает два тапа (mood + energy)
- **THEN** ядро заполнено
- **AND** целевое время ≤ 3 секунды `[ref: A3-q1, A2-q3]`

### Requirement: Optional blocks
The system SHALL allow each non-core block of the form to be optional and skippable.

#### Scenario: User submits form with only core fields
- **GIVEN** форма с ядром (mood, energy) и опциональными блоками (сон, тревога, заметка)
- **WHEN** пользователь заполняет только ядро и отправляет
- **THEN** запись создаётся успешно
- **AND** опциональные поля остаются `nil` в БД `[ref: A3-q1]`

#### Scenario: Optional blocks are expandable
- **GIVEN** форма с опциональными блоками
- **WHEN** пользователь видит форму
- **THEN** опциональные блоки свёрнуты по умолчанию
- **AND** каждый блок раскрывается по тапу на "+" с меткой названия блока
- **AND** раскрытый блок можно свернуть обратно `[ref: A3-q1]`

### Requirement: Optional block fields
The system SHALL support three optional blocks: sleep, anxiety, and note.

#### Scenario: Sleep block
- **GIVEN** пользователь раскрывает блок «Сон»
- **WHEN** вводит значение
- **THEN** поле `sleep_hours` — float с шагом 0.5, диапазон 0–24
- **AND** поле не обязательно (можно свернуть блок без ввода)

#### Scenario: Anxiety block
- **GIVEN** пользователь раскрывает блок «Тревога»
- **WHEN** выбирает значение
- **THEN** поле `anxiety` — int 1–5, emoji-селектор (😌 😐 😟 😰 😱)
- **AND** поле не обязательно

#### Scenario: Note block
- **GIVEN** пользователь раскрывает блок «Заметка»
- **WHEN** вводит текст
- **THEN** поле `note` — textarea, свободный текст
- **AND** поле не обязательно `[ref: A2-q2]`

### Requirement: Entry templates
The system SHALL support four entry templates with different field sets, auto-selected by time of day.

#### Scenario: Morning template (6:00–11:59)
- **GIVEN** пользователь открывает новую запись в утреннее время
- **WHEN** форма рендерится
- **THEN** активен шаблон «Утро»
- **AND** форма отображает: ядро (mood, energy) + опциональные блоки (сон, заметка) `[ref: A3-q1]`

#### Scenario: Day template (12:00–17:59)
- **GIVEN** пользователь открывает новую запись в дневное время
- **WHEN** форма рендерится
- **THEN** активен шаблон «День»
- **AND** форма отображает: ядро (mood, energy) + опциональные блоки (тревога, заметка)

#### Scenario: Evening template (18:00–5:59)
- **GIVEN** пользователь открывает новую запись в вечернее время
- **WHEN** форма рендерится
- **THEN** активен шаблон «Вечер»
- **AND** форма отображает: ядро (mood, energy) + опциональные блоки (тревога, заметка)

#### Scenario: Quick entry template (soft mode)
- **GIVEN** пользователь в плохом состоянии
- **WHEN** выбирает шаблон «Быстрая запись»
- **THEN** форма содержит только ядро (mood, energy)
- **AND** ни один опциональный блок не отображается
- **AND** целевое время заполнения ≤ 3 секунды `[ref: A2-q3]`

#### Scenario: Manual template switch
- **GIVEN** шаблон определён автоматически по времени суток
- **WHEN** пользователь переключает шаблон вручную
- **THEN** набор опциональных блоков меняется согласно выбранному шаблону
- **AND** значения уже заполненных полей сохраняются

### Requirement: Single-tap entry creation
The system SHALL allow creating a minimal entry with a single primary action.

#### Scenario: Quick entry from feed
- **GIVEN** пользователь на странице «лента/мой день»
- **WHEN** нажимает «+»
- **THEN** открывается форма с шаблоном по умолчанию
- **AND** после сохранения форма закрывается и запись появляется в ленте `[ref: A3-q1]`

### Requirement: No streak-based pressure
The system SHALL NOT display «streak broken» or any guilt-inducing notification when a user misses a day.

#### Scenario: User misses a day and returns
- **GIVEN** пользователь не делал записи вчера
- **WHEN** открывает приложение сегодня
- **THEN** нет уведомлений «цепочка разорвана» или «вы пропустили день»
- **AND** приветствие нейтральное или поддерживающее `[ref: A2-q3]`

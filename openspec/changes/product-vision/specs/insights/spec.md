# Insights Specification (Hypothesis)

## Purpose

Инсайт-артефакт — структурированная единица «совета себе» с привязкой к контексту состояния. Хранится отдельно от дневниковых записей и подбирается системой по текущему состоянию для доставки через каналы советов. Ядро продуктовой ценности: доступ к собственным инсайтам именно в тот момент, когда они нужны.

**Статус:** гипотеза. Fallback при отсутствии инсайта — open question OQ3. Финализируется в имплементационном change `add-insights-artefact` (Фаза 3).

## ADDED Requirements

### Requirement: Insight artefact structure
The system SHALL store insights as structured artefacts with fields: `context` (description of state when the insight arrived), `category` (e.g. «productivity» / «coping» / «identity» / «general»), `advice_to_self` (list of recommendations), optional `identity` (statement about self relevant to the state).

#### Scenario: User creates an insight
- **GIVEN** пользователь в состоянии «лёгкий подъём энергии» получил инсайт
- **WHEN** создаёт инсайт-артефакт
- **THEN** заполняет `context`, `category`, хотя бы один `advice_to_self`
- **AND** опционально заполняет `identity`
- **AND** артефакт сохраняется с привязкой к текущей розе ветров `[ref: A2-q2, A2-q4]`

### Requirement: Insight matching by current state
The system SHALL surface historically relevant insights based on the user's current rose-of-winds distribution.

#### Scenario: User in anxious state sees past anxious-state insights
- **GIVEN** у пользователя есть исторические инсайты с контекстом «тревога 4, фокус 2»
- **WHEN** текущая роза ветров близка к этому контексту
- **THEN** эти инсайты предлагаются в «ленте/мой день» и/или через каналы доставки `[ref: A2-q4, A3-q3]`

### Requirement: No insight loss
The system SHALL preserve all insights across state changes and time, including insights created during depressive or mixed states, so they remain accessible when the user returns to a similar state.

#### Scenario: User in depression creates insight, later returns to depression
- **GIVEN** пользователь в депрессивном состоянии создал инсайт «как пережить спад»
- **WHEN** спустя месяцы возвращается в похожее состояние
- **THEN** инсайт доступен и предлагается системой
- **AND** пользователь не должен помнить об инсайте, чтобы его увидеть `[ref: A4-q5, A2-q4]`

### Requirement: Fallback when no matching insight exists
The system SHALL define a fallback strategy when no historical insight matches the current state (open question OQ3 — to be finalized in implementation change).

#### Scenario: User in a novel state with no past insights
- **GIVEN** пользователь в состоянии, для которого ещё нет инсайтов
- **WHEN** открывает «ленту» или нажимает «что мне делать сейчас»
- **THEN** система применяет согласованную стратегию fallback (молчать / AI-генерация / чужой совет — TBD) `[ref: OQ3]`

### Requirement: Insight editability and versioning
The system SHALL allow the user to edit, extend, or reclassify insights over time without losing the original context.

#### Scenario: User extends an old insight with new advice
- **GIVEN** у пользователя есть инсайт «как пережить подъём»
- **WHEN** в новом подъёме добавляет к нему ещё один совет
- **THEN** артефакт обновляется, история изменений сохраняется
- **AND** контекст оригинала не теряется `[ref: A2-q2]`

### Requirement: Insights are personal
The system SHALL NOT share or expose a user's insights to other users. AI operates only on the user's own insights (except explicit future opt-in for shared library).

#### Scenario: AI generates advice from user's own insights
- **GIVEN** у пользователя есть N инсайтов
- **WHEN** AI генерирует совет для текущего состояния
- **THEN** совет основан только на инсайтах этого пользователя
- **AND** чужие инсайты не используются `[ref: A3-q4, proposal Non-goals]`

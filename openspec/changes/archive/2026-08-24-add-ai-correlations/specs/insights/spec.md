## ADDED Requirements

### Requirement: AI advice widget under insights
The system SHALL display an AI-synthesised advice (`ai-advice`) in the /feed insights area, generated from the user's own historical insights with context similar to the current state. The widget SHALL show a human-readable explanation («почему этот совет?»), a link to the originating insight («из твоего инсайта от …»), and a «пожаловаться» button. If the user has no own insights or AI is disabled, the widget SHALL NOT appear.

#### Scenario: AI advice shown with source link
- **WHEN** у пользователя есть исторические инсайты с близким к текущему контекстом и AI-советы включены
- **THEN** под виджетом инсайтов на /feed показывается секция `ai-advice`
- **AND** совет содержит ссылку «из твоего инсайта от …» на исходный инсайт
- **AND** совет содержит кнопку «почему этот совет?» с объяснением
- **AND** совет содержит кнопку «пожаловаться»

#### Scenario: No own insights → no AI advice
- **WHEN** у пользователя нет ни одного инсайта (или AI-советы отключены)
- **THEN** секция `ai-advice` не показывается на /feed
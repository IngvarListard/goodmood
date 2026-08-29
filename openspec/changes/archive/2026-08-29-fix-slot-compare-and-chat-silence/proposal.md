## Why

Два бага ломают пользовательский опыт:

1. **`/feed/pending-insight` падает с ClassCastException** — `past-slot-time?` сравнивает строки `"HH:MM"` через `>=` (числовой оператор). Polling-баннер «пока тебя не было» не работает у всех пользователей с включёнными слотами.
2. **AI-чат молчит** — при отсутствии `OPENROUTER_API_KEY` (или любой ошибке сети/non-200) `call-chat` возвращает `nil`, ассистент-сообщение не сохраняется, и пользователь видит только своё сообщение без ответа и без ошибки. Случай «нет ключа» даже не логируется.

## What Changes

- **fix**: `past-slot-time?` — сравнивать время числово (минуты с полуночи), не строками через `>=`.
- **fix**: при `reply = nil` в `chat-send` показывать пользователю фолбэк-сообщение («не удалось получить ответ, попробуйте позже») вместо тишины.
- **fix**: логировать случай отсутствия `OPENROUTER_API_KEY` в `call-chat` (сейчас логируются только non-200 и network errors).
- ВRequirement в спеке `ai-assistant`: добавить требование graceful failure для AI-чата.

## Capabilities

### New Capabilities
<!-- нет новых -->

### Modified Capabilities
- `ai-assistant`: добавить требование «AI chat graceful failure» — при невозможности получить ответ (нет ключа, сеть, non-200) пользователь видит фолбэк-сообщение, а не тишину.

## Impact

- `src/app/routes/notifications.clj` — функция `past-slot-time?`
- `src/app/domains/ai.clj` — функция `call-chat` (логирование отсутствия ключа)
- `src/app/routes/ai.clj` — функция `chat-send` (фолбэк при `reply = nil`)
- `src/app/views/ai.clj` — фолбэк-сообщение в `chat-response`
- Затрагивает пользователей: polling `/feed/pending-insight` и AI-чат

## Why

Фаза 6 продуктового roadmap'а (`product-vision`). Фаза 5 дала AI-корреляции, AI-ярлыки и AI-советы из своих инсайтов. Но AI не может предложить подходящую технику, когда у пользователя НЕТ своих инсайтов для текущего состояния; нет живой «поддержки в моменте» (чата) для плохих состояний; и нет способа зафиксировать устойчивый период (спад/подъём/тревожную неделю) для ретроспективы.

Фаза 6 закрывает эти пробелы: опт-ин «новые советы» (общие DBT/CBT-техники, не из своих записей), AI-чат on-demand с guardrails (признаки кризиса → ресурс помощи, disclaimer, контекст = свои данные), и «периоды состояния» с явным началом/концом и привязкой записей. Решения зафиксированы в `DECISIONS.md` (D6-1…D6-4) и `product-vision/design.md` (Decision 5 — гранулярность, Decision 8 — AI поэтапно, resolve OQ7, OQ8 часть 2).

`[ref: A3-q4, A3-q6, A2-q4, OQ7, OQ8]`

## What Changes

- **Новые AI-советы (novel advice)**: opt-in в настройках (`allow_novel_advice`, default **off**). Основаны на общих техниках (DBT/CBT), НЕ на своих инсайтах. Помечены «не из твоих записей». Выключаемы в любой момент. Показываются в секции `ai-novel-advice` на /feed, когда нет релевантных собственных инсайтов.
- **AI-чат on-demand**: доступен по кнопке «чат» (не push). Контекст: текущая роза ветров + последние записи + свои инсайты. Disclaimer при первом открытии («не заменяет профессиональную помощь»). При признаках кризиса (ключевые слова) → напоминание о ресурсе (телефон доверия) вместо просто ответа. Сообщения кэшируются в новой таблице `ai_chat_messages`.
- **Периоды состояния**: таблица `state_periods` (id, user_id, label, started_at, ended_at nullable, notes, created_at). Пользователь вручную отмечает начало/конец. Записи при создании автоматически привязываются к активному периоду через `entries.state_period_id` (колонка уже есть, миграция 006). Периоды НЕ создаются автоматически (только по воле пользователя) — автоопределение эпизода это Фаза 7 с guardrails.
- **AI-провайдер**: тот же (OpenRouter, deepseek/glm-5.2), уже в deps (Фаза 5).

## Capabilities

### New Capabilities
- `state-periods`: «периоды состояния» с явным началом/концом и привязкой записей; ретроспективная группировка записей по периодам.

### Modified Capabilities
- `ai-assistant`: добавляются «новые советы» (opt-in, documented as not from user's notes) и AI-чат on-demand с guardrails (кризис → ресурс, disclaimer). (Фаза 6 часть.)
- `entry-granularity`: финализируется вариант «в» — периоды состояния с явным началом/концом и привязкой записей (OQ7 resolved).

## Impact

- **БД**: миграция `011-add-state-periods` (таблица `state_periods` + колонка `allow_novel_advice` в `user_ai_settings` + таблица `ai_chat_messages`).
- **Код**: `src/app/db/state_periods.clj`, `src/app/db/ai.clj` (расширение — чат), `src/app/domains/state_periods.clj`, `src/app/domains/ai.clj` (novel advice, chat), `src/app/routes/state_periods.clj`, `src/app/routes/ai.clj` (чат, novel advice endpoints), `src/app/views/state_periods.clj`, `src/app/views/ai.clj` (чат UI, novel advice UI), интеграция в `views/feed.clj`, `views/settings.clj`, `routes/check_in.clj`, `routes/app.clj`, `i18n`.
- **Среда**: API-ключ OpenRouter из env (уже есть из Фазы 5).
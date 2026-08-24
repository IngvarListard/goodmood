## Why

Фаза 7 — финальная и самая рискованная: AI предупреждает о возможном начале эпизода (депрессивного или (гипо)маниакального) на основе трендового анализа. Фазы 5–6 дали корреляции, ярлыки, советы из своих и новых, чат и периоды. Но пользователь с биполярным расстройством остаётся один на один с наступающим эпизодом: система пассивно ждёт, пока он сам отметит состояние.

Фаза 7 добавляет упреждающий, но строго guarded сигнал. Риск «AI вызывает тревогу/панику» критичен (design.md Risks — AI anxiety amplification), поэтому все решения зафиксированы в `DECISIONS.md` (D7-1) и spec `ai-assistant` (Episode onset warning): opt-in default OFF, confidence threshold > 0.75, объяснимость, лёгкое выключение, false-alarm feedback, кризис → ресурс, не предупреждение.

`[ref: A3-q4, design.md Risks, OQ8 часть 3]`

## What Changes

- **Предупреждение о возможном начале эпизода**: AI анализирует тренд последних N дней (энергия, тревога, сон, настроение) и при паттерне, похожем на начало депрессивного или (гипо)маниакального эпизода, показывает мягкое, объяснимое предупреждение на /feed.
- **Guardrails (критично, все обязательны)**:
  - Opt-in default OFF (в настройках и toggle на предупреждении).
  - Confidence threshold > 0.75 — ниже НЕ показывать, логировать для уточнения.
  - Объяснимость: паттерн («последние 3 дня: энергия растёт, сон падает»).
  - Лёгкое выключение в один клик (в предупреждении и в настройках).
  - False-alarm feedback → логируется в БД (`episode_warnings.feedback`).
  - Признаки кризиса (ключевые слова) → ресурс (телефон доверия/112/03), НЕ предупреждение.
- **Копия — поддерживающая, не тревожная**: «Похоже, последние дни похожи на твой прежний паттерн спада. Хочешь посмотреть, что помогало тогда?» — НЕ «ВНИМАНИЕ! Возможен эпизод!».
- **AI-провайдер**: тот же (OpenRouter, deepseek для анализа тренда), уже в deps.

## Capabilities

### New Capabilities
- (нет новых — фича расширяет `ai-assistant`)

### Modified Capabilities
- `ai-assistant`: добавляется Episode onset warning (opt-in, guarded) с guardrails: confidence threshold, объяснимость, выключаемость, false-alarm feedback, кризис → ресурс. (Фаза 7 часть.)

## Impact

- **БД**: миграция `013-add-episode-warnings` (таблица `episode_warnings`: id, user_id, type depressive/hypomanic, pattern_description, confidence REAL, feedback nullable, dismissed INTEGER DEFAULT 0, created_at) + колонка `episode_warning_enabled` (в `user_ai_settings`, default 0).
- **Код**: расширение `src/app/db/ai.clj` (warnings CRUD), `src/app/domains/ai.clj` (trend analysis, confidence, threshold, pattern description), `src/app/routes/ai.clj` (warning endpoint, feedback, dismiss, toggle), `src/app/views/ai.clj` (warning UI в feed, toggle в settings), интеграция в `views/feed.clj`, `views/settings.clj`, `routes/feed.clj`, `routes/app.clj`, `i18n`.
- **Среда**: API-ключ OpenRouter из env (уже есть).
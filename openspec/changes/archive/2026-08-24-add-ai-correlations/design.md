## Context

Фаза 5 добавляет AI-ассистента на собственных данных пользователя. До сих пор подбор инсайтов rule-based (exact match по `state_label`, Фаза 3). Архитектура: ring + reitit, integrant, hiccup2 + htmx/hyperscript, SQLite + next.jdbc + HoneySQL + migratus. AI-вызовы — к OpenRouter API. Ключ в env `OPENROUTER_API_KEY` (файл в gitignore). Библиотеки: `clj-http` (новая, утверждена человеком) для HTTP, `cheshire` (уже в deps) для JSON.

## Goals / Non-Goals

**Goals:**
- Фоновый поиск корреляций при ≥ 14 дней записей, кэш в `ai_findings`.
- AI-предложение названия состояния (не заменяет ручной ярлык).
- AI-советы из собственных инсайтов с объяснимостью и feedback.
- Per-function opt-out + master-toggle в настройках.
- Все AI-вызовы асинхронные (не блокируют UI).

**Non-Goals:**
- Генерация «новых» советов (не из своих заметок) — Фаза 6.
- AI-чат — Фаза 6.
- Предупреждение эпизода — Фаза 7.
- Использование чужих данных — запрещено (Non-goal из proposal).
- Полный PWA push / service worker — future.

## Decisions

### 1. Модель данных `ai_findings`
Таблица `ai_findings` (миграция 009): `id`, `user_id`, `type` (`correlation`/`label`/`advice`), `content` (JSON), `confidence` (`high`/`medium`/`low`, nullable), `source_refs` (JSON-массив), `feedback` (nullable), `hidden` (INTEGER DEFAULT 0), `created_at`. Без FK на `entries`/`insights` (развязка, как `medication_logs`). Находки не удаляются — только `hidden`. Записи не нужно хранить `entries.state_period_id` (это Фаза 6).

### 2. AI-провайдер и модели (D5-1)
OpenRouter `https://openrouter.ai/api/v1/chat/completions`. Корреляции/ярлыки — `deepseek/deepseek-chat`; советы — `z-ai/glm-5.2`. HTTP через `clj-http`, JSON — `cheshire`. Промпт для корреляций требует от модели вернуть структурированный JSON (находка + confidence + объяснение), для советов — совет + ссылки на инсайты.

### 3. Guardrails корреляций (D5-2)
Порог: ≥ 14 дней записей. Каждая находка — с уровнем уверенности. Пользователь: «не релевантно» / «уже знал» → feedback + hidden. Находки не удаляются.

### 4. Guardrails советов (D5-3)
Совет строится ТОЛЬКО на собственных инсайтах. Ссылки на исходные инсайты. «Пожаловаться» → hidden + feedback. «Новые» советы не генерируются (Фаза 6).

### 5. Асинхронность
AI-вызовы — background. Вариант: отдельный background thread per запрос (future/mk) с кэшем результата в `ai_findings`. В тестах и для детерминизма e2e — триггер анализа явный (endpoint). В продакшене — по накоплении данных / cron-подобный таймер. Учитывая конвенцию htmx-first, используем polling: /feed запрашивает последние `ai_findings`, а анализ запускается через отложенную задачу или при первом обращении после порога.

### 6. Feedback и opt-out (D5-4, D5-2/3)
Таблица `user_ai_settings`? — НЕТ, храним настройки AI в расширяемой таблице настроек или как флаги у пользователя. Учитывая паттерн notification-settings, добавим лёгкий механизм: per-user JSON-настройки AI в новой колонке `users.ai_settings` (JSON) либо таблица `user_ai_settings` (user_id PK, correlations_enabled, labels_enabled, advice_enabled, master_enabled). Примем: таблица `user_ai_settings` по аналогии с `user_notification_settings`.

### 7. UI placement
- /feed: секция `ai-correlations` (внизу, data-testid), `ai-state-label` рядом с `state_label`, `ai-advice` под виджетом инсайтов.
- /settings: секция «AI» с master-toggle и per-function тумблерами.
- Все элементы — htmx-фрагменты (OOB / swap), DaisyUI-классы.

## Risks / Trade-offs

- **AI-вызов может быть медленным/недоступным** → [Risk] → Mitigation: асинхронность + кэш в `ai_findings`; UI не блокируется; graceful fallback (показать пустую секцию).
- **Неточность модели** → Mitigation: уровни уверенности, feedback-петля, «не релевантно»/«пожаловаться».
- **Chirke данных** → Mitigation: ТОЛЬКО собственные данные (нельзя включить чужие в промпт), фильтры по `user_id`.
- **API-ключ в коде** → Mitigation: env `OPENROUTER_API_KEY`, не логировать, не коммитить (gitignore).
- **Дрейф схемы при миграциях** → Mitigation: down-миграции, проверка через `sqlite3`.

## Migration Plan

1. Миграция 009: `ai_findings` (+ down).
2. Миграция 010: `user_ai_settings` (настройки AI) — если не вместили в users.
3. Прогнать `clojure -M:test`, smoke `clj -M -m app.core`.

## Open Questions

- Нет блокирующих — все OQ8 (часть 1) решены в DECISIONS.md (D5-1…D5-5).
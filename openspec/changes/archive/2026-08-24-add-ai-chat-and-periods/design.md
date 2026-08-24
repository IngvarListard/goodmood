## Context

Фаза 6 развивает AI-ассистента из Фазы 5 (корреляции, ярлыки, советы из своих инсайтов) и вводит «периоды состояния». Стек: Clojure, ring+reitit, integrant, hiccup2+htmx/hyperscript, SQLite+next.jdbc, migratus. AI через OpenRouter (`clj-http` + `cheshire`, из Фазы 5). Решения: DECISIONS.md D6-1…D6-4.

## Goals / Non-Goals

**Goals:**
- Novel advice (opt-in, default OFF, помечены «не из твоих записей»).
- AI-чат on-demand с guardrails (кризис → ресурс, disclaimer при первом открытии, контекст = свои данные).
- Периоды состояния: user-defined, с явным началом/концом, авто-привязка записей.
- Всё на htmx/hyperscript, без новых библиотек.

**Non-Goals:**
- Автоопределение эпизода — Фаза 7 (с guardrails).
- PWA push — future.
- Использование чужих данных — запрещено.

## Decisions

### 1. Модель данных
- `state_periods` (миграция 011): id, user_id, label, started_at, ended_at nullable, notes nullable, created_at.
- `entries.state_period_id` уже есть (миграция 006); при создании записи при активном периоде — проставляется.
- `user_ai_settings.allow_novel_advice` (добавляем колонку в 011) — opt-in по умолчанию 0.
- `ai_chat_messages` (миграция 012): id, user_id, role, content, created_at — кэш чата.

### 2. Novel advice
- Endpoint `POST /ai/advice` (novel) или `/feed`-фрагмент. Проверка `allow_novel_advice`. Пометка «не из твоих записей». Если есть релевантные свои инсайты — не показываем novel (приоритетом свои).
- Модель: glm-5.2 (как советы из своих).

### 3. Чат
- Кнопка «чат» на /feed открывает фрагмент `ai-chat`. Сообщения POST `/ai/chat` (json-enc, CSRF). Ответ — htmx-swap в `#chat-response`.
- Промпт: роза ветров последней записи + последние записи + свои инсайты.
- Кризис-детекция: словарь ключевых слов (ru/en); при совпадении ответ — напоминание о помощи (телефон доверия) + ответ AI.
- Disclaimer при первом открытии (можно dismiss, в сессии).

### 4. Периоды
- UI: на /feed кнопка «начать период» → модалка/меню выбора label → POST `/periods/start`; активный период — индикатор `active-period-indicator` + кнопка «закрыть».
- Check-in: при создании записи, если есть активный период — `state_period_id`.
- Список периодов в ретроспективе (read-only на /feed или /insights).

## Risks / Trade-offs

- **Кризис-детекция не идеальна** → Mitigation: словарь + напоминание при любом подозрении; AI не единственная линия.
- **Novel advice может противоречить своим инсайтам** → Mitigation: приоритет своим; явная пометка; opt-out.
- **Период заперт** → Mitigation: всегда можно закрыть; no auto-lock.

## Migration Plan

1. Миграции 011, 012 (up/down).
2. `clojure -M:test`, smoke `clj -M -m app.core`.

## Open Questions

Нет блокирующих — DECISIONS.md D6-1…D6-4 закрывают OQ7 и OQ8 (часть 2).
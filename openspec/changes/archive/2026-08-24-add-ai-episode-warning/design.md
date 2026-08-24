## Context

Фаза 7 — самая рискованная фича: AI предупреждает о начале эпизода. Главный риск — «AI вызывает тревогу/панику» (design.md Risks — AI anxiety amplification). Поэтому guardrails критичны и все обязательны. Стек: Clojure + ring/reitit/integrant/hiccup2/htmx/hyperscript/SQLite/migratus. AI через OpenRouter (deepseek). Решения: DECISIONS.md D7-1, D7-2.

## Goals / Non-Goals

**Goals:**
- Opt-in default OFF; confidence threshold > 0.75; объяснимость; лёгкое выключение; false-alarm feedback; кризис → ресурс (не предупреждение); поддерживающий (не тревожный) copy.

**Non-Goals:**
- Диагнозы / медицинские утверждения — строго запрещено.
- Авто-включение или push без согласия.
- Использование чужих данных.

## Decisions

### 1. Модель данных
- Миграция `013-add-episode-warnings`: таблица `episode_warnings` (id, user_id, type depressive/hypomanic, pattern_description TEXT, confidence REAL, feedback TEXT nullable, dismissed INTEGER DEFAULT 0, created_at). Без FK (развязка).
- Колонка `episode_warning_enabled` в `user_ai_settings` (INTEGER DEFAULT 0) — opt-in.

### 2. Trend analysis
- Вход: последние N дней (например 7) записей (энергия, тревога, сон, настроение). AI (deepseek) получает компактную сводку тренда и возвращает JSON: `{type: "depressive"|"hypomanic"|"none", confidence: 0.0-1.0, pattern: "строка-объяснение"}`.
- Guardrail: если `type` != none И `confidence` > 0.75 → сохранить warning и показать. Если confidence ≤ 0.75 → НЕ показывать, логировать (вставить строку с feedback=null? нет — логировать отдельно: хранить в episode_warnings с confidence низким, но не показывать). Решение: при низкой уверенности пишем строку `hidden_pattern_log` (можно использовать ту же таблицу с флагом, но проще: не показывать и не сохранять — но спеку требует «паттерн логируется». Заведём отдельную лёгкую запись: при low confidence пишем в episode_warnings строку с `confidence`, `dismissed=1` как маркер «не показано»; UI фильтрует по dismissed=0).
- Анализ фоновый (future), как в Фазе 5, триггер при первом обращении к /feed.

### 3. Guardrails
- `episode-warning-enabled?` — opt-in (master AI + episode_warning_enabled).
- Threshold: `> 0.75` (0.75 → НЕ показать, 0.76 → показать). Тесты границ.
- Кризис: словарь ключевых слов в последней записи (note) / чате → показать ресурс (баннер с телефоном доверия), НЕ warning. Приоритет кризиса выше паттерна.
- Copy: `pattern_description` от AI + поддерживающая формулировка. Не «ВНИМАНИЕ».
- Выключение: кнопка «выключить» в самом warning (POST → toggle off) и toggle в настройках.
- False alarm: кнопка → `feedback='false_alarm'`, `dismissed=1`, скрыть.

### 4. UI
- `/feed`: секция `episode-warning` (data-testid) как мягкий alert-info, под hero. Кнопки: «выключить», «ложная тревога», «почему?» (раскрывает pattern_description).
- `/settings`: toggle `episode-warning-toggle` (data-testid), default off.

## Risks / Trade-offs

- **Паника от предупреждения** → Mitigation: только > 0.75, поддерживающий copy, объяснимость, лёгкое выключение.
- **Неточность модели** → Mitigation: false-alarm feedback, low-confidence логирование.
- **Кризис пропущен** → Mitigation: словарь + приоритет ресурса.

## Migration Plan

1. Миграция 013 (up/down).
2. `clojure -M:test`, smoke `clj -M -m app.core`, e2e `phase7-episode-warning.spec.ts`.

## Open Questions

Нет блокирующих — DECISIONS.md D7-1/D7-2 закрывают OQ8 (часть 3).
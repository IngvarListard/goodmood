# START HERE — точка входа для автономного прохождения фаз

## Статус проекта

```
Фаза 0: MVP-мобильная форма              ✅ архивирован (2026-08-19)
Фаза 1: Медикаменты                       ✅ архивирован (2026-08-21)
Фаза 2: Роза ветров + гранулярность       ✅ архивирован (2026-08-22)
Фаза 3: Инсайт-артефакт + подбор          ✅ архивирован (2026-08-23)
Фаза 4: Каналы доставки советов           ✅ архивирован (2026-08-23)
Фаза 5: AI-корреляции, ярлыки, советы     ✅ архивирован (2026-08-24)
Фаза 6: AI-новые советы, чат, периоды     ✅ архивирован (2026-08-24)
Фаза 7: AI-предупреждение эпизода         ✅ архивирован (2026-08-24)
```

## Статус: все фазы 0–7 завершены и архивированы.

Для продолжения проверь `openspec/changes/product-vision/tasks.md` (сквозные
задачи 8.x) и `openspec doctor`.

## Как начать (для свежей сессии)

Прочитай эти файлы по порядку:

1. **`DECISIONS.md`** — все предзаписанные решения для фаз 5–7 (AI-провайдер, guardrails, push-вариант, commit-стратегия, retry-политики). Не оспаривать.
2. **`harness/orchestrator.md`** — твой playbook: как диспатчить сабагентов, retry, commit, когда СТОП.
3. **`openspec/changes/product-vision/tasks.md`** — найди первый unchecked блок (Фаза 5).
4. **`harness/phase-prompts/phase5.md`** — промпт для сабагента-реализатора Фазы 5.
5. **`harness/test-prompts/phase5.md`** — промпт для сабагента-тестера Фазы 5.
6. **`openspec/changes/product-vision/specs/ai-assistant/spec.md`** — спека (контракт).

## Команда запуска

Пользователь скажет: «пройди оставшиеся фазы по harness/orchestrator.md».

Начни с Фазы 5. Следуй orchestrator.md по шагам A–H.

## Ключевые факты для контекста

- **Стек:** Clojure, deps.edn, ring + jetty, reitit, integrant, hiccup2, htmx, hyperscript, Tailwind + DaisyUI, SQLite, next.jdbc, HoneySQL, migratus, cheshire (для AI JSON).
- **AI-провайдер:** OpenRouter API. Ключ в файле `OPENROUTER_API_KEY` (в gitignore). Экспортировать в env: `export OPENROUTER_API_KEY="$(cat OPENROUTER_API_KEY)"`. Модели: deepseek (корреляции), z-ai/glm-5.2 (советы).
- **Тесты:** `clojure -M:test`. Приложение: `clj -M -m app.core`. E2E: `cd e2e && npx playwright test`.
- **Коммитить** после каждой архивированной фазы: `phase N: <change-name> (<описание>)`.
- **Никаких библиотек** без вопроса человеку (правило `openspec/config.yaml`). cheshire уже добавлен.
- **Сабагенты:** использовать `Task(general, ...)` с промптами из `harness/phase-prompts/` и `harness/test-prompts/`. Тестер — слепой (не видит реализацию).
- **Гейты «спроси человека»** — все закрыты в `DECISIONS.md`. Если встретишь нерешённый — СТОП.

## Структура harness

```
DECISIONS.md                           — ответы на все гейты фаз 4-7
harness/
├── orchestrator.md                    — playbook loop (A→H)
├── phase-prompts/{phase5,6,7}.md      — промпты реализатора
└── test-prompts/{phase5,6,7}.md       — промпты слепого тестера
e2e/tests/
├── phase5-ai-correlations.spec.ts     — Playwright-ассерты Фазы 5
├── phase6-novel-advice-chat-periods.spec.ts  — Фазы 6
└── phase7-episode-warning.spec.ts     — Фазы 7 (guardrails-приоритет)
```

## Что НЕ делать

- Не пиши код сам (оркестратор) — диспатчить сабагентов.
- Не оспаривай `DECISIONS.md`.
- Не добавляй библиотеки без вопроса.
- Не пропускай test-writer-сабагента (контракт-дыры).
- Не забывай `openspec archive` + commit после каждой фазы.

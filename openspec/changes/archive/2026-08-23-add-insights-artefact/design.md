## Context

Фаза 3 продуктового roadmap'а (`product-vision`). Фазы 0–2 заложили: мобильную форму (`mobile-entry`), медикаменты (`medications`), розу ветров состояний с rule-based ярлыками (`mood-states`, 6 значений: `mixed/anxiety/elevated/low/balanced/neutral`) и /feed как landing с hero-карточкой последней записи.

Решения для Фазы 3 зафиксированы в `product-vision/design.md` Decision 13 (2026-08-23), OQ3 resolved. UI-скетчи четырёх экранов в `openspec/changes/product-vision/ui-sketches/insights-{list,new,feed-widget,show}.clj`.

Текущая архитектура (по `CLOJURE-RULES.md`, `03-architecture.md`): `src/app/{db,domains,routes,views,domains}/...`, явные reitit-роуты в `app.routes.app`, интегрант-инициализация, HoneySQL для запросов, migratus для миграций, malli для валидации, hiccup2 для рендера, htmx для свапов. Loose-coupling без FK — устоявшийся паттерн (`state_period_id`, medications link-by-date — Decision 4a.4, 12.7).

`[ref: A2-q2, A2-q4, A4-q5, A3-q3]`

## Goals / Non-Goals

**Goals:**
- Таблица `insights` (миграция 007) со структурированным артефактом.
- Создание инсайта из 3 entry point (из /feed, post-save из /check-in, standalone /insights/new).
- Rule-based подбор по `state_label` (exact match) — 1 инсайт на /feed.
- Fallback при отсутствии (OQ3): молчать + мягкий онбординг per-state.
- In-place редактирование (htmx swap per-поле), `state_label` нередактируемый.
- Удаление с подтверждением (modal).
- Навигация: 5 пунктов (`/feed, /check-in, /insights, /medications, /settings`).
- Персональность: только свои инсайты.

**Non-Goals:**
- AI-генерация fallback'а и советов (Фаза 5).
- Euclidean-distance matching по осям розы (Фаза 5).
- Medication-aware matching (Фаза 5).
- Каналы доставки / push (Фаза 4).
- Полная версия истории `insight_versions` (Фаза 6).
- Редактирование `state_label` post-creation (Фаза 6+).
- Shared insight library (future opt-in, Non-goal proposal).
- Вечерние инсайты на «завтра» (Фаза 4, OQ6).

## Decisions

### 1. Схема `insights` (миграция 007)

```sql
CREATE TABLE insights (
  id             INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id        INTEGER NOT NULL REFERENCES users(id),
  context        TEXT NOT NULL,
  category       TEXT NOT NULL CHECK (category IN ('productivity','coping','identity','general')),
  advice_to_self TEXT NOT NULL,   -- JSON-массив строк: ["...","..."]
  identity       TEXT,            -- nullable
  state_label    TEXT,             -- nullable; ключ подбора, raw-ключ (как entries.state_label)
  entry_id       INTEGER,          -- nullable, soft reference на entries.id (БЕЗ FK)
  created_at     TEXT NOT NULL DEFAULT (datetime('now')),
  updated_at     TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX idx_insights_user_state
  ON insights (user_id, state_label);
```

Down: `DROP TABLE insights;`

**Рationale:** `category` как TEXT с CHECK — устоявшийся паттерн (`medication_logs.status` Decision 4a). `advice_to_self` — JSON-массив в TEXT (не отдельная таблица, не newline-separated). `entry_id` без FK — soft reference по mandate spec «No insight loss» (инсайт переживает удаление записи). `state_label` — nullable для standalone-инсайтов. Снапшот осей НЕ хранится — backfill в Фазе 5 из `entry_id`.

**Альтернативы (отвергнуты):**
- Отдельная таблица `insight_advice` (YAGNI: список 1–5 пунктов, читается/пишется целиком, нет cross-insight запросов; mirrors `medications.schedule` TEXT-JSON).
- `entry_id` с FK CASCADE (нарушает «No insight loss») / FK RESTRICT (блокирует удаление записи).
- Хранение снапшота energy/anxiety/focus сейчас (Фаза 3 матчится по `state_label`; Фаза 5 backfill из `entry_id`).

### 2. `advice_to_self` — JSON-массив в TEXT

Сериализация через `clojure.data.json` (уже в проекте, `app.db.medications`). Чтение: `json/read-str` → вектор строк, пустой вектор при ошибке. Запись: `json/write-str` → TEXT. Валидация на домене: min 1 non-empty строка.

Редактирование = перезапись массива целиком, один атомарный UPDATE. На форме — динамический список textarea per пункт, кнопка «Добавить пункт» (htmx-get за фрагмент), удаление через hyperscript `on click remove closest .advice-item`. Сериализация: все `textarea[name="advice_to_self[]"]` собираются в массив.

### 3. Подбор — exact match по `state_label` (variant A)

```sql
SELECT … FROM insights
WHERE user_id = ? AND state_label = ?
ORDER BY updated_at DESC
LIMIT 1;
```

`:neutral` — равноправный 6-й ярлык, обрабатывается одинаково (матч + онбординг). Если у пользователя нет записей сегодня — виджет не показывается (нет current state для матчинга). Индекс `idx_insights_user_state` покрывает горячий путь.

**Рationale:** `state-label` fn (domains/entries.clj) уже редуцирует розу в 6 бакетов. Exact-match O(1)-ish via index, ноль математики, достаточно для «что я себе говорил в таком состоянии». Euclidean distance (variant C) — Фаза 5, снапшот осей backfill из `entry_id`.

**Альтернативы (отвергнуты):**
- Euclidean distance по осям (variant B/C) — ложная точность при малом числе инсайтов; Фаза 5.

### 4. Fallback (resolve OQ3) — молчать + мягкий онбординг

При отсутствии релевантных инсайтов — мягкий per-state промпт: «Инсайтов для состояния "X" ещё нет» + кнопка «Создать» → `/insights/new?state_label=X`. Dismissible по смыслу (не modal, а empty-state в секции). Тон фактический («ещё нет»), без strik/self-blame языка.

**Альтернативы (отвергнуты):** AI-генерация (Фаза 5, противоречит «Insights are personal»); чужой совет (Non-goal).

### 5. UI placement

- `/insights` — список + htmx-фильтр-табы по category + кнопка «Новый».
- `/insights/new` — форма (context, state_label select, category select, advice_to_self динамический список, identity опционально).
- `/insights/:id` — просмотр + in-place edit (4 карточки: шапка с бейджами, контекст, советы, идентичность, метаданные+действия).
- Виджет на `/feed` — секция под hero-карточкой в `today-section`, 1 компактная карточка или онбординг.

**Референсы:** `ui-sketches/insights-list.clj`, `insights-new.clj`, `insights-feed-widget.clj`, `insights-show.clj`. Hiccup v2, daisyui (`card bg-base-200 shadow-sm`, `badge-secondary`, `badge-ghost`, `tabs-boxed`, `modal-bottom`), htmx-атрибуты в мапе, hyperscript для динамики.

### 6. UX flow создания — 3 entry point

- **A (основной):** из /feed — кнопка «записать инсайт» в виджете/онбординге. `state_label` и `entry_id` из последней записи сегодня, `context` предзаполнен описанием состояния.
- **B (опционально):** post-save из /check-in — мягкая ссылка «записать инсайт для этого состояния?» (НЕ поле формы — Decision 9 минимализм).
- **C:** standalone /insights/new — `state_label` из dropdown, без `entry_id`.

### 7. Редактирование — in-place htmx, `updated_at`-only

Кнопка «Править» возле каждого поля (context, advice, identity) → `hx-get` возвращает edit-form-фрагмент → свап read-блока. `hx-post` сохраняет → свап обратно. `category` — select в шапке, отдельный свап. `state_label` — НЕ редактируемый (привязан к контексту создания; delete+recreate для исправления).

Версионирование: `updated_at`-only. Spec-clarification: «история изменений сохраняется» трактуется как «артефакт переживает изменения состояния и время, context не перезаписывается вслепую», а не byte-level diff. Полная версия — Фаза 6.

### 8. Удаление — hard delete с подтверждением

Modal-bottom на мобиле (`modal-bottom sm:modal-middle`), текст поддерживающий. Hard delete (`DELETE FROM insights WHERE id=? AND user_id=?`). Insait не хранит отдельные артефакты-версии, восстановление невозможно — но запись-источник (`entries`) сохраняется (нет FK). Soft delete не нужен: нет аналитики по удалённым, нет «корзины».

**Альтернатива (отвергнута):** soft delete (`deleted_at`) — оверкилл, нет потребителей.

### 9. Навигация — 5 пунктов

`nav-items` (navigation.clj): `:feed, :check-in, :insights, :medications, :settings`. 5 — потолок мобильной bottom-nav. Иконка для `:insights` — из `app.icons` (light-bulb или document-text).

## Risks / Trade-offs

- **State_label match слишком груб** (6 бакетов скрывают нюансы — «плохой фокус» vs «тревога» в одном `neutral`). → Mitigation: Фаза 5 — euclidean distance по осям, backfill снапшота из `entry_id`. Для Фазы 3 — достаточно, ложная точность хуже грубой правды.
- **JSON в TEXT не типобезопасен** (нет schema на уровне БД; повреждённый JSON ломает чтение). → Mitigation: валидация на домене (malli), `clojure.data.json` строгий парсер, defensive read (пустой вектор при ошибке, как в `medications`). Приемлемо для Фазы 3.
- **`entry_id` dangling после удаления записи** (нет FK, запись удалена, `entry_id` висит). → Mitigation: display опускает ссылку если запись не найдена; backfill в Фазе 5 не сломается (просто нет снапшота). Acceptable per spec «No insight loss».
- **Онбординг может раздражать** при частой смене состояний без инсайтов. → Mitigation: per-state (не глобальный), factual тон, не modal. Если будет сигнал — Фаза 4 добавит «не показывать снова».
- **In-place edit теряет данные при сбое сети** (htmx POST failed → форма остаётся, read-блок не вернулся). → Mitigation: htmx `hx-target` swap on success; при ошибке — `alert-error` в форме, данные в textarea сохраняются. Hyperscript `on htmx:responseError` показывает сообщение.

## Migration Plan

1. Миграция 007: `CREATE TABLE insights` + индекс. Down: `DROP TABLE insights`. Без потери `entries` (нет FK).
2. Релиз: код с новыми роутами/доменом/вьюхами. Старые данные не затронуты.
3. Rollback: миграция down + revert кода. Insait-данные потеряны (user-generated), `entries` сохранены.

## Open Questions

Нет открытых. OQ3 resolved (Decision 13.5). Все решения приняты в explore-сессии (2026-08-23) и зафиксированы в `product-vision/design.md` Decision 13.

`[ref: A2-q2, A2-q4, A4-q5, A3-q3, OQ3]`

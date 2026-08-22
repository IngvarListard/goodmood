# Полный пример: прохождение Фазы 0 (MVP-мобильная форма)

Рабочий шаблон прохождения одной фазы по `product-vision`. Копируй промпты и команды, подставляй результаты шагов в следующие шаги. После прохождения Фазы 0 этот же скелет переиспользуется для Фаз 1–7 (с заменой контекста).

## Что строим в Фазе 0

MVP-форма создания записи состояния, mobile-first, с шаблонами (утро/день/вечер/событие), опциональными блоками, целевым временем заполнения ≤ 15 секунд. Без «розы ветров» как концепции (она в Фазе 2), без AI, без медикаментов. Блокирующий open question: **OQ1** — состав минимальной формы.

---

## Шаг 1 — Explore: закрыть OQ1 (15–25 мин)

### Команда запуска

```
/opsx-explore Реши OQ1: состав минимальной формы Фазы 0 из product-vision/design.md
```

### Промпт для explore (скопируй целиком)

```
Контекст: openspec/changes/product-vision/design.md, Open Question OQ1
и specs/mobile-entry/spec.md. Текущая форма — src/app/views/entries.clj
(не mobile-first, 4 обязательных поля, не заполняется даже основателем).

Задача: принять решение по OQ1 — состав минимальной формы Фазы 0.

Что нужно определить:
1. Обязательные поля ядра (то, что заполняется всегда, даже в плохом
   состоянии). Кандидаты: mood_score 0-10, энергия 0-10, тревога 0-10,
   фокус 0-10, сон (часы), свободная заметка.
   — Сколько полей в ядре (1? 3? 5?) и почему.
2. Опциональные блоки (skip-able, раскрываются по желанию).
3. Шаблоны: какие (минимум утро/день/вечер/событие), какие поля в каждом.
4. Целевое время заполнения ядра — секунды.
5. Что делать с mood_score 0-10: оставить, убрать, сделать опциональным.
6. Идём ли мы в Фазе 0 на ввод «осей будущего розы ветров» (энергия/тревога/
   фокус) как опциональных полей, чтобы в Фазе 2 дать им смысл — или
   откладываем до Фазы 2.

Ограничения:
- Mobile-first, PWA, тёмная тема как дефолт.
- Никакого стрика/геймификации давления.
- Минимум когнитивной нагрузки (пользователь в депрессии должен мочь
  заполнить форму).
- Опциональные блоки явно выделены как skip-able.
- Поддержка «мягкого режима» (минимальный ввод в плохом состоянии) —
  хотя бы на уровне UX, даже если state detection появится в Фазе 2.

Дай:
- Финальный список полей (ядро + опциональные) с обоснованием каждого.
- Состав шаблонов.
- Целевое время заполнения.
- Ответ на «оси сейчас или в Фазе 2».
- Обновлённую формулировку OQ1-решения для записи в design.md
  имплементационного change'а.
```

### Что должно выйти из explore

- Список обязательных полей ядра (например: `mood_score`, `energy`, `anxiety` — 3 слайдера).
- Список опциональных блоков (например: `focus`, `sleep_hours`, `note`, `activity`).
- Шаблоны и их состав (например: «утро» = ядро + сон; «день» = ядро; «вечер» = ядро + сон + заметка; «событие» = ядро + activity + note).
- Целевое время (например: 12 секунд на ядро).
- Решение по осям (рекомендация: ввести `energy/anxiety/focus` в Фазе 0 как опциональные, чтобы Фаза 2 дала им смысл «розы ветров» без миграции).

### Зафиксируй результат

Сохрани вывод explore в временный файл или заметку — он пойдёт в:
- `design.md` имплементационного change'а (как Decision)
- `product-vision/design.md` (убрать OQ1 из Open Questions, добавить решение)

---

## Шаг 2 — Дизайн UI: генерация hiccup2 (20–40 мин)

### Промпт для генерации (скопируй целиком, подставь результат Шага 1)

```
[КОНТЕКСТ]
Продукт: трекер настроения для людей со спектральной аффективной
нестабильностью. Ядро ценности — возврат пользователю его же инсайтов
в нужный момент. Фаза 0 — MVP-форма, без AI, без розы ветров как
концепции, без медикаментов.

[СТЕК]
Clojure, hiccup2 (html/raw), Tailwind + DaisyUI, htmx, hyperscript.
Mobile-first. PWA. Вывод — hiccup2 вектор, не HTML-строка.
Тёмная тема как дефолт (data-theme="dark" или daisyui dark theme).

[ЭКРАН]
Минимальная форма создания записи состояния, открывается по «+» из
ленты. Template selector сверху (утро/день/вечер/событие). После
сохранения — htmx-swap обратно в ленту, форма закрывается.

[КОНТЕНТ — РЕШЕНИЕ ПО OQ1]
Ядро (обязательные):
- mood_score 0-10 (range input, крупный)
- energy 0-10 (range input)
- anxiety 0-10 (range input)

Опциональные блоки (раскрытие по «показать ещё», skip-able):
- focus 0-10 (range input)
- sleep_hours (number, hours, step 0.1)
- note (textarea, короткая)
- activity (text input)

Шаблоны:
- утро: ядро + sleep_hours
- день: ядро
- вечер: ядро + sleep_hours + note
- событие: ядро + activity + note

[СОСТОЯНИЯ]
- пусто (дефолт)
- заполнено частично (ядро)
- сохраняется (htmx-loading)
- ошибка валидации (мягкий alert, не красный alarm)

[ТОН И UX-约束 — ОБЯЗАТЕЛЬНО]
- спокойная палитра, тёмная тема как дефолт (фотосенситивность, депрессия)
- крупные touch-targets (≥44px), читаемость при «голова не соображает»
- прощающие ошибки: мягкие алерты (alert-warning, не alert-error)
- поддерживающий copy, без вины
- минимум когнитивной нагрузки, максимум опциональности
- мягкий режим: ядро всегда доступно, опциональные блоки скрыты по
  умолчанию в шаблоне «день» (минимальный)

[ОГРАНИЧЕНИЯ]
- никаких стриков/геймификации
- никаких «вы пропустили день»
- опциональные блоки явно выделены как skip-able (collapse/expand)
- не использовать emoji

[ФОРМАТ]
Верни hiccup2-вектор одного экрана формы. Без ns, без (:require). Только
тело. Используй daisyui-классы: card, input, input-bordered, range,
btn, btn-primary, alert, collapse, tabs, tab, form-control, label.
HTMX: hx-post="/entries", hx-target="#entries-list", hx-swap="afterbegin",
hx-ext="json-enc", _ на reset после success.
Hyperscript на опциональных блоках: on click toggle collapse.
```

### Луп генерации

1. Сгенерируй вариант hiccup2 → вставь в `dev/preview.clj` (в `(def body …)` помечено PLACEHOLDER).
2. Запусти `clj -M -i dev/preview.clj` → открой `resources/proto/preview.html` в браузере.
   - Скрипт оборачивает твой hiccup в полный HTML с DaisyUI/Tailwind/htmx/hyperscript через CDN, тёмная тема по дефолту.
3. Если не нравится — корректируй промпт (не код). Типичные корректировки:
   - «touch-targets слишком мелкие»
   - «палитра слишком яркая»
   - «шаблон "утро" выглядит перегруженным»
4. Зафиксируй финальный hiccup2 — он пойдёт в `design.md` имплементационного change'а как код-референс.

### Что должно выйти

Один hiccup2-вектор формы, готовый к переносу в `src/app/views/entries.clj`. Не финальный код — референс для design.md change'а.

---

## Шаг 3 — Propose: создать change `add-mobile-entry-form` (15–25 мин)

### Команда запуска

```
/opsx-propose Фаза 0: MVP-мобильная форма
```

### Промпт для /opsx-propose (скопируй целиком, подставь результаты Шагов 1–2)

```
Создай имплементационный change add-mobile-entry-form для Фазы 0
из openspec/changes/product-vision.

Контекст:
- Прочитай openspec/changes/product-vision/{proposal,design,tasks}.md
  и openspec/changes/product-vision/specs/mobile-entry/spec.md.
- Прочитай текущую реализацию: src/app/views/entries.clj,
  src/app/routes/entries.clj, src/app/domains/entries.clj,
  src/app/db/entries.clj, openspec/specs/entries-data/spec.md,
  openspec/specs/entries-ui/spec.md (если есть).

Решение по OQ1 (из Шага 1):
<ВСТАВЬ СЮДА РЕЗУЛЬТАТ EXPLORE>

Дизайн UI (из Шага 2):
<ВСТАВЬ СЮДА HICCUP2-РЕФЕРЕНС ФОРМЫ>

Создай:
- proposal.md: почему Фаза 0, что меняется, scope in/out, non-goals
  (роза ветров, AI, медикаменты — out), impact (новые/изменённые файлы,
  миграции), acceptance criteria (GIVEN/WHEN/THEN из spec).
- design.md: решения (состав формы, шаблоны, опциональные блоки,
  mobile-first layout, мягкий режим, тёмная тема), риски
  (мульти-шкала → бросание — митигация опциональностью), ссылка на
  [ref: A3-q1, A2-q3] в discovery.
- specs/mobile-entry/spec.md: delta — ADDED Requirements (уточнённые
  из vision-гипотезы в финальные требования, с конкретными полями).
- specs/entries-data/spec.md: delta — MODIFIED Requirements (новые
  опциональные колонки energy/anxiety/focus, шаблон записи, nullable
  поля, миграция 004).
- specs/entries-ui/spec.md: delta — MODIFIED Requirements (mobile-first,
  template selector, optional blocks, soft mode).
- specs/entries-api/spec.md: delta — MODIFIED Requirements (POST /entries
  принимает partial body, возвращает созданную запись для htmx-swap).
- tasks.md: чек-лист реализации (миграция → db layer → domains → routes
  → views → i18n-ключи → тесты → ручной прогон на мобилке).

Принципы:
- Hiccup v2, daisyui-классы строкой или вектором, htmx-атрибуты в мапе.
- Все сценарии в spec — GIVEN/WHEN/THEN.
- Каждое требование с [ref: A*] на discovery.
- depersonalized: никаких личных данных, только продуктовые требования.
- не предлагать библиотеки без вопроса человеку (правило из config.yaml).
```

### Что проверить в сгенерированном change

```bash
openspec validate --change add-mobile-entry-form
openspec status --change add-mobile-entry-form
```

Критерии:
- `validate` → «is valid»
- `status` → 5/5 артефактов (proposal/design/specs/tasks + validation)
- Каждое требование в spec имеет `#### Scenario:` и `[ref: A*]`
- Delta-спеки используют `## ADDED Requirements` / `## MODIFIED Requirements`
- Non-goals явно исключают розу ветров, AI, медикаменты
- Acceptance criteria → конкретные GIVEN/WHEN/THEN, которые можно покрыть тестами

Если `validate` падает — правь и перегенерируй проблемные файлы, не двигайся дальше.

---

## Шаг 4 — Implement (2–6 часов, по tasks.md)

### Запуск

```
/opsx-apply-change add-mobile-entry-form
```

или вручную: открой `openspec/changes/add-mobile-entry-form/tasks.md` и реализуй пункт за пунктом.

### Луп реализации (для каждой задачи в tasks.md)

1. Прочитай задачу.
2. Прочитай релевантный сценарий из spec.md (GIVEN/WHEN/THEN) — это контракт.
3. Реализуй в коде:
   - миграция → `resources/migrations/004-add-entry-fields.{up,down}.sql`
   - db layer → `src/app/db/entries.clj` (новые колонки)
   - domains → `src/app/domains/entries.clj` (схема с опциональными полями)
   - routes → `src/app/routes/entries.clj` (partial body, htmx-swap)
   - views → `src/app/views/entries.clj` (форма из Шага 2, template selector)
   - i18n → `resources/i18n/{ru,en}.edn` (новые ключи)
4. Напиши тест, проверяющий сценарий из spec.
5. Прогон:
   ```bash
   clojure -M:test
   clj -M -m app.core  # smoke test
   curl http://localhost:3000/
   ```
6. Перейди к следующей задаче.

### Критические чекпоинты (обязательно)

- [ ] Миграция `004` накатывается и откатывается (`migratus rollback` потом `migratus migrate`).
- [ ] Форма открывается с мобилки (Chrome devtools mobile viewport 375px) — нет горизонтального скролла, touch-targets ≥ 44px.
- [ ] Заполнение только ядра → запись создаётся, опциональные поля `nil`.
- [ ] Смена шаблона → набор полей меняется.
- [ ] htmx-swap после сохранения — запись появляется в ленте без перезагрузки.
- [ ] Тёмная тема включается по дефолту.
- [ ] Нет стрика, нет «вы пропустили день».
- [ ] Все тесты `clojure -M:test` проходят.

---

## Шаг 5 — Verify + Archive (10 мин)

### Verify

```bash
openspec status --change add-mobile-entry-form
# → все задачи [x], артефакты complete
openspec validate --change add-mobile-entry-form
# → is valid
clojure -M:test
# → все тесты проходят
clj -M -m app.core
# → приложение запускается, /entries работает, форма mobile-first
```

### Archive

```bash
openspec archive add-mobile-entry-form
```

Что произойдёт:
- Delta-спеки из `changes/add-mobile-entry-form/specs/*/spec.md` синхронизируются в `openspec/specs/*/spec.md` (гипотезы становятся утверждёнными спецификациями).
- Change перемещается в `openspec/changes/archive/<date>-add-mobile-entry-form/`.

### Обнови product-vision

1. Открой `openspec/changes/product-vision/tasks.md`.
2. Найди «## 0. Фаза 0 — MVP-мобильная форма».
3. Отметь все задачи `[x]` и добавь в конец секции:
   ```
   - [x] 0.10 Фаза 0 завершена и архивирована. Change: add-mobile-entry-form.
   ```
4. Открой `openspec/changes/product-vision/design.md`.
5. В «Open Questions» удали OQ1 (или пометь «✅ разрешён в add-mobile-entry-form: <краткое решение>»).
6. Если в ходе реализации гипотеза в `specs/mobile-entry/spec.md` уточнилась — обнови её в `product-vision/specs/mobile-entry/spec.md` с сохранением `[ref: A*]`.
7. Если появился новый инсайт от использования — допиши в `openspec/context/discovery-notes.md` новые якоря `#A5…`.

### Проверка здоровья

```bash
openspec doctor
openspec list
# → product-vision отмечает Фазу 0 ✅ (через tasks), add-mobile-entry-form в archive
```

---

## Чек-лист копипасты (краткий)

```
[ ] 1. /opsx-explore <промпт из Шага 1> → решение OQ1
[ ] 2. <промпт из Шага 2> в Claude/GPT → hiccup2-референс формы
[ ] 3. /opsx-propose <промпт из Шага 3> → change add-mobile-entry-form
[ ] 3.1 openspec validate --change add-mobile-entry-form → is valid
[ ] 4. /opsx-apply-change add-mobile-entry-form → реализация по tasks.md
[ ] 4.1 clojure -M:test → все тесты проходят
[ ] 4.2 clj -M -m app.core → smoke test на мобилке
[ ] 5. openspec status --change add-mobile-entry-form → все [x]
[ ] 5.1 openspec archive add-mobile-entry-form → delta sync в openspec/specs/
[ ] 5.2 Обновить product-vision/tasks.md (Фаза 0 ✅) и design.md (OQ1 закрыт)
[ ] 5.3 openspec doctor → всё здорово
```

---

## Переиспользование для следующих фаз

Для Фазы 1 (медикаменты) и далее — тот же скелет, замени:
- в Шаге 1: OQ1 → соответствующий OQ (для Фазы 1 — нет блокирующего OQ, но нужно решить модель данных `medications`/`medication_logs`)
- в Шаге 2: экран формы → экран медикаментов
- в Шаге 3: `add-mobile-entry-form` → `add-medications`, delta-спеки `medications` (new) + `entries-data` (modified)
- в Шаге 4: миграция 005, db/domains/routes/views для meds
- в Шаге 5: archive `add-medications`, отметить Фазу 1 ✅

Шаблон масштабируется на все 8 фаз.

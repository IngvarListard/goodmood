# Полный пример: прохождение Фазы 1 (Медикаменты + сон как базовые факторы)

Рабочий шаблон прохождения Фазы 1 по `product-vision`. Структура повторяет `OPSX_PHASE0_EXAMPLE.md` с заменой контекста. После прохождения — скелет переиспользуется для Фаз 2–7.

## Что строим в Фазе 1

Медикаменты как first-class фактор: реестр медикаментов (название, доза, расписание), ежедневный лог приёма (принят/пропущен/задержан), история изменений дозы, privacy-флаг. Данные закладываются сейчас, **анализ и корреляции — в Фазе 5** (AI). Сон уже в БД (`sleep_hours` с Фазы 0) — как фактор используется автоматически.

**Блокирующих open questions нет**, но нужно решить модель данных (`medications` / `medication_logs` / dose-change tracking) — это делает Шаг 1.

### Что явно OUT of scope для Фазы 1

- AI-корреляции медикаментов и состояния (Фаза 5)
- Medication-aware insight matching (Фаза 5)
- Push-напоминания о приёме (Фаза 4, coping-channels)
- Экспорт/шаринг медикаментов (Non-goal всего product-vision)
- Авто-детекция пропусков с уведомлением (Фаза 4)

### Текущее состояние кода (старт Фазы 1)

- `entries` таблица: `id, user_id, date, activity, effect, mood_score, energy, anxiety, focus, sleep_hours, note, template, created_at` (миграция 004 уже применена)
- `src/app/db/entries.clj` — `create-entry!`, `get-entries` (с новыми полями)
- `src/app/domains/entries.clj` — `create-entry-schema`, `create-entry`, `list-entries`
- `src/app/routes/entries.clj` — хендлеры `/entries`
- `src/app/views/entries.clj` — форма, список, страница
- Нет: таблиц медикаментов, UI медикаментов, связи записей с медикаментами

---

## Шаг 1 — Explore: решить модель данных медикаментов (15–25 мин)

### Команда запуска

```
/opsx-explore Модель данных медикаментов для Фазы 1 из product-vision
```

### Промпт для explore (скопируй целиком)

```
Контекст: openspec/changes/product-vision/specs/medications/spec.md и
openspec/changes/product-vision/design.md (Decision 4 — медикаменты
first-class фактор, вес выше).

Задача: принять решение по модели данных медикаментов для Фазы 1.
Блокирующих open questions нет, но нужно зафиксировать схему.

Что нужно определить:
1. Таблица `medications` (реестр):
   — поля: id, user_id, name, dose, dose_unit, schedule, active,
     sensitive, notes, created_at, updated_at?
   — Как хранить расписание: строка "08:00, 20:00"? JSON-массив?
     Отдельная таблица medication_schedules? Обосновать.
   — Доза: числовое значение + единица (мг/мл/таб) — один столбец
     или два?

2. Таблица `medication_logs` (ежедневный лог приёма):
   — поля: id, user_id, medication_id, scheduled_time, taken_at,
     status (taken/skipped/delayed), actual_dose, notes?
   — Связь с entries: явный FK (entry_id) или неявная по timestamp
     (тот же день)? Обосновать.
   — Статус: enum строкой или integer-кодом?

3. Dose change tracking (история изменений дозы):
   — Вариант A: отдельная таблица `medication_dose_changes`
     (id, medication_id, old_dose, new_dose, changed_at, reason)
   — Вариант B: versioning на `medications` (valid_from, valid_to,
     dose) — SCD type 2
   — Вариант C: при изменении дозы создаём запись в medication_logs
     со status="dose_changed"
   — Какой выбрать и почему? Учитывать, что в Фазе 5 AI будет искать
     корреляции «доза изменилась → состояние сдвинулось».

4. UI placement:
   — Отдельная страница /medications (реестр + лог)?
   — Виджет в сайдбаре/ленте (быстрая отметка приёма)?
   — Часть формы создания записи (приём вместе с записью состояния)?
   — Комбинация? Что в Фазе 1, что отложить?

5. Daily logging UX:
   — Checkbox per scheduled slot (08:00 ☑, 20:00 ☐)?
   — Big "took it" button?
   — Что делать с пропущенными — пользователь отмечает вручную
     или система определяет по концу дня?

6. Sensitive flag:
   — Boolean `sensitive` на medications?
   — Влияет ли на что-то в Фазе 1 (когда экспорта нет)?

7. Reminders:
   — В скоупе Фазы 1 или отложить в Фазу 4 (coping-channels)?

Ограничения:
- SQLite (без enum-типов, используй TEXT с CHECK или строку).
- next.jdbc + HoneySQL (как в существующем db/ слое).
- migratus для миграций (нумерация 005).
- Mobile-first, тёмная тема.
- Sensitive-данные не логировать.

Дай:
- Финальную схему таблиц (SQL DDL для миграции 005).
- Обоснование каждого решения.
- Список того, что откладывается в Фазы 4–5.
- Обновлённую формулировку для записи в design.md change'а.
```

### Что должно выйти из explore

- SQL DDL для миграции 005 (3 таблицы: `medications`, `medication_logs`, + dose-change tracking — таблица или механизм)
- Решение по расписанию (рекомендация: строка "HH:MM, HH:MM" — проста, парсится, хватает для Фазы 1; JSON — оверкилл)
- Решение по связи logs ↔ entries (рекомендация: неявная по дате, без FK — логи и записи независимы, в Фазе 5 AI сопоставляет по timestamp)
- Решение по dose-change tracking (рекомендация: вариант A — отдельная таблица `medication_dose_changes`, проста для запросов корреляций в Фазе 5)
- UI placement (рекомендация: отдельная страница /medications для реестра + виджет быстрой отметки в форме записи или на ленте — решить в Шаге 2)
- Reminders — отложить в Фазу 4

### Зафиксируй результат

Сохрани вывод explore — он пойдёт в `design.md` имплементационного change'а (как Decisions).

---

## Шаг 2 — Дизайн UI: генерация hiccup2 (20–40 мин)

### Промпт для генерации (скопируй целиком, подставь результат Шага 1)

Для Фазы 1 нужно **два экрана**: реестр медикаментов и быстрый лог приёма. Генерируй по очереди.

#### Экран 1: Реестр медикаментов (/medications)

```
[КОНТЕКСТ]
Продукт: трекер настроения. Медикаменты — first-class фактор.
Фаза 1: реестр медикаментов + ежедневный лог приёма. Без AI,
без корреляций, без напоминаний.

[СТЕК]
Clojure, hiccup2 (html/raw), Tailwind + DaisyUI, htmx, hyperscript.
Mobile-first. PWA. Вывод — hiccup2 вектор, не HTML-строка.
Тёмная тема как дефолт (data-theme="dark").

[ЭКРАН]
Страница /medications — реестр медикаментов пользователя.
Список активных медикаментов карточками. Кнопка «добавить».
Редактирование inline или в модалке. Отметка sensitive.

[КОНТЕНТ]
Карточка медикамента:
- название (крупно)
- доза + единица (например "600 мг")
- расписание (например "08:00, 20:00")
- бейдж "sensitive" если отмечен
- кнопки: редактировать, отметить приём, деактивировать

Форма добавления/редактирования (модалка или отдельная карточка):
- name (text, обязательно)
- dose (number) + dose_unit (select: мг/мл/таб/кап)
- schedule (text, placeholder "08:00, 20:00")
- sensitive (checkbox/toggle)
- notes (textarea, опционально)

[СОСТОЯНИЯ]
- пусто (нет медикаментов — онбординг-сообщение)
- есть активные
- есть неактивные (archived, свёрнуты)
- форма открыта (добавление/редактирование)
- сохраняется (htmx-loading)

[ТОН И UX-约束 — ОБЯЗАТЕЛЬНО]
- спокойная палитра, тёмная тема как дефолт
- крупные touch-targets (≥44px)
- прощающие ошибки: мягкие алерты
- поддерживающий copy
- минимум когнитивной нагрузки
- sensitive-бейдж ненавязчивый, но заметный

[ОГРАНИЧЕНИЯ]
- никаких стриков/геймификации
- не использовать emoji
- sensitive-флаг не прятать глубоко в настройках

[ФОРМАТ]
Верни hiccup2-вектор страницы реестра. Без ns, без (:require).
DaisyUI: card, btn, modal, badge, toggle, input, select, textarea,
form-control, label, alert.
HTMX: hx-get/hx-post на /medications (CRUD), hx-target на список.
```

#### Экран 2: Быстрый лог приёма (виджет)

```
[КОНТЕКСТ]
Тот же продукт и стек. Это виджет быстрой отметки приёма медикаментов.
Должен быть доступен с минимальными действиями — пользователь отмечает
приём в момент, когда выпил таблетку.

[ЭКРАН]
Виджет «отметить приём» — список сегодняшних слотов приёма с кнопками.
Каждый слот: медикамент + запланированное время + кнопка «принял» /
«пропустил». Можно встроить в форму записи состояния или показать
на /medications на сегодня.

[КОНТЕНТ]
На сегодня:
- Препарат А 600 мг — 08:00 — [принял] [пропустил]
- Препарат А 600 мг — 20:00 — [принял] [пропустил]

Состояния кнопок:
- не отмечено (дефолт)
- принят (зелёная галочка, спокойный зелёный, не яркий)
- пропущен (мягкий жёлтый/оранжевый, не красный alarm)
- задержан (опционально — если отметил позже запланированного)

[ТОН И UX-约束 — ОБЯЗАТЕЛЬНО]
- спокойная палитра
- крупные touch-targets
- «пропустил» — без вины, нейтральный тон
- минимум когнитивной нагрузки

[ФОРМАТ]
Верни hiccup2-вектор виджета лога приёма.
DaisyUI: card, btn, btn-success (приглушённый), badge.
HTMX: hx-post на /medications/<id>/log, hx-target на карточку слота.
```

### Луп генерации

1. Сгенерируй вариант hiccup2 → вставь в `dev/preview.clj` (в `(def body …)` помечено PLACEHOLDER).
2. Запусти `clj -M -i dev/preview.clj` → открой `resources/proto/preview.html` в браузере.
3. Если не нравится — корректируй промпт (не код). Типичные корректировки для Фазы 1:
   - «карточка медикамента слишком плотная»
   - «кнопки "принял/пропустил" слишком мелкие»
   - «sensitive-бейдж кричащий»
   - «форма добавления перегружена»
4. Зафиксируй финальные hiccup2 для обоих экранов — пойдут в `design.md` change'а.

---

## Шаг 3 — Propose: создать change `add-medications` (15–25 мин)

### Команда запуска

```
/opsx-propose Фаза 1: медикаменты как first-class фактор
```

### Промпт для /opsx-propose (скопируй целиком, подставь результаты Шагов 1–2)

```
Создай имплементационный change add-medications для Фазы 1
из openspec/changes/product-vision.

Контекст:
- Прочитай openspec/changes/product-vision/{proposal,design,tasks}.md
  и openspec/changes/product-vision/specs/medications/spec.md.
- Прочитай текущую реализацию: src/app/db/entries.clj,
  src/app/domains/entries.clj, src/app/routes/entries.clj,
  src/app/routes/app.clj (роуты), src/app/views/layout.clj,
  src/app/views/navigation.clj (навигация), openspec/specs/entries-data/spec.md.

Решение по модели данных (из Шага 1):
<ВСТАВЬ СЮДА РЕЗУЛЬТАТ EXPLORE — схему таблиц, обоснования>

Дизайн UI (из Шага 2):
<ВСТАВЬ СЮДА HICCUP2-РЕФЕРЕНСЫ обоих экранов>

Создай:
- proposal.md: почему Фаза 1, что меняется (3 новые таблицы, новый домен,
  новые роуты, новый view, навигация), scope in/out, non-goals (AI-корреляции,
  напоминания, экспорт — out), impact (миграция 005, новые файлы
  app/db/medications.clj, app/domains/medications.clj,
  app/routes/medications.clj, app/views/medications.clj, обновление
  navigation.clj), acceptance criteria (GIVEN/WHEN/THEN из spec).
- design.md: решения (схема таблиц, расписание как строка, dose-change
  tracking, UI placement, logging UX, sensitive flag, связь logs↔entries
  неявная по дате), риски (расширение schedule позже → миграция;
  sensitive-флаг пока no-op → future-proofing), ссылка на
  [ref: A3-q5, A1-q2] в discovery.
- specs/medications/spec.md: delta — ADDED Requirements (уточнённые из
  vision-гипотезы, с конкретными полями и сценариями).
  ВАЖНО: требования про "correlation analysis" и "insight matching"
  из vision-спеки — отметить как deferred to Phase 5 (data captured now,
  analysis later). В Фазе 1 — только capture + display.
- specs/entries-data/spec.md: delta — MODIFIED Requirements если нужно
  (например, связь entries с medication context по дате — но скорее
  без изменения схемы entries).
- tasks.md: чек-лист (миграция 005 → db/medications.clj →
  domains/medications.clj → routes/medications.clj →
  views/medications.clj → navigation update → i18n keys → тесты →
  ручной прогон).

Принципы:
- Hiccup v2, daisyui-классы, htmx-атрибуты в мапе.
- Все сценарии — GIVEN/WHEN/THEN.
- Каждое требование с [ref: A*] на discovery.
- Depersonalized: никаких личных данных.
- Не предлагать библиотеки без вопроса человеку.
- Кодстайл: см. CODESTYLE.md, CLOJURE-RULES.md.
```

### Что проверить в сгенерированном change

```bash
openspec validate --change add-medications
openspec status --change add-medications
```

Критерии:
- `validate` → «is valid»
- Delta-спеки используют `## ADDED Requirements` / `## MODIFIED Requirements`
- Требования про correlation analysis / insight matching явно deferred to Phase 5
- Non-goals: напоминания (Phase 4), экспорт (Non-goal), AI (Phase 5)
- Acceptance criteria → конкретные GIVEN/WHEN/THEN
- Миграция 005 упомянута в impact

---

## Шаг 4 — Implement (3–8 часов, по tasks.md)

### Запуск

```
/opsx-apply-change add-medications
```

или вручную: открой `openspec/changes/add-medications/tasks.md` и реализуй пункт за пунктом.

### Луп реализации (типичный порядок)

1. **Миграция 005:**
   - `resources/migrations/005-add-medications.up.sql` — `medications`, `medication_logs`, `medication_dose_changes`
   - `resources/migrations/005-add-medications.down.sql` — `DROP TABLE` в обратном порядке
   - Прогон: накатить, проверить таблицы, откатить, накатить снова

2. **db layer** — `src/app/db/medications.clj`:
   - `create-medication!`, `get-medication`, `list-medications`, `update-medication!`, `deactivate-medication!`
   - `log-intake!`, `get-todays-logs`, `get-logs-for-date`
   - `record-dose-change!`, `get-dose-history`
   - HoneySQL, `rs/as-unqualified-kebab-maps` (как в entries.clj)

3. **domains** — `src/app/domains/medications.clj`:
   - malli-схемы: `medication-schema`, `intake-log-schema`, `dose-change-schema`
   - оркестрация: `create-medication`, `list-medications`, `log-intake`, `record-dose-change`, `get-todays-intake`
   - бизнес-логика: при `update-medication!` если доза изменилась → автоматически `record-dose-change!`

4. **routes** — `src/app/routes/medications.clj`:
   - `GET /medications` — страница реестра
   - `POST /medications` — создать (htmx → карточка в список)
   - `PUT /medications/:id` — обновить
   - `POST /medications/:id/deactivate` — деактивировать
   - `POST /medications/:id/log` — отметить приём/пропуск (htmx → обновить слот)
   - `GET /medications/today` — виджет сегодняшних слотов (для встраивания)

5. **views** — `src/app/views/medications.clj`:
   - `medications-page` — полная страница реестра
   - `medication-card` — карточка в списке
   - `medication-form` — форма добавления/редактирования (из Шага 2)
   - `intake-widget` — виджет быстрой отметки (из Шага 2)
   - `intake-slot` — один слот приёма

6. **navigation** — `src/app/views/navigation.clj`:
   - добавить пункт «Медикаменты» (i18n-ключ `:nav/medications`)
   - иконку найти в `resources/icons/` через grep (например, pill/capsule)

7. **routes/app.clj** — `src/app/routes/app.clj`:
   - подключить `app.routes.medications`
   - добавить роуты в таблицу

8. **i18n** — `resources/i18n/{ru,en}.edn`:
   - ключи: `:medications/title`, `:medications/add`, `:medications/name`,
     `:medications/dose`, `:medications/dose-unit`, `:medications/schedule`,
     `:medications/sensitive`, `:medications/notes`, `:medications/taken`,
     `:medications/skipped`, `:medications/delayed`, `:medications/empty`,
     `:nav/medications`

9. **Тесты:**
   - `test/app/db/medications_test.clj` — CRUD, dose-change tracking
   - `test/app/routes/medications_test.clj` — HTTP-контракты
   - Сценарии из `specs/medications/spec.md` → GIVEN/WHEN/THEN

10. **Прогон:**
    ```bash
    clojure -M:test
    clj -M -m app.core
    ```

### Критические чекпоинты (обязательно)

- [ ] Миграция 005 накатывается и откатывается.
- [ ] `/medications` открывается — реестр пустой → онбординг-сообщение.
- [ ] Добавление медикамента через htmx — карточка появляется без перезагрузки.
- [ ] Отметка приёма через htmx — слот обновляется (принял/пропустил).
- [ ] Изменение дозы → автоматически создаётся запись в `medication_dose_changes`.
- [ ] Sensitive-флаг сохраняется и отображается бейджем.
- [ ] Деактивация медикамента — исчезает из активных, остаётся в archived.
- [ ] Навигация — пункт «Медикаменты» виден, кликабелен, иконка на месте.
- [ ] Mobile viewport 375px — нет горизонтального скролла, touch-targets ≥ 44px.
- [ ] Тёмная тема — форма/реестр корректно в тёмной теме.
- [ ] Нет стрика, нет уведомлений «вы пропустили приём».
- [ ] Все тесты `clojure -M:test` проходят.

---

## Шаг 5 — Verify + Archive (10 мин)

### Verify

```bash
openspec status --change add-medications
# → все задачи [x], артефакты complete
openspec validate --change add-medications
# → is valid
clojure -M:test
# → все тесты проходят
clj -M -m app.core
# → приложение запускается, /medications работает
```

Ручной smoke test:
```bash
curl http://localhost:3000/medications
curl -X POST http://localhost:3000/medications \
  -H "Content-Type: application/json" \
  -d '{"name":"Test Med","dose":100,"dose_unit":"мг","schedule":"08:00"}'
```

### Archive

```bash
openspec archive add-medications
```

Delta-спеки синхронизируются в `openspec/specs/medications/spec.md`. Change перемещается в `archive/`.

### Обнови product-vision

1. `openspec/changes/product-vision/tasks.md` — найди «## 1. Фаза 1»:
   - Отметь все задачи `[x]`
   - Добавь:
     ```
     - [x] 1.7 Фаза 1 завершена и архивирована. Change: add-medications.
     ```

2. `openspec/changes/product-vision/design.md` — если в ходе реализации что-то уточнилось:
   - Обнови Decision 4 (медикаменты) если модель данных отличается от задуманного
   - Если появились новые инсайты → допиши в `discovery-notes.md` (якоря `#A5…`)

3. `openspec/changes/product-vision/specs/medications/spec.md` — если гипотеза уточнилась:
   - Обнови с сохранением `[ref: A*]`
   - Если требования про correlation analysis / insight matching стали конкретнее — обнови формулировки (но реализация всё равно в Фазе 5)

### Проверка здоровья

```bash
openspec doctor
openspec list
# → product-vision: Фаза 0 ✅, Фаза 1 ✅; add-medications в archive
```

---

## Чек-лист копипасты (краткий)

```
[ ] 1. /opsx-explore <промпт из Шага 1> → модель данных медикаментов
[ ] 2. <промпты из Шага 2> → hiccup2-референсы реестра + виджета лога
[ ] 2.1 dev/preview.clj → clj -M -i dev/preview.clj → браузер
[ ] 3. /opsx-propose <промпт из Шага 3> → change add-medications
[ ] 3.1 openspec validate --change add-medications → is valid
[ ] 4. /opsx-apply-change add-medications → реализация по tasks.md
[ ] 4.1 clojure -M:test → все тесты проходят
[ ] 4.2 clj -M -m app.core → smoke test /medications
[ ] 5. openspec status --change add-medications → все [x]
[ ] 5.1 openspec archive add-medications → delta sync
[ ] 5.2 Обновить product-vision/tasks.md (Фаза 1 ✅) и design.md
[ ] 5.3 openspec doctor → всё здорово
```

---

## Отличия от Фазы 0 (на что обратить внимание)

| Аспект | Фаза 0 | Фаза 1 |
|---|---|---|
| Блокирующий OQ | OQ1 (состав формы) | нет, но модель данных —.design decision в explore |
| Миграция | 004 (уже есть) | 005 (новая — 3 таблицы) |
| Количество экранов | 1 (форма) | 2 (реестр + виджет лога) |
| Новый домен | нет (entries расширяется) | да (`medications`) |
| Новые роуты | `/entries` (существующий) | `/medications` + sub-routes (новые) |
| Навигация | не меняется | добавляется пункт «Медикаменты» |
| Связь с будущими фазами | поля осей для розы ветров (Фаза 2) | данные для AI-корреляций (Фаза 5), напоминания (Фаза 4) |
| Deferred-требования | нет | correlation analysis, insight matching — Phase 5 |

---

## Переиспользование для Фазы 2

Для Фазы 2 («Роза ветров состояний» + несколько записей в день) — тот же скелет, замени:
- в Шаге 1: модель данных медикаментов → **OQ2 (оси розы ветров)** + модель нескольких записей/день
- в Шаге 2: реестр медикаментов → **страница «лента/мой день» с розой ветров** + ручное перераспределение осей
- в Шаге 3: `add-medications` → `add-mood-states-rose`, delta-спеки `mood-states` (new) + `entries-data` (modified) + `entry-granularity` (variant а)
- в Шаге 4: миграция 006, db/domains/routes/views для розы ветров
- в Шаге 5: archive `add-mood-states-rose`, отметить Фазу 2 ✅, закрыть OQ2

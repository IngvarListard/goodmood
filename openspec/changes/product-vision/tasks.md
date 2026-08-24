## 0. Фаза 0 — MVP-мобильная форма ✅

**Цель фазы:** форма, которую основатель реально заполняет каждый день на мобилке.
**Капабилити:** `mobile-entry` (spec-as-hypothesis).
**Блокирующие open questions:** OQ1 (состав минимальной формы).

- [x] 0.1 Принять решение по OQ1: состав минимальной формы, обязательные/опциональные поля, целевое время заполнения. Зафиксировано в design.md Decision 9 и specs/mobile-entry/spec.md (2026-08-18).
- [x] 0.2 Создать имплементационный change `add-mobile-entry-form` (proposal/design/specs/tasks). Delta-спеки уточняют `mobile-entry` и `entries-data`/`entries-ui` из umbrella-гипотез.
- [x] 0.3 `openspec validate --change add-mobile-entry-form` — структура валидна.
- [x] 0.4 Реализовать: mobile-first layout формы, опциональные блоки, шаблоны (минимум утро/день/вечер/событие).
- [x] 0.5 Покрыть сценарии из `specs/mobile-entry/spec.md` тестами (GIVEN/WHEN/THEN).
- [x] 0.6 `openspec status --change add-mobile-entry-form` — все задачи выполнены, тесты проходят.
- [x] 0.7 Запустить приложение, вручную заполнить форму с мобилки, убедиться, что UX минимален.
- [x] 0.8 `openspec archive --change add-mobile-entry-form` → delta-спеки синхронизированы в `openspec/specs/`.
- [x] 0.9 В `product-vision/tasks.md` отметить Фазу 0 ✅. В `product-vision/design.md` при необходимости обновить гипотезы по `[ref: A3-q1]`.

## 1. Фаза 1 — Медикаменты + сон как базовые факторы ✅

**Цель фазы:** медикаменты (расписание, доза, пропуски) интегрированы в данные и учитываются в анализе.
**Капабилити:** `medications` (spec-as-hypothesis).

- [x] 1.1 Создать change `add-medications` (proposal/design/specs/tasks). Delta-спеки: `medications` (new), `entries-data` (modified — связь с meds).
- [x] 1.2 Решить модель данных: таблица `medications` (название, доза, расписание), таблица `medication_logs` (приём/пропуск, timestamp, доза).
- [x] 1.3 Реализовать UI: ввод/редактирование медикаментов, отметка приёма, список пропусков.
- [x] 1.4 Тесты: сценарии из `specs/medications/spec.md`.
- [x] 1.5 `openspec validate`, реализация, `openspec status`, `openspec archive`.
- [x] 1.6 Отметить Фазу 1 ✅ в `product-vision/tasks.md`. Change архивирован: `2026-08-21-add-medications`.

## 2. Фаза 2 — «Роза ветров состояний» + несколько записей в день ✅

**Цель фазы:** состояние описывается многомерным распределением; несколько записей в день.
**Капабилити:** `mood-states`, `entry-granularity` (вариант а).
**Блокирующие open questions:** OQ2 (оси розы ветров).

- [x] 2.1 Принять решение по OQ2: оси розы ветров, их количество, диапазоны.
- [x] 2.2 Создать change `add-mood-states-rose` (proposal/design/specs/tasks). Delta-спеки: `mood-states` (new), `entries-data` (modified — поля осей), `entry-granularity` (вариант а — несколько записей/день).
- [x] 2.3 Реализовать: ввод осей в форме (опционально, в шаблонах), страница «лента/мой день» с автоматически определённой розой и возможностью ручного перераспределения, производная «роль/ярлык» состояния.
- [x] 2.4 Тесты: сценарии из `specs/mood-states/spec.md` и `specs/entry-granularity/spec.md` (вариант а).
- [x] 2.5 `openspec validate`, реализация, `openspec status`, `openspec archive`.
- [x] 2.6 Отметить Фазу 2 ✅ в `product-vision/tasks.md`.

## 3. Фаза 3 — Инсайт-артефакт + подбор из прошлого

**Цель фазы:** инсайты хранятся как структурированные артефакты и подбираются по текущему состоянию.
**Капабилити:** `insights` (spec-as-hypothesis).
**Блокирующие open questions:** OQ3 (fallback при отсутствии инсайта).

- [ ] 3.1 Принять решение по OQ3: стратегия fallback (молчать / AI / чужой).
- [ ] 3.2 Создать change `add-insights-artefact` (proposal/design/specs/tasks). Delta-спеки: `insights` (new), `entries-data` (modified — связь с insights).
- [ ] 3.3 Реализовать: форма создания инсайта (структурный шаблон: context / category / advice-to-self / identity), хранение, подбор по текущей розе ветров, отображение в «ленте».
- [ ] 3.4 Тесты: сценарии из `specs/insights/spec.md`.
- [ ] 3.5 `openspec validate`, реализация, `openspec status`, `openspec archive`.
- [ ] 3.6 Отметить Фазу 3 ✅ в `product-vision/tasks.md`.

## 4. Фаза 4 — Каналы доставки советов ✅

**Цель фазы:** советы доставляются через push-уведомления, кнопку «что делать сейчас», при заполнении; вечерние инсайты на «завтра».
**Капабилити:** `coping-channels` (spec-as-hypothesis).
**Блокирующие open questions:** OQ6 (формат вечерних инсайтов).

- [x] 4.1 Принять решение по OQ6: формат вечерних инсайтов на «завтра».
- [x] 4.2 Создать change `add-coping-channels` (proposal/design/specs/tasks). Delta-спеки: `coping-channels` (new), `entries-ui` (modified — кнопка «что делать сейчас»).
- [x] 4.3 Реализовать: push-уведомления (утро/день/вечер), кнопка «что мне делать сейчас», подсказки при заполнении, вечерняя сводка на завтра.
- [x] 4.4 Тесты: сценарии из `specs/coping-channels/spec.md`.
- [x] 4.5 `openspec validate`, реализация, `openspec status`, `openspec archive`.
- [x] 4.6 Отметить Фазу 4 ✅ в `product-vision/tasks.md`. Change архивирован: `2026-08-23-add-coping-channels`.

## 5. Фаза 5 — AI: корреляции, название состояния, советы из своих заметок ✅

**Цель фазы:** AI ищет корреляции, предлагает название/описание состояния, генерирует советы на основе своих же заметок.
**Капабилити:** `ai-assistant` (Фаза 5 часть).
**Блокирующие open questions:** OQ8 (AI-провайдер, guardrails).

- [x] 5.1 Принять решение по OQ8 (часть 1): провайдер, модель, guardrails для корреляций и советов из своих заметок.
- [x] 5.2 Создать change `add-ai-correlations` (proposal/design/specs/tasks). Delta-спеки: `ai-assistant` (new — Фаза 5 часть).
- [x] 5.3 Реализовать: фоновый анализ корреляций (сон ↔ настроение, медикаменты ↔ состояние, …), AI-предложение названия состояния, AI-генерация советов из своих инсайтов с объяснимостью и «пожаловаться на совет».
- [x] 5.4 Тесты: сценарии из `specs/ai-assistant/spec.md` (Фаза 5 часть).
- [x] 5.5 `openspec validate`, реализация, `openspec status`, `openspec archive`.
- [x] 5.6 Отметить Фазу 5 ✅ в `product-vision/tasks.md`. Change архивирован: `2026-08-24-add-ai-correlations`.

## 6. Фаза 6 — AI: новые советы, чат + «период состояния»

**Цель фазы:** AI генерирует новые советы (не из своих заметок), чатится в плохом состоянии; «период состояния» (вариант в гранулярности).
**Капабилити:** `ai-assistant` (Фаза 6 часть), `entry-granularity` (вариант в).
**Блокирующие open questions:** OQ7 (модель данных периода состояния), OQ8 (часть 2).

- [ ] 6.1 Принять решение по OQ7: модель данных для периода состояния.
- [ ] 6.2 Принять решение по OQ8 (часть 2): guardrails для новых советов и чата.
- [ ] 6.3 Создать change `add-ai-chat-and-periods` (proposal/design/specs/tasks). Delta-спеки: `ai-assistant` (modified — Фаза 6 часть), `entry-granularity` (modified — вариант в).
- [ ] 6.4 Реализовать: AI-чат в плохом состоянии, AI-генерация новых советов (с opt-in), UI для отметки начала/конца периода состояния, привязка записей к периоду.
- [ ] 6.5 Тесты: сценарии из `specs/ai-assistant/spec.md` (Фаза 6 часть) и `specs/entry-granularity/spec.md` (вариант в).
- [ ] 6.6 `openspec validate`, реализация, `openspec status`, `openspec archive`.
- [ ] 6.7 Отметить Фазу 6 ✅ в `product-vision/tasks.md`.

## 7. Фаза 7 — AI: предупреждение эпизода (осторожно)

**Цель фазы:** AI предсказывает возможный начало эпизода с guardrails.
**Капабилити:** `ai-assistant` (Фаза 7 часть).
**Блокирующие open questions:** OQ8 (часть 3 — guardrails для предсказания).

- [ ] 7.1 Принять решение по OQ8 (часть 3): пороги уверенности, opt-in, объяснимость, выключаемость, «пожаловаться на ложную тревогу».
- [ ] 7.2 Создать change `add-ai-episode-warning` (proposal/design/specs/tasks). Delta-спеки: `ai-assistant` (modified — Фаза 7 часть).
- [ ] 7.3 Реализовать: предсказание с guardrails, opt-in, объяснимый вывод, лёгкое выключение.
- [ ] 7.4 Тесты: сценарии из `specs/ai-assistant/spec.md` (Фаза 7 часть) — особенно «AI ошибся, эпизода нет → пользователь не должен получить тревожное уведомление».
- [ ] 7.5 `openspec validate`, реализация, `openspec status`, `openspec archive`.
- [ ] 7.6 Отметить Фазу 7 ✅ в `product-vision/tasks.md`.

## 8. Сквозные задачи

- [ ] 8.1 После каждой фазы: обновить `product-vision/specs/<cap>/spec.md` если гипотеза уточнилась или опровергнута (с сохранением `[ref: A*]`).
- [ ] 8.2 После каждой фазы: дописать raw-документ `discovery-notes.md` новыми якорями `#A5…` если появился новый инсайт от использования.
- [ ] 8.3 Периодически: `openspec doctor` для проверки здоровья root'а.
- [ ] 8.4 При появлении запроса на «отчёт врачу» или «нерепрессивный стрик» — создать отдельные change'ы (out of Phases 0–7 по умолчанию).

# Design: sleep-duration-input

## Context

Хранение — `sleep_hours REAL` (десятичные часы) — остаётся. Меняются только
края: ввод (h+мин) и отображение («7ч30м»). AI-промпты продолжают получать
десятичные часы — ноль правок в `domains/ai.clj`.

## Goals

- Ввод сна «как время»: часы + минуты (шаг 15).
- Понятный дисплей «7ч30м» во всех точках, честный для legacy-значений (7.2 → «7ч12м»).
- Обратная совместимость API и тестов (одиночный числовой sleep_hours).

## Decisions

### D1. Не мигрировать схему
INTEGER-минуты дали бы «чистое время», но требуют append-only миграцию с
переносом данных и правки db/AI/тестов без функционального выигрыша: 7.5
представимо в float точно, весь обсчёт уже в часах.

### D2. Ввод — daisyUI join: number(ч) + select(мин, 0/15/30/45)
Отвергнуты: `<input type="time">` (семантика времени суток, виджет зависит от
браузера), слайдер (точность драгом на мобиле, сон — опциональный блок в
коллапсе). Два поля — явная семантика, ноль JS, тапабельный select.

### D3. Контракт: `sleep_hours` + опциональный `sleep_minutes`
Конверсия на границе домена (`app.domains.entries`, create и update):
`(when (or (some? h) (pos? m)) (+ (or h 0) (/ (or m 0) 60)))`. Схема malli:
`sleep_hours [:maybe :double]` (как есть) + `sleep_minutes
[:maybe [:int {:min 0 :max 59}]]`. Blank→nil коерция уже существует.
sleep_minutes без sleep_hours → 0.5. Часы пусты + минуты 0 → nil: edit-форма
всегда шлёт select минут (дефолт 0) — без этого правила любое сохранение
edit-формы «создавало» бы сон 0ч0м у записи без сна (найдено в реализации).

### D4. Форматтер дисплея — `format-sleep` в `app.views.layout`
Нужен в `views/feed` и `views/entries` (item-фрагмент + карточка) — оба уже
require layout. `h = trunc(hours)`, `m = round((hours − h) × 60)`; единицы
через i18n-ключи `:entries/h-unit` / `:entries/m-unit` («ч»/«м», «h»/«m»).
Без отдельного ns ради одной функции.

### D5. Prefill edit-формы — обратный split с округлением к 15
`m = round(rest × 60 / 15) × 15`. Legacy 7.2 → 12 мин → select 15; сохранение
без правки даст 7.25 — отмечено в спеке как приемлемая цена гранулярности.

### D6. Hyperscript-тоггл коллапса: селектор не пропустить select
Тоггл `optional-block` дизейблит `<input, textarea/>` в `.collapse`. Select
минут под это правило не попадает — селектор расширить до
`<input, select, textarea/>`, иначе enabled-select в свёрнутом блоке засабмитит 0.

### D7. i18n
`ru.edn`/`en.edn`: `:entries/h-unit`, `:entries/m-unit`; `:entries/sleep`
«Сон (часы)» → «Сон» (units теперь видны в значении, не в заголовке).

### D8. e2e
`e2e/helpers.ts submitEntry`: заполнение `input[name="sleep_hours"]` +
выбор `select[name="sleep_minutes"]` (снять disabled с обоих). AI-спеки,
шлющие sleep-hours через API/домен, не затронуты.

## Risks / Trade-offs

- Rounding legacy при edit-save (7.2 → 7.25) — задокументировано, спека покрывает.
- `sleep_minutes` в API открыт любому int 0–59 (шаг 15 — только UI-конвенция):
  принят intentionally, валидация диапазоном, не шагом.

## Migration Plan

Не требуется (схема не меняется). Деплой — обычный deploy.sh.

## Open Questions

Нет — все решения приняты в explore-сессии (ввод: ч+мин select, шаг 15,
дисплей «7ч30м» везде).

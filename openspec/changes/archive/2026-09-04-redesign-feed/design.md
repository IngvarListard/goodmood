# Design: redesign-feed

## Context

Референс `design/lenta.html` — единственный источник визуальных решений этой страницы. Текущая разметка: `views/feed.clj` (секции дней → `compact-card`, `hero-card` с розой, `fab`, период через `state_periods.clj`, AI-секции через `views/ai.clj`), htmx-контракты: soft-order виджетов, `#ai-chat-open`, тосты уведомлений. Токены и `.gm-*` классы предоставлены `redesign-tokens`.

## Goals / Non-Goals

- Goals: timeline-лента, чипы метрик, state-бейджи, empty state с иллюстрацией, градиентная пилюля периода, AI-инсайт-карточка с графиком.
- Non-Goals: новые эндпоинты/данные, десктоп-спецвёрстка, замена розы.

## Decisions

### Decision 1. Timeline — отдельная функция `timeline-day` в feed.clj

Вертикальная линия — `absolute` div `w-0.5 bg-base-300` на контейнере секции; точка дня — absolute `w-3.5 h-3.5 rounded-full` с цветом по state (`.gm-badge-danger`→red, `.gm-badge-ok`→green, иначе `bg-base-content/40`) и рамкой цвета фона (`border-base-100`), позиция — по верхнему краю первой карточки дня. Карточки — `ml-8`.

- Альтернатива (переиспользовать `compact-card` с левым бордером) — не даёт точек и линии из макета.

### Decision 2. State → цвет: маппинг на стороне вьюхи

`state_label` `low`/`anxiety` → `gm-badge-danger`; `ok`/`calm` → `gm-badge-ok`; остальное/nil → нейтральный `bg-base-300 text-base-content`. Списки — константа `def` в feed.clj (не в i18n: это визуальная семантика). Дублирование логики с мягким режимом недопустимо — только читаем `state_label`, не меняем.

### Decision 3. Метрики — чипы `.gm-chip` grid-cols-3

Иконки heroicons: энергия `bolt` (`--gm-metric-energy`), тревога `water`-заменитель `arrow-down-tray`→ нет, использовать `fire` (`--gm-metric-anxiety`), фокус `eye` (`--gm-metric-focus`), настроение `face-smile`. Чип: подпись 11px muted + значение 15px. Отсутствующее значение — `–`.

- Замечание: в `app.icons` могут отсутствовать нужные имена — расширить `icons.clj` (heroicons) при необходимости, не инлайнить SVG в feed.clj.

### Decision 4. График AI-инсайта — inline SVG polyline на существующих данных

`ai-correlations` рендерит пары метрик; в макете — недельный ряд из 7 точек. Строим из уже доступных в feed-запросе записей последних 7 дней (передаются в `ai` opts? нет — берём `entries`, уже есть в `page`): настроение/тревога по дням, точки красятся по порогам (≤3 green, 4–6 amber, ≥7 red), линия `stroke-error` при тревоге / `stroke-primary` при настроении. Нет данных за день → точка контурная (как «Вс» в макете). Меньше 2 точек — fallback-текст, SVG не рендерим.

- Альтернатива (новый htmx-запрос за историей) — вне скоупа, данные уже есть.

### Decision 5. Empty state — полный перенос блока «Как ты сегодня?»

SVG-иллюстрация из макета (контурное лицо + звёздочки) — `raw` не нужен, обычный hiccup-SVG, stroke `#5b5bf0` → `stroke-primary` (переменная). Кнопка «Создать запись» — `btn-primary w-full` + белый круглый плюс-бейдж внутри. Порядок секций страницы (period → empty → AI) сохраняется.

### Decision 6. Пилюля периода — `.gm-gradient`, структура без изменений

В `state_periods.clj` `active-indicator`: пилюля `gm-gradient` с иконкой `bolt`, кнопка «Закрыть период» — `btn-outline btn-sm`. hx-атрибуты (POST закрытия, csrf) сохранить как есть.

### Decision 7. Ширина контента

Обёртка страницы `max-w-2xl` → `max-w-md mx-auto` (48px wider than макет 430 — приемлемо, решение по вопросу №3 сессии explore).

## Risks / Trade-offs

- [Переработка разметки ломает htmx-селекторы (`hx-target`, OOB-свапы, soft-order)] → после каждой задачи проверять в Playwright MCP реальные свапы, не только картинку; e2e smoke запускать в конце каждой задачи.
- [Иконок нужных нет в app.icons] → добавлять в `icons.clj`, держать имена семантическими.
- [График из live-данных может выглядеть перегружено] → ≤7 точек, ограничение снизу fallback'ом.

## Migration Plan

Визуальный слой, деплой = рестарт. Откат revert'ом; i18n-ключи аддитивны.

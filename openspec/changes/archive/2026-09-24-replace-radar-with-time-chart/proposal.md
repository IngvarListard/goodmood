# Proposal: replace-radar-with-time-chart

## Why

Роза ветров (радар) на `/feed` кодирует состояние многоугольником: форма читается только «сверху вниз», не показывает динамику во времени и плохо масштабируется на периоды. Человеку с биполярным расстройством важнее видеть тренд — как менялись настроение, энергия, тревога, фокус и агрессия день ко дню. Владелец решил полностью убрать радар и заменить его линейным графиком по времени: по X — дни, по Y — 0–10, отдельная линия на каждую отслеживаемую ось плюс одна составная линия «общее настроение».

## What Changes

- **Удаление радара целиком**: удаляются `src/app/views/rose.clj` и `resources/public/js/radar.js`; из `app.views.feed` убирается require `rose` и вызов `period-radar`; из `app.routes.feed` — handler `radar`; из `app.routes.app` — маршрут `/feed/radar`. Мёртвые radar-only хелперы домена удаляются.
- **Новый линейный график** в hero-карточке `/feed`: Chart.js (уже подключён, pinned 4.x) тип `line`; новый рендерер `resources/public/js/feed-chart.js`.
- **Контракт**: сервер рендерит `<canvas data-gm-chart="{...}">` с JSON `{labels, datasets:[{key,label,values,colorVar}]}`, i18n `aria-label`; составной датасет — последний. Цвета — только из CSS-переменных.
- **Переключатель периода 3 дня / неделя / месяц**, по умолчанию **неделя**; фрагмент и маршрут `GET /feed/chart?period=3d|week|month` с OOB-свапом контейнера (по образцу прежнего радара).
- **Доменная агрегация**: per-day mean по каждой оси + составная, на ограниченном окном запросе к БД (не грузить все записи).
- **Фикс цвета мини-графика недели**: классы `fill-success` / `fill-warning` / `fill-error` / `stroke-primary` рендерятся чёрными (Tailwind browser CDN не генерирует daisyUI `fill-*`); заменяются на inline `var(--color-*)`. Сам мини-график иначе не меняется.
- **`layout` head**: `/js/radar.js` → `/js/feed-chart.js`; Chart.js CDN остаётся.
- **Пустые/недостаточные данные**: разрыв линии в день без значения; при < 2 днях данных — приглушённый fallback-текст.

## Capabilities

### Modified Capabilities

- `entries`: удаляются radar-требования, добавляются требования агрегации по дням и контракта canvas.
- `feed-timeline`: hero-график заменяет радар; фикс цвета мини-графика недели.
- `ui-shell`: head-скрипт и ссылка на статик (`radar.js` → `feed-chart.js`).

## Impact

- Удаляются: `src/app/views/rose.clj`, `resources/public/js/radar.js`.
- Правятся: `src/app/views/feed.clj`, `src/app/routes/feed.clj`, `src/app/routes/app.clj`, `src/app/domains/entries.clj`, `src/app/db/entries.clj`, `src/app/views/layout.clj`, `src/app/domains/ai.clj` (переименование `rose-line`), `resources/public/js/push.js` (комментарий), `resources/i18n/{ru,en}.edn`.
- Добавляется: `resources/public/js/feed-chart.js`.
- Тесты: `test/app/routes/app_test.clj`, `test/app/domains/entries_test.clj`, `e2e/tests/feed.spec.ts`, `e2e/tests/radar.spec.ts`.
- **Зависимость**: изменение опирается на `add-aggression-axis` — составной ряд и один датасет используют ось `aggression`. Реализацию начинать после того, как `add-aggression-axis` добавит колонку и ось; иначе составная формула и набор датасетов неполны.
- Без новых зависимостей; миграции — только те, что приходят из `add-aggression-axis`.

## Scope

Замена радара линейным графиком по времени в hero-карточке `/feed`: удаление radar-кода, доменная агрегация по дням с ограниченным окном запросом, новый canvas-контракт и клиентский рендерер, переключатель периодов 3 дня / неделя / месяц, фикс цвета мини-графика недели.

## Non-goals

- Сохранение радара где-либо (включая AI-контекст чата) — удаляется полностью.
- Новые зависимости и библиотеки графиков.
- Пагинация ленты (отдельное изменение `feed-pagination`).
- Редизайн мини-графика недели сверх фикса цвета.
- Сохранение выбранного периода между перезагрузками (дефолт всегда «неделя»).

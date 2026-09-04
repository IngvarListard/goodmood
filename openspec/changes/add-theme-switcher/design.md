# Design: add-theme-switcher

## Context

Тема захардкожена: `[:body {:data-theme "dark"}]` в `layout.clj:48` и так же в `auth.clj:28`. Локаль решена через cookie `gm-locale` + `wrap-locale` (cookie → middleware → dynamic var/`request`) — это готовый паттерн для темы. Tailwind и daisyUI подключены через CDN (`@tailwindcss/browser@4`, `daisyui.css`), сборки нет.

Браузерный эксперимент (Playwright + локальные копии CDN-файлов) подтвердил:

1. `@theme` в обычном `<style>` (как сейчас) — мёртвый код: Tailwind-CDN компилирует только `<style type="text/tailwindcss">`, браузер выбрасывает неизвестный at-rule. Тёплая палитра `#e8e5df` никогда не применялась.
2. Даже при обработке `@theme` переменные живут на `:root`, а `body[data-theme=dark]` определяет их прямо на себе и перекрывает наследование — тёплый цвет не доезжает до элементов.
3. В CDN-сборке `daisyui.css` нет ни одного `prefers-color-scheme`: темы определены как `:root` (light default), `[data-theme=light]`, `[data-theme=dark]` и `:root:has(input.theme-controller[value=X]:checked)`. Системный режим чистым CSS не выражается.
4. Встроенный механизм daisyUI: радио с классом `theme-controller` и атрибутом `value=light|dark` мгновенно перекрашивает страницу через `:has()` (специфичность `:root:has(...)` выше, чем `[data-theme=X]`).

## Goals / Non-Goals

**Goals:**
- Три режима темы: `system` (default) / `dark` / `light`, выбор в настройках, сохранение в cookie.
- Тема применяется ко всем страницам, включая `/login`, до первой отрисовки (без вспышки).
- Восстановить тёплую палитру: тёмная — тёплый текст `#e8e5df`, светлая — тёплая бумажная пара.
- Ноль фронтенд-стейта кроме одного inline-скрипта для system-режима.

**Non-Goals:**
- Живой listener на смену темы ОС в открытой вкладке (отдельный proposal).
- Переключатель в user-menu, кастомизация primary-цветов, хранение темы в БД.

## Decisions

### D1: Стейт = cookie `gm-theme`, по паттерну `gm-locale`
Cookie ∈ {`system`, `light`, `dark`}, default `system`. POST /theme + redirect + middleware — точная копия locale-механики (`locale-post-handler`, `wrap-locale`).
- *Альтернативы*: localStorage + JS (ломает server-side рендер, вспышка темы); хранение в БД (лишняя миграция, cookie и так персистентен, работает у анонима на логине).

### D2: `data-theme` на `<html>`, а не `<body>`
Сервер ставит `data-theme="light|dark"` на html. Причины: (а) механизм `theme-controller` ставит переменные через `:root:has(...)` — при data-theme на body превью перекрывалось бы; (б) `color-scheme` на корне красит системный UI (скроллбары, инпуты); (в) избыточные переменные на двух уровнях — источник уже найденного каскадного бага.
- *Альтернатива*: оставить на body — ломает превью радио-кнопками.

### D3: System-режим = inline-скрипт до первой отрисовки
Для cookie=`system` (или отсутствия cookie) сервер **не** рендерит `data-theme`; в `<head>` вставляется ~5-строчный скрипт, который до paint читает `matchMedia('(prefers-color-scheme: dark)')` и ставит `html.dataset.theme`. Без JS деградация в стоковый light (`:root` default CDN) — приемлемо.
- *Альтернативы*: Client Hints `Sec-CH-Prefers-Color-Scheme` (только Chromium, нужен Critical-CH retry-танец) — отвергнуто; чистый CSS через дублирование палитры в media query — хрупко, отвергнуто; hyperscript — исполняется после DOMContentLoaded, даёт вспышку — отвергнуто. Это осознанное исключение из правила «никакого сырого JS».

### D4: UI в настройках — `join` из трёх `radio.theme-controller` + hyperscript-автосабмит
Радио-кнопки дают мгновенное превью (нативный daisyUI `:has()`), форма постится через `hyperscript` (`on change send submit to the closest <form/>`), `hx-boost="false"` — потому что htmx-boost свапает innerHTML `<body>`, но не атрибуты `<html>`/`<body>`: после boosted-перехода атрибут остался бы старым до полной перезагрузки.
- *Альтернатива*: три кнопки как у языкового переключателя — консистентнее кодовая база, но нет мгновенного превью и переключение целиком через full reload; radio-превью приятнее и опирается на документированный механизм daisyUI.

### D5: Палитра — скоупленный plain CSS вместо `@theme`
Обычный `<style>` (без type) со скоупом по атрибуту — не требует обработки Tailwind и бьёт по тому же элементу, где daisyUI определяет темы:

```css
[data-theme="dark"]  { --color-base-content: #e8e5df; }
[data-theme="light"] {
  --color-base-100: #faf8f4; --color-base-200: #f1ede5; --color-base-300: #e6e0d5;
  --color-base-content: #4a4238;
}
```

Тёмная получает задуманный тёплый текст, светлая — тёплую «бумажную» пару. Оттенки `/60…/85` в коде — это color-mix от `--color-base-content`, тёплые полутона выведутся сами; кастомные переменные `--color-base-content-60…85` из мёртвого блока не используются нигде и удаляются. Точные значения светлой пары — стартовые, подкручиваются в браузере при реализации.
- *Альтернативы*: `@plugin "daisyui/theme"` (build-only, CDN не поддерживает) — отвергнуто; фиксить `@theme` через `type="text/tailwindcss"` — не решает перекрытие на `<body>`/`<html>`.

### D6: Middleware и роут — рядом с locale
`wrap-theme` в `middleware.clj` (читает cookie, валидирует значение, `assoc :theme`), `theme-post-handler` в `routes/auth.clj` (set-cookie + redirect, копия locale), роут `/theme` в `routes/app.clj` рядом с `/locale`, middleware подключается в стек рядом с `wrap-locale` — автоматически накрывает и публичные (логин), и приватные страницы. Валидация значения cookie: что не `light`/`dark` → `system`.

## Risks / Trade-offs

- [Boost-навигация не обновляет атрибуты html/body] → форма темы с `hx-boost="false"`: каждое переключение = честный full reload с серверным `data-theme`; между переключениями тема cookie не меняется, поэтому boosted-навигации остаются консистентными.
- [Блокировщики режут CDN (`ERR_BLOCKED_BY_CLIENT`)] → стили не грузятся целиком, обе темы недоступны; вне скоупа (системная проблема приложения, не этой фичи).
- [Inline-скрипт против «никакого JS»] → ~5 строк, единственный способ узнать тему ОС до отрисовки; задокументировано как исключение (правило AGENTS.md требует вопроса — вопрос задан и согласован в эксплорации).
- [Светлая палитра «на глаз»] → стартовые значения могут потребовать подкрутки контраста; проверка контраста text/base в браузере входит в задачи реализации.
- [Тема до логина] → cookie ставится без http-only (как `gm-locale`) — нечувствительное значение, риск только в подмене на клиенте; валидация на сервере сводит эффект к «косметике».

## Open Questions

_(нет — все развилки закрыты в эксплорации: default=system согласован, живой listener отложен, палитра — скоупленный CSS)_
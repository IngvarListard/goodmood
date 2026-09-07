# Proposal: add-mobile-page-shell

## Why

Каждая страница самостоятельно выбирает свою ширину и отступы (`max-w-md` / `max-w-2xl` / `max-w-sm`, у части страниц нет нижнего отступа под bottom-nav, у части — двойной `pb-16`+`pb-24`). Итог: страницы выглядят по-разному, нет единого места для управления шириной, а мобильная оболочка не доделана (`env(safe-area-inset-*)` написан, но не активен без `viewport-fit=cover`; `.gm-input` 14px вызывает авто-зум iOS). Сейчас приложение используется в первую очередь с телефона — консистентность мобильной вёрстки важнее всего.

## What Changes

- **PageShell в layout**: единая обёртка контента `mx-auto w-full max-w-lg px-4 pt-4` с нижним отступом под bottom-nav (`pb-[calc(env(safe-area-inset-bottom)+5rem)]`) внутри `<main>` в `app.views.layout/layout`. Страницы перестают оборачивать контент в собственные `max-w-*` контейнеры.
- Удалить per-page обёртки ширины из 9 мест: feed, check-in, insights (3 страницы), medications, settings, placeholder (+ их `pb-24`).
- Убрать `pb-16` у `<main>` — отступ под bottom-nav теперь один, в shell.
- `<main>`/`<body>`: `min-h-screen` → `min-h-dvh` (высота без дёрганий от мобильной адресной строки).
- Meta viewport: добавить `viewport-fit=cover`, чтобы `env(safe-area-inset-bottom)` заработал на устройствах с «челкой»/home-indicator.
- `.gm-input`: `font-size` 14px → 16px — против авто-зума iOS при фокусе.
- Auth-страница остаётся с собственным центрированным layout (исключение).

## Capabilities

### New Capabilities

_(нет — изменение попадает в существующий `ui-shell`)_

### Modified Capabilities

- `ui-shell`: требование «Main content compensates for navigation» меняется — нижний отступ переезжает из `<main>` в page-shell; добавляются требования: единая content-колонка (PageShell) для всех страниц, viewport-fit=cover, dvh-высота, 16px-инпуты против iOS-зума.

## Impact

- `src/app/views/layout.clj` — shell, meta viewport, body-классы, `.gm-input`
- `src/app/views/{feed,check_in,insights,medications,settings,placeholder}.clj` — удаление обёрток ширины
- Десктоп: meds/settings/insights станут чуть уже (672→512px) — осознанно, десктоп-вёрстка отдельно позже (non-goal)
- htmx-фрагменты, свапаемые в контент страниц, ширину не несут — не затронуты

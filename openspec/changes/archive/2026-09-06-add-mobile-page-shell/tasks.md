# Tasks: add-mobile-page-shell

## 1. Оболочка (layout.clj)

- [x] 1.1 Meta viewport: добавить `viewport-fit=cover` в `head`
- [x] 1.2 `body`: `min-h-screen` → `min-h-dvh`
- [x] 1.3 `<main>`: убрать `pb-16`; обернуть `content` в shell `div` с классами `mx-auto w-full max-w-lg px-4 pt-4 pb-[calc(env(safe-area-inset-bottom)+5rem)]`
- [x] 1.4 `.gm-input`: `font-size: 14px` → `16px`

## 2. Удаление per-page обёрток

- [x] 2.1 `views/feed.clj`: убрать обёртку `max-w-md mx-auto p-4 pb-24` в `page`
- [x] 2.2 `views/check_in.clj`: убрать обёртку `max-w-md mx-auto p-4 pb-24` в `page`
- [x] 2.3 `views/insights.clj`: убрать обёртки в списке (`max-w-2xl`), new (`max-w-md`), show (`max-w-2xl ... :id "insight-page"` — id сохранить на содержимом)
- [x] 2.4 `views/medications.clj`: убрать обёртку `max-w-2xl mx-auto p-4` в `page`
- [x] 2.5 `views/settings.clj`: убрать обёртку `max-w-2xl mx-auto p-4` в `page`
- [x] 2.6 `views/placeholder.clj`: убрать обёртку `max-w-2xl mx-auto p-4` в `page`

## 3. Проверка

- [x] 3.1 Grep: в views не осталось `max-w-2xl|pb-24` вне auth; `pb-16` ушёл из layout
- [x] 3.2 Приложение запускается, health-check 200; проверить сессию и htmx-навигацию (hx-boost) между страницами
- [x] 3.3 Playwright: все роуты (/feed, /check-in, /medications, /settings, /insights, /insights/new) в мобильном вьюпорте (390px) — одинаковая визуальная ширина контента, нет горизонтального скролла; bottom-nav не перекрывает контент; на iPhone-вьюпорте с safe-area отступ снизу корректен
- [x] 3.4 Десктоп-вьюпорт (1280px): контент в колонке ≤512px справа от sidebar; auth-страница не изменилась

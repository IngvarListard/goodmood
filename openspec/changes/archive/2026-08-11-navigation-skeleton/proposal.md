## Why

Приложение «Дневник настроения» не имеет общего каркаса навигации. Каждая страница рендерит собственный html/head/body, нет единого layout. При переключении между экранами пользователь не видит навигационной панели — нет sidebar на десктопе и bottom bar на мобильном. Это делает UX фрагментированным и не соответствует скриншотам-референсам.

## What Changes

- Новый компонент `app.views.navigation` — единый источник пунктов меню, рендерит :mobile (bottom bar) и :desktop (sidebar) варианты без дублирования разметки.
- Новый компонент `app.views.layout` — обёртка html/head/body с CDN-скриптами (htmx, hyperscript, Tailwind, DaisyUI) и встраиванием навигации.
- Модуль `app.icons` — загрузчик self-hosted SVG из `resources/icons/heroicons/`, memoized.
- Рефакторинг `app.views.entries/page` — убираем собственный html/head/body, страница использует общий layout.

## Capabilities

### New Capabilities

- `navigation`: компонент навигации с двумя вариантами (:mobile bottom bar, :desktop sidebar), единый источник данных nav-items, подсветка активного пункта, SVG-иконки через app.icons.
- `layout`: обёртка HTML-страницы (html/head/body, CDN-скрипты, data-theme), встроенная навигация с CSS media query для переключения mobile/desktop, компенсация паддингами (md:pl-64, pb-16).

### Modified Capabilities

- `entries-ui`: page() переходит на общий layout, бизнес-логика не меняется.

## Impact

- `src/app/icons.clj` — новая функция `svg` для загрузки и мемоизации SVG.
- `src/app/views/navigation.clj` — новый файл.
- `src/app/views/layout.clj` — новый файл.
- `src/app/views/entries.clj` — `page()` возвращает только контент, html/head/body переносится в layout.
- `test/app/views/navigation_test.clj`, `test/app/views/layout_test.clj`, `test/app/icons_test.clj` — новые тесты.
- Нет новых зависимостей в deps.edn.
- Роутинг и реальные экраны (чек-ин, лекарства, давление) — вне скоупа.

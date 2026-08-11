## Tasks

### 1. Icons: fixed-size box and explicit svg size
- [x] `src/app/icons.clj`: обёртка svg — `inline-flex items-center justify-center w-6 h-6 shrink-0`;
      при загрузке инжектить `width="24" height="24"` в строку svg (внутри `load-svg`, под memoize).
- [x] `test/app/icons_test.clj`: обновить/добавить проверки размера обёртки и атрибутов svg.

### 2. Navigation: daisyUI menu, links, server-side active
- [x] `src/app/views/navigation.clj`:
      - `:desktop` → `ul.menu.menu-vertical` + заголовок «Good Mood»; пункты `li > a` с
        `href` из `:route`, иконка (v1) рядом с лейблом, `w-full`.
      - `:mobile` → `ul.menu.menu-horizontal` + `flex-1` на пунктах (иконка над лейблом —
        компактный bottom bar на 6 пунктов).
      - активный: solid-иконка + `menu-active`/`text-primary` + `aria-current="page"`.
      - удалить `<button type="button">`.
- [x] `test/app/views/navigation_test.clj`: кнопки → ссылки (href = :route), меню-контейнеры,
      активный/неактивный стили, solid/outline иконки, no active при nil.

### 3. Layout: hx-boost, fixed bottom bar with z-index and safe-area
- [x] `src/app/views/layout.clj`:
      - `[:body {:hx-boost "true" ...}]` (атрибуты в мапе).
      - мобильный wrapper: `md:hidden fixed bottom-0 inset-x-0 z-50 bg-base-100 border-t
        border-base-200 pb-[env(safe-area-inset-bottom)]`.
      - десктоп wrapper без изменений (`hidden md:flex fixed left-0 top-0 h-screen w-64`).
- [x] `test/app/views/layout_test.clj`: hx-boost на body, z-50, safe-area; старые проверки
      позиционирования сохранить.

### 4. Placeholder pages for navigation routes
- [x] `src/app/views/placeholder.clj`: `(defn page [{:keys [title]}] ...)` — stub-контент +
      layout; хелпер активного пункта из пути роута.
- [x] `src/app/routes/app.clj`: 6 GET-роутов (/dashboard, /check-in, /history, /statistics,
      /insights, /settings) → placeholder page с `:active` от пути.
- [x] Хелпер `active-id`/отображение path → keyword (напр. "/statistics" → :statistics).
- [x] Тесты: каждый роут → 200 + заголовок из лейбла; единственный активный пункт = свой роут
      (в обеих панелях — мобильной и десктопной).

### 5. Entries page: активность nil (без изменений спеки)
- [x] Проверить, что `/entries` не подсвечивает ни один пункт (путь не входит в nav-items) —
      код уже передаёт без `:active`; `placeholder/active-id` возвращает nil для /entries.

### 6. Specs and validation
- [x] Дельта-спеки: navigation (RENAMED + MODIFIED + ADDED), layout (MODIFIED + ADDED),
      placeholder-pages (ADDED) — содержимое в design-артефакте; поправлен текст MODIFIED
      требования навигации под фактический mobile-лейаут (иконка над лейблом).
- [x] `openspec validate` изменение — валидно; после реализации — `/opsx-archive` (смерджит спеку).

### 7. Browser verification (ручная проверка пользователем)
- [ ] Сайдбар: иконки на одной вертикальной оси при разных длинах лейблов.
- [ ] Мобильный бар: computed style `position: fixed` — прижат к низу viewport при скролле;
      safe-area на iPhone-эмуляции.
- [ ] Клик по пункту → AJAX-навигация, страница заглушки, только соответствующий пункт активен,
      иконка outline→solid, hover/active-анимации работают.
- [ ] Без JS (отключить) — ссылки ведут обычным переходом.
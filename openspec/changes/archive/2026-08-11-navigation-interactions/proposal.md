## Why

Навигация после skeleton-реализации декоративна и некорректна: иконки сайдбара «прыгают» по
горизонтали из-за контейнеров с шириной от контента (кнопки не заполняют `li`, span с `w-6 h-6`
inline — размеры не применяются), мобильный bottom bar не закреплён к низу viewport (гуляет с
документом), а клики по пунктам ничего не делают — нет переключения иконки outline/solid при выборе.

## What Changes

- Сайдбар переводится на даisyUI-компонент `menu` (menu-vertical): пункты `li > a`, иконка рядом
  с лейблом (конвенция daisyUI), активный пункт — solid-иконка + `menu-active`/aria-current + accent.
  **BREAKING**: текущая спека требует «buttons, not links» — пункты становятся ссылками `<a href>`.
- **BREAKING**: требование «No click handlers are attached» заменяется на серверную модель выбора:
  активный пункт вычисляется из пути роута, клик — навигация.
- Навигация через `hx-boost` на body: клик — AJAX-переход, сервер перерисовывает страницу с новым
  `:active` (без фулл-релоада; без JS деградирует в обычные переходы).
- Заглушки-страницы для всех 6 роутов из nav-items (/dashboard, /check-in, /history, /statistics,
  /insights, /settings) — без бизнес-логики.
- Мобильный bottom bar: фиксированный к низу viewport, `z-50`, safe-area отступ
  (`pb-[env(safe-area-inset-bottom)]`), пункты `menu menu-horizontal` равной ширины.
- Исправление контейнеров иконок в `app.icons`: иконка в боксе `inline-flex w-6 h-6 shrink-0`,
  svg получает явный размер; пункты меню растягиваются на ширину контейнера.
- Анимации и hover — по конвенции daisyUI `menu` + Tailwind-утилиты (transition-colors, hover/active).

## Capabilities

### New Capabilities

- `placeholder-pages`: страницы-заглушки для всех роутов навигации — рендерятся через общий layout,
  помечают активный пункт от пути роута, без бизнес-логики.

### Modified Capabilities

- `navigation`: пункты становятся ссылками `<a href>` (вместо `<button>` без обработчиков);
  активный пункт — серверно по пути роута; sidebar строится на даisyUI `menu`
  (выравнивание иконок, hover/active); вариант :mobile — пункты равной ширины.
- `layout`: `hx-boost` на body; мобильный bottom bar получает z-index и safe-area отступ
  при сохранении фиксированного позиционирования; структура классов сайдбара под даisyUI menu.

## Scope / Non-goals

**Non-goals:**
- Реальные экраны (чек-ин, статистика, инсайты и т.д.) — только заглушки.
- Клиентское переключение активного пункта (hyperscript-toggle класса) — активность полностью серверная.
- Рефакторинг роутера за пределами добавления роутов заглушек.
- Изменение бизнес-логики экрана entries (форма, список, добавление).

## Impact

- `src/app/views/navigation.clj` — даisyUI `menu`, `<a href>`-пункты, серверный `:active`.
- `src/app/views/layout.clj` — `hx-boost`, z-50/safe-area, hx-атрибуты в мапе параметров.
- `src/app/icons.clj` — фиксированный бокс иконки, явный размер svg.
- `src/app/routes/app.clj` — 6 роутов заглушек.
- `src/app/views/placeholder.clj` — новый view заглушек.
- `src/app/views/entries.clj` — активный пункт nil для /entries (не в nav) — без изменений спеки.
- Обновления: `openspec/specs/navigation/spec.md`, `openspec/specs/layout/spec.md`,
  тесты navigation/layout/icons + новые тесты placeholder.
- Зависимости: новых нет.
# Design: add-mobile-page-shell

## Context

`app.views.layout/layout` рендерит оболочку (sidebar на md+, fixed bottom-nav на телефоне) и `<main>`, который не задаёт ни ширину, ни горизонтальные паддинги. Каждая страница сама оборачивает контент: feed/check-in — `max-w-md mx-auto p-4 pb-24`, insights/medications/settings/placeholder — `max-w-2xl mx-auto p-4` (у части нет `pb-24`). Нижний отступ под bottom-nav задваивается: `pb-16` у `<main>` + `pb-24` у страницы = 160px, а у meds/settings — только 64px.

Мобильные детали: meta viewport без `viewport-fit=cover` → `pb-[env(safe-area-inset-bottom)]` на bottom-nav всегда 0; `body` использует `min-h-screen` (100vh дёргается от адресной строки iOS/Android); `.gm-input` 14px → iOS Safari зумит при фокусе.

Инварианты 03-architecture.md: изменение целиком в слое `views/` — layout и page-fn'ы; слои ниже не затронуты.

## Goals / Non-Goals

**Goals:**
- Один источник правды по ширине и отступам контента: PageShell в `layout`
- Одинаковая визуальная ширина всех страниц на телефоне (и предсказуемая — на десктопе)
- Активировать safe-area на iPhone; убрать дёргание высоты (dvh); исключить iOS-зум инпутов

**Non-Goals:**
- Десктопная вёрстка (тиры ширин, multi-column) — отдельное изменение позже
- Переделка bottom-nav на DaisyUI `dock`
- Sticky-заголовки, full-bleed секции, container queries
- Auth-страница (`app.views.auth`) — остаётся с собственным центрированным layout
- htmx-фрагменты: ширину не несут, рендерятся внутри shell — не трогаем

## Decisions

### 1. Shell в layout, а не константа для per-page обёрток

`layout` оборачивает `content` в:

```clojure
[:main {:class "md:pl-64 min-h-dvh"}
 [:div {:class "mx-auto w-full max-w-lg px-4 pt-4 pb-[calc(env(safe-area-inset-bottom)+5rem)]"}
  content]]
```

- *Почему*: контент проходит через `layout` при любой навигации (`hx-boost` перерисовывает body) и в htmx-свапах фрагментов — одна точка гарантирует ширину везде. Альтернатива (общая строка-константа `def page-wrap`, которую страницы вставляют сами) оставляет 9 мест копипасты и не защищает от забывчивости.
- `<main>` теряет `pb-16` — нижний отступ один, в shell. 5rem (80px) покрывает высоту bottom-nav (~64px) + зазор.

### 2. Одна ширина `max-w-lg` без тир-системы

- *Почему*: на телефоне (<512px viewport) `max-w-md`/`max-w-lg`/`max-w-2xl` неразличимы — все дают «100% − 32px»; выбор влияет только на десктоп. Одна ширина проще и не вводит осмысленных категорий до их реальной нужды. Тир-система (`:width :narrow|:default|:wide` в opts layout) — задел на десктопную фазу, не сейчас.
- Следствие (осознанное): meds/settings/insights на десктопе станут уже (672→512px).

### 3. `min-h-dvh` вместо `min-h-screen`

Tailwind v4 умеет `min-h-dvh` нативно (100dvh = видимая высота без адресной строки). Кладётся на `<body>`; на `<main>` не нужен — высоту держит body.

### 4. `viewport-fit=cover` + safe-area в shell

Meta: `width=device-width, initial-scale=1, viewport-fit=cover`. После этого `env(safe-area-inset-bottom)` на bottom-nav активируется, а shell добавляет его к нижнему отступу через `calc(...)`. Без cover env() = 0 — нынешний `pb-[env(...)]` мёртвый код.

### 5. `.gm-input` 16px

`font-size: 14px` → `16px` в инлайн-CSS layout. iOS Safari зумит поле при фокусе, если font-size < 16px. Альтернатива (`maximum-scale=1` в meta) ломает accessibility.

### 6. Отступы страницы переносятся в shell: `p-4` → `px-4 pt-4`

Per-page `p-4` давал и нижний паддинг, который теперь конфликтует с pb-shell. Страницы после удаления обёрток не задают внешних паддингов вовсе.

## Risks / Trade-offs

- [Страницы с неочевидными собственными контейнерами] → после правки пройтись Playwright по всем роутам (/feed, /check-in, /medications, /settings, /insights, /insights/:id, /insights/new, /login) и проверить визуальную ширину и отсутствие горизонтального скролла.
- [Фрагменты, ожидающие ширину страницы (fixed-баннер notifications `max-w-sm bottom-24`)] → не зависят от shell (fixed к вьюпорту), не трогаем; проверить на телефоне.
- [Десктоп стал уже] → принятый trade-off (решение №2), откат — трёр-система позже.
- [Двойной pb у страниц после рефакторинга, если обёртку удалили не всю] → grep `pb-24` и `max-w-2xl\|max-w-md` по views должен стать пустым (кроме auth).

## Open Questions

_(нет — решения приняты в explore-фазе: одна ширина, десктоп потом, auth не трогаем)_

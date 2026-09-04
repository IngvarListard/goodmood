# Design: redesign-tokens

## Context

Дизайн-референсы: `design/lenta.html`, `design/otmetka.html`, `design/new-insight.html` (тёмный мобильный UI-kit, значения расходятся в пределах ±5%). Текущий слой темы: daisyUI 5 CDN + Tailwind v4 browser CDN; палитра переопределяется скоупленным CSS в `<style>`-блоке `views/layout.clj:48-58` (уже есть паттерн для light-темы). Компоненты вьюх — daisyUI-классы (`btn-primary`, `bg-base-200`, `badge`), иконки — heroicons через `app.icons`.

Ограничение Tailwind-CDN: `@theme`/`@plugin` не обрабатываются, поэтому все токены — чистые CSS-переменные в scope `[data-theme=dark]` (или `:root` для не-тематических).

## Goals / Non-Goals

- Goals: усреднённая палитра dark через daisyUI-переменные (автоперекраска всех существующих компонентов), Inter, семантические state-цвета, единый bottom nav, градиент-хелпер.
- Non-Goals: light-тема (вариант B — не трогаем), редизайн страниц, смена иконочного набора, анимации.

## Decisions

### Decision 1. Палитра — переопределение daisyUI CSS-переменных, а не кастомные классы

`[data-theme=dark]` в `<style>` layout.clj получает полную карту: `--color-base-100: #0c0f17`, `--color-base-200: #151827`, `--color-base-300: #1c2032`, `--color-base-content: #e6e8f0`, `--color-primary: #5b5bea`, `--color-primary-content: #ffffff`, `--color-secondary: #7c5ce0`, `--color-neutral: #1c2032`, `--color-success: #3fbf5f`, `--color-warning: #e8b33a`, `--color-error: #e05545`, `--color-info: #3b82f6`.

- **Почему**: все существующие `btn-*`, `card bg-base-*`, `badge-*` перекрашиваются без правки ~15 файлов вьюх. Альтернатива (свои классы на каждой странице) — дублирование и рассинхрон.
- Усреднение значений трёх макетов — таблица в `specs/design-tokens/spec.md`.

### Decision 2. State-цвета и метрики — префиксованные переменные `--gm-*`

Не маппятся на daisyUI-семантику напрямую (state-бейдж = пара «фон+текст», не один цвет), поэтому отдельные переменные: `--gm-state-danger-bg: #4a1f22`, `--gm-state-danger-text: #ef8a86`, `--gm-state-ok-bg: #1d3d28`, `--gm-state-ok-text: #7fd493`, `--gm-metric-energy: #f0c22e`, `--gm-metric-anxiety: #e05545`, `--gm-metric-focus: #3b82f6`. Плюс два хелпер-класса `.gm-badge-danger`, `.gm-badge-ok` (и `.gm-chip` для чипов метрик) в том же `<style>`-блоке, т.к. повторяемая комбинация фон+текст+радиус.

- **Почему не `success/error`**: daisyUI `badge-success` тянет свой контентный цвет и форму; state-бейджи в макете имеют конкретные тёмные подложки. Альтернатива — `badge-success/error` с оверрайдами: больше CSS, тот же результат.

### Decision 3. Inter — Google Fonts `<link>` в head

`<link rel="preconnect">` + `<link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&display=swap">`, затем `--gm-font: 'Inter', system-ui, sans-serif` и `body { font-family: var(--gm-font) }`. Альтернатива — self-host (woff2 в resources/public/fonts): надёжнее при блокировках, но добавляет бинарные ассеты; отложено, CDN-паттерн уже принят в проекте (daisyUI, htmx).

### Decision 4. Радиусы — классами Tailwind, без переменных

Карточки `rounded-[18px]`, чипы/инпуты `rounded-xl`, кнопки `rounded-2xl` — проставляются в задачах редизайна страниц, в токены не выносятся (Tailwind-классы уже есть, переменная не даст выигрыша).

### Decision 5. Bottom nav — активный пункт «контейнер + точка»

В `views/navigation.clj` мобильный вариант: активный пункт — `rounded-xl bg-primary/10` на иконке (вместо полной плашки на `li`), точка `bg-primary` снизу сохраняется. Это медиана трёх макетов и минимальный дифф от текущего кода. Десктопное меню не трогаем.

### Decision 6. Градиент — один хелпер-класс

`.gm-gradient { background: linear-gradient(135deg, #6366f1, #7c5ce0) }` в `<style>` layout. Применяется точечно (period pill, save-кнопки) в задачах страниц; solid `bg-primary` — база для остальных кнопок.

## Risks / Trade-offs

- [daisyUI CDN частично перекрывает переменные своей темой] → браузерная проверка `data-theme=dark` сразу после задачи палитры (Playwright MCP), сравнение computed-цветов.
- [Tailwind v4 browser CDN и `@import` шрифта в `<style>` конфликтуют] → шрифт подключается `<link>`, не `@import`.
- [color-mix-производные (`text-base-content/60`) на новой палитре могут выглядеть тускло] → приемлемо, значения подобраны контрастными; точечная правка при редизайне страниц.
- [Inter грузится с задержкой → FOUT] → `display=swap` (уже в URL), деградация на system-ui.

## Migration Plan

Изменение чисто визуальное, деплой = рестарт. Откат — revert коммита (палитра light не затронута, риск регрессии только в dark).

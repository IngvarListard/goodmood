## Context

Goodmood MVP использует DaisyUI 5.7.15 + Tailwind 4 (CDN browser build) с дефолтной dark темой. Все view-файлы написаны на Clojure hiccup2. Проблемы контраста, tap targets и визуального шума были выявлены через UX-аудит и подтверждены превью-прототипами в `design/`.

Текущее состояние:
- `data-theme="dark"` в `layout.clj:39` без кастомизации цветов
- `opacity-50/60/70` повсеместно для secondary текста
- `btn-sm` без `min-h-[44px]` на кнопках
- Разные цвета слайдеров (primary/success/warning) без семантики
- `divider my-1` между toggle в настройках

## Goals / Non-Goals

**Goals:**
- Увеличить contrast ratio secondary текста до WCAG AA (4.5:1)
- Сделать все tap targets ≥44px (Apple HIG)
- Унифицировать badge-систему для статусов
- Убрать визуальный шум (dividers, inconsistent opacity)
- Сохранить текущую структуру приложения и конвенции

**Non-Goals:**
- Полный редизайн или смена стека
- Добавление новых функций
- Изменение бизнес-логики
- Оптимизация bundler/CDN (остаётся browser build)

## Decisions

### D1: Кастомная тема через @theme CSS variables

**Решение:** Добавить `<style>` block в `layout.clj` с `@theme` directives для переопределения `--color-base-content` и его opacity-variants.

**Альтернативы:**
- Tailwind config файл — невозможен при CDN browser build
- Inline opacity classes — не решает проблему дефолтного `base-content`

**Rationale:** `@theme` работает с Tailwind 4 browser build, не требует сборки, применяется глобально.

### D2: opacity → text-base-content/N

**Решение:** Заменить `opacity-50` на `text-base-content/60`, `opacity-60` на `text-base-content/70`, `opacity-70` на `text-base-content/80` и т.д.

**Rationale:** Tailwind color opacity modifier применяет opacity только к цвету текста, не к фону. Это даёт предсказуемый contrast.

### D3: Все слайдеры range-primary

**Решение:** Заменить `range-success` (энергия) и `range-warning` (тревога) на `range-primary`.

**Rationale:** Цвета не несут смысловой нагрузки (зелёный ≠ "хорошо", жёлтый ≠ "опасно"). Один цвет减少了 cognitive load.

### D4: Active tab — bg-primary/10 + dot

**Решение:** Добавить `bg-primary/10` на активный tab + dot-indicator снизу.

**Rationale:** Двойной signal (цвет + фон + dot) надёжнее одного цвета. Важно для users с когнитивными симптомами.

### D5: Badge система

**Решение:**
- Taken: `badge badge-success badge-sm` (filled)
- Skipped: `badge badge-warning badge-outline badge-sm` (outlined)
- Sensitive: `badge badge-ghost badge-sm` (ghost)

**Rationale:** Визуальная иерархия: success > warning > ghost. Outline для skipped показывает "не критично, но важно".

## Risks / Trade-offs

- **[CDN dependency]** → Тема кастомизируется через inline CSS, не зависит от внешних ресурсов
- **[Regression]** → Превью-прототипы в `design/`可用于视觉 regression testing перед мержем
- **[Accessibility]** → Увеличение contrast помогает users с нарушениями зрения, но не решает все a11y проблемы

## Migration Plan

1. Deploy кастомную тему (layout.clj) — immediate effect
2. Пофайловые правки — от P0 (feed, nav) к P2 (settings, notifications)
3. Визуальная проверка через превью или Playwright
4. Rollback: revert style block + opacity classes

## Open Questions

- Нужен ли `--color-primary` override для一致性? (デフォルト primary — фиолетовый, визуально подходит)
- Стоит ли выносить кастомную тему в отдельный CSS file? (При CDN build — нет, inline проще)

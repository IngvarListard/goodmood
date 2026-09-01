## Why

Текущий дизайн Goodmood MVP имеет проблемы с контрастом текста на тёмном фоне (opacity-50/60 на `text-base-content`), слишком мелкими tap targets (<44px), inconsistent badge-системой и визуальным шумом (dividers между toggle). Это мешает использованию приложения людьми с когнитивными симптомами и тремором.

## What Changes

- **Кастомная тема DaisyUI**: переопределение `--color-base-content` для increased contrast (`#e8e5df` вместо `#d8d5ce`)
- **Контраст текста**: замена `opacity-N` на `text-base-content/N` во всех view-файлах
- **Tap targets**: добавление `min-h-[44px]` на все кнопки и интерактивные элементы
- **Badge-система**: унификация бейджей (success/warning/ghost) для статусов медикаментов
- **Bottom nav**: добавление `bg-primary/10` + dot-indicator для активного таба
- **Слайдеры**: все три оси (настроение/энергия/тревога) одного цвета `range-primary`
- **Dividers**: замена `divider` на `divide-y` в секциях настроек
- **Feed CTA**: prominent "Создать запись" по центру при пустом экране, FAB только при наличии записей

## Capabilities

### New Capabilities
- `design-tokens`: кастомная тема DaisyUI (цвета, contrast, spacing)

### Modified Capabilities
<!-- Нет существующих specs, которые требуют изменения -->

## Impact

**Затронутые файлы:**
- `src/app/views/layout.clj` — style block с кастомной темой
- `src/app/views/feed.clj` — opacity fixes, CTA hierarchy, conditional FAB
- `src/app/views/check_in.clj` — uniform slider color
- `src/app/views/medications.clj` — tap targets, badge system, opacity
- `src/app/views/insights.clj` — tap targets, opacity
- `src/app/views/settings.clj` — divider cleanup
- `src/app/views/notifications.clj` — divider cleanup
- `src/app/views/ai.clj` — divider cleanup
- `src/app/views/navigation.clj` — active tab indicator

**Зависимости:** не требует новых зависимостей (Tailwind + DaisyUI уже подключены через CDN)

**Серьёзность:** косметические изменения, не затрагивают бизнес-логику

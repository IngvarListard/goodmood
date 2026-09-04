# Proposal: redesign-insight-form

## Why

Дизайн-референс `design/new-insight.html` задаёт форму нового инсайта: карточка со сгруппированными полями, тёмные инпуты с видимыми бордерами, кастомные select'ы, пилюля «Добавить пункт» с иконкой, поля-подсказки под инпутами, highlight-рамка для поля утверждения. Текущая форма (`views/insights.clj` `new-page`) структурно совпадает (контекст, state_label, категория, динамические советы, утверждение, сохранить) — меняется визуальный слой. Форма редактирования инсайта (`context-edit-fragment` и др.) получает те же стили, чтобы форма не рассинхронизировалась. Требует `redesign-tokens`.

## What Changes

- **Карточка формы**: `bg-base-200 rounded-[18px] p-5` вместо текущей обёртки; поля формы идут по макету: label (14px semibold) → input → подсказка (12px muted).
- **Инпуты/textarea/select**: общий стиль `bg-base-300 border border-base-content/15 rounded-xl px-4 py-3 text-sm focus:border-primary` (заменяет `textarea-bordered`/`select-bordered`) — класс-хелпер не вводим, паттерн прописывается в вьюхах change'а (переиспользование на edit-форме — Decision design.md).
- **Кнопка «Добавить пункт»**: пилюля `rounded-full border border-base-content/15 hover:border-primary` с иконкой plus-circle и текстом muted; существующий htmx-контракт добавления/удаления пунктов сохраняется дословно.
- **Highlight поля «Утверждение о себе»**: `border-primary` + мягкое свечение `shadow-[0_0_20px_rgba(99,102,241,0.15)]` (из макета).
- **Save**: `w-full py-4 rounded-xl gm-gradient font-semibold`.
- **up-arrow кнопки макета** — не включаются (артефакт, решение сессии explore).
- **Ширина**: `max-w-2xl` → `max-w-md mx-auto`.
- i18n: новых ключей не требуется.

## Capabilities

### New Capabilities
- `insight-form-visual`: форма нового/редактируемого инсайта отображается в дизайн-стиле (карточка, бордер-инпуты, пилюля добавления, highlight утверждения, gradient save) при сохранении всех htmx-контрактов и валидации.

### Modified Capabilities

_(функциональные контракты инсайтов не меняются)_

## Scope

- `src/app/views/insights.clj` — `new-page` (основной), edit-фрагменты (`context-edit-fragment`, `advice-edit-fragment`, `identity-edit-fragment`) — те же классы инпутов.

## Non-goals

- Список инсайтов (`list-page`), карточки просмотра, feed-widget — только форма.
- Кнопки up-arrow из макета — артефакт.
- Логика AI-подбора, категории, валидации.

## Impact

- **Код**: `src/app/views/insights.clj`.
- **Миграции/БД/зависимости**: нет.
- **Риски**: edit-фрагменты инлайнятся htmx-свапами в show-страницу — при смене классов проверить, что свапы (`context-edit-fragment` и др.) не ломают вёрстку карточки; select с кастомной стрелкой из макета — оставить нативный select без кастомного `::after` (упрощение, см. design.md).

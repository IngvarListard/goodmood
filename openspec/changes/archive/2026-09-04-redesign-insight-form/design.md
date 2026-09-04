# Design: redesign-insight-form

## Context

Референс `design/new-insight.html`. Текущая форма: `views/insights.clj` `new-page` — обёртка-карточка, поля `textarea textarea-bordered`, `select select-bordered`, динамические advice-пункты через htmx (`advice-item-fragment`, `advice-items-fragment`, `add-advice-item` роут), валидационные ошибки (`validation-error-fragment`). Edit-фрагменты инлайнятся в show-страницу.

## Goals / Non-Goals

- Goals: дизайн-стиль формы new + единые стили инпутов на edit-фрагментах.
- Non-Goals: список/просмотр инсайтов, up-arrow артефакт, логика.

## Decisions

### Decision 1. Общий паттерн инпута — копируемым классом, без нового хелпера

Строка классов: `w-full bg-base-300 border border-base-content/15 rounded-xl px-4 py-3 text-sm text-base-content placeholder:text-base-content/40 focus:outline-none focus:border-primary transition-colors`. Не заводим CSS-хелпер (в отличие от `.gm-range`): класс нужен в двух файлах максимум, а хелпер в `<style>` layout скрыл бы использование. Edit-фрагменты получают ту же строку — единообразие без абстракции.

- Альтернатива `.gm-input` хелпер — оправдан если класс расползётся на medications/settings; отложено, при третьем использовании — вынести (rule of three).

### Decision 2. Select — нативный без кастомной стрелки

Макет рисует `::after`-стрелку; нативный select daisyUI со стилем Decision 1 достаточен, кастомная стрелка — чистый CSS-декор с риском в разных браузерах. appearance не трогаем.

### Decision 3. Динамические advice-пункты — стилизация существующих фрагментов

`advice-item-fragment` (textarea + кнопка удаления): textarea — стиль Decision 1; кнопка удаления — `btn-ghost btn-circle btn-sm` с иконкой trash (x-mark), muted → error на hover. Кнопка «Добавить пункт» — пилюля `rounded-full border border-base-content/15 hover:border-primary px-4 py-2` + `plus-circle` иконка + текст `text-base-content/60 text-sm`. hx-атрибуты (`hx-post /insights/...`, targets, OOB) — дословно.

### Decision 4. Highlight утверждения

Textarea identity: базовый стиль + `border-primary shadow-[0_0_20px_rgba(99,102,241,0.15),0_0_40px_rgba(99,102,241,0.05)]`. Пояснение «(необязательно)» — `text-base-content/50 font-normal` в label (по макету).

### Decision 5. Подсказки под полями

Под каждым блоком `p.text-xs.text-base-content/60` с существующими текстами-описаниями (ключи уже есть: контекст/состояние/утверждение). Если ключа подсказки нет в i18n — не добавлять новую копию, использовать существующие описания формы.

### Decision 6. Edit-фрагменты — только классы инпутов

`context-edit-fragment`, `advice-edit-fragment`, `identity-edit-fragment` меняют классы textarea на Decision 1 (без highlight), чтобы inline-редактирование в show-странице совпадало с формой. Разметку карточек show-страницы не трогаем.

## Risks / Trade-offs

- [htmx-свапы advice-пунктов и валидационных ошибок зависят от id/структуры] → id и hx-атрибуты не трогать; проверить add/remove пункта и сабмит с ошибкой в Playwright MCP.
- [Класс-строка инпута дублируется в 2 местах] → принято (Decision 1), правило трёх применений.

## Migration Plan

Визуальный слой, откат revert'ом.

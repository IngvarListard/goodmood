# Proposal: redesign-check-in

## Why

Дизайн-референс `design/otmetka.html` задаёт форму отметки состояния: segmented control (утро/день/вечер/событие) с разделителями, кастомные слайдеры с белым glow-тумбом, аккордеоны опциональных полей, крупная save-кнопка с glow-тенью. Текущая форма (`views/check_in.clj`) — daisyUI `tabs-boxed`, `range`, `collapse`, `btn-primary`. Функционально формы совпадают 1:1 (4 шаблона, mood+energy+anxiety слайдеры 0–10, опциональные фокус/сон/заметка/активность, мягкий режим), меняется визуальный слой и компоненты-обёртки. Требует `redesign-tokens`.

## What Changes

- **Segmented control**: контейнер `bg-base-300 rounded-2xl p-[5px]` с inset-тенью, активная кнопка `bg-primary rounded-xl`, разделители `w-px` между сегментами; hyperscript-логика `template-tabs` сохраняется (data-template, скрытый input).
- **Glow-слайдер**: обёртка над `<input type=range>` — daisyUI `range` остаётся для нативности (a11y, hyperscript-биндинг), стилизуется CSS: track `bg-base-300 h-2 rounded-full`, thumb белый 20px с двойным glow (`box-shadow` белый + accent) через скоупленный CSS-класс `.gm-range` в layout или в check_in-вьюхе; значение по центру строки 0/5/10 сохраняется.
- **Аккордеоны**: daisyUI `collapse collapse-arrow` остаётся, фон `bg-base-300`, радиус 12px, заголовок 14px medium; hyperscript-логика disabled-инпутов не меняется.
- **Заголовок «дополнительно»**: uppercase 10px muted tracking-wider.
- **Save-кнопка**: `w-full h-14 rounded-2xl` с `.gm-gradient` и glow-тенью (`shadow-[0_4px_24px_rgba(98,94,252,0.35)]`).
- **Шапка**: back-кнопка (иконка chevron-left, уже есть) + заголовок 22px bold; мягкий режим — баннер сохраняется, приводится к палитре (alert-info → `bg-base-200 border border-info/30` стиль карточки).
- **Ширина**: `max-w-2xl` → `max-w-md mx-auto`.
- i18n: новых ключей не требуется (тексты уже есть).

## Capabilities

### New Capabilities
- `check-in-form-visual`: форма отметки отображается segmented control'ом, glow-слайдерами и аккордеонами в дизайн-стиле при полном сохранении существующего поведения (шаблоны, мягкий режим, htmx-сабмит).

### Modified Capabilities

_(функциональные контракты check-in не меняются)_

## Scope

- `src/app/views/check_in.clj` — основной объём.
- `src/app/views/layout.clj` — только если `.gm-range` хелпер кладём в общий `<style>` (по decision design.md).

## Non-goals

- Изменение полей, валидации, роутов, мягкого режима (Decision 14.4).
- Кнопка up-arrow из макета — артефакт, не включать (решение сессии explore).
- Десктоп-спецвёрстка.

## Impact

- **Код**: `src/app/views/check_in.clj`, возможно `src/app/views/layout.clj`.
- **Миграции/БД/зависимости**: нет.
- **Риски**: стилизация нативного range (webkit/moz-псевдоэлементы) — проверять в Chromium Playwright; hyperscript-биндинги слайдеров не должны потеряться при смене классов.

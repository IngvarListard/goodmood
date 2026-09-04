# Design: redesign-check-in

## Context

Референс `design/otmetka.html`. Текущая форма: `views/check_in.clj` — `template-tabs` (hyperscript: data-template + скрытый input), `range-field` (hyperscript on input → value-span), `optional-block` (collapse + checkbox-логика disabled), `soft-mode-banner`, htmx-сабмит с редиректом. Функциональность не меняется — только разметка/классы, hyperscript-атрибуты сохраняются дословно.

## Goals / Non-Goals

- Goals: segmented control, glow-слайдеры, аккордеоны в стиле макета, gradient save.
- Non-Goals: логика формы, мягкий режим, up-arrow артефакт, десктоп.

## Decisions

### Decision 1. Segmented control — свой маркап поверх существующей логики

Контейнер: `flex bg-base-300 rounded-2xl p-[5px] shadow-[inset_0_2px_4px_rgba(0,0,0,0.1)]`, между кнопками `div.w-px.bg-base-content/10.my-2.mx-1`; активная — `bg-primary text-primary-content rounded-xl font-medium`, неактивная — `text-base-content/60 hover:text-base-content`. Hyperscript-атрибут кнопок и скрытый `#template-value` — без изменений.

- Альтернатива daisyUI `tabs-box` — не даёт inset-контейнера и разделителей.

### Decision 2. Слайдер — CSS-стилизация нативного range (класс `.gm-range`)

Оставляем `<input type=range>` (доступность + живой hyperscript-биндинг), стилизуем в `<style>` layout: track 8px `rounded-full` `bg-base-300`, thumb 20px белый, `box-shadow: 0 0 15px rgba(255,255,255,.8), 0 0 25px rgba(94,92,230,.8)`. Прогресс заполнения слева от тумба — webkit требует JS-хак; в макете есть, но принимаем trade-off: **без progress-fill** (не критично для функции, экономит JS). Класс глобальный `.gm-range` — переиспользуется фокус-слайдером опционального блока.

- Альтернатива div-слайдер с hyperscript-драгом — теряет клавиатуру/a11y, отвергнуто.

### Decision 3. Аккордеоны — daisyUI collapse с новыми классами

`bg-base-300 rounded-xl` вместо `bg-base-300/50 rounded-lg`, заголовок `text-sm font-medium`, стрелка штатная collapse-arrow. Checkbox/hyperscript disabled-логика — без изменений.

### Decision 4. Save-кнопка — gradient + glow

`btn w-full h-14 rounded-2xl gm-gradient text-white font-semibold shadow-[0_4px_24px_rgba(98,94,252,0.35)]`. hx-сабмит-атрибуты формы не трогаем.

### Decision 5. Мягкий режим — рестайл без изменения логики

`alert alert-info` → карточка `bg-base-200 border border-info/30 rounded-[18px]` с иконкой `bell` (уже есть в баннере). Кнопки: «Только настроение» — `btn-primary btn-sm`, «Полная форма» — `btn-ghost btn-sm`. Гиперскрипт-тоггл `#soft-targets` не трогаем.

### Decision 6. Порядок полей — как в макете

Настроение → Энергия → Тревога → «дополнительно (необязательно)» → фокус/сон/заметка/активность → save. Совпадает с текущим порядком — только переименовать визуальный заголовок опционального блока в uppercase-стиль (текст ключа `:entries/optional` уже есть).

## Risks / Trade-offs

- [Тумб-стилизация range различается в webkit/firefox] → чиним оба префикса (`-webkit-slider-thumb`, `-moz-range-thumb`), проверка в Chromium Playwright (основной браузер e2e).
- [Потеря hyperscript-биндингов при переносе атрибутов] → атрибуты копировать дословно, после задачи проверять в браузере обновление значения при движении слайдера.
- [Glow-тень на тёмном фоне может выглядеть грязно] → проверка скрином не используется; визуальная оценка в браузере, при сомнении — ослабить второй слой свечения.

## Migration Plan

Визуальный слой, откат revert'ом. Риск регрессии — только в UX формы, e2e smoke покрывает сабмит.

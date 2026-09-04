# Tasks: redesign-check-in

> Инструкция исполнителю-агенту: работай автономно (см. `openspec/changes/_orchestration.md`).
> Перед стартом прочитай AGENTS.md, CODESTYLE.md, CLOJURE-RULES.md. Референс:
> `design/otmetka.html`. Требуется выполненный `redesign-tokens`. Комментарии в коде —
> на русском. КЛЮЧЕВОЕ ПРАВИЛО: hyperscript-атрибуты (`:_ ...`), hx-атрибуты и имена
> полей копируются дословно — меняются только классы и обёртки.

- [x] 1. Segmented control
  - Файлы: `src/app/views/check_in.clj` (`template-tabs`), референс otmetka строки 62–70
  - Контейнер `flex bg-base-300 rounded-2xl p-[5px] shadow-[inset_0_2px_4px_rgba(0,0,0,0.1)] mb-8`,
    разделители `w-px bg-base-content/10 my-2 mx-1` между кнопками
  - Активная кнопка: `bg-primary text-primary-content rounded-xl font-medium`, неактивная:
    `text-base-content/60 hover:text-base-content`; кнопки `flex-1 py-[10px] text-[13px]`
  - Hyperscript переключения и скрытый `#template-value` — без изменений
  - Проверка: Playwright MCP — клик по «Вечер» красит сегмент, скрытое поле получает
    `evening` (browser_evaluate на input#template-value); e2e smoke
- [x] 2. Glow-слайдеры (.gm-range)
  - Файлы: `src/app/views/layout.clj` (`<style>`: класс `.gm-range` с webkit/moz thumb),
    `src/app/views/check_in.clj` (`range-field`, `soft-fields` фокус-слайдер)
  - CSS: track `height:8px; border-radius:9999px; background: var(--color-base-300)`;
    thumb 20px белый, `box-shadow: 0 0 15px rgba(255,255,255,0.8), 0 0 25px rgba(94,92,230,0.8)`;
    оба префикса `-webkit-slider-thumb` и `-moz-range-thumb`
  - В check_in: слайдерам класс `gm-range` вместо `range range-primary`, убрать inline `:style height`
  - Строку значений 0/5/10 оставить, текущее значение выделить `text-base font-bold tabular-nums`
  - Проверка: Playwright MCP — движение слайдера обновляет число (hyperscript жив),
    тумб белый со свечением; клавиатурные стрелки работают
- [x] 3. Аккордеоны и секция «дополнительно»
  - Файлы: `src/app/views/check_in.clj` (`optional-block`, обёртка опциональной секции)
  - `collapse collapse-arrow bg-base-300 rounded-xl mb-2.5`, заголовок `text-sm font-medium min-h-0 py-3.5`
  - Заголовок секции: `text-[10px] uppercase font-bold tracking-wider text-base-content/50 mb-4`
  - Checkbox/hyperscript disabled-логика — дословно
  - Проверка: Playwright MCP — раскрытие «Заметка» разблокирует textarea, введённый текст
    уходит в сабмит (заполнить и сохранить запись целиком)
- [x] 4. Save-кнопка, шапка, мягкий режим, ширина
  - Файлы: `src/app/views/check_in.clj` (`form`, `page`, `soft-mode-banner`)
  - Save: `btn w-full h-14 rounded-2xl gm-gradient text-white font-semibold text-[15px]
    shadow-[0_4px_24px_rgba(98,94,252,0.35)] border-0`
  - Шапка: back `btn-ghost btn-circle` как есть, заголовок `text-[22px] font-bold`;
    обёртка страницы `max-w-2xl` → `max-w-md mx-auto`
  - Soft-banner: `bg-base-200 border border-info/30 rounded-[18px]` карточка с текущей
    иконкой; кнопки `btn-primary btn-sm` / `btn-ghost btn-sm`; hyperscript-тоггл — дословно
  - Проверка: Playwright MCP — полный цикл: чек-ин с low-настроением → баннер мягкого
    режима → «Только настроение» → save → редирект на ленту с тостом; полная форма тоже
- [x] 5. Финальная верификация и коммит
  - Полный `cd e2e && npx playwright test`; lint по изменённым файлам
  - Проверить обе локали страницы
  - git commit по стилю репозитория

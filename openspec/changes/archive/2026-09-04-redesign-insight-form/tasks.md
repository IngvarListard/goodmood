# Tasks: redesign-insight-form

> Инструкция исполнителю-агенту: работай автономно (см. `openspec/changes/_orchestration.md`).
> Перед стартом прочитай AGENTS.md, CODESTYLE.md, CLOJURE-RULES.md. Референс:
> `design/new-insight.html`. Требуется выполненный `redesign-tokens`. Комментарии в коде —
> на русском. КЛЮЧЕВОЕ ПРАВИЛО: id, hx-атрибуты, имена полей и hyperscript — дословно,
> меняются только классы и обёртки. up-arrow кнопки макета НЕ переносить (артефакт).

- [x] 1. Каркас формы и общие паттерны полей
  - Файлы: `src/app/views/insights.clj` (`new-page`)
  - Обёртка формы: `bg-base-200 rounded-[18px] p-5 pb-6`; ширина страницы `max-w-2xl` → `max-w-md mx-auto`
  - Паттерн инпута (копируемая строка): `w-full bg-base-300 border border-base-content/15
    rounded-xl px-4 py-3 text-sm text-base-content placeholder:text-base-content/40
    focus:outline-none focus:border-primary transition-colors`
  - Применить к: контекст (textarea h-24), state_label (select), категория (select),
    advice-текстареа, identity
  - Label: `block text-sm font-semibold mb-2`; подсказки: `text-xs text-base-content/60 mb-5`
  - Проверка: Playwright MCP — `/insights/new` рендерится, фокус подсвечивает бордер;
    селекты открываются нативно
- [x] 2. Динамические советы и кнопка добавления
  - Файлы: `src/app/views/insights.clj` (`advice-textarea`, `advice-item-fragment`,
    `advice-items-fragment`, разметка кнопки добавления в `new-page`)
  - Кнопка «Добавить пункт»: `flex items-center gap-2 px-4 py-2 rounded-full border
    border-base-content/15 hover:border-primary` + иконка `plus-circle` (добавить в
    `app.icons` если нет) + текст `text-sm text-base-content/60`
  - Кнопка удаления пункта: `btn btn-ghost btn-circle btn-sm` + иконка `x-mark`
    (muted, hover:text-error)
  - hx-атрибуты add/remove и OOB-свапы — дословно
  - Проверка: Playwright MCP — добавить 2 пункта, заполнить, удалить один, сохранить
    инсайт целиком; убеждаемся в сохранении значений оставшихся пунктов
- [x] 3. Highlight утверждения и save
  - Файлы: `src/app/views/insights.clj` (`new-page`)
  - Identity textarea: паттерн инпута + `border-primary shadow-[0_0_20px_rgba(99,102,241,0.15),0_0_40px_rgba(99,102,241,0.05)]`;
    в label — `(необязательно)` через `text-base-content/50 font-normal` (текст i18n-ключа уже содержит?)
    — проверить существующий ключ `:insights/identity-optional` или аналог, НЕ плодить дубли в edn
  - Save: `w-full py-4 rounded-xl gm-gradient text-white font-semibold tracking-wide`
  - Валидационная ошибка: существующий `validation-error-fragment` не менять, проверить свап
  - Проверка: Playwright MCP — сабмит пустой формы показывает ошибку в существующую цель;
    сабмит валидной формы сохраняет инсайт и редиректит
- [x] 4. Единый стиль edit-фрагментов
  - Файлы: `src/app/views/insights.clj` (`context-edit-fragment`, `advice-edit-fragment`,
    `identity-edit-fragment`)
  - Textarea этих фрагментов — тот же паттерн инпута (без highlight); разметку show-страницы не трогать
  - Проверка: Playwright MCP — на странице существующего инсайта включить редактирование
    контекста и утверждения, изменить, сохранить через htmx-свап
- [x] 5. Финальная верификация и коммит
  - Полный `cd e2e && npx playwright test`; lint по изменённым файлам
  - Обе локали формы; убедиться что список инсайтов и feed-widget не изменились
  - git commit по стилю репозитория

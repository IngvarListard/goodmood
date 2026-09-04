# Tasks: redesign-tokens

> Инструкция исполнителю-агенту: работай автономно, задачи выполняй по порядку через
> сабагентов (см. `openspec/changes/_orchestration.md`). Перед стартом прочитай
> AGENTS.md, CODESTYLE.md, CLOJURE-RULES.md. Дизайн-референсы: `design/lenta.html`,
> `design/otmetka.html`, `design/new-insight.html`. Комментарии в коде — на русском.

- [x] 1. Палитра тёмной темы в layout.clj
  - Файлы: `src/app/views/layout.clj` (только `<style>`-блок в `head`)
  - В блоке `[data-theme=dark]` задать полную карту daisyUI-переменных:
    `--color-base-100: #0c0f17`, `--color-base-200: #151827`, `--color-base-300: #1c2032`,
    `--color-base-content: #e6e8f0`, `--color-primary: #5b5bea`, `--color-primary-content: #ffffff`,
    `--color-secondary: #7c5ce0`, `--color-neutral: #1c2032`, `--color-success: #3fbf5f`,
    `--color-warning: #e8b33a`, `--color-error: #e05545`, `--color-info: #3b82f6`
  - Блок `[data-theme=light]` не трогать
  - Проверка: `clj -M -m app.core`, открыть `http://localhost:3000/feed` в Playwright MCP
    (логин e2e@goodmood.test / e2e-test-password-123), переключить тему на тёмную
    (cookie gm-theme=dark), убедиться что фон `#0c0f17`, карточки `#151827`, кнопки `#5b5bea`
    (computed styles), light-тема не изменилась
- [x] 2. Шрифт Inter
  - Файлы: `src/app/views/layout.clj` (`head`)
  - Добавить `<link rel="preconnect">` на fonts.googleapis.com и fonts.gstatic.com,
    `<link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&display=swap" rel="stylesheet">`
  - В `<style>`-блок: `body { font-family: 'Inter', system-ui, sans-serif; }` (вне `[data-theme]`-блоков)
  - Проверка: в браузере computed font-family на body — Inter; нет FOUT-мигания критичных страниц
- [x] 3. GM-переменные и хелпер-классы
  - Файлы: `src/app/views/layout.clj` (`<style>`-блок, вне `[data-theme]`-блоков)
  - Переменные: `--gm-state-danger-bg: #4a1f22`, `--gm-state-danger-text: #ef8a86`,
    `--gm-state-ok-bg: #1d3d28`, `--gm-state-ok-text: #7fd493`,
    `--gm-metric-energy: #f0c22e`, `--gm-metric-anxiety: #e05545`, `--gm-metric-focus: #3b82f6`
  - Классы: `.gm-badge-danger` (bg/текст/radius 12px), `.gm-badge-ok` (аналогично),
    `.gm-chip` (bg `#191d2c`, border `#282d42`, radius 12px, padding по макету lenta),
    `.gm-gradient` (linear-gradient 135deg, #6366f1 → #7c5ce0)
  - Комментарии в CSS — на русском, поясняют назначение
  - Проверка: временная проверочная страница не нужна — классы применятся задачами
    редизайна; проверить в браузере, что переменные доступны на `:root` (DevTools/evaluate)
- [x] 4. Bottom nav — единый активный паттерн
  - Файлы: `src/app/views/navigation.clj` (мобильный вариант `:mobile`)
  - Активный пункт: контейнер иконки `rounded-xl bg-primary/10` (на блоке иконки, не на `li`),
    точка-индикатор снизу `bg-primary` сохранить, текст активного — `text-primary`
  - Неактивные: `text-base-content/60`, hover по макету otmetka (`group-hover:text-white` эквивалент — `hover:text-base-content`)
  - Сохранить размер кликабельной зоны ≥44px по высоте и текущую структуру `nav-items`
  - Проверка: Playwright MCP — на `/feed` активна «Лента», на `/check-in` активна «Отметка»,
    свап по hx-boost не ломает выделение; мобильная вёрстка (узкое окно) — навигация внизу
- [x] 5. Финальная верификация
  - Запустить полный e2e: `cd e2e && npx playwright test` (webServer переиспользует запущенный app)
  - Прогнать lint по изменённым файлам согласно CODESTYLE.md
  - Скриншот-проверка не используется (дизайн не устоялся)
  - Зафиксировать результат: git commit по стилю репозитория

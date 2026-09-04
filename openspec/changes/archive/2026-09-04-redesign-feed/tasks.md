# Tasks: redesign-feed

> Инструкция исполнителю-агенту: работай автономно (см. `openspec/changes/_orchestration.md`).
> Перед стартом прочитай AGENTS.md, CODESTYLE.md, CLOJURE-RULES.md. Референс: `design/lenta.html`
> (все размеры/цвета — оттуда, палитра уже в токенах после redesign-tokens). Требуется
> выполненный `redesign-tokens`. Комментарии в коде — на русском. Сабагенту на задачу
> передавай: список файлов, референс-строки макета, критерий приёмки и проверку ниже.

- [x] 1. State-маппинг и утилиты карточки
  - Файлы: `src/app/views/feed.clj`, при отсутствии иконок — `src/app/icons.clj`
  - Константы маппинга state_label → бейдж: `low`/`anxiety` → `gm-badge-danger`,
    `ok`/`calm` → `gm-badge-ok`, прочее/nil → `bg-base-300 text-base-content`
  - Функция чипа метрики: иконка (energy `bolt`, anxiety `fire`, focus `eye`, mood `face-smile`
    — добавить в icons.clj если нет) + подпись + значение или «–», класс `.gm-chip`,
    цвет иконки из `--gm-metric-*` (inline style var() — допустимо)
  - Проверка: `clj -M -m app.core` компилируется; unit-вызов в REPL через nrepl возвращает hiccup-вектор
- [x] 2. Timeline-секции дней
  - Файлы: `src/app/views/feed.clj` (`past-day-section`, новый `timeline-day`)
  - Вертикальная линия `absolute w-0.5 bg-base-300`, точка дня (цвет из маппинга,
    `border-2 border-base-100`), дата-заголовок, карточки `ml-8`
  - Карточка записи: state-бейдж + время + иконка `ellipsis-horizontal` справа,
    ниже grid-cols-3 чипов из задачи 1
  - Заменить `compact-card` в секциях дней; `hero-card` (роза) — сохранить, обновить шапку
    (state-бейдж + время) в том же стиле
  - Проверка: Playwright MCP — лента с записями e2e-юзера показывает линию, точки
    разных цветов, чипы; сузить окно до mobile — вёрстка не плывёт; e2e smoke `npx playwright test tests/smoke.spec.ts`
- [x] 3. Empty state «Как ты сегодня?»
  - Файлы: `src/app/views/feed.clj` (`today-section` ветка пустых), `resources/i18n/{ru,en}.edn`
  - SVG-иллюстрация из макета lenta (строки 53–65: контур лица, глаза, улыбка, звёзды),
    stroke = `stroke-primary` (var), hiccup-вектор без `raw`
  - Кнопка: `btn-primary w-full h-14 rounded-2xl` + белый круглый бейдж с плюсом внутри
  - i18n-ключи `:feed/how-are-you`, `:feed/how-are-you-desc` (+ en), использовать `i18n/t`
  - Проверка: Playwright MCP — залогиниться свежим пользователем (создать через /register),
    пустая лента показывает карточку и кнопку, клик ведёт на /check-in; обе локали (cookie gm-locale)
- [x] 4. Пилюля активного периода
  - Файлы: `src/app/views/state_periods.clj` (`active-indicator`), референс lenta строки 38–49
  - Пилюля `.gm-gradient` с иконкой `bolt` и state-меткой периода; кнопка «Закрыть период»
    — `btn-outline btn-sm rounded-xl`; даты периода — 13px muted под пилюлей
  - hx-атрибуты и csrf текущего `active-indicator` сохранить без изменений
  - Проверка: Playwright MCP — создать период POST /state-periods/start (или через UI),
    пилюля с градиентом, закрытие работает; e2e smoke
- [x] 5. AI-инсайт-карточка + график недели
  - Файлы: `src/app/views/ai.clj` (`ai-advice`, `ai-correlations` — стиль обёртки),
    `src/app/views/feed.clj` (новый `week-chart`)
  - Обёртка: `rounded-[18px] border border-primary/50 bg-base-200`, лампочка `light-bulb`
    в кружке `bg-primary/10`, бейдж уверенности (outline, amber — `text-warning border-warning`)
  - `week-chart`: из `entries` последние 7 дней по дате, SVG viewBox="0 0 300 34",
    polyline + круги r=6, пороги: ≤3 `fill-success`, 4–6 `fill-warning`, ≥7 `fill-error`;
    день без данных — контурный круг; <2 точек — fallback-текст (i18n `:ai/no-week-data`, +en)
  - Подписи дней недели (Пн…Вс) — i18n-ключи `:feed/wd-1..7` (+en), переиспользовать если уже есть
  - Проверка: Playwright MCP с e2e-юзером (есть истории) — график рендерится; у пользователя
    с пустой неделей — fallback; секции AI (адвайс/корреляции) визуально в карточках; OOB-свапы
    `#ai-chat-open` работают после переработки
- [x] 6. Финальная верификация и коммит
  - Полный `cd e2e && npx playwright test`; lint по изменённым файлам
  - Проверить обе локали и soft-order (запись low поднимает виджет инсайта выше hero — не сломать)
  - git commit по стилю репозитория

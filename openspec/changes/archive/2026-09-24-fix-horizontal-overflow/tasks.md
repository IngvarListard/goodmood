# Tasks: fix-horizontal-overflow

> Инструкция исполнителю: работай автономно (см.
> `openspec/changes/_orchestration.md`). Перед стартом прочитай AGENTS.md,
> CODESTYLE.md, CLOJURE-RULES.md. Комментарии в коде — на русском. Правки
> `.clj` — структурными тулами clojure-mcp (`clojure_edit`,
> `clojure_edit_replace_sexp`). Поведение и данные НЕ менять: только
> классы/структура hiccup-разметки против горизонтального переполнения.

- [x] 1.1 Пилюля активного периода
  - Файлы: `src/app/views/state_periods.clj` (`active-indicator`)
  - Приёмка: pill имеет `min-w-0 flex-1`, подпись `(:label period)` —
    `truncate`, кнопка «Закрыть период» — `shrink-0` и полностью видна;
    `hx-*`-атрибуты и csrf не изменены
  - Проверка: `(require 'app.views.state-periods :reload)` без ошибок;
    Playwright 360px с названием ~60 символов → нет горизонтального скролла,
    кнопка видна

- [x] 1.2 Список периодов
  - Файлы: `src/app/views/state_periods.clj` (`period-list`)
  - Приёмка: длинное название переносится (`break-words`) и не расширяет
    контентную колонку
  - Проверка: `(require 'app.views.state-periods :reload)`; Playwright 360px
    с длинным названием в списке → нет горизонтального скролла

- [x] 2.1 Hero-карточка и строка осей
  - Файлы: `src/app/views/feed.clj` (`hero-card`, `axes-line`)
  - Приёмка: заметка/подписи внутри `min-w-0`-контейнера, многострочный
    текст — `break-words`/`line-clamp`, `axes-line` — `truncate`
  - Проверка: `(require 'app.views.feed :reload)`; Playwright 360px с длинной
    заметкой → нет горизонтального скролла

- [x] 2.2 Карточка записи и чипы
  - Файлы: `src/app/views/feed.clj` (`entry-card`, `metric-chip`,
    `entry-label`)
  - Приёмка: ярлык состояния в шапке обрезается (`min-w-0` + `truncate`),
    чипы не растягивают grid, время и иконка меню видимы
  - Проверка: `(require 'app.views.feed :reload)`; Playwright 360px со
    свободным длинным `state_label` → нет горизонтального скролла

- [x] 3.1 Read-блок записи
  - Файлы: `src/app/views/entries.clj` (`read-view`)
  - Приёмка: заметка и активность — `break-words`; ярлык и строка осей —
    `truncate` внутри `min-w-0`
  - Проверка: `(require 'app.views.entries :reload)`; Playwright 360px
    `/entries/:id` с длинной заметкой → нет горизонтального скролла

- [x] 3.2 Карточка списка записей
  - Файлы: `src/app/views/entries.clj` (`list-card`)
  - Приёмка: ярлык и `axes-line` обрезаются, карточка не расширяет колонку
  - Проверка: `(require 'app.views.entries :reload)`; Playwright 360px
    `/entries` с длинным ярлыком → нет горизонтального скролла

- [x] 4.1 Инсайты
  - Файлы: `src/app/views/insights.clj` (`insight-list-card`, read-блоки,
    `feed-widget`)
  - Приёмка: пользовательский текст контекста и советов переносится внутри
    карточки
  - Проверка: `(require 'app.views.insights :reload)`; Playwright с длинным
    текстом инсайта → нет горизонтального скролла

- [x] 4.2 AI-вывод и чат
  - Файлы: `src/app/views/ai.clj` (`chat-bubble`, `chat-response`,
    `ai-advice`, `ai-correlations`), `src/app/views/assistant.clj` (`modal`)
  - Приёмка: длинные строки AI переносятся/обрезаются внутри своей
    карточки или бабла
  - Проверка: `(require 'app.views.ai :reload)`; Playwright с длинным
    AI-текстом → нет горизонтального скролла

- [x] 4.3 Медикаменты
  - Файлы: `src/app/views/medications.clj` (`med-card`, `inactive-card`,
    `intake-slot`)
  - Приёмка: длинное название медикамента переносится/обрезается, карточка
    не расширяет колонку
  - Проверка: `(require 'app.views.medications :reload)`; Playwright 360px с
    длинным названием → нет горизонтального скролла

- [x] 5.1 Сквозная проверка overflow
  - Приёмка: на 360px для длинного названия периода, длинной заметки записи
    и длинной строки инсайта/чата
    `document.documentElement.scrollWidth <=
    document.documentElement.clientWidth`; обе локали (ru/en) и обе темы
    (light/dark)
  - Проверка: ручной прогон сценариев через Playwright MCP;
    `curl http://localhost:3000/` отвечает 200

- [x] 5.2 Регрессия htmx и финал
  - Приёмка: закрытие периода, редактирование/удаление записи, инсайты и
    чат работают как раньше (hx/csrf не сломаны)
  - Проверка: `cd e2e && npm run test:fast`; приложение стартует `./bin/dev`;
    `git commit` по стилю репозитория
## Why

В проекте используется `hiccup/hiccup` v2, но часть файлов всё ещё импортирует устаревший `hiccup.core` вместо актуального `hiccup2.core`. В v2 `hiccup.core/html` deprecated — он не эскейпит строки автоматически, что может привести к XSS-уязвимостям. Кроме того, в `icons.clj` используется `h2/raw` через alias, а не прямой `:refer [raw]`, что нарушает единообразие.

## What Changes

- `src/app/routes/auth.clj`: `[hiccup.core :as hc]` → `[hiccup2.core :refer [html]]`, `(hc/html ...)` → `(html ...)`
- `src/app/routes/app.clj`: `[hiccup.core :as hc]` → `[hiccup2.core :refer [html]]`, `(hc/html ...)` → `(html ...)`
- `src/app/routes/entries.clj`: `[hiccup.core :as hc]` → `[hiccup2.core :refer [html]]`, `(hc/html ...)` → `(html ...)`
- `src/app/icons.clj`: `[hiccup2.core :as h2]` → `[hiccup2.core :refer [html raw]]`, `(h2/raw ...)` → `(raw ...)`

Никаких изменений поведения или функциональности — только импорты и вызовы.

## Capabilities

### New Capabilities

Нет — это технический рефакторинг, не новая фича.

### Modified Capabilities

Нет.

## Impact

Затрагивает 4 файла в `src/app/`. Никаких изменений схемы БД, API-контрактов или зависимостей. После рефакторинга нужно проверить, что приложение компилируется и страницы рендерятся корректно.
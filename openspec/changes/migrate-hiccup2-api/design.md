## Context

В проекте используется `hiccup/hiccup {:mvn/version "2.0.0"}`. Пакет предоставляет два API:

- `hiccup.core/html` — старый API, в v2 deprecated, **не эскейпит строки** (XSS-риск)
- `hiccup2.core/html` — новый API, эскейпит строки по умолчанию
- `hiccup2.core/raw` — для сознательного вставки сырого HTML (SVG и т.п.)

Три файла в `routes/` используют `hiccup.core`, один файл `icons.clj` использует `hiccup2.core` через alias `h2`.

## Goals / Non-Goals

**Goals:**
- Заменить все вхождения `hiccup.core :as hc` на `hiccup2.core :refer [html]`
- Заменить `hiccup2.core :as h2` на `hiccup2.core :refer [html raw]` в icons.clj
- Везде использовать прямой `:refer` вместо алиасов (идиома Clojure-сообщества)

**Non-Goals:**
- Не менять логику рендеринга
- Не добавлять/убирать функциональность
- Не трогать view-файлы (они не импортируют hiccup — возвращают чистые данные)

## Decisions

| Решение | Альтернатива | Почему |
|---|---|---|
| `:refer [html raw]` вместо `:as hc` / `:as h2` | `:as` с алиасом | `:refer` идиоматичнее в Clojure-сообществе для 1-2 функций, улучшает читаемость |
| `raw` для SVG в icons.clj | — | `raw` — единственный корректный способ вставить сырой HTML (SVG) через hiccup2 |

## Risks / Trade-offs

- Риск: случайно не заметить `h2/raw` в icons.clj при замене → Mitigation: явно добавить `raw` в `:refer` и заменить вызов
- Изменения только в ns-формах и вызовах — регресс маловероятен
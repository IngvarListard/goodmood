## Why

Текущие конвенции в `AGENTS.md` и `CLOJURE-RULES.md` не содержат явных указаний для AI-агентов:
1. Какой импорт hiccup использовать (`hiccup2.core` с `:refer` вместо deprecated `hiccup.core`).
2. Когда писать docstrings (нетривиальные публичные функции).
3. В результате агенты (та самая «другая нейронка») пишут код с устаревшим API и без документации.

## What Changes

- `AGENTS.md`: добавить секцию «Импорты» с правилом про `hiccup2.core :refer [html raw]`
- `AGENTS.md`: добавить правило про docstrings — нетривиальные публичные функции
- `CLOJURE-RULES.md`: в секцию про Hiccup v2 добавить явный import pattern
- Добавить docstrings к нетривиальным публичным функциям в коде

## Capabilities

### New Capabilities

Нет — это обновление проектных конвенций.

### Modified Capabilities

Нет.

## Impact

- Файлы конвенций: `AGENTS.md`, `CLOJURE-RULES.md`
- Исходный код: docstrings в публичных функциях (без изменения поведения)
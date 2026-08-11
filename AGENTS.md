# AGENTS.md

# Проект

трекер настроения при биполярном расстройстве.

# Стек

Clojure, deps.edn, ring + ring-jetty-adapter, reitit, integrant, hiccup2, htmx, hyperscript, Tailwind + DaisyUI, SQLite, next.jdbc, HoneySQL, migratus.

# Конвенции

- src/app/{db,routes,views,domains}/..., явные reitit-роуты, простые hiccup2-функции без сложных макросов.
- при добавлении новых зависимостей в проект убедиться, что это последняя версия пакета если не сказано другого

# Codestyle
codestyle см. в ./CODESTYLE.md

# Фронтенд

При работе над фронтендом используй tailwind и dailsyui классы
При реализации любых ui компонентов используй компоненты tailwind daisyui. Написание своих компонентов крайний случай

## Иконки

Иконки находятся по пути ./resources/icons/. Объем иконок большой. Используй grep для поиска

# Запуск

```bash
# Запуск приложения
clj -M -m app.core

# Проверка health-check
curl http://localhost:3000/
```

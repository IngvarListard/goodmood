# AGENTS.md

# Проект

трекер настроения при биполярном расстройстве.

# Стек

Clojure, deps.edn, ring + ring-jetty-adapter, reitit, integrant, hiccup2, htmx, hyperscript, Tailwind + DaisyUI, SQLite, next.jdbc, HoneySQL, migratus.

# Конвенции

src/app/{db,routes,views,domains}/..., явные reitit-роуты, простые hiccup2-функции без сложных макросов.

# Запуск

```bash
# Запуск приложения
clj -M -m app.core

# Проверка health-check
curl http://localhost:3000/
```

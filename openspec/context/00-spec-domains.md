# Карта спец-доменов

Главные спеки в `openspec/specs/` организованы **по областям поведения** (feature / bounded context), а не по слоям реализации. Одна фича может менять несколько доменов; один домен накапливает многие фичи. При архивации дельта-спека изменения вливается в существующий домен — новый домен создаётся только когда появляется действительно новая область поведения.

## Актуальный набор доменов (9)

| Домен            | Что в него входит                                        |
|------------------|----------------------------------------------------------|
| `entries`        | Запись дневника: форма, check-in, feed, mood/rose, все поля записи, гранулярность, периоды, mobile, API, БД-слой |
| `medications`    | Приём препаратов                                         |
| `insights`       | Инсайт-артефакты: контекст, советы себе, привязка к state-меткам |
| `users-auth`     | Аккаунт, вход, сессии, CSRF, RBAC, route protection, identity в request, entry scoping |
| `i18n`           | Локализация (ru/en)                                      |
| `ui-shell`       | Навигация, layout, заглушки, оформление                  |
| `platform`       | Инфраструктура: http-server, db-connection, db-migrations, слоистость исходника (project-structure) |
| `ai-assistant`   | AI: корреляции, ярлыки, советы, чат, предупреждения эпизодов, guardrails, feedback |
| `coping-channels`| Каналы доставки копинга: in-app уведомления (3 слота), toast, вечерние сводки, soft mode, настройки уведомлений |

## История реструктуризации (2026-08-29)

Реструктуризация проведена через 3 change'а:

```
merge-entries-domain     6→1  (entries-api + entries-data + entries-ui
                                + entry-granularity + mobile-entry + mood-states → entries)
merge-platform-and-shell 4→1, 3→1 (http-server + db-connection + db-migrations
                                   + project-structure → platform;
                                   layout + navigation + placeholder-pages → ui-shell)
merge-users-auth         2→1  (users-auth + access-control → users-auth)
```

## Принципы

- Домен = область поведения (feature area / bounded context), а НЕ слой (api/data/ui) и НЕ разовый пропозал.
- Пропозал по умолчанию модифицирует существующие домены, а не заводит новый.
- Гранулярность пропорциональна размеру системы: трекеру настроения хватает ~9 устойчивых доменов.

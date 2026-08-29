## Why

В репозитории нет README — новый разработчик (или AI-агент) не знает, что это за проект, как его запустить, какие env-переменные нужны. AGENTS.md не упоминает половину фич (AI-чат, episode warnings, notifications, i18n, миграции) и не описывает env-переменные вообще, из-за чего возникают silent failures (например, чат молчит без `OPENROUTER_API_KEY`).

## What Changes

- **NEW**: `README.md` — краткое описание проекта, стек, как запустить, env-переменные, как запускать тесты, список основных фич.
- **UPDATE**: `AGENTS.md` — добавить:
  - Секцию env-переменных (`GOODMOOD_SESSION_SECRET`, `OPENROUTER_API_KEY`)
  - Краткий обзор фич/роутов (домены: entries, feed, check-in, medications, insights, state periods, notifications, AI, settings)
  - Упоминание i18n (`app.i18n`, локали ru/en, `t`-функция)
  - Упоминание миграций (`resources/migrations/`, migratus, автозапуск при старте)
  - Упоминание AI-домена (OpenRouter, opt-in, graceful nil при отсутствии ключа)

## Scope

- `README.md` — новый файл, короткий (1-2 экрана)
- `AGENTS.md` — обновить существующие секции + добавить новые

## Non-goals

- Не пишем полную архитектурную документацию (это в `openspec/specs/`)
- Не описываем каждый роут подробно (это в коде)
- Не создаём CONTRIBUTING.md / CHANGELOG.md

## Capabilities

### New Capabilities
<!-- нет — это документация, не спецификация поведения -->

### Modified Capabilities
<!-- нет — спек-уровень поведения не меняется -->

## Impact

- `README.md` — новый файл
- `AGENTS.md` — обновление
- Не затрагивает код, зависимости, БД

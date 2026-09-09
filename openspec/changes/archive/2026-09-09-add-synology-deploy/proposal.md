# Proposal: add-synology-deploy

## Why

Приложение работает только локально на ноутбуке разработчика. Есть Synology DS224+ (x86_64) с Docker/Portainer, доступный по SSH, уже используемый для других сервисов (FreshRSS) с reverse proxy наружу. Нужен воспроизводимый путь «сборка → деплой → обновление» в одну команду, включая безопасное обновление схемы БД (миграции) и бэкап данных перед деплоем.

## What Changes

- Сборка uberjar через tools.build (`clj -T:build uberjar`) + alias `:build` в deps.edn.
- Docker-образ на базе `eclipse-temurin:25-jre` (non-root, HEALTHCHECK, `-Xmx384m`), собирается локально на ноутбуке (архитектура совпадает — x86_64), доставка на NAS через `docker save | ssh docker load`.
- **BREAKING**: миграции squash — 13 существующих миграций схлопываются в одну `001-init.up.sql` / `001-init.down.sql`. Существующая dev-БД (`resources/goodmood.db`) удаляется и пересоздаётся с нуля (важных данных нет). После первого деплоя squash-переезды запрещены, миграции — append-only.
- Приложение конфигурируется env-переменными с подхватом из `.env`-файла через dotenv-библиотеку: `GOODMOOD_PORT`, `GOODMOOD_DB_PATH` добавляются к существующим `GOODMOOD_SESSION_SECRET`, `GOODMOOD_ADMIN_EMAIL`, `GOODMOOD_ADMIN_PASSWORD`, `OPENROUTER_API_KEY`.
- `system.clj`: порт и путь к БД читаются из env (с дефолтами для локальной разработки).
- `bin/dev` — локальный старт с загрузкой `.env`; `.env` gitignored, коммитится `.env.example`; файл `OPENROUTER_API_KEY` в корне репозитория заменяется на `.env`.
- `bin/deploy.sh` — деплой одной командой: uberjar → docker build → stop → бэкап БД (держим 10 последних) → docker load → up → health-check.
- compose.yaml + `.env.example` для деплоя на NAS: bind mount `/volume1/docker/goodmood/data` для БД, `backups/` рядом, `env_file: .env`.
- **BREAKING**: при пересоздании БД с нуля seed всегда создаёт одного пользователя с фиксированными кредами (из `GOODMOOD_ADMIN_EMAIL`/`GOODMOOD_ADMIN_PASSWORD`, дефолт `admin@goodmood.local`) и наполняет БД тестовыми данными (рыба: несколько записей состояния за последние дни, пара инсайтов, медикаменты) — для визуальной проверки после каждого чистого подъёма.
- Правила миграций фиксируются в AGENTS.md (append-only, .up/.down в паре, деструктив — только новой миграцией).
- Доступ наружу через существующий reverse proxy DSM (правило `goodmood.<домен> → localhost:3000`) — настройка на NAS вручную, вне репозитория.

## Capabilities

### New Capabilities
- `deployment`: сборка дистрибутива (uberjar + docker-образ), деплой на Synology по SSH (stop → backup → load → up → health-check), структура файлов деплоя (Dockerfile, compose.yaml, .env.example), бэкап/rollback БД, health-check.
- `dev-env`: подхват конфигурации из `.env`-файла для локальной разработки (dotenv), env-переменные `GOODMOOD_PORT`/`GOODMOOD_DB_PATH`, `bin/dev`.
- `seed-data`: детерминированный seed при первом запуске на пустой БД — фиксированный админ-юзер + тестовые данные (записи, инсайты, медикаменты) для визуальной проверки.

### Modified Capabilities
- `platform`: требование «SQLite datasource» дополняется — путь к БД и порт сервера конфигурируются env-переменными (вместо захардкоженных значений).
- `users-auth`: требование «Admin seed at startup» уточняется — seed обязателен при пустой БД (в т.ч. после пересоздания), с фиксированными кредами из env; добавляется создание тестовых данных.

## Impact

- **Код**: `src/app/system.clj` (env: порт, путь БД), `src/app/domains/users.clj` или новый seed-неймспейс (тестовая рыба), `deps.edn` (alias `:build`, dotenv-зависимость), `.gitignore` (`.env`).
- **Новые файлы**: `build.clj`, `deploy/Dockerfile`, `deploy/compose.yaml`, `deploy/.env.example`, `bin/dev`, `bin/deploy.sh`, `resources/migrations/001-init.{up,down}.sql` (squash).
- **Удаляется**: 13 существующих миграций (26 файлов), dev-БД `resources/goodmood.db` (+wal/shm), файл `OPENROUTER_API_KEY` в корне.
- **AGENTS.md**: секция «Правила миграций при деплое», обновление секции «Запуск».
- **NAS (вручную, вне репо)**: docker group для ssh-юзера (или sudo в скрипте), stack в Portainer, правило reverse proxy DSM, Let's Encrypt.
- **Зависимости**: tools.build (dev), dotenv-библиотека (выбрать актуальную при имплементации: environ / dotenv / cprop).

## Scope

Внутри: сборка, образ, деплой-скрипт, squash миграций, env-конфигурация, seed с рыбой, правила миграций в AGENTS.md.

Non-goals: CI/CD (нет), zero-downtime деплой (single-user, даунтайм 5–10 сек ок), HTTPS на уровне приложения (терминирует DSM reverse proxy), GraalVM native-image (не нужен), бэкапы за пределами NAS (off-site), автоподнятие на NAS из git (образ доставляется с ноутбука), ежедневный scheduled-бэкап (можно добавить позже через Task Scheduler DSM).

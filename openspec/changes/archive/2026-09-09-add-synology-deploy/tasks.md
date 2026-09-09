# Tasks: add-synology-deploy

## 1. Env-конфигурация и .env

- [x] 1.1 Выбрать актуальную dotenv-библиотеку с Clojars, добавить в deps.edn; проверить последнюю версию
- [x] 1.2 Неймспейс `app.env`: загрузка `.env` (KEY=VALUE, комментарии), merge поверх `System/getenv` (env сильнее файла), отсутствие `.env` не ошибка
- [x] 1.3 `system.clj`: `GOODMOOD_PORT` (default 3000) и `GOODMOOD_DB_PATH` (default `resources/goodmood.db`) из env; `GOODMOOD_SESSION_SECRET` через общий env
- [x] 1.4 Создать `.env.example` (SESSION_SECRET, ADMIN_EMAIL, ADMIN_PASSWORD, OPENROUTER_API_KEY, PORT, DB_PATH), добавить `.env` в .gitignore, удалить файл `OPENROUTER_API_KEY` из корня
- [x] 1.5 Создать `bin/dev` (исполняемый: source `.env` если есть + `clj -M:dev`) и проверить локальный запуск: приложение на :3000, health-check 200, юзер и рыба на чистой БД (после шага 2)
- [x] 1.6 Прогнать тесты `clj -M:test` — все зелёные

## 2. Squash миграций + seed-рыба

- [x] 2.1 Собрать `001-init.up.sql` конкатенацией 13 up-миграций по порядку; `001-init.down.sql` — дроп всех таблиц в обратном порядке
- [x] 2.2 Удалить 13 старых миграций (26 файлов) и dev-БД (`resources/goodmood.db`, -wal, -shm)
- [x] 2.3 Проверить подъём с нуля: `clj -M:dev` на чистой БД применяет 001-init, схема эквивалентна прежней (таблицы/индексы), health-check 200
- [x] 2.4 Seed: при пустой таблице users — админ из `GOODMOOD_ADMIN_EMAIL`/`GOODMOOD_ADMIN_PASSWORD` (дефолт admin@goodmood.local) + рыба: несколько записей состояния за последние дни, ≥1 инсайт, ≥1 медикамент с расписанием; при существующих юзерах — ничего
- [x] 2.5 Проверить: удаление БД → рестарт → тот же юзер + рыба; рестарт без удаления → дублей нет
- [x] 2.6 Прогнать e2e smoke (`cd e2e && npm run test:fast`) — зелёные

## 3. Сборка и Docker-образ

- [x] 3.1 Добавить `:build` alias (io.github.clojure/tools.build, последняя версия) в deps.edn; написать `build.clj` (uberjar с main `app.core` в `target/`)
- [x] 3.2 Проверить: `clj -T:build uberjar` → `java -jar target/goodmood-*.jar` стартует, health-check 200 (порт/путь из env)
- [x] 3.3 Написать `deploy/Dockerfile`: eclipse-temurin:25-jre, non-root, COPY uberjar, `-Xmx384m`, HEALTHCHECK по GET /, env-дефолты для контейнера
- [x] 3.4 Проверить образ локально: `docker build` → `docker run` с временной папкой для БД и тестовым env → health-check, миграции с нуля, рыба на месте

## 4. Деплой на Synology

- [x] 4.1 Подготовить NAS (вручную/скриптом): docker group для ssh-юзера (или решение по sudo), каталоги `/volume1/docker/goodmood/{data,backups}`, `.env` на NAS (chmod 600) с прод-секретами
- [x] 4.2 Написать `deploy/compose.yaml`: образ goodmood, `env_file: .env`, bind mounts data/backups, `restart: unless-stopped`, порт 3000
- [x] 4.3 Написать `bin/deploy.sh`: uberjar → docker build (тег `<date>-<sha7>`) → ssh stop → ssh cp бэкап db+wal+shm с retention 10 → `docker save | ssh docker load` → ssh up → health-check через https://goodmood.<домен> (или localhost:3000 на NAS), non-zero при провале
- [x] 4.4 Первый деплой: `bin/deploy.sh` → на NAS поднялось, юзер + рыба на месте, health-check зелёный
- [x] 4.5 Reverse proxy DSM: правило <public-host> → localhost:32710, wildcard-сертификат; вход проверен из LAN (снаружи 404 — роутер пробрасывает 443 не на NAS, вне скоупа репо)
- [x] 4.6 Проверить rollback: предыдущий тег в compose → up; restore бэкапа при остановленном контейнере → up
- [x] 4.7 (Опционально) Импорт stack в Portainer — пропущено: стеку Portainer не требуется

## 5. Документация

- [x] 5.1 AGENTS.md: секция «Правила миграций» (append-only, .up/.down в паре, деструктив новой миграцией, тест на чистой БД), обновить «Запуск» (bin/dev, .env), секция «Деплой» (bin/deploy.sh, бэкап/rollback)
- [x] 5.2 Финальный полный прогон: `clj -M:test` + `cd e2e && npm run test` перед коммитом

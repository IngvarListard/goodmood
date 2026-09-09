#!/usr/bin/env bash
# Деплой goodmood на Synology: сборка uberjar -> docker build -> передача тара
# -> одна ssh -tt сессия со всеми sudo-шагами (stop, backup, load, up)
# -> health-check.
#
# Использование:
#   NAS_HOST=ssh://<ssh-user>@<nas-lan-ip>:37132 ./bin/deploy.sh
# Пароль sudo запрашивается один раз интерактивно внутри ssh -tt сессии
# (tty не искажает бинарные данные — тар передаётся отдельным каналом).
# Переменные:
#   NAS_HOST       (обяз.) ssh-назначение, напр. ssh://user@host:port
#   NAS_BASE_DIR   база на NAS (default: /volume1/docker/goodmood)
#   NAS_HTTP_PORT  http-порт стека на NAS для health-check (default: 32710)
#   KEEP_BACKUPS   сколько бэкапов хранить (default: 10)
set -euo pipefail

NAS_HOST=${NAS_HOST:?Укажи NAS_HOST=ssh://user@host:port}
NAS_BASE_DIR=${NAS_BASE_DIR:-/volume1/docker/goodmood}
NAS_HTTP_PORT=${NAS_HTTP_PORT:-32710}
KEEP_BACKUPS=${KEEP_BACKUPS:-10}

TAG="$(date +%Y%m%d-%H%M)-$(git rev-parse --short HEAD)"
TAR_LOCAL="/tmp/goodmood-$TAG.tar"
TAR_REMOTE="/tmp/goodmood-deploy.tar"

echo "── 1/6 Собираю uberjar (clj -T:build uberjar)"
clj -T:build uberjar

echo "── 2/6 Собираю образ goodmood:$TAG"
docker buildx build -f deploy/Dockerfile -t "goodmood:$TAG" -t goodmood:latest .

echo "── 3/6 Передаю образ на NAS"
docker save -o "$TAR_LOCAL" "goodmood:$TAG" goodmood:latest
# stdout-only канал (без tty, бинарные данные не искажаются)
ssh "$NAS_HOST" "cat > '$TAR_REMOTE'" < "$TAR_LOCAL"
rm -f "$TAR_LOCAL"

echo "── 4/6 stop + бэкап БД + load + up (пароль sudo один раз)"
# Всё root- work в ОДНОЙ сессии: sudo кэширует credentials на этот tty.
ssh -tt "$NAS_HOST" "sudo -p 'sudo (NAS): ' sh -c '
  set -e
  cd '$NAS_BASE_DIR'
  /usr/local/bin/docker compose stop goodmood || true
  mkdir -p backups
  if compgen -G \"data/goodmood.db*\" > /dev/null; then
    mkdir -p \"backups/$TAG\"
    cp -a data/goodmood.db* \"backups/$TAG/\"
    ls -1t backups | grep -v @eaDir | tail -n +$((KEEP_BACKUPS + 1)) \
      | while read -r f; do rm -rf \"backups/\$f\"; done
  fi
  /usr/local/bin/docker load -i \"$TAR_REMOTE\"
  rm -f \"$TAR_REMOTE\"
  GOODMOOD_TAG='$TAG' /usr/local/bin/docker compose up -d
'"

echo "── 5/6 Жду health-check (:${NAS_HTTP_PORT})"
ok=""
for _ in $(seq 1 30); do
  if ssh "$NAS_HOST" "curl -fsS -o /dev/null http://localhost:${NAS_HTTP_PORT}/"; then
    ok=1
    break
  fi
  sleep 2
done

if [ -z "$ok" ]; then
  echo "ОШИБКА: health-check не прошёл. Откат: GOODMOOD_TAG=<пред.тег> sudo docker compose up -d; при порче данных — restore из '$NAS_BASE_DIR/backups'." >&2
  exit 1
fi

echo "✓ Задеплоено goodmood:$TAG"

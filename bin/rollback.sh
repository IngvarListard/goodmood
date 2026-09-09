#!/usr/bin/env bash
# Откат goodmood на предыдущий образ (и опционально данных БД).
#
#   NAS_HOST=ssh://<ssh-user>@<nas-lan-ip>:37132 ./bin/rollback.sh            # список тегов
#   NAS_HOST=ssh://<ssh-user>@<nas-lan-ip>:37132 ./bin/rollback.sh 20260908-2333-f6a7b68
#   NAS_HOST=... ./bin/rollback.sh 20260908-2333-f6a7b68 --data           # + restore БД из бэкапа тега
#
# Пароль sudo запрашивается один раз (одна ssh -tt сессия).
set -euo pipefail

NAS_HOST=${NAS_HOST:?Укажи NAS_HOST=ssh://user@host:port}
NAS_BASE_DIR=${NAS_BASE_DIR:-/volume1/docker/goodmood}
NAS_HTTP_PORT=${NAS_HTTP_PORT:-32710}
TAG=${1:-}
RESTORE_DATA=${2:-}

if [ -z "$TAG" ]; then
  echo "Теги goodmood на NAS (свежие сверху):"
  ssh -tt "$NAS_HOST" "sudo -p 'sudo (NAS): ' /usr/local/bin/docker images goodmood --format '  {{.Tag}}  ({{.CreatedSince}})' | grep -v '<none>'"
  echo "Откат: $0 <тег> [--data]"
  exit 0
fi

if [ "$RESTORE_DATA" = "--data" ]; then
  echo "⚠ Restore БД из backups/$TAG/ — ТЕКУЩИЕ данные будут заменены."
  read -rp "Продолжить? [y/N] " a
  [ "$a" = "y" ] || exit 1
fi

ssh -tt "$NAS_HOST" "sudo -p 'sudo (NAS): ' sh -c '
  set -e
  cd '$NAS_BASE_DIR'
  /usr/local/bin/docker compose stop goodmood || true
  if [ \"$RESTORE_DATA\" = \"--data\" ]; then
    test -d backups/'\"$TAG\"' || { echo \"Нет бэкапа backups/'\"$TAG\"';\"; exit 1; }
    rm -f data/goodmood.db data/goodmood.db-wal data/goodmood.db-shm
    cp -a backups/'\"$TAG\"'/goodmood.db* data/
    echo \"data restored from backups/'\"$TAG\"'\"
  fi
  GOODMOOD_TAG='$TAG' /usr/local/bin/docker compose up -d
'"

echo "── Жду health-check (:${NAS_HTTP_PORT})"
ok=""
for _ in $(seq 1 30); do
  if ssh "$NAS_HOST" "curl -fsS -o /dev/null http://localhost:${NAS_HTTP_PORT}/"; then
    ok=1; break
  fi
  sleep 2
done
[ -n "$ok" ] && echo "✓ Откат на goodmood:$TAG выполнен" || { echo "ОШИБКА health-check" >&2; exit 1; }

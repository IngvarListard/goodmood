#!/usr/bin/env bash
# Одноразовая настройка NAS для goodmood (запускается с ноутбука):
#   NAS_HOST=ssh://<ssh-user>@<nas-lan-ip>:37132 ./deploy/nas-setup.sh
# Делает: каталоги data/backups, права для uid 10001 (non-root в контейнере),
# заливает compose.yaml и .env (из deploy/nas.env, chmod 600).
# sudo запрашивается интерактивно (ssh -tt), пароль спросит дважды.
set -euo pipefail

NAS_HOST=${NAS_HOST:?Укажи NAS_HOST=ssh://user@host:port}
NAS_BASE_DIR=${NAS_BASE_DIR:-/volume1/docker/goodmood}
ENV_FILE=${ENV_FILE:-deploy/nas.env}

[ -f "$ENV_FILE" ] || { echo "Нет $ENV_FILE — создай по образцу deploy/.env.example"; exit 1; }

# DSM-sshd без sftp-сабсистемы, scp не годится — льём файлы пайпом через ssh
scp_to() { # $1 = путь на NAS, $2 = локальный файл
  ssh "$NAS_HOST" "cat > '$1'" < "$2"
}

echo "── Каталоги + права"
ssh -tt "$NAS_HOST" "sudo -p 'sudo (NAS): ' sh -c '
  set -e
  mkdir -p '$NAS_BASE_DIR'/data '$NAS_BASE_DIR'/backups
  chown -R 10001:10001 '$NAS_BASE_DIR'/data
  chmod 755 '$NAS_BASE_DIR'/data
  chmod 755 '$NAS_BASE_DIR' '$NAS_BASE_DIR'/backups
' && echo ok"

echo "── compose.yaml + .env на NAS"
scp_to /tmp/goodmood-compose.yaml deploy/compose.yaml
scp_to /tmp/goodmood-nas.env "$ENV_FILE"
ssh -tt "$NAS_HOST" "sudo -p 'sudo (NAS): ' sh -c '
  set -e
  mv /tmp/goodmood-compose.yaml '$NAS_BASE_DIR'/compose.yaml
  mv /tmp/goodmood-nas.env '$NAS_BASE_DIR'/.env
  chmod 644 '$NAS_BASE_DIR'/compose.yaml
  chmod 600 '$NAS_BASE_DIR'/.env
' && echo ok"

echo "✓ NAS готов: $NAS_BASE_DIR"
echo "  Дальше: NAS_HOST=$NAS_HOST ./bin/deploy.sh"

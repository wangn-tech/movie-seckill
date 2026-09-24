#!/usr/bin/env bash
set -Eeuo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$PROJECT_ROOT"

set -a
# shellcheck disable=SC1091
source .env
set +a
export MYSQL_USER="${MYSQL_USER:-maoyan}"
export MYSQL_PASSWORD="${MYSQL_PASSWORD:-maoyan-demo-password}"
export MYSQL_DATABASE="${MYSQL_DATABASE:-movie_seckill}"
if [[ "$MYSQL_USER" == "root" ]]; then
  export MYSQL_USER=maoyan
  export MYSQL_PASSWORD=maoyan-demo-password
fi

docker compose --env-file .env -p maoyan-app -f deploy/docker/compose.app.yml down
docker compose --env-file .env -p maoyan-infra -f deploy/docker/compose.infra.yml down

#!/usr/bin/env bash
set -Eeuo pipefail

if [[ "${1:-}" != "--yes" ]]; then
  echo "该命令会删除 movie-seckill 的 MySQL/Redis/RocketMQ Docker 数据卷。"
  echo "确认后执行：bash deploy/scripts/reset-demo.sh --yes"
  exit 1
fi

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$PROJECT_ROOT"

docker compose --env-file .env -p maoyan-app -f deploy/docker/compose.app.yml down -v
docker compose --env-file .env -p maoyan-infra -f deploy/docker/compose.infra.yml down -v
echo "演示数据已删除，可重新执行 bash deploy/scripts/start-demo.sh。"

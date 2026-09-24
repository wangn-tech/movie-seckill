#!/usr/bin/env bash
set -Eeuo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$PROJECT_ROOT"

if [[ ! -f .env ]]; then
  cp .env.example .env
  echo "已从 .env.example 创建演示配置 .env"
fi

set -a
# shellcheck disable=SC1091
source .env
set +a

# 兼容改造前生成的 .env；演示环境默认使用非 root 应用账号。
export MYSQL_USER="${MYSQL_USER:-maoyan}"
export MYSQL_PASSWORD="${MYSQL_PASSWORD:-maoyan-demo-password}"
export MYSQL_DATABASE="${MYSQL_DATABASE:-movie_seckill}"
if [[ "$MYSQL_USER" == "root" ]]; then
  export MYSQL_USER=maoyan
  export MYSQL_PASSWORD=maoyan-demo-password
fi

bash deploy/scripts/start-infra.sh
docker compose --env-file .env -p maoyan-app -f deploy/docker/compose.app.yml up -d --build

port="${HTTP_PORT:-8088}"
for attempt in $(seq 1 30); do
  if curl -fsS "http://127.0.0.1:${port}/health" >/dev/null 2>&1; then
    echo "演示环境已就绪：http://127.0.0.1:${port}"
    exit 0
  fi
  echo "等待应用就绪... (${attempt}/30)"
  sleep 2
done

echo "应用未在预期时间内就绪，请执行：docker compose -p maoyan-app -f deploy/docker/compose.app.yml logs"
exit 1

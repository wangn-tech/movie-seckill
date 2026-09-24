#!/usr/bin/env bash
# 一键启动脚本：启动中间件（MySQL/Redis/RocketMQ）
set -e

cd "$(dirname "$0")/../.."

echo "=== 1. 检查 .env ==="
if [ ! -f .env ]; then
  echo "⚠️  未找到 .env，从 .env.example 复制"
  cp .env.example .env
fi

set -a
# shellcheck disable=SC1091
source .env
set +a
export MYSQL_USER="${MYSQL_USER:-maoyan}"
export MYSQL_PASSWORD="${MYSQL_PASSWORD:-maoyan-demo-password}"
export MYSQL_DATABASE="${MYSQL_DATABASE:-movie_seckill}"
if [ "$MYSQL_USER" = "root" ]; then
  export MYSQL_USER=maoyan
  export MYSQL_PASSWORD=maoyan-demo-password
fi

echo "=== 2. 启动中间件 ==="
docker compose --env-file .env -p maoyan-infra -f deploy/docker/compose.infra.yml up -d

echo "=== 3. 等待 MySQL 就绪 ==="
for i in $(seq 1 30); do
  if docker exec maoyan-mysql sh -c 'mysqladmin ping -uroot -p"$MYSQL_ROOT_PASSWORD" --silent' 2>/dev/null; then
    echo "✅ MySQL 就绪"
    break
  fi
  echo "  等待 MySQL... ($i/30)"
  sleep 2
done

echo "=== 4. 等待 Redis 就绪 ==="
for i in $(seq 1 10); do
  if docker exec maoyan-redis redis-cli ping 2>/dev/null | grep -q PONG; then
    echo "✅ Redis 就绪"
    break
  fi
  sleep 1
done

echo "=== 5. 等待 RocketMQ NameServer 就绪 ==="
for i in $(seq 1 15); do
  if docker exec rocketmq-namesrv bash -c '</dev/tcp/127.0.0.1/9876' 2>/dev/null; then
    echo "✅ NameServer 就绪"
    break
  fi
  sleep 2
done

echo "=== 6. 等待 RocketMQ Topic 初始化 ==="
for i in $(seq 1 30); do
  topic_state=$(docker inspect --format '{{.State.Status}}:{{.State.ExitCode}}' maoyan-rocketmq-topic-init 2>/dev/null || true)
  if [ "$topic_state" = "exited:0" ]; then
    echo "✅ RocketMQ Topic 就绪"
    break
  fi
  if [[ "$topic_state" == exited:* ]]; then
    echo "❌ RocketMQ Topic 初始化失败：$topic_state"
    docker logs maoyan-rocketmq-topic-init
    exit 1
  fi
  if [ "$i" -eq 30 ]; then
    echo "❌ RocketMQ Topic 初始化超时"
    exit 1
  fi
  sleep 1
done

echo ""
echo "=== 中间件已启动 ==="
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}" | grep -E "mysql|redis|rocketmq"
echo ""
echo "下一步："
echo "  后端: cd backend && mvn spring-boot:run"
echo "  前端: cd frontend && npm run dev"

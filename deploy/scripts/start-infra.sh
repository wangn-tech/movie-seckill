#!/usr/bin/env bash
# 一键启动脚本：启动中间件（MySQL/Redis/RocketMQ）
set -e

cd "$(dirname "$0")/../.."

echo "=== 1. 检查 .env ==="
if [ ! -f .env ]; then
  echo "⚠️  未找到 .env，从 .env.example 复制"
  cp .env.example .env
fi

echo "=== 2. 启动中间件 ==="
docker compose --env-file .env -f deploy/docker/compose.infra.yml up -d

echo "=== 3. 等待 MySQL 就绪 ==="
for i in $(seq 1 30); do
  if docker exec maoyan-mysql mysqladmin ping -uroot -proot123 --silent 2>/dev/null; then
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

echo ""
echo "=== 中间件已启动 ==="
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}" | grep -E "mysql|redis|rocketmq"
echo ""
echo "下一步："
echo "  后端: cd backend && mvn spring-boot:run"
echo "  前端: cd frontend && npm run dev"

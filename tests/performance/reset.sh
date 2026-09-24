#!/usr/bin/env bash
set -Eeuo pipefail

if [[ "${1:-}" != "--yes" ]]; then
  echo "该命令会清空 9001 压测场次的订单、座位锁、Outbox 与 Redis 投影。"
  echo "确认后执行：bash tests/performance/reset.sh --yes"
  exit 1
fi

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

docker exec -i maoyan-mysql sh -c 'mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE"' \
  < tests/performance/reset.sql

for pattern in \
  'seckill:seat:9001:*' \
  'seckill:reservation:k6-*' \
  'seckill:released:k6-*' \
  'rate:u9*'; do
  docker exec maoyan-redis sh -c "redis-cli --scan --pattern '$pattern' | xargs -r redis-cli del" >/dev/null
done
docker exec maoyan-redis redis-cli SET seckill:stock:9001 10000 >/dev/null
echo "压测场次 9001 已重置。"

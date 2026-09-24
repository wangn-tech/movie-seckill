#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

if [[ ! -f .env ]]; then
  echo "缺少 .env，请先执行 bash deploy/scripts/start-demo.sh。" >&2
  exit 1
fi

set -a
# shellcheck disable=SC1091
source .env
set +a

test_name="${1:-read}"
case "$test_name" in
  read)
    script="read.js"
    ;;
  unique|contention|batch|rate-limit)
    script="seckill.js"
    export SCENARIO="$test_name"
    ;;
  *)
    echo "用法：$0 {read|unique|contention|batch|rate-limit}" >&2
    exit 1
    ;;
esac

docker run --rm --network maoyan-net \
  -v "$ROOT/tests/performance/k6:/scripts:ro" \
  -e BASE_URL="${K6_BASE_URL:-http://maoyan-nginx/api}" \
  -e JWT_SECRET="$JWT_SECRET" \
  -e SCENARIO="${SCENARIO:-}" \
  -e VUS="${VUS:-}" \
  -e INVALID_VUS="${INVALID_VUS:-}" \
  -e DURATION="${DURATION:-}" \
  -e ITERATIONS="${ITERATIONS:-}" \
  grafana/k6:0.49.0 run "/scripts/$script"

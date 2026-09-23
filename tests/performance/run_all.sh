#!/bin/bash
# 一键压测脚本
# 前置: Docker 中间件已启动, 后端跑在 8080

set -e
BASE=http://localhost:8080
SCHEDULE_ID=${1:-1}

echo "=== Step 1: 登录拿 token ==="
wrk -t1 -c1 -d1s -s login.lua $BASE

echo ""
echo "=== Step 2: 读接口压测 (电影列表, 多级缓存) ==="
wrk -t4 -c100 -d30s -s hot_movies.lua $BASE

echo ""
echo "=== Step 3: 抢座压测 (500并发, 30秒) ==="
SCHEDULE_ID=$SCHEDULE_ID wrk -t8 -c500 -d30s -s seckill.lua $BASE

echo ""
echo "=== Step 4: 限流验证 (1000并发打抢座, 看429比例) ==="
SCHEDULE_ID=$SCHEDULE_ID wrk -t16 -c1000 -d10s -s seckill.lua $BASE

echo ""
echo "=== 压测完成 ==="

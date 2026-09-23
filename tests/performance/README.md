# 压测方案

## 目标
验证秒杀核心链路的性能与正确性：

| 指标 | 目标 |
|---|---|
| 抢座接口单机 QPS | ≥ 2000（Redis Lua 预扣，不等落单） |
| P99 响应时间 | < 50ms |
| 超卖/座位重售 | 0（DB available 不为负，seat_lock 无重复） |
| 缓存综合命中率 | > 95% |
| 限流触发 | 超阈值返回 429，DB QPS 平稳 |

## 前置准备
1. 启动中间件：`docker compose -f deploy/docker/compose.infra.yml up -d`
2. 启动后端：`cd backend && mvn spring-boot:run`
3. 登录拿 token：
```bash
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"account":"13800000001","password":"123456"}'
```
4. 设置环境变量：
```bash
export TEST_TOKEN=<上一步返回的 accessToken>
export SCHEDULE_ID=1
```

## 场景 1：裸压抢座（验证 QPS 和防超卖）
```bash
# 4 线程 200 并发 30 秒
wrk -t4 -c200 -d30s -s tests/performance/seckill.lua http://localhost:8080
```

**验收**：
- 看 QPS（Requests/sec）是否 ≥ 2000
- 看 P99 Latency
- 压完后查 DB：
```sql
-- 1. 库存不能为负
SELECT id, total_seats, available_seats FROM movie_schedule WHERE id=1;
-- 2. 同一座位不能有两条已售记录
SELECT schedule_id, row_num, col_num, COUNT(*) FROM order_seat
GROUP BY schedule_id, row_num, col_num HAVING COUNT(*)>1;
```

## 场景 2：缓存命中率对比
```bash
# 连续打电影详情 1 分钟
wrk -t2 -c50 -d60s http://localhost:8080/movies/1
# 在 Redis 里看命中率
redis-cli info stats | grep keyspace
```

## 场景 3：限流验证
```bash
# 抢座接口限流是 5 QPS/用户，打 10 QPS 看是否大量 429
wrk -t1 -c10 -d10s -s tests/performance/seckill.lua http://localhost:8080
```

## 场景 4：MQ 削峰验证
- 压测期间观察 MySQL QPS：`SHOW GLOBAL STATUS LIKE 'Questions';`
- 预期：Redis 承受高 QPS，MySQL 落单速率平稳（消费者限速），不会被打挂。

## JMeter 补充
需要多用户多 token 压测时用 JMeter：
- 线程组：1000 线程，循环 10 次
- HTTP 默认值：http://localhost:8080
- HTTP Header Manager：Authorization: Bearer ${token}
- CSV Data Set Config：从 users.csv 读不同用户 token

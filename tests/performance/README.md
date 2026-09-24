# 压测方案

## 目标（非实测结果）

> 本节是设计目标，不代表当前环境实测能力。需要对外引用的数据请使用 `tests/performance/压测报告.md` 中的真实执行记录。
验证秒杀核心链路的性能与正确性。下表仅是设计阶段的验收目标，不能替代压测报告中的实测结果：

| 指标 | 目标 |
|---|---|
| 抢座接口 | 以 k6 实际输出为准，不预设 QPS |
| P99 响应时间（设计目标） | < 50ms |
| 超卖/座位重售 | 0（DB available 不为负，seat_lock 无重复） |
| 缓存综合命中率（设计目标） | > 95%（当前未独立采集） |
| 限流触发 | 超阈值返回 429，DB QPS 平稳 |

## 前置准备
1. 复制 `.env.example` 为 `.env`，执行 `bash deploy/scripts/start-demo.sh`。
2. 使用专用压测场次 `9001`；每个写场景前运行 `bash tests/performance/reset.sh --yes`。
3. k6 通过 Docker 接入 `maoyan-net`，不需要在宿主机安装 k6。

## 场景 1：热点读和布隆过滤
```bash
bash tests/performance/run.sh read
```

## 场景 2：抢座与一致性
```bash
bash tests/performance/run.sh unique
bash tests/performance/run.sh contention
bash tests/performance/run.sh batch
bash tests/performance/run.sh rate-limit
bash tests/performance/verify.sh
```

`verify.sh` 只检查专用场次，确认 MySQL/Redis 库存、有效座位、requestId 和 Outbox 最终一致。

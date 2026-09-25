# Movie Seckill 电影票秒杀系统

> Java 21 + Spring Boot 3 + MyBatis-Plus + MySQL + Redis + RocketMQ + Caffeine + Outbox 高并发秒杀演示项目，用于 Java 后端岗位面试。

![架构图](docs/architecture.svg)

## 技术亮点

- **Redis + Lua 原子抢座**：一次脚本完成 requestId 幂等、座位冲突检查、库存预扣、座位锁定，杜绝超卖和座位重售
- **三层防超卖**：Redis Lua 预扣 → MySQL `UPDATE ... WHERE available>=n` 条件更新 → `seat_lock UNIQUE(schedule_id,row,col)` 唯一索引兜底
- **多级缓存**：Caffeine L1 + Redis L2 + 布隆过滤器 + 空值缓存 + 逻辑过期 + Redisson 异步重建，覆盖缓存穿透/击穿/雪崩
- **Outbox + RocketMQ 削峰**：抢座入口只写入 outbox_event，Relay 定时投递 MQ 异步落单；DB 扫描负责超时关单，释放事件幂等执行
- **令牌桶限流**：Redis Lua 令牌桶 + AOP + 用户维度策略
- **JWT 双 Token**：AccessToken(30min) + RefreshToken(7d) + ThreadLocal 用户上下文
- **支付幂等与状态机**：余额 CAS 扣减、订单 `0→1` CAS、重复支付安全返回，支付确认通过 Outbox 异步更新 Redis 座位投影
- **设计模式**：策略（限流粒度）、模板化缓存流程、进程内 single-flight、Outbox 可靠消息、订单状态机

## 技术栈

| 层 | 选型 |
|---|---|
| 后端 | Java 21, Spring Boot 3.3, MyBatis-Plus 3.5 |
| 存储 | MySQL 8, Redis 7, Caffeine, Redisson |
| 消息 | RocketMQ 5 |
| 前端 | Next.js 14, React 18, TypeScript, Tailwind |
| 部署 | Docker Compose |
| 文档 | Knife4j / OpenAPI3 (`/doc.html`) |

## 快速启动

```bash
# 1. 启动中间件（MySQL/Redis/RocketMQ；仅首次创建 .env）
[ -f .env ] || cp .env.example .env
bash deploy/scripts/start-infra.sh

# 2. 终端 A：启动后端（本地开发，连接宿主机 Docker 映射端口）
cd backend
/opt/apache-maven-3.9.11/bin/mvn spring-boot:run
# 访问 http://localhost:8080/doc.html 查看接口

# 3. 终端 B（在项目根目录）：启动前端（Next.js 将 /api 代理至后端）
cd frontend
npm ci
BACKEND_URL=http://localhost:8080 npm run dev
# 访问 http://localhost:3000
```

**测试账号**：`13800000001` / `13800000002`，密码 `123456`

## 项目结构

```
├── backend/                # Spring Boot 后端（MVC 单模块）
│   └── src/main/java/com/wangning/seckill/
│       ├── common/         # Result/异常/ThreadLocal/常量/工具
│       ├── config/         # Redis/Redisson/Caffeine/MyBatis/Web/Knife4j
│       ├── controller/     # MVC 的 C
│       ├── service/        # 业务接口
│       │   └── impl/       # 业务实现
│       ├── mapper/         # MVC 的 M（MyBatis-Plus）+ resources/mapper/*.xml
│       ├── entity/ dto/ vo/
│       ├── lua/            # Redis Lua 脚本
│       ├── mq/              # RocketMQ producer/consumer
│       ├── job/            # Outbox投递/订单超时关单
│       └── cache/          # 多级缓存/布隆过滤器
├── frontend/               # Next.js 前端
├── deploy/docker/          # Compose: MySQL/Redis/RocketMQ/Nginx
└── docs/                   # 开发文档
```

## 核心链路

```
选座提交 → 令牌桶限流 → 布隆过滤 → Redis Lua 原子抢座
  → 立即返回"排队中" → RocketMQ 异步落单
  → MySQL 条件更新 + 唯一索引兜底
  → 用户轮询订单 → 模拟支付 → 座位置已售
  → 超时未支付 → DB扫描关单 → Outbox释放事件 → Lua 返还库存
```

## 面试演示重点

- **缓存击穿**：逻辑过期返回旧值，Redisson 负责跨实例重建锁，锁竞争后的数据库回源由进程内 single-flight 合并。
- **可靠消息**：抢座入口只写 `outbox_event` 并立即返回 `PROCESSING`；Relay 以至少一次语义投递 RocketMQ，消费者和 Lua 补偿按 `requestId/event_key` 幂等。
- **最终一致性边界**：MySQL `available_seats`、订单和 `seat_lock` 是最终事实；Redis 负责入口预扣与座位投影，启动预热和专用 reset 脚本用于重建投影。
- **可验证证据**：`backend/src/test` 覆盖缓存并发回源、订单创建、支付和库存不足；`tests/performance/verify.sh` 校验专用场次库存、座位、requestId 和 Outbox。
- **简历与话术**：项目描述、30 秒介绍、验证数据和高频追问见 [`docs/简历与面试表达.md`](docs/简历与面试表达.md)。

## 一键演示与验证

```bash
cp .env.example .env
bash deploy/scripts/start-demo.sh
bash tests/performance/run.sh read
bash tests/performance/run.sh unique
bash tests/performance/run.sh contention
bash tests/performance/run.sh batch
bash tests/performance/run.sh rate-limit
bash tests/performance/verify.sh
```

## 默认测试账号

| 账号 | 密码 |
|---|---|
| 13800000001 | 123456 |
| 13800000002 | 123456 |

# AGENTS.md

本文件为 AI 编码 Agent（Codex / Cursor / Claude Code 等）提供接手本仓库所需的上下文。修改代码前请先读完本文件。

## 项目概述

电影票高并发秒杀系统。核心目标：在单机部署下，通过 Redis + Lua + MQ + Outbox 模式支撑抢座请求，不超卖、不丢单、可补偿。

- 仓库：`wangn-tech/movie-seckill`
- 主分支：`main`
- 语言：Java 21 + TypeScript (Next.js)
- 作者：wangn

## 技术栈

### 后端 `backend/`
- **Spring Boot 3.3.5** (Java 21, Maven)
- **MyBatis-Plus 3.5.7** — ORM，Mapper 在 `backend/src/main/java/com/wangning/seckill/mapper/`，XML 在 `backend/src/main/resources/mapper/`
- **MySQL 8** — 业务库 `movie_seckill`
- **Redis 7 + Redisson 3.32** — 库存扣减（Lua 脚本）、分布式锁、布隆过滤器、本地 Caffeine 二级缓存
- **RocketMQ** — 异步落单（可选，未启动时用 `RocketmqFallbackConfig` 兜底）
- **JJWT** — JWT 双 Token（access + refresh）
- **Lombok** — 用 `@RequiredArgsConstructor` 构造注入，**不要**手写 setter 或字段 `@Autowired`
- **Knife4j** — API 文档 `/doc.html`

### 前端 `frontend/`
- **Next.js 14.2** (App Router, TypeScript)
- **React 18** + **Zustand**（状态管理）
- **Axios** — 已封装拦截器，401 自动刷新 token
- **Tailwind CSS 3.4**

### 部署 `deploy/`
- `deploy/docker/compose.infra.yml` — MySQL / Redis / RocketMQ 中间件
- `deploy/docker/compose.app.yml` — 应用容器
- `deploy/docker/mysql/init/schema.sql` — 建表 + 初始数据
- `deploy/scripts/start-infra.sh` — 一键启动中间件并健康检查

## 目录结构

```
movie-seckill/
├── backend/
│   └── src/main/java/com/wangning/seckill/
│       ├── common/        # Result、BizException、ResultCode、ThreadLocal、常量
│       ├── config/        # Redis、Redisson、Caffeine、MyBatis、Web、Knife4j、RocketmqFallback
│       ├── controller/    # MVC C 层
│       ├── service/       # 业务接口
│       │   └── impl/      # 业务实现（业务逻辑都在 impl）
│       ├── mapper/        # MyBatis-Plus Mapper 接口
│       ├── entity/        # DB 实体（MyBatis-Plus 注解）
│       ├── dto/           # 请求 DTO
│       ├── vo/            # 响应 VO
│       ├── lua/           # Redis Lua 脚本（库存扣减原子操作）
│       ├── job/           # 定时任务：OrderTimeoutJob、OutboxRelayJob
│       └── mq/
│           ├── producer/   # MQ 消息发送
│           └── consumer/  # SeckillOrderConsumer 异步落单
├── frontend/
│   └── src/app/           # Next.js App Router 页面
├── deploy/
│   ├── docker/            # compose 文件 + MySQL init SQL
│   └── scripts/           # 启动脚本
├── docs/                  # 架构设计、开发文档、面试讲解手册
└── tests/performance/     # 压测脚本和报告
```

## 本地启动

### 前置
- JDK 21、Maven 3.9+、Node 18+、Docker（可选，用于中间件）

### 1. 启动中间件
```bash
bash deploy/scripts/start-infra.sh
# 或手动：docker compose -f deploy/docker/compose.infra.yml up -d
```

### 2. 启动后端
```bash
cd backend
mvn spring-boot:run
# 端口 8080，profile=dev
```
- 若本地没有 RocketMQ，加 JVM 参数：`--rocketmq.consumer.enabled=false`
- `RocketmqFallbackConfig` 会自动提供 fallback producer，不影响编译

### 3. 启动前端
```bash
cd frontend
npm install
npm run dev
# 端口 3000
```

### 测试账号
- 手机号：`13800000001` / `13800000002`
- 密码：`123456`

## 核心架构：抢座链路

```
用户点击抢座
  → 网关/拦截器：JWT 校验 + 限流（令牌桶）
  → SeckillController.seize()
    → 布隆过滤器判断 scheduleId 是否存在（防穿透）
    → Redis Lua 脚本原子扣库存 + 座位占用
       （脚本在 backend/src/main/resources/lua/）
    → 写 outbox_event 表（PENDING 状态，同事务）
    → 同步发送 MQ（失败不阻断，由 OutboxRelayJob 兜底）
  → 返回 requestId 给前端

SeckillOrderConsumer 消费 MQ
  → MySQL 事务落单：ticket_order + order_seat + seat_lock + 条件更新 available_seats
  → OutboxRelayJob 每 2 秒轮询 outbox_event，重试发送
```

**关键不变量：**
- Redis Lua 是库存扣减的唯一权威来源，MySQL 是兜底
- 座位防重：`seat_lock` 表唯一键 `(schedule_id, row_num, col_num)`
- 订单幂等：`ticket_order` 表唯一键 `uk_lock_token = requestId`
- 金额一律用 `BigDecimal`，分/元换算用 `setScale(HALF_UP)` + `intValueExact()`

## 代码约定

### 后端
- **构造注入**：用 `@RequiredArgsConstructor` + `private final` 字段，**禁止**字段 `@Autowired`
- **分层**：Controller → Service（接口）→ ServiceImpl → Mapper。Controller 不直接注入 Mapper
- **统一返回**：所有 Controller 返回 `Result<T>`，业务异常抛 `BizException(ResultCode.XXX, msg)`
- **异常映射**：`GlobalExceptionHandler` 统一把 BizException 映射到正确 HTTP 状态码
- **事务**：写操作在 ServiceImpl 方法上加 `@Transactional(rollbackFor = Exception.class)`
- **Mapper XML**：复杂 SQL 写在 `resources/mapper/`，简单 CRUD 用 MyBatis-Plus LambdaQueryWrapper
- **配置**：profile 在 `application-dev.yml` / `application-prod.yml` / `application-docker.yml`
- **日志**：用 `@Slf4j`，不要用 System.out

### 前端
- **App Router**：页面在 `src/app/`，共享组件在 `src/components/`
- **状态**：Zustand store 在 `src/store/`
- **API**：统一用 `src/lib/request.ts` 封装的 axios 实例，不要直接 fetch
- **类型**：API 响应类型在 `src/types/`
- **样式**：Tailwind class，不要写自定义 CSS 文件（除全局 variables）

### Git 提交
- 英文 commit message，祈使句：`fix: ...`, `feat: ...`, `refactor: ...`
- 一个 commit 只做一件事
- 不要提交 `target/`、`node_modules/`、`.env`（已在 .gitignore）

## 常见陷阱（务必避开）

1. **`@RequiredArgsConstructor` 不能省**：`OrderTimeoutJob` 之前因为没写默认构造器导致 Spring 启动失败。所有需要 Spring 管理且有 final 字段的类都要加这个注解。
2. **logback 用 `%i` 必须配 `SizeAndTimeBasedRollingPolicy`**，不能用 `TimeBasedRollingPolicy`，否则启动直接报错。
3. **MySQL 连接串字符集**：JDBC URL 里写 `characterEncoding=utf8`（不是 `utf8mb4`，Java 不识别后者作为编码名）。
4. **RocketMQ consumer 在没装 RocketMQ 的环境会阻断启动**：本地开发加 `--rocketmq.consumer.enabled=false`。
5. **Redis Lua 脚本修改后必须重新加载**：改了 `*.lua` 文件要重启 Spring Boot，因为脚本是启动时加载缓存的。
6. **布隆过滤器需要预热**：新增 schedule 或 movie 后必须调用 `BloomFilterService.addSchedule()` / `addMovie()`，否则抢座直接报"场次不存在"。
7. **JWT secret 长度**：启动时会校验，短于 32 字符会警告。生产环境必须改环境变量 `JWT_SECRET`。
8. **前端 token 过期**：axios 拦截器会自动刷新，业务代码不需要手动处理 401。

## 测试

```bash
# 后端单元测试
cd backend && mvn test

# 前端
cd frontend && npm run build   # 类型检查 + 构建
```

## 压测参考

- 压测脚本和历史结果在 `tests/performance/`
- 压测前确保：Redis 库存已预热（`seckill:stock:{scheduleId}`）、布隆过滤器已添加对应 scheduleId
- 历史数据：单实例 Redis Lua 抢座 P50 ~50ms，QPS ~1000（Redis 直连场景）；加 MySQL outbox 同步写入后 QPS 降到 ~130

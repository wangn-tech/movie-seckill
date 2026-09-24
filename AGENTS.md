# AGENTS.md

本文件为 AI 编码 Agent（Codex / Cursor / Claude Code 等）提供本仓库的事实来源。修改代码前必须先阅读本文件；代码、测试与文档不一致时，以可验证的代码和测试结果为准。

## 工具链

- 后端：Java 21、Spring Boot 3.3.5、Maven 3.9+
- Maven：`/opt/apache-maven-3.9.11/bin/mvn`
- Maven settings：`/opt/apache-maven-3.9.11/conf/settings.xml`
- Maven 本地仓库：`/opt/maven-repository`
- 前端：Next.js 14、React 18、TypeScript、Tailwind CSS
- 基础设施：MySQL 8、Redis 7、RocketMQ 5、Docker Compose

## 项目定位

这是一个用于 Java 后端面试演示的电影票高并发抢座系统。核心目标是通过 Caffeine + Redis 多级缓存、Redisson、Redis Lua、RocketMQ 和 Outbox 展示缓存治理、限流、幂等、防超卖及最终一致性。

核心演示链路：登录 → 浏览电影 → 选择影院/场次 → 选座 → 抢座排队 → MQ 异步落单 → 模拟支付 → 查询订单。

## 目录

- `backend/`：Spring Boot MVC 单体服务
- `frontend/`：Next.js App Router 前端
- `deploy/docker/`：MySQL、Redis、RocketMQ、应用与 Nginx
- `deploy/scripts/`：启动、检查、重置脚本
- `tests/performance/`：Docker 化 k6 压测与一致性校验
- `docs/`：实施计划、开发文档、架构设计、面试讲解与压测报告

## 后端架构约束

- 分层固定为 Controller → Service → Mapper；Controller 不得直接注入 Mapper。
- Controller 返回 `Result<T>`，输入使用 DTO，输出使用 VO，不直接暴露数据库实体。
- 写操作使用 `@Transactional(rollbackFor = Exception.class)`；不要依赖同类方法自调用触发事务或 `@Async`。
- 依赖注入使用构造注入和 `@RequiredArgsConstructor`，禁止字段 `@Autowired`。
- 金额使用 `BigDecimal`；余额分/元换算必须显式舍入并使用 `intValueExact()`。
- 日志使用 Slf4j，不使用 `System.out`，日志中不得记录密码、Token 或 Cookie。

## 缓存与 Redisson

- Caffeine 是 JVM 内 L1，只缓存电影、影院、场次等读多写少数据；库存、座位、订单和处理状态不得进入 L1。
- 读取路径为 Caffeine L1 → Redis L2 → MySQL；写操作按需同时失效 L1/L2。
- Redisson `RBloomFilter` 用于合法 movieId/scheduleId 防穿透，启动预热并在新增数据时更新。
- Redisson `RLock` 只用于多实例缓存重建；拿锁后必须再次检查 Redis，避免重复回源。
- 抢座禁止使用 Redisson 分布式锁，必须使用 Redis Lua 一次性原子完成冲突检查与库存预扣。

## 抢座不变量

- API 座位坐标从 1 开始。
- 单次选座不设人为数量上限，但必须非空、无重复、在布局边界内且不是过道或不可售位置。
- `seatRows`/`seatCols` 只表示布局边界；`totalSeats` 是有效可售座位数，不等于 `rows × cols`。
- requestId 是幂等键；相同 requestId 只有在用户、场次和座位指纹完全一致时才能幂等成功。
- `ticket_order.lock_token` 和 Outbox `event_key` 均有唯一约束。
- Redis Lua 是流量入口，MySQL 条件更新和 `seat_lock` 唯一键是最终兜底。
- 抢座接口只写入入口 Outbox，不同步等待 MQ；MQ Consumer 在单事务内完成订单落库。
- 支付和超时关单通过 Outbox 事件确认或释放 Redis 座位，所有补偿必须幂等。

## 前端约束

- 页面放在 `src/app/`，共享组件放在 `src/components/`，API 类型放在 `src/types/`。
- 所有请求统一使用 `src/lib/api.ts`；核心业务代码禁止使用 `any`。
- 使用 SWR 做客户端请求去重与刷新；互不依赖的请求应并行发起。
- 大座位图用 `Set`/`Map` 查询状态，避免每个格子反复遍历数组。
- 基于现有 Tailwind 体系实现简洁红白商业风格，不引入重量级 UI 框架。

## 常用命令

```bash
# 后端
cd backend && /opt/apache-maven-3.9.11/bin/mvn test

# 前端
cd frontend && npm ci && npm run build

# 基础设施/完整演示
bash deploy/scripts/start-infra.sh
bash deploy/scripts/start-demo.sh

# Compose 静态校验
docker compose --env-file .env -f deploy/docker/compose.infra.yml config -q
docker compose --env-file .env -f deploy/docker/compose.app.yml config -q
```

## Git 规范

- 使用 Conventional Commits：`feat(scope): ...`、`fix(scope): ...`、`perf(scope): ...`、`docs: ...`、`test(scope): ...`。
- 一个提交只处理一个可独立审查的主题；提交前运行对应测试和 `git diff --check`。
- 不提交 `.env`、密钥、Token、`target/`、`.next/`、`node_modules/` 或压测临时结果。
- 不覆盖用户未提交的修改，不使用 `git reset --hard` 或 `git checkout --` 清理工作区。
- 性能文档只记录真实执行结果，禁止把设计目标或推算数据写成实测数据。

## 已知注意事项

- Lua 文件在 `backend/src/main/resources/lua/`，修改后必须重启后端以重新加载。
- JWT secret 至少 32 字符，生产/公网环境必须通过环境变量覆盖。
- 本地关闭 RocketMQ Consumer 只能用于非订单接口开发，不能用于完整抢座演示。
- MySQL JDBC 字符编码写 `UTF-8`，表和连接排序规则使用 `utf8mb4`。
- Logback 文件名包含 `%i` 时必须使用 `SizeAndTimeBasedRollingPolicy`。

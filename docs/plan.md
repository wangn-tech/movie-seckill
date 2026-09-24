# 面试演示优化实施计划

## 目标

在保留 Spring Boot 单体和现有中间件的前提下，交付一套可一键启动、可完整演示、可重复压测的电影票抢座系统。重点展示 Caffeine + Redis 多级缓存、Redisson、Redis Lua、RocketMQ + Outbox、幂等、防超卖和补偿机制。

## 当前基线

- 后端 Maven 测试、前端生产构建、Compose 配置校验和 Docker 启动检查均已通过。
- 代码已覆盖缓存类型安全反序列化、逻辑过期、Redisson 重建锁和锁竞争 single-flight。
- 抢座入口只写入口 Outbox，返回 `PROCESSING`；订单消费者、支付确认和超时释放均按业务键幂等。
- 压测使用专用场次 `9001`，报告中的吞吐和延迟只来自真实 Docker/k6 输出。

## 阶段

### 1. 缓存治理

- [x] Caffeine 按电影、影院、场次设置 L1 TTL。
- [x] 使用 Jackson `JavaType` 正确反序列化集合。
- [x] Redisson 锁内二次检查 Redis，并用进程内 single-flight 合并锁竞争回源。
- [x] 测试 L1/L2 命中、布隆过滤和并发回源。

### 2. 抢座与订单一致性

- [x] 建模座位布局边界和不可售位置；库存是有效座位数而非行列乘积。
- [x] 座位坐标改为 1-based，选座不限数量但校验非空、去重和有效性。
- [x] Lua 校验请求指纹并原子完成库存预扣和批量锁座。
- [x] Outbox 增加唯一事件键、处理状态和失败原因，热路径不再同步发 MQ。
- [x] Consumer 幂等落单，支付确认与超时释放通过事件最终一致。
- [x] 提供 `PROCESSING / CREATED / FAILED` 抢座状态查询。

### 3. 前端演示

- [x] 建立 API 类型和 SWR 请求层，修复 Token 刷新队列。
- [x] 电影、影院、场次、座位、订单、支付完整闭环。
- [x] 支持过道/不可售座位和不限量选座，用 Set/Map 渲染状态。
- [x] 完成红白商业风格、加载/错误/空状态和响应式布局。

### 4. 部署与压测

- [x] 补齐前端 Dockerfile、Nginx API 转发、MySQL 用户与健康检查。
- [x] 增加一键启动、检查和重置命令。
- [x] 使用 Docker k6 覆盖热点读、非法 ID、成功抢座、冲突、批量座位和限流。
- [x] 压测后校验库存、座位、订单、Outbox 一致性。

### 5. 文档

- [x] README、开发文档、架构设计、架构图和面试手册与代码同步。
- [x] 压测报告只填写真实环境和真实结果；未执行的数据明确标记为待测。

## 验收条件

- 完整 Docker 环境能从登录走到支付成功。
- 同一 requestId 不重复落单，相同 requestId 不同内容被拒绝。
- 并发抢同一座位不会重售，库存不会为负。
- 超时释放不会重复返还，释放后的座位可再次购买。
- Caffeine、Redis、Redisson Bloom/RLock 和 single-flight 均有清晰代码路径和测试证据。
- Maven 测试、Next.js 构建、Compose 校验与 k6 一致性检查通过。

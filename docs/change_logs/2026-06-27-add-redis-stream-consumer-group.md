## 日期

2026-06-27

## 变更摘要

- 新增 `onlineIM-server/src/main/java/icu/tianqingyuluo/onlineim/config/ServerIdentity.java`
  - 原因：将 `serverId` 从 `VertxWebSocketServer` 内部状态提取为独立组件，供 Redis Stream 消费者组配置与 WebSocket 服务注册共同复用。

- 修改 `onlineIM-server/src/main/java/icu/tianqingyuluo/onlineim/config/RedisConfig.java`
  - 原因：将 Redis Stream 消费模式从 `XREAD + fromStart` 改为 `XREADGROUP + ReadOffset.lastConsumed()`，并在启动时创建本实例消费者组，解决重启重放与无消费者组的问题。

- 修改 `onlineIM-server/src/main/java/icu/tianqingyuluo/onlineim/websocket/listener/RedisEventListener.java`
  - 原因：为 Stream 消费增加 `XACK` 逻辑；本地无接收者时仅 ack，不再丢弃未确认状态；异常时保留 PEL 留痕。

- 修改 `onlineIM-server/src/main/java/icu/tianqingyuluo/onlineim/websocket/server/VertxWebSocketServer.java`
  - 原因：正常停机时销毁本实例 consumer group，并改用 `ServerIdentity` 统一管理 `serverId`。

- 修改 `onlineIM-server/pom.xml`
  - 原因：引入 Testcontainers Redis 集成测试依赖，并为 Maven Surefire 配置 Docker API 版本与禁用 Ryuk 的环境变量，保证 `mvn test` 可执行 Redis 集成测试。

- 修改 `.gitignore`
  - 原因：取消对 `onlineIM-server/src/test` 的忽略，确保后端测试源码可被 Git 跟踪与提交。

- 新增后端测试文件
  - `onlineIM-server/src/test/java/icu/tianqingyuluo/onlineim/websocket/listener/RedisEventListenerTest.java`
  - `onlineIM-server/src/test/java/icu/tianqingyuluo/onlineim/config/RedisConfigStreamContainerTest.java`
  - `onlineIM-server/src/test/java/icu/tianqingyuluo/onlineim/config/RedisStreamConsumerGroupIntegrationTest.java`
  - `onlineIM-server/src/test/java/icu/tianqingyuluo/onlineim/config/RedisStreamRestartIntegrationTest.java`
  - `onlineIM-server/src/test/java/icu/tianqingyuluo/onlineim/websocket/server/VertxWebSocketServerTest.java`
  - 原因：补齐消费者组广播、本地过滤 ack、启动配置、停机销毁 group、以及“新 group 从流尾开始不回放历史消息”的验证覆盖。

- 修改 `.trellis/spec/backend/quality-guidelines.md`
  - 原因：沉淀 Redis Stream/Testcontainers 集成测试的命名规范、Docker 运行约束与默认 `mvn test` 可发现性要求，避免后续再次踩坑。

## 关联任务

- `v1-redis-stream-consumer-group`

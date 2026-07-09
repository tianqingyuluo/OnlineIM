# Redis Stream 消费者组多实例去重任务完成记录

## 任务信息

- 任务 ID：`v1-redis-stream-consumer-group`
- 任务标题：Redis Stream 消费者组多实例去重

## 变更范围

- 后端 Redis Stream 消费链路改为每实例独立消费者组，实现跨实例广播、本地过滤与消费确认。
- 新增实例身份组件，并在 WebSocket 服务停止时清理对应消费者组。
- 新增 Redis Stream 消费者组、重启行为、监听器确认逻辑及 WebSocket 心跳相关后端测试。
- 修复 Java 21 环境下 Mockito 无法自附加的问题，在测试 JVM 启动时显式加载 Byte Buddy agent。
- 主要影响文件：
  - `onlineIM-server/pom.xml`
  - `onlineIM-server/src/main/java/icu/tianqingyuluo/onlineim/config/RedisConfig.java`
  - `onlineIM-server/src/main/java/icu/tianqingyuluo/onlineim/config/ServerIdentity.java`
  - `onlineIM-server/src/main/java/icu/tianqingyuluo/onlineim/websocket/listener/RedisEventListener.java`
  - `onlineIM-server/src/main/java/icu/tianqingyuluo/onlineim/websocket/server/VertxWebSocketServer.java`
  - `onlineIM-server/src/test/java/icu/tianqingyuluo/onlineim/config/`
  - `onlineIM-server/src/test/java/icu/tianqingyuluo/onlineim/websocket/`

## 验收结果

- 后端编译：通过。
- 后端单元测试：通过。
- Redis Testcontainers 集成测试：通过。
- 全量测试：执行 `./mvnw clean test`，共 43 项测试，失败 0 项，错误 0 项，跳过 0 项。
- lint：项目未配置独立 Java lint 命令。
- typecheck：由 Maven Java 编译阶段完成并通过。

## 遗留问题

- 双真实应用实例的人工消息投递验证属于可选验收项，本次由真实 Redis 集成测试覆盖核心广播、确认与重启语义，未额外进行人工验证。
- Maven 仍提示旧版 MySQL 驱动坐标已迁移，该警告与本任务无关。

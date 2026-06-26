# 执行计划 - Redis Stream 消费者组多实例去重

## 前置条件
- [x] `prd.md` 已完成
- [x] `design.md` 已完成
- [ ] 本文件经用户 review 通过

## 实施步骤

### 阶段 1：基础设施

- [ ] **1.1 新增 `ServerIdentity` 组件**
  - 路径：`onlineIM-server/src/main/java/icu/tianqingyuluo/onlineim/config/ServerIdentity.java`（或 `websocket/server/`）
  - 内容：`@Component`，`@PostConstruct` 生成 `serverId = "serverID_" + IdUtil.simpleUUID()`，暴露 `getServerId()` 和 `getGroupId()`（返回 `"im-msg-grp-" + serverId`）
  - 验证：`mvn compile` 通过

- [ ] **1.2 `VertxWebSocketServer` 改用 `ServerIdentity`**
  - 文件：`websocket/server/VertxWebSocketServer.java`
  - 改动：注入 `ServerIdentity`，删除自己的 `serverId` 字段（行 42）和 `registerServiceToRedis` 里的生成逻辑（行 256），改用 `serverIdentity.getServerId()`
  - `registerServiceToRedis`/`unregisterServiceFromRedis` 里的 `serverId` 引用改为 `serverIdentity.getServerId()`
  - 验证：`mvn compile` 通过

- [ ] **1.3 `pom.xml` 添加 testcontainers 依赖**
  - 文件：`onlineIM-server/pom.xml`
  - 依赖：`org.testcontainers:testcontainers` + `com.redis:testcontainers-redis`（或 `org.testcontainers:spock-redis` 若版本支持），scope=test
  - 验证：`mvn test-compile` 通过

### 阶段 2：消费侧改造

- [ ] **2.1 `RedisConfig.streamContainer` 改 consumer group 注册**
  - 文件：`config/RedisConfig.java`（行 86-108）
  - 改动：
    - 注入 `ServerIdentity` 和 `RedisTemplate`
    - 在 `container.receive` 前调 `redisTemplate.opsForStream().createGroup("im:message:stream", ReadOffset.from("$"), groupId)`（catch `BUSYGROUP`）
    - `container.receive(StreamOffset.fromStart(...), listener)` 改为 `container.receive(Consumer.from(groupId, serverId), StreamOffset.create("im:message:stream", ReadOffset.lastConsumed()), listener)`
  - 验证：`mvn compile` 通过

- [ ] **2.2 `RedisEventListener` 添加 XACK**
  - 文件：`websocket/listener/RedisEventListener.java`（行 39-86）
  - 改动：
    - 注入 `RedisTemplate` 和 `ServerIdentity`（取 groupId）
    - `onMessage` 末尾加 `redisTemplate.opsForStream().acknowledge(stream, groupId, messageId)`
    - 本地过滤分支（行 51-54）也加 ack 后再 return
    - 异常分支不 ack（留 PEL 留痕）
  - 删除注释代码块（行 70-82）
  - 验证：`mvn compile` 通过

- [ ] **2.3 `VertxWebSocketServer.stop()` 加 `XGROUP DESTROY`**
  - 文件：`websocket/server/VertxWebSocketServer.java`（行 227-249）
  - 改动：在 `stop()` 开头新增 `destroyConsumerGroup()`，调 `redisTemplate.opsForStream().delete("im:message:stream", groupId)` 或 `connection.streamCommands().deleteConsumerGroup`（实现时确认 API）
  - 验证：`mvn compile` 通过

### 阶段 3：测试

- [ ] **3.1 `RedisEventListener` 单元测试**
  - 路径：`src/test/java/.../websocket/listener/RedisEventListenerTest.java`
  - 用例：
    - 本地有接收者 → `publishEvent` 被调用 + `acknowledge` 被调用
    - 本地无接收者且无 sender → 不 `publishEvent` 但仍 `acknowledge`
    - 反序列化异常 → 不 `acknowledge`（留 PEL）
    - `publishEvent` 抛异常 → 不 `acknowledge`
  - Mockito mock `RedisTemplate`/`ApplicationEventPublisher`/`LocalSessionRegistry`/`ServerIdentity`
  - 验证：`mvn test -Dtest=RedisEventListenerTest` 通过

- [ ] **3.2 集成测试 `RedisStreamConsumerGroupIT`**
  - 路径：`src/test/java/.../config/RedisStreamConsumerGroupIT.java`
  - testcontainers 起 Redis
  - 场景：
    - 同一 stream 创建两个 group A、B
    - `XADD` 一条消息
    - 两 group 各自 `XREADGROUP` 都收到同一条
    - 各自 `XACK` 后 `XPENDING` 为空
  - 验证：`mvn test -Dtest=RedisStreamConsumerGroupIT` 通过

- [ ] **3.3 集成测试 `RedisStreamRestartIT`**
  - 路径：`src/test/java/.../config/RedisStreamRestartIT.java`
  - testcontainers 起 Redis
  - 场景：
    - 创建 group，`XADD` 消息 M1，`XREADGROUP` 收到 M1，`XACK` M1
    - 不销毁 group，`XADD` 消息 M2
    - 用同 group 再次 `XREADGROUP`（ReadOffset.lastConsumed）
    - 只收到 M2，不重放 M1
  - 验证：`mvn test -Dtest=RedisStreamRestartIT` 通过

### 阶段 4：验证

- [ ] **4.1 全量编译与测试**
  - `mvn clean test`
  - 全绿

- [ ] **4.2 手动验证（可选，需双实例环境）**
  - 起两实例，一用户给另一实例的用户发消息，双方各收一份，无重复
  - 单实例重启，不重放历史消息

## 验证命令

```bash
# 编译
cd onlineIM-server && mvn compile

# 单测
mvn test -Dtest=RedisEventListenerTest

# 集成测试（需 Docker）
mvn test -Dtest=RedisStreamConsumerGroupIT,RedisStreamRestartIT

# 全量测试
mvn clean test
```

## 风险文件与回滚点

| 文件 | 风险 | 回滚 |
|---|---|---|
| `RedisConfig.java` | 容器注册改造，启动失败风险 | 还原 `StreamOffset.fromStart` |
| `RedisEventListener.java` | ack 逻辑侵入消费链路 | 删 ack 调用 |
| `VertxWebSocketServer.java` | serverId 提取可能遗漏引用 | 还原 serverId 字段 |
| `ServerIdentity.java` | 新增文件，低风险 | 删除 |
| `pom.xml` | testcontainers 依赖冲突 | 删除依赖 |

## review 门槛

- [ ] 用户 review `design.md` + `implement.md`
- [ ] 确认 `XGROUP DESTROY` 的 Spring Data Redis API（实现 2.3 时）
- [ ] 确认 testcontainers redis 依赖坐标（实现 1.3 时）

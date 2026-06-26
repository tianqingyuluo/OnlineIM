# 技术设计 - Redis Stream 消费者组多实例去重

## 1. 目标与边界

将 `im:message:stream` 的消费模式从"每实例 XREAD 全量流 + 无 ACK"改为"每实例独立 consumer group + XREADGROUP + XACK"，解决：
- 多实例重复消费（横向扩展正确性 bug）
- 重启重放全部历史（`StreamOffset.fromStart` bug）
- 消费失败无重投痕迹（无 PEL）

**不在本任务范围**：
- `XAUTOCLAIM`/`XCLAIM` 跨 serverId 恢复旧 PEL（D1）
- 异常崩溃孤儿 group 的定时清理（D2）
- `UserSessionService` 的 `@Value` + `final` bug（D5）
- 跨实例显式消息转发（广播模型不需要）
- WS 协议变更

## 2. 架构与数据流

### 2.1 改造后的消费模型

```
实例 A 启动:
  1. serverId = "serverID_<uuid>"  (VertxWebSocketServer.java:256, 不变)
  2. XGROUP CREATE im:message:stream im-msg-grp-<serverId> $ MKSTREAM  (新增, BUSYGROUP 忽略)
  3. container.receive(
       Consumer.from("im-msg-grp-<serverId>", "<serverId>"),
       StreamOffset.create("im:message:stream", ReadOffset.lastConsumed()),
       redisEventListener
     )  (RedisConfig.java:99-102 改造)
  4. container.start()

实例 B 启动: 同上, group name 不同, 各读各的 offset

消息 XADD (生产侧不变):
  RedisStreamService.publishMessage -> opsForStream().add("im:message:stream", ...)
  每个实例的 group 各自收到这条消息 (Redis 广播语义)

消费 (RedisEventListener.onMessage 改造):
  反序列化 -> 本地过滤 (hasReceiverConnected, 保留) 
  -> publishEvent (保留) 
  -> XACK im:message:stream im-msg-grp-<serverId> <messageId>  (新增)

实例停机 (@PreDestroy):
  XGROUP DESTROY im:message:stream im-msg-grp-<serverId>  (新增)
```

### 2.2 关键不变量

| 不变量 | 保证机制 |
|---|---|
| 每条消息被每个实例的 group 各消费一次 | Redis consumer group 广播语义（每 group 独立 offset） |
| 重启不重放历史 | `ReadOffset.lastConsumed()` + group 记住 last-delivered-id |
| 正常停机不留孤儿 group | `@PreDestroy` 调 `XGROUP DESTROY` |
| 投递失败有 PEL 留痕 | 处理完才 ack（D3），异常时消息留 PEL |

## 3. 改造落点

### 3.1 `RedisConfig.java`（容器注册）

**现状**（`RedisConfig.java:86-108`）：
```java
container.receive(
    StreamOffset.fromStart("im:message:stream"),  // 从 0 重放
    redisEventListener                            // 无 Consumer, 走 XREAD
);
```

**改造后**：
```java
// 注入 serverId 来源 (VertxWebSocketServer 暴露 getter, 或单独 bean)
String groupId = "im-msg-grp-" + serverId;
String consumerName = serverId;

// 启动前建组 (BUSYGROUP 忽略)
redisTemplate.opsForStream().createGroup(
    "im:message:stream", 
    ReadOffset.from("$"), 
    groupId
);  // MKSTREAM 由 Redis 5.0+ 的 createGroup 自动处理流不存在

container.receive(
    Consumer.from(groupId, consumerName),
    StreamOffset.create("im:message:stream", ReadOffset.lastConsumed()),
    redisEventListener
);
```

**serverId 获取方式**：`VertxWebSocketServer` 当前 `serverId` 是 private 字段（`VertxWebSocketServer.java:42`），且在 `@PostConstruct start()` 里才赋值（行 256）。`RedisConfig` 的 `streamContainer` Bean 创建时机需在 `serverId` 就绪后。两种方案：
- **方案 A（推荐）**：给 `VertxWebSocketServer` 加 `getServerId()` getter，`streamContainer` Bean 注入 `VertxWebSocketServer` 并调用。需保证 Bean 创建顺序：`VertxWebSocketServer` 先于 `streamContainer`。因 `streamContainer` 已注入 `RedisEventListener`，而 `RedisEventListener` 注入 `LocalSessionRegistry`，无循环依赖风险。但 `VertxWebSocketServer` 的 `serverId` 在 `@PostConstruct` 才赋值——Bean 构造完成时 `serverId` 还是 null。
- **方案 B（推荐）**：把 `serverId` 生成提取为独立 bean 或配置，`VertxWebSocketServer` 和 `streamContainer` 共用。例如新增 `ServerIdentity` 组件，`@PostConstruct` 生成 serverId，`getServerId()` 暴露。`streamContainer` 用 `@DependsOn` 或显式注入 `ServerIdentity`。

**决策**：选方案 B，`ServerIdentity` 组件。理由：`serverId` 当前是 `VertxWebSocketServer` 的实现细节，但本任务后它成为消费侧的关键依赖，应提取为独立组件，职责清晰，且 `VertxWebSocketServer` 的 `registerServiceToRedis`/`unregisterServiceFromRedis` 也改用它。

### 3.2 `RedisEventListener.java`（消费 + ack）

**现状**（`RedisEventListener.java:39-86`）：无 ack。

**改造后**：
```java
// 新增字段
private final RedisTemplate<String, Object> redisTemplate;
private final String groupId;  // 或注入 ServerIdentity

@Override
public void onMessage(ObjectRecord<String, String> message) {
    String stream = message.getStream();
    String messageId = message.getId().getValue();
    String eventData = message.getValue();
    
    try {
        RedisStreamEvent redisEvent = objectMapper.readValue(eventData, RedisStreamEvent.class);
        
        if (!hasReceiverConnected(redisEvent.getReceiverIDs()) 
            && !hasReceiverConnected(List.of(redisEvent.getSenderID()))) {
            log.debug("接收者不在本实例, 仅 ack: {}", redisEvent.getType());
            ack(stream, messageId);  // 无接收者也要 ack
            return;
        }
        
        WebSocketMessageEvent wsEvent = ...;  // 保留
        eventPublisher.publishEvent(wsEvent);  // 保留
        
        ack(stream, messageId);  // 处理完 ack (D3)
    } catch (Exception e) {
        log.error("处理 Stream 消息异常, 留 PEL: {}", e.getMessage(), e);
        // 不 ack, 留 PEL 留痕 (但不自动重投, D1)
    }
}

private void ack(String stream, String messageId) {
    try {
        redisTemplate.opsForStream().acknowledge(stream, groupId, messageId);
    } catch (Exception e) {
        log.warn("XACK 失败: stream={}, id={}, err={}", stream, messageId, e.getMessage());
    }
}
```

### 3.3 `VertxWebSocketServer.java`（停机清理 group）

**现状**（`VertxWebSocketServer.java:227-249`）：`@PreDestroy stop()` 调 `unregisterServiceFromRedis()`。

**改造后**：在 `stop()` 中新增 group 销毁：
```java
@PreDestroy
public void stop() {
    destroyConsumerGroup();  // 新增
    unregisterServiceFromRedis();
    // ... 其余不变
}

private void destroyConsumerGroup() {
    try {
        redisTemplate.opsForStream().delete("im:message:stream", "im-msg-grp-" + serverId);
        // 注意: Spring Data Redis 的 delete Consumer 是 XGROUP DESTROY
        // 实际 API: redisTemplate.opsForStream().delete(stream, group) 
        // 或用 connection.rawCommands().xGroupDelConsumer(...)
        log.info("已销毁 consumer group: {}", "im-msg-grp-" + serverId);
    } catch (Exception e) {
        log.warn("销毁 consumer group 失败: {}", e.getMessage());
    }
}
```

> ⚠️ **API 待验证**：Spring Data Redis 对 `XGROUP DESTROY` 的支持需在实现时确认。`StreamOperations.delete(key, group)` 在某些版本是删消息，`XGROUP DESTROY` 可能需走 `RedisStreamCommands.deleteConsumerGroup` 或 `connection` 层。实现时查 Spring Data Redis 版本 API。

### 3.4 新增 `ServerIdentity` 组件

```java
@Component
public class ServerIdentity {
    private String serverId;
    
    @PostConstruct
    public void init() {
        this.serverId = "serverID_" + IdUtil.simpleUUID();
    }
    
    public String getServerId() { return serverId; }
    public String getGroupId() { return "im-msg-grp-" + serverId; }
}
```

`VertxWebSocketServer` 改用注入 `ServerIdentity`，去掉自己的 `serverId` 字段和生成逻辑。

## 4. 依赖与兼容性

### 4.1 新增依赖
- `org.testcontainers:testcontainers` + `com.redis:testcontainers-redis`（或 `org.testcontainers:testcontainers` + 手动 `GenericContainer` 起 redis 镜像）——仅 test scope

### 4.2 配置变更
- 无 `application.properties` 变更（group name 由 serverId 派生，无新配置项）

### 4.3 兼容性
- 生产侧（`RedisStreamService`）零改动
- WS 协议零改动
- `LocalSessionRegistry` 零改动
- `WebSocketMessageProcessor` 及各 `*MessageReceiver` 零改动
- Redis 数据兼容：旧 `im:message:stream` 流数据保留，新 group 从 `$` 开始（只读新消息，不重放旧消息）

### 4.4 回滚
- 回滚 = 还原 `RedisConfig.java:99-102` 为 `StreamOffset.fromStart` + 还原 `RedisEventListener` 去掉 ack + 删除 `ServerIdentity`
- Redis 里残留的 group 会随实例停机被 `XGROUP DESTROY` 清理（若回滚版本仍保留该逻辑）或手动清理

## 5. 设计决策备注

- **D1（不恢复旧 PEL）**：因 serverId 每次启动重新生成，跨 serverId 认领语义模糊，且消息已存 MongoDB 兜底
- **D2（停机销毁 group）**：与 `unregisterServiceFromRedis` 对称，异常崩溃残留 group 列 out-of-scope
- **D3（处理完 ack）**：at-most-once 语义，PEL 仅留痕不重投
- **D4（testcontainers）**：核心正确性需真实 Redis 验证
- **D5（不修 UserSessionService bug）**：本任务广播模型绕开跨实例路由，不依赖 `currentNodeIP`；该 bug 留给 `v1-multi-device-login`/`v1-user-presence` 任务修

## 6. 风险

| 风险 | 缓解 |
|---|---|
| `XGROUP DESTROY` 的 Spring Data Redis API 不确定 | 实现时查版本 API，必要时走 `RedisConnection` 底层 |
| `streamContainer` Bean 创建时 `serverId` 未就绪 | 用 `ServerIdentity` + `@PostConstruct` 保证 serverId 在 group 创建前生成 |
| testcontainers 在 CI 环境 Docker 不可用 | CI 配置 Docker-in-Docker 或用 GitHub Actions services 起 redis |
| group 创建失败导致实例启动失败 | `createGroup` catch `BUSYGROUP`，其他异常只 log 不 fail-fast（与当前 `registerServiceFromRedis` 容错风格一致） |

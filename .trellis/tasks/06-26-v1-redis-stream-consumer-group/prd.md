# v1 - Redis Stream 消费者组多实例去重

## 背景

跨实例消息转发当前用 Redis Stream（`im:message:stream`，`RedisStreamService.java:23`）。`RedisEventListener` 实现 `StreamListener`（`RedisEventListener.java:23`），容器注册为 `container.receive(StreamOffset.fromStart("im:message:stream"), listener)`（`RedisConfig.java:99-102`）——**无消费者组、无 XREADGROUP、无 XACK**。若多实例都从 `0` 读全量流，同一消息会被每个实例消费；且每次重启都从流头重放全部历史。这是横向扩展的正确性 bug，不是功能增强。

## 代码取证确认事实（brainstorm 前已查证）

### 生产侧（XADD，本任务不改）
- 唯一 XADD 入口：`RedisStreamService.publishMessage` → `opsForStream().add("im:message:stream", {message: eventJson})`（`RedisStreamService.java:42-45`）
- 事件 DTO：`RedisStreamEvent { type, senderID, message, receiverIDs }`（`RedisStreamEvent.java:12-17`）
- **关键**：生产者在 XADD 时把**全部 receiverIDs** 塞进事件体（`publishGroupMessage` 行 86-94 把全体群成员放入 `receiverIDs`；`publishPrivateMessage` 行 68-76 放入单个 receiverID）。→ **每个实例都必须看到每条消息**才能正确投递到本地接收者。这决定了消费模型必须是**广播**（每实例各读全量 + 本地过滤），而非轮询分发（单消费者组 round-robin）。
- 三个生产者：`PrivateMessageSender`、`GroupMessageSender`、`RecallMessageSender`（均调 `redisStreamService.publishXxx`）

### 消费侧（本任务改造对象）
- `RedisEventListener.onMessage`（`RedisEventListener.java:39-86`）：
  - 反序列化 `RedisStreamEvent`
  - **本地过滤**：`if (!hasReceiverConnected(receiverIDs) && !hasReceiverConnected([senderID])) return`（行 51-54）→ `hasReceiverConnected` → `sessionRegistry.hasUserOnline`（行 88-90），只查本地内存
  - `eventPublisher.publishEvent(new WebSocketMessageEvent(...))`（行 68）→ Spring 事件总线 → `WebSocketMessageProcessor` → 各 `*MessageReceiver` → `sessionRegistry.getByUserId` + `session.sendMessage`
  - **全程无 XACK**；消费完即丢，无 PEL、无重投
- **容器配置**（`RedisConfig.java:86-108`）：
  - `container.receive(StreamOffset.fromStart("im:message:stream"), redisEventListener)` —— 无 `Consumer.from(...)`，走非组模式（底层 XREAD）
  - offset = `fromStart` = `0` → 每次重启重放全部历史

### 实例身份
- `serverId = "serverID_" + IdUtil.simpleUUID()`（`VertxWebSocketServer.java:256`），**每次启动重新生成**（非持久化）
- 注册到 Redis Hash `websocket_servers`（`VertxWebSocketServer.java:257-259`）
- **当前未被消费侧使用**（消费者无 consumer name）
- 另有 `node.ip` 配置（`application.properties:23`）注入 `UserSessionService`，但有已知 `@Value` + `final` bug（`quality-guidelines.md:69,81`）

### 本地会话注册表
- `LocalSessionRegistry`（`LocalSessionRegistry.java`）：三个 `ConcurrentHashMap`，纯内存
- API：`getByUserId`、`hasUserOnline(List<String>)`、`register`、`unregister`、`cleanupInactiveSessions`
- **userId → connectionId 是一对一映射**（多端登录归 `v1-multi-device-login` 任务）

### 跨实例路由元数据（已存在，本任务可复用）
- `UserSessionService`：Redis 键 `user:sessions:{userId}:{deviceId}` → `RedisConnectionMeta { currentNodeIP, ... }`（`UserSessionService.java:31-44`）
- `isCurrentNode(nodeIP)`（行 46-48）

### 测试覆盖
- `src/test/` 下 4 个测试类，全部纯 Mockito 单元测试
- **Stream 层零覆盖**：无 `RedisEventListener`/`RedisStreamService`/`RedisConfig.streamContainer`/`WebSocketMessageProcessor`/各 `*Receiver`/`*Sender` 测试
- 无 `@SpringBootTest`、无 embedded Redis、无集成测试

## 架构决策（代码证据推导，非产品选择）

**消费模型 = 每实例独立消费者组（广播）**：
- 每个实例启动时创建自己的 consumer group（group name 绑定 serverId，如 `im-msg-grp-{serverId}`），`XGROUP CREATE ... MKSTREAM`，`BUSYGROUP` 忽略
- `XREADGROUP GROUP {group} {serverId}` 读流，每实例看到全量消息
- 本地有接收者则投递，无论是否有都 `XACK`
- group 记住 last-delivered-id → 重启不重放历史（解决 `fromStart` bug）
- PEL 存未 ack 消息 → 崩溃恢复可重投（解决无 ACK bug）

**不采用单组轮询（round-robin）**：因生产者把全部 receiverIDs 塞进事件体，每条消息必须被每个实例看到；单组 round-robin 会导致消息只到一个实例，其余实例的接收者收不到。

**跨实例路由**：广播模型下无需显式转发——每个实例都读到消息，有本地接收者的实例自然投递，无本地接收者的实例过滤后 ack。当前 `hasReceiverConnected` 过滤逻辑可保留。

## 用户旅程

> ⚠️ **占位符 —— 待头脑风暴**
>
> 待以下关键决策确定后，在此记录编号的端到端步骤，随后据此编写 e2e 测试。

## 关键决策记录

### D1：崩溃恢复 / PEL 处理（已定）

**决策**：不恢复旧 PEL，接受崩溃时在途消息丢失。

**理由**：
- `serverId` 每次启动重新生成（`VertxWebSocketServer.java:256`），新实例不会自动认领旧 serverId 的 PEL
- IM 消息投递是"尽力推送实时性"，接收方不在线时归 `v1-offline-push` 任务范畴，不靠 Stream 重投
- 消息已先持久化到 MongoDB（`PrivateMessageSender.java:57` 等），接收方上线后从历史拉取，不会真丢
- 跨 serverId 认领需 `XAUTOCLAIM` + 枚举旧 serverId，复杂度高且语义模糊

**影响**：不实现 `XAUTOCLAIM`/`XCLAIM` 逻辑；group 按需清理（见后续决策）。

### D2：孤儿 consumer group 清理（已定）

**决策**：`@PreDestroy` 时 `XGROUP DESTROY` 本实例的 group；异常崩溃残留的孤儿 group 清理列为 out-of-scope。

**理由**：
- `VertxWebSocketServer.stop()` 已有 `@PreDestroy` + `unregisterServiceFromRedis()`（`VertxWebSocketServer.java:227-232`），在同一钩子里加 `XGROUP DESTROY` 是对称操作
- 正常停机不留孤儿；异常崩溃属极端场景，定时清理可后续迭代
- 定时清理需 `XINFO GROUPS` 枚举 + 比对 `websocket_servers` 注册表 + 处理竞态，v1 复杂度过高

### D3：XACK 时机（已定）

**决策**：处理完 ack（投递链路完成后 `XACK`）。语义为 at-most-once，不靠 PEL 自动重投。

**理由**：
- 与 D1 一致——不靠 Stream PEL 做重投，PEL 仅留痕便于排查
- 投递失败（session 关闭等）由 `v1-ws-heartbeat-reconnect` 连接清理 + `v1-offline-push` 兜底
- 投递前 ack 会丢失异常痕迹；处理完 ack 至少 PEL 留痕
- `publishEvent` 同步链路完成后 ack，异常时消息留 PEL（但不自动重投）

### D4：测试策略（已定）

**决策**：引入 testcontainers-redis 写集成测试 + Mockito 单测。

**集成测试（2 个）**：
1. `RedisStreamConsumerGroupIT`：同一 stream 注册两个 consumer group（模拟两实例）→ XADD 一条 → 两 group 都收到、各 ack 各的 → 验证"广播"
2. `RedisStreamRestartIT`：group 消费到 offset N 后停 → 再启动同 group → XADD 新消息 → 只收到新消息、不重放 N 之前的 → 验证"不重放"

**单测（Mockito）**：
- `RedisEventListener` 单测：验证 ack 调用、`hasReceiverConnected` 过滤逻辑、反序列化
- `RedisConfig`/group 注册逻辑单测（如可隔离）

**理由**：
- 核心正确性（多实例广播 + group offset 不重放）必须靠真实 Redis，Mockito 模拟不了 `XREADGROUP` 的 group offset 语义
- testcontainers 是 Spring Boot 生态主流，CI 友好，嵌入式 Redis 已不维护且不可靠

### D5：UserSessionService @Value + final bug（已定，out-of-scope）

**决策**：不在本任务修，列为 out-of-scope，design.md 留备注。

**理由**：
- 本任务"广播 + 本地过滤"模型绕开了跨实例路由，不依赖 `currentNodeIP`
- 该 bug 影响的是 `v1-multi-device-login`、`v1-user-presence` 等任务，由它们自然触达修复
- 保持本任务范围聚焦

## 需求

- 启动时创建本实例的 consumer group（`XGROUP CREATE im:message:stream im-msg-grp-{serverId} $ MKSTREAM`，BUSYGROUP 忽略）。group name 绑定 serverId（非持久化，每次启动新建）。
- 容器注册改用 `container.receive(Consumer.from("im-msg-grp-{serverId}", "{serverId}"), StreamOffset.create("im:message:stream", ReadOffset.lastConsumed()), listener)`。
- 消费侧：`RedisEventListener.onMessage` 处理完成后 `XACK`（D3：处理完 ack）。本地有接收者则投递，无则仅 ack。
- `@PreDestroy` 时 `XGROUP DESTROY im:message:stream im-msg-grp-{serverId}`（D2：正常停机清理 group）。
- 不实现 `XAUTOCLAIM`/`XCLAIM`（D1：不恢复旧 PEL）。
- 在 `RedisConfig` 中声明 `StreamMessageListenerContainer` + group + consumer。
- 跨实例路由无需新增逻辑（广播模型下每实例各读全量，靠 `hasReceiverConnected` 本地过滤，无需显式转发）。

## 用户旅程

1. 实例 A 启动 → `XGROUP CREATE im:message:stream im-msg-grp-{A} $ MKSTREAM` → 从流尾开始消费（不重放历史）
2. 实例 B 启动 → 创建自己的 group `im-msg-grp-{B}` → 同样从流尾消费
3. 用户 U 通过实例 A 发私聊消息 M 给用户 V（V 连在实例 B）
   - A 的 `PrivateMessageSender` 先存 MongoDB，再 `XADD` 到 stream（receiverIDs=[V]）
   - A 的 `RedisEventListener` 读到 M → `hasReceiverConnected([V])` = false（V 不在 A），`hasReceiverConnected([U])` = true（U 在 A）→ `publishEvent` → 投递给 U 的本地 session（回显）→ `XACK`
   - B 的 `RedisEventListener` 读到 M → `hasReceiverConnected([V])` = true → `publishEvent` → 投递给 V 的本地 session → `XACK`
4. 实例 A 正常停机 → `@PreDestroy` → `XGROUP DESTROY im:message:stream im-msg-grp-{A}`
5. 实例 A 崩溃（kill -9）→ group `im-msg-grp-{A}` 残留 + 其 PEL 残留 → 不自动恢复（D1）；崩溃时在途消息靠 MongoDB 历史兜底

## e2e 测试场景

1. **双实例广播**：两 consumer group 各自 `XREADGROUP` 同一 stream，`XADD` 一条消息后两 group 都收到、各 ack 各的，无重复、无遗漏
2. **重启不重放**：group 消费到 offset N 后停止消费 → 再以同 group 名消费 → 只收到 N 之后的新消息，不重放 N 之前
3. **本地过滤**：消息 receiverIDs 不含本实例任何在线用户且 sender 也不在 → 不 `publishEvent`，但仍 `XACK`
4. **正常停机清理 group**：实例停机后 `XGROUP DESTROY` 被调用，group 从 Redis 消失

## 测试范围

**集成测试（testcontainers-redis）**：
- `RedisStreamConsumerGroupIT`：场景 1（双实例广播）+ 场景 3（本地过滤 ack）
- `RedisStreamRestartIT`：场景 2（重启不重放）

**单元测试（Mockito）**：
- `RedisEventListenerTest`：ack 调用、`hasReceiverConnected` 过滤分支、反序列化、异常时仍尝试 ack
- group 注册/销毁逻辑（若可从 `RedisConfig` 隔离）

**不测试**：
- 异常崩溃的孤儿 group（out-of-scope）
- `XAUTOCLAIM`/PEL 恢复（不实现）
- 跨实例显式路由（不实现）

## 验收标准

- [ ] 双实例各自 consumer group 读同一 stream，同一消息被两实例各收一次、各 ack 一次，在线用户无重复投递
- [ ] 重启后 group 从 last-delivered-id 续读，不重放历史
- [ ] `@PreDestroy` 正确销毁 group
- [ ] `RedisEventListener.onMessage` 处理完成后调用 `XACK`
- [ ] 单元测试（Mockito）+ 集成测试（testcontainers）全绿
- [ ] 协议字段无新增（本任务不改 WS 协议）

## 依赖

- 与 [[v1-ws-heartbeat-reconnect]] 并行可启动；本任务解决分发正确性，不依赖心跳。
- [[v1-message-ack-read]] 的多实例回执路由依赖本任务完成。

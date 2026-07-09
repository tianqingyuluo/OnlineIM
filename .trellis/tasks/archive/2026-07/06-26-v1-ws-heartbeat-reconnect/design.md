# Design — WebSocket 心跳与断线重连

> 配套 `prd.md`。本文记录技术设计：架构边界、数据流与契约、兼容性、权衡、运维/回滚。精确方法签名在 `implement.md` 前置核查步骤中确认。

## 架构与边界

### 后端（`onlineIM-server`）

- **控制帧分发**：`HEARTBEAT` 是控制帧，**不进**业务 `MessageTypeSenderRegistry`（该 registry 处理 PRIVATE/GROUP/RECALL/NOTICE 的 Sender/Receiver 对）。在 `WebSocketMessageRouter` 中于业务分发前短路：若 `type==HEARTBEAT` → 调 `HeartbeatHandler` 处理。落点（独立 handler 类 vs router 内联）由 implement 前置核查 1 定。
- **`WebSocketSession`** 加 `lastActiveAt`（`long` epoch millis）。刷新时机：在 `textMessageHandler` 入口、路由前统一刷新（任何入站帧都算活跃），而非只在心跳帧刷新——这样业务消息也保活。
- **`LocalSessionRegistry.cleanupInactiveSessions()`** 现有判据是 `isActive()`（查 socket 状态）。改为以 `lastActiveAt` 为主判据：`now - lastActiveAt > timeout` → `close()` + unregister；保留 `isActive()==false` 兜底（socket 已半关闭的孤儿）。该方法已存在，只是从未被调度——本次接上调度器。
- **调度器**：`VertxWebSocketServer` 是 Vert.x 组件，但本身是 Spring `@Component`。用 Spring `@Scheduled(fixedDelay=...)` 起扫描任务最简；或 Vert.x `vertx.setPeriodic`。选型见 implement。扫描间隔 = `timeout/3`（默认 30s），保证 90s 阈值下最多延迟 30s 发现死会话。
- **`UserSessionService`**：收到 HEARTBEAT 时调其刷新 `user:sessions:{userId}:{deviceId}` 的 `activeTime`（一次 HSET/expire）。方法签名见前置核查 3。
- **配置**：`application.properties` 加 `websocket.heartbeat.interval=30s` / `websocket.heartbeat.timeout=90s`，`@Value` 注入（沿用现有 `websocket.port`/`websocket.path` 风格）。v1 静态配置，调参需重启（局限性见下）。

### 前端（`OnlineIM-Vue`）

- **`websocketStore`**：`isConnected:boolean` → `connectionState` 4 态枚举（`connecting`/`online`/`reconnecting`/`offline`）+ getter；更新既有消费处。
- **`websocket.service.ts`**（单例）新增三块：
  - **心跳发送器**：`setInterval(interval)` 发 `HEARTBEAT`；`HEARTBEAT_ACK` 看门狗——连续 2 周期未收 ACK → `close()` + 转 `reconnecting`。
  - **重连管理器**：状态机 + 指数退避（1→2→4→8→16→30s，±25% 抖动）+ 5 次转 `offline` + 手动"立即重试" + 成功重置。
  - **出站队列 + 补齐**：断连时入队 IndexedDB；`onopen` 成功后 flush 队列（按 seq）+ 对活跃会话调 `/sync`。
- **`utils/indexedDB.ts`**：新增 `outbound_queue` object store（复用既有 `pending_messages` 模式），per-conversation、单会话上限 50。
- **`ConnectionStatusBadge.vue`**（新组件）：顶栏状态点+文案+离线态"立即重试"按钮，挂 `Main.vue` header。
- **头像灰度**：定位既有头像组件（前置核查 6），绑 `connectionState∈{reconnecting,offline}` → `filter:grayscale(1)`。
- **vue-sonner toast**：断开/恢复/转入离线三处关键转换。

## 数据流与契约

### HEARTBEAT 帧

```json
// client → server
{"type":"HEARTBEAT","message":{}}
// server → client
{"type":"HEARTBEAT_ACK","message":{"serverTime":<epoch millis>}}
```

- 复用既有 `{type,message}` 信封，无新传输层。
- `HEARTBEAT` **不持久化**、**不进 Redis Stream**、**不进业务 registry**——纯控制路径。

### 出站队列

- IndexedDB store `outbound_queue`，键 `(conversationId, clientLocalId)`。字段：`conversationId` / `type`(PRIVATE|GROUP) / `payload` / `clientLocalId` / `status`(pending|sent) / `createdAt`。
- 断连发送：入队 → UI 立即渲染"待发送"（时钟图标，clientLocalId 关联）。
- 重连 flush：按会话、按 clientLocalId/seq 顺序逐条发 `*_MESSAGE_REQUEST`；收到 `*_MESSAGE_RESPONSE` → 标 `sent`。
- 上限：单会话 50，超限 reject + toast"消息过多，请稍后"。

### 重连补齐

- `onopen` 成功后：对 `activeConversationId` 取本地 maxSeq → `GET /api/v1/messages/sync/{convId}?seq_id=<maxSeq>` → 合并 `history` store + IndexedDB。
- 非活跃会话：用户切换时走既有 `loadInitialHistory` 间隙填充，不在此触发。

## 兼容性

- 控制帧纯增量，现有 4 类业务帧路径不动。`WebSocketMessageRouter` 对未知 `type` 必须容错（不抛、记 warn 日志）——前置核查 2 确认；若现不容错则补。
- `WebSocketSession` 加字段为向后兼容（新字段默认 0/null）。
- Redis `activeTime` 刷新复用既有 `UserSessionService` 键结构，无 schema 变更。
- `application.properties` 新键有默认值，非破坏性。
- **滚动升级注意**：旧客户端不发心跳 → 90s 后被服务端判死、强制断连 → 触发重连循环。v1 三端同步升级，可接受；协议清单登记时注明 HEARTBEAT 为 v1 必需帧。

## 权衡

- **应用层 HEARTBEAT vs 原生 ping/pong**：选应用层，因浏览器/Flutter 的 JS/Dart 层无法发原生 ping。代价：每 30s 一帧 + 自定义处理；换来三端统一。值得。
- **本地扫描 vs Redis**：每实例只扫本地持有的 socket（正确模型），心跳顺带刷 Redis `activeTime` 为 task 3/7/14 铺路。代价：每 30s/session 一次 Redis 写，可忽略。
- **ACK 纯活性 vs 带 lastSeq**：选纯 ACK（解耦）。客户端用本地 seq + `/sync`。代价：重连只立即补活跃会话，其他懒加载。可接受（Q7）。
- **出站队列上限 50**：防无限堆积；代价：长断网可能触上限——已 toast 提示。
- **静态配置 vs 运行时调参**：v1 用 `@Value` 静态配置，调参需重启。后续若需热调可加 `@RefreshScope`/actuator，不在本任务。

## 运维 / 回滚

- **回滚开关**：设 `websocket.heartbeat.timeout` 极大值（如 24h）即可实质禁用超时断连，退回现状。前端重连器可由配置/flag 关闭退回 `isConnected` 布尔。
- **测试与生产阈值分离**：test profile 用缩短阈值（如 `interval=1s`/`timeout=3s`）快进，避免测试慢且不污染生产默认。
- **监控**：`/health` 已返回连接数；可选加"超时关闭会话计数"指标（非本任务硬要求）。

## 风险点

- 调度器选型（Spring `@Scheduled` vs Vert.x `setPeriodic`）——implement 定。
- 测试时间快进依赖可配短阈值（test profile）。
- `userIdToConnectionId` 1:1 覆盖问题（task 7 范畴）：本任务扫描按 `connectionId` close、不按 userId，不会恶化该问题。
- 多实例各扫本地正确，但需确保扫描不误关本实例活跃会话——`lastActiveAt` 是本实例本地值，安全。

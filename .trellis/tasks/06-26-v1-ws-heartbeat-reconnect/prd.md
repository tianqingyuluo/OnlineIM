# v1 - WebSocket 心跳与断线重连

## 背景

当前 `VertxWebSocketServer` 已有 `closeHandler`/`exceptionHandler`，但没有客户端心跳（ping/pong）与服务端保活超时检测。长连接在网络抖动、NAT 超时、移动端切后台等场景会"假死"——连接看似在线实则不通，消息投递失败且无重连。

## 代码现状（已确认 · 头脑风暴前探查）

后端（`onlineIM-server`，Spring Boot 3.4.4 / Java 21 / Vert.x 4.5.11）：

- `VertxWebSocketServer`（`websocket/server/`）：`@PostConstruct` 同步启动，侦听 8081；挂了 `textMessageHandler`→`WebSocketMessageRouter`、`closeHandler`、`exceptionHandler`。**无任何 ping/pong/保活/超时逻辑**，仅有 TCP 级 `setTcpKeepAlive(true)`（无法检测应用层假死）。JWT 经 `?token=Bearer <jwt>` 查询参认证。
- `WebSocketSession`（`websocket/session/`）：字段 `connectionId / userId / deviceId / token / socket`。**无 `lastActiveAt`**，无活动时间戳。
- `LocalSessionRegistry`（`websocket/registry/`）：纯内存 `ConcurrentHashMap`，**仅本实例可见**。`userIdToConnectionId` 为**一对一**——同用户第二设备登录会覆盖第一设备的用户映射（属 [[v1-multi-device-login]] 范畴，本任务不修）。`cleanupInactiveSessions()` 方法**已存在但从未被调用**（无调度器）。
- 消息协议：`{"type":"<TYPE>","message":{<payload>}}`，经 `WebSocketMessageRouter`→`MessageTypeSenderRegistry` 分发。现有 4 类业务帧：`PRIVATE_MESSAGE_*` / `GROUP_MESSAGE_*` / `RECALL_MESSAGE_*`(含 `MESSAGE_RECALLED`) / `NOTICE_MESSAGE_*`。**无任何控制帧**。
- 漏收补齐 API **已存在**：`GET /api/v1/messages/sync/{conversationId}?seq_id=<seq>` 返回 seqId 严格大于该值的消息（`MessageServiceImpl.syncMessages`）。`seqId` 为 Hutool Snowflake 字符串。
- 多实例：Redis Stream `im:message:stream` 是跨实例消息总线，但 `RedisEventListener.hasReceiverConnected()` **只查本地注册表**——接收方在别实例时消息**静默丢弃**。`UserSessionService` 已在 Redis 存 `user:sessions:{userId}:{deviceId}` → `RedisConnectionMeta`（含 `activeTime` / `currentNodeIP`），`VertxWebSocketServer` 自注册到 `websocket_servers` hash。跨实例路由/去重属 [[v1-redis-stream-consumer-group]] 范畴。

前端（`OnlineIM-Vue`，Vue 3.5 + Vite 6 + TS，原生 WebSocket）：

- `websocket.service.ts`：单例，`onopen`→`isConnected=true`，`onclose`/`onerror`→`isConnected=false`。`onclose` 处有注释 `// Implement auto-reconnect logic here if needed` **但未实现**。`onmessage` 仅处理 `PRIVATE_MESSAGE_RESPONSE` / `GROUP_MESSAGE_RESPONSE`。
- `isConnected` ref 已暴露到 Pinia store，但**无任何组件消费它**——UI 无连接状态指示。
- `loadInitialHistory()`（`stores/history.ts`）在**初始化时**用 IndexedDB `pending_messages`（`min_seq_id`/`max_seq_id` 间隙）调 `/sync` 补齐；**重连后不触发**。
- **零测试基建**：前端 `package.json` 无 vitest/jest/playwright/cypress、无 test 脚本、无 spec 文件；后端无 `src/test` 目录（`spring-boot-starter-test` 在 pom 但未用）。

## 已确认的设计决策（基于代码约束，无需再问）

- **机制：应用层 `HEARTBEAT`（客户端→服务端）+ `HEARTBEAT_ACK`（服务端→客户端）文本帧**，不复用 WS 原生 ping/pong。理由：浏览器 `WebSocket` 与 Flutter `web_socket_channel` 的 JS/Dart 层**无法发送原生 ping 帧**（浏览器仅自动回 pong，JS 不可主动发 ping/读 pong），故客户端发起的活性检测必须走应用层文本帧，才能在 Web/Tauri/Flutter 三端统一。
- `WebSocketSession` 新增 `lastActiveAt`（任何入站帧含心跳均刷新）。
- 将既有的 `cleanupInactiveSessions()` 接到定时调度器，扫描超时会话 `close()` + 清理。
- 客户端新增重连器（退避 + 状态机），重连成功后对活跃会话触发既有 `/sync` 补齐漏收。
- 心跳作为独立 control 帧，**不改动现有 4 类业务帧**。

## 已敲定的头脑风暴结论

### Q1 · 测试基建 scope（已确认）

- task 1 内置搭建**最小可用**测试基建，不拆独立 task。
- **后端**：`spring-boot-starter-test` + 嵌入式 Vert.x 苓一个 8081 WS 端点的集成测试基类，能模拟多客户端连/断/发心跳/验超时；该基类复用给后续所有 WS 相关 child。
- **前端**：暂不引入 Playwright/Cypress；引入 vitest 做重连器纯逻辑单测（退避公式、状态机迁移）；连接状态 UI 用手动验证。
- **e2e 语义**：本任务 e2e 由后端集成测试覆盖"心跳保活→超时断连→重连→/sync 补齐"全链路，不依赖真实浏览器。
- **前端 e2e 框架选型**（Playwright vs Cypress）拆为独立决策点，不在本任务仓促定；推迟到真正需要时（如 task E 客户端对接）。

### Q2 · 多实例活性边界（已确认）

- 心跳**顺带刷新** Redis `user:sessions:{userId}:{deviceId}` 的 `activeTime`（一次 `HSET`/`expire`，开销可忽略），但**不做**跨实例会话发现/路由。
- 超时扫描**仍只扫本地 `LocalSessionRegistry`**：会话是本实例持有的本地 socket 对象，只有持有它的实例能 `close()`；每实例各扫各的、无冲突。
- 不在 task 1 做：跨实例消息路由、Redis Stream 消费者组改造、别实例会话查找——归 [[v1-redis-stream-consumer-group]]。

### Q3 · 心跳周期与死亡阈值（已确认）

- 服务端：ping 间隔 **30s**、死亡阈值 **90s**（3×，容忍丢 2 个心跳）；做成 `application.properties` 可配：`websocket.heartbeat.interval=30s` / `websocket.heartbeat.timeout=90s`。
- 客户端：发出 `HEARTBEAT` 后连续 2 个周期（≈60s）未收到 `HEARTBEAT_ACK`，判定连接已死，主动 `close()` 进入重连。
- 不做移动端后台差异化（本任务端 = 后端 + Vue Web）；后台 timer 节流致心跳变慢、被服务端判死、前端恢复时重连+sync 即期望行为。Flutter 原生后台保活归 task E。

### Q4 · 重连退避与终态（已确认）

- 指数退避 + ±25% 抖动：1s → 2s → 4s → 8s → 16s → **30s 封顶**。
- 封顶后无限重试（每 30s 一次），**永不硬放弃**。
- 连续失败 **5 次**（≈31s+抖动）后 UI 转"离线（自动重连中）"并露"立即重试"按钮；后台仍持续重试。
- 重连成功：重置退避计数器、触发 `/sync` 补齐、UI 回"在线"。
- 手动"立即重试"：立即发起连接，成功则重置退避调度。

### Q5 · 连接状态 UI（已确认）

- `connectionState` 4 态枚举：`connecting`（首次连接中）/ `online`（在线）/ `reconnecting`（重连中，<5 次）/ `offline`（离线，≥5 次，可点"立即重试"）。把现有 `isConnected`（布尔）升级为 4 态，暴露到 Pinia store。
- 持久指示：`Main.vue` 顶栏一个状态点 + 文案——绿"在线" / 黄"重连中…" / 灰"离线"，低调常驻；离线态文案可点击触发"立即重试"。
- 转换 toast（vue-sonner，仅关键转换弹）：断开→"连接已断开，正在重连"；恢复→"已重新连接"；转入离线→"网络离线，将自动重连"。
- 不做全屏遮罩/阻断式 UI——重连不阻塞已缓存会话浏览。
- 新增轻量 `ConnectionStatusBadge.vue` 消费 `connectionState`。
- **头像灰度**：`connectionState ∈ {reconnecting, offline}` 时对所有头像施 `filter: grayscale(1)`，作为"presence/在线状态可能陈旧、不可信"的附加视觉线索（在线/连接中不灰）。具体头像组件位置在 design 阶段定位。

### Q6 · 断连期间待发消息（已确认）

- **入队 + 重连后补发**：断连时发送的消息写入 IndexedDB 出站队列，UI 显示"待发送"（时钟图标），不阻塞继续打字/切会话。
- 重连成功后按会话、按 seq 顺序 flush 出站队列，逐条发 `PRIVATE_MESSAGE_REQUEST`/`GROUP_MESSAGE_REQUEST`。
- 状态流转：`pending`（待发送）→ `sent`（服务端已受理）。服务端受理确认复用既有 `PRIVATE_MESSAGE_RESPONSE`；若现无 sender 侧回执，design 阶段核定，必要时加最小"已受理"回执。
- 边界：`sent`→`delivered`→`read` 属 [[v1-message-ack-read]]，不在本任务。
- 出站队列单会话上限 50 条，超限提示"消息过多，请稍后"，防无限堆积。

### Q7 · `HEARTBEAT_ACK` 载荷与重连补齐策略（已确认）

- `HEARTBEAT_ACK` **纯活性回执**，只回可选 `serverTime`（诊断/时钟偏差用），**不带** per-conversation lastSeq。心跳职责单一、帧轻量。
- 重连成功后：客户端对**当前打开的活跃会话**用本地 lastSeq 调 `/sync` 立即补齐。
- 其他会话：依赖既有"打开即补"的 `loadInitialHistory` 间隙填充懒加载；非活跃会话未读计数/上线主动推送属 [[v1-offline-push]]。
- `/sync` 返回 seqId>本地 的消息，无新增则空（开销小）。

## 用户旅程

- **UJ-1 首次连接**：用户登录 → Web 端 `websocket.service.connect()` → `connectionState=connecting` → WS 握手成功 → `connectionState=online`，顶栏状态点变绿。
- **UJ-2 在线保活**：在线期间客户端每 30s 发 `HEARTBEAT`；服务端收到后刷新本地 `lastActiveAt` + Redis `activeTime`，回 `HEARTBEAT_ACK`（含 `serverTime`）；客户端收到 ACK 重置"未收到 ACK 计数"。
- **UJ-3 服务端超时断连（假死/丢心跳）**：客户端因网络中断停止发心跳；服务端扫描器发现某会话 `lastActiveAt` 超 90s 未刷新 → `close()` 该会话 + 清理本地注册表 + Redis 标记离线。
- **UJ-4 客户端自检断连**：客户端发出 `HEARTBEAT` 后连续 2 周期（≈60s）未收到 `HEARTBEAT_ACK` → 判定连接已死 → 主动 `close()` → `connectionState=reconnecting`，顶栏点变黄"重连中…"，头像变灰度。
- **UJ-5 自动重连退避**：客户端按 1s→2s→4s→8s→16s→30s（±25% 抖动）依次重试，每次失败累加计数。
- **UJ-6 转入离线态**：连续失败 5 次后 `connectionState=offline`，顶栏变灰"离线"露"立即重试"按钮，弹 toast"网络离线，将自动重连"；后台仍每 30s 重试。
- **UJ-7 断连期间发送**：用户断连期间发消息 → 写入 IndexedDB 出站队列 → UI 显示"待发送"（时钟图标）；单会话队列达 50 条上限时提示"消息过多，请稍后"。
- **UJ-8 重连成功补齐**：网络恢复、某次重连成功 → `connectionState=online`，点变绿、头像恢复彩色、弹 toast"已重新连接"、退避计数重置；客户端对当前活跃会话用本地 lastSeq 调 `/sync` 补齐漏收；按序 flush 出站队列，逐条发业务帧，收到 `*_MESSAGE_RESPONSE` 后状态 `pending→sent`。
- **UJ-9 手动立即重试**：离线态用户点"立即重试" → 立即发起一次连接，成功则重置退避调度、走 UJ-8 补齐流程。
- **UJ-10 非活跃会话懒补齐**：用户切到其他会话 → 该会话走既有 `loadInitialHistory` 间隙填充补齐（不依赖重连触发）。

## 需求

- 新增 WS control 帧 `HEARTBEAT`（客户端→服务端）与 `HEARTBEAT_ACK`（服务端→客户端，载荷仅可选 `serverTime`），经 `WebSocketMessageRouter` 分发，不改动现有 4 类业务帧。
- `WebSocketSession` 新增 `lastActiveAt`，任意入站帧（含心跳）刷新；服务端收到 `HEARTBEAT` 同时刷新本地 `lastActiveAt` 与 Redis `user:sessions:{userId}:{deviceId}` 的 `activeTime`。
- 服务端用定时调度器（间隔 < 死亡阈值）调用既有 `cleanupInactiveSessions()`，对 `lastActiveAt` 超过 `websocket.heartbeat.timeout`（默认 90s）的会话 `close()` + 清理。
- 客户端重连器：30s 间隔发 `HEARTBEAT`；连续 2 周期未收 `HEARTBEAT_ACK` 判死；指数退避 1→2→4→8→16→30s（±25% 抖动）封顶无限重试；连续 5 次失败转离线态；支持手动"立即重试"。
- 客户端 `connectionState` 4 态 + `ConnectionStatusBadge.vue` 顶栏指示 + vue-sonner 转换 toast + 断开态头像 `grayscale(1)`。
- 客户端断连期间出站消息入 IndexedDB 队列（单会话上限 50），重连后按 seq flush，状态 `pending→sent`；重连后对活跃会话调 `/sync` 补齐。
- `application.properties` 新增 `websocket.heartbeat.interval=30s` / `websocket.heartbeat.timeout=90s`。

## e2e 测试场景

> 全部由后端集成测试（嵌入式 Vert.x 8081 基类）覆盖，不依赖真实浏览器；前端重连器逻辑由 vitest 单测覆盖、UI 由手动验证。测试用缩短后的心跳/超时阈值快进。

- **E2E-1 心跳保活**（UJ-1,UJ-2）：客户端连上 8081，发 `HEARTBEAT`，断言收到 `HEARTBEAT_ACK` 且服务端 `lastActiveAt` 与 Redis `activeTime` 均被刷新；持续在线超过一个死亡阈值周期不断连。
- **E2E-2 服务端超时断连**（UJ-3）：客户端连上后停止发心跳，快进 >90s（测试阈值）；断言服务端 `close()` 该会话、本地注册表移除、Redis 标记离线。
- **E2E-3 客户端自检断连**（UJ-4）：服务端模拟不回 `HEARTBEAT_ACK`；客户端连续 2 周期未收 ACK → 主动 `close()`、状态转 `reconnecting`。
- **E2E-4 重连退避与恢复**（UJ-5,UJ-8）：断连后客户端按指数退避重试；恢复服务端后某次重连成功 → 状态回 `online`、退避重置、对活跃会话调 `/sync` 收到漏收消息、出站队列 flush 成功。
- **E2E-5 转入离线态 + 手动重试**（UJ-6,UJ-9）：连续失败 5 次后状态转 `offline`；点"立即重试"成功后恢复 online。
- **E2E-6 断连期间入队补发**（UJ-7,UJ-8）：断连期间发 3 条消息 → 队列有 3 条、UI 待发送；重连后按序 flush、收到 RESPONSE 后转 sent。
- **E2E-7 非活跃会话懒补齐**（UJ-10）：切到另一会话触发间隙填充补齐漏收。
- **E2E-8 出站队列上限**（UJ-7）：断连期间发第 51 条 → 被拒并提示"消息过多"。

## 测试范围

后端单元测试：

- `WebSocketMessageRouter` 对 `HEARTBEAT` 类型的解析与分发。
- `WebSocketSession.lastActiveAt` 在任意入站帧后刷新。
- `LocalSessionRegistry.cleanupInactiveSessions()` 超时判定（活跃保留、超时移除）。
- 收到 `HEARTBEAT` 后刷新 Redis `activeTime` 的 `UserSessionService` 调用。

后端集成测试（嵌入式 Vert.x 8081 基类，复用给后续 child）：

- E2E-1 ~ E2E-3、E2E-8 的服务端行为（模拟客户端连/断/发心跳/快进时间）。
- 超时扫描调度器按配置间隔触发。

前端单元测试（vitest）：

- 重连器退避公式（1→2→4→8→16→30 + ±25% 抖动边界）。
- `connectionState` 状态机迁移（connecting→online→reconnecting→offline→online；手动重试路径）。
- 出站队列入队/flush/上限 50 逻辑。
- 客户端"连续 2 周期未收 ACK→判死"逻辑。

前端手动验证（无 e2e 框架）：

- 顶栏 `ConnectionStatusBadge` 4 态点颜色与文案。
- 关键转换 toast（断开/恢复/转入离线）。
- 头像灰度在 reconnecting/offline 生效、online 恢复彩色。
- 离线态"立即重试"按钮可点且生效。

## 验收标准

- [ ] `HEARTBEAT`/`HEARTBEAT_ACK` 协议端到端工作（E2E-1 通过）
- [ ] 服务端 90s 超时断连可复现（E2E-2 通过）
- [ ] 客户端 60s 未收 ACK 自检断连可复现（E2E-3 通过）
- [ ] 重连退避恢复 + `/sync` 补齐 + 出站队列 flush 端到端通过（E2E-4、E2E-6 通过）
- [ ] 离线态 + 手动立即重试可用（E2E-5 通过）
- [ ] 出站队列 50 条上限生效（E2E-8 通过）
- [ ] UI 4 态指示、转换 toast、头像灰度、立即重试按钮手动验证通过
- [ ] 后端单元+集成测试全绿，前端 vitest 单测全绿
- [ ] 心跳作为独立 control 帧，现有 4 类业务帧行为无回归
- [ ] `websocket.heartbeat.interval`/`timeout` 可配且默认 30s/90s

## 依赖

无前置依赖，建议作为 v1 最先启动的地基任务。后续所有依赖稳定长连接的功能（ACK/已读、离线推送）在本任务完成后启动。

## 范围边界（明确不做）

- 跨实例消息路由 / Redis Stream 消费者组改造 / 别实例会话查找 → [[v1-redis-stream-consumer-group]]。
- `sent`→`delivered`→`read` 已读/送达确认 → [[v1-message-ack-read]]。
- 非活跃会话未读计数 / 上线主动推送 → [[v1-offline-push]]。
- 多设备会话映射（`userIdToConnectionId` 一对一覆盖问题）→ [[v1-multi-device-login]]。
- 用户在线状态聚合呈现 → [[v1-user-presence]]。
- Flutter 原生后台保活 / Tauri+Flutter 客户端 → [[v1-tauri-flutter-clients]]。
- 前端 e2e 框架（Playwright/Cypress）选型 → 推迟到真正需要时单独立项。

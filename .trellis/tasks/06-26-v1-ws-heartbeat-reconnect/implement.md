# Implement — WebSocket 心跳与断线重连

> 配套 `prd.md` + `design.md`。有序实施清单、验证命令、风险/回滚点、`task.py start` 前检查。

## 前置核查（编码前完成）

1. 读 `WebSocketMessageRouter` + `MessageTypeSenderRegistry` + 任一 `*Receiver` handler，确认控制帧分发模式：`HEARTBEAT` 走独立控制路径还是塞进既有 registry。定 `HeartbeatHandler` 落点。
2. 确认 `WebSocketMessageRouter` 对未知 `type` 的容错（不抛异常、记 warn）；若现不容错则补。
3. 读 `UserSessionService`，找刷新 `user:sessions:{userId}:{deviceId}` 的 `activeTime` 的方法名/签名。
4. 读 `application.properties` 确认键风格，绑定用 `@Value` 还是 `@ConfigurationProperties`。
5. 读前端 `websocket.service.ts` / `websocketStore.ts` / `utils/indexedDB.ts`，确认重构切入点与 `pending_messages` 既有结构。
6. 定位头像组件（消费 `connectionState` 做灰度的挂载点）。

## 实施清单（按序）

### 后端

1. `WebSocketSession` 加 `lastActiveAt`（`long`）；`textMessageHandler` 入口、路由前统一刷新。
2. 新增 `HEARTBEAT`/`HEARTBEAT_ACK` 类型常量；`HeartbeatHandler`：收 `HEARTBEAT` → 刷新 `lastActiveAt` + 调 `UserSessionService` 刷 Redis `activeTime` → 回 `HEARTBEAT_ACK{serverTime}`。
3. `WebSocketMessageRouter` 在业务分发前短路 `HEARTBEAT` → `HeartbeatHandler`（独立控制路径，不进业务 registry）。
4. `LocalSessionRegistry.cleanupInactiveSessions()` 改判据：`now - lastActiveAt > timeout` → `close()` + unregister；保留 `isActive()==false` 兜底。
5. 调度器（选型见前置）：`@Scheduled(fixedDelay)` 或 `vertx.setPeriodic`，间隔 = `timeout/3`，调 `cleanupInactiveSessions()`。
6. `application.properties` 加 `websocket.heartbeat.interval=30s` / `websocket.heartbeat.timeout=90s`；`@Value` 注入。
7. **测试基建**：建 `onlineIM-server/src/test/...`，嵌入式 Vert.x 8081 集成测试基类（起 server + 模拟 `WebSocketClient` 连/断/发帧/快进）；test profile 缩短阈值（`interval=1s`/`timeout=3s`）。
8. 后端单测：router 分发 `HEARTBEAT`、`lastActiveAt` 刷新、`cleanupInactiveSessions` 判定、heartbeat 刷 Redis `activeTime` 调用。
9. 后端集成测试：E2E-1 ~ E2E-3、E2E-8 服务端行为。

### 前端

10. `websocketStore`：`isConnected:boolean` → `connectionState` 4 态枚举 + getter；更新既有消费处。
11. `websocket.service.ts`：
    - 心跳发送器（`setInterval(30s)` 发 `HEARTBEAT`）；
    - ACK 看门狗（连续 2 周期未收 → `close()` + 转 `reconnecting`）；
    - 重连管理器（指数退避+±25% 抖动+30s 封顶+5 次转 `offline`+手动"立即重试"+成功重置）；
    - `onopen`→`online`+触发补齐；`onclose`/`onerror`→启动重连。
12. 出站队列：`utils/indexedDB.ts` 加 `outbound_queue` store；`websocket.service` 断连时入队（单会话上限 50，超限 toast）；重连后按 seq flush，收到 `*_MESSAGE_RESPONSE` 标 `sent`。
13. 重连补齐：`onopen` 成功后对 `activeConversationId` 调 `/sync`，合并 `history` store + IndexedDB。
14. `ConnectionStatusBadge.vue`（新组件）：顶栏状态点+文案+离线态"立即重试"按钮；挂 `Main.vue` header。
15. 头像灰度：定位头像组件，绑 `connectionState∈{reconnecting,offline}` → `grayscale(1)` class。
16. vue-sonner 转换 toast：断开/恢复/转入离线三处。
17. **前端测试基建**：装 `vitest` + `@vue/test-utils`，加 `test` 脚本；单测：退避公式、状态机迁移、出站队列、判死逻辑。

### 协议登记

18. 在 v1 协议清单（文档/wikicode）登记 `HEARTBEAT` / `HEARTBEAT_ACK` 控制帧（parent PRD 约束 4）。

### 手动验证

19. 起 `npm run dev-all` + 后端，断网/恢复走 UJ-1 ~ UJ-10；核对 4 态点颜色/文案、toast、头像灰度、立即重试。

## 验证命令

- 后端：`cd onlineIM-server && ./mvnw test`
- 前端：`cd OnlineIM-Vue && npm run test`（新增脚本）+ `npm run build`（vue-tsc 类型检查）
- 手动：`cd OnlineIM-Vue && npm run dev-all` + 起后端 8081

## 风险 / 回滚点

- **关键文件**：`VertxWebSocketServer.java`、`WebSocketSession.java`、`LocalSessionRegistry.java`、`WebSocketMessageRouter.java`、`UserSessionService.java`、`application.properties`、`websocket.service.ts`、`websocketStore.ts`、`utils/indexedDB.ts`、`Main.vue`。
- **回滚**：设 `websocket.heartbeat.timeout` 极大值即禁用超时断连；前端重连器由 flag 关闭退回 `isConnected` 布尔。
- **测试/生产阈值分离**：test profile 用短阈值，避免测试慢、不污染生产默认。

## `task.py start` 前检查

- [ ] `prd.md` / `design.md` / `implement.md` 已评审
- [ ] 前置核查 1 ~ 6 完成（handler 落点、router 容错、`UserSessionService` 签名、配置绑定、前端切入点、头像组件位置）
- [ ] test profile 缩短阈值方案确定

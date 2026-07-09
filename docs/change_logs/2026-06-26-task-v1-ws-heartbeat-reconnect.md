# 2026-06-26 任务完成记录 — ws-heartbeat-reconnect

## 任务信息

- **任务 ID**：v1-ws-heartbeat-reconnect
- **标题**：WebSocket 心跳与断线重连
- **状态**：实现与自动化验收完成

## 变更范围

### 后端（onlineIM-server）

| 文件 | 变更类型 | 说明 |
|------|----------|------|
| `websocket/session/WebSocketSession.java` | 修改 | 新增 `lastActiveAt` 字段（volatile long）、`refreshLastActiveAt()`、`isHeartbeatTimeout()` 方法 |
| `websocket/handler/HeartbeatHandler.java` | 新增 | 心跳控制帧处理器，刷新 lastActiveAt + Redis activeTime + 回复 HEARTBEAT_ACK |
| `websocket/handler/WebSocketMessageRouter.java` | 修改 | 入站帧统一刷新 lastActiveAt；HEARTBEAT 短路到 HeartbeatHandler（不进业务 registry） |
| `websocket/registry/LocalSessionRegistry.java` | 修改 | cleanupInactiveSessions 增加 lastActiveAt 超时判据；@PostConstruct 启动定时清理调度器 |
| `service/UserSessionService.java` | 修改 | 新增 `refreshActiveTime()` 方法（read-modify-write Redis RedisConnectionMeta） |
| `application.properties` | 修改 | 新增 `websocket.heartbeat.interval=30s` / `websocket.heartbeat.timeout=90s` |

### 后端测试

| 文件 | 变更类型 | 说明 |
|------|----------|------|
| `src/test/.../websocket/session/WebSocketSessionTest.java` | 新增 | 9 个单测：lastActiveAt 初始化/刷新/超时判定、isActive、sendMessage |
| `src/test/.../websocket/handler/HeartbeatHandlerTest.java` | 新增 | 7 个单测：刷新 lastActiveAt、调 refreshActiveTime、发 ACK、Redis 故障容错 |
| `src/test/.../websocket/handler/WebSocketMessageRouterTest.java` | 新增 | 6 个单测：HEARTBEAT 短路、lastActiveAt 刷新、业务帧走 registry、未知类型发 ERROR |
| `src/test/.../websocket/registry/LocalSessionRegistryTest.java` | 新增 | 10 个单测：注册/注销/查询/cleanup 超时断连/socket close |
| `src/test/resources/application-test.properties` | 新增 | 测试 profile 缩短心跳阈值（interval=1s, timeout=3s） |
| `src/test/.../websocket/server/VertxWebSocketServerIntegrationTest.java` | 新增 | 3 个真实 Vert.x WebSocket 集成测试：心跳 ACK、静默连接超时关闭、错误路径拒绝 |

### 前端（OnlineIM-Vue）

| 文件 | 变更类型 | 说明 |
|------|----------|------|
| `services/websocket.service.ts` | 重写 | isConnected → connectionState 4 态枚举；心跳发送器 + ACK 看门狗 + 指数退避重连器 + 出站队列 + 重连补齐 |
| `stores/websocketStore.ts` | 修改 | 暴露 connectionState + isOnline/isReconnecting/isOffline getters + manualRetry action |
| `utils/indexedDB.ts` | 修改 | DB_VERSION 1→2；新增 outbound_queue store + dbService 出站队列方法 |
| `utils/reconnect-utils.ts` | 新增 | 纯逻辑：computeBackoff、状态机转换校验、常量定义 |
| `services/message.service.ts` | 修改 | 新增 `syncMessages()` 调用 `/messages/sync/{convId}` |
| `stores/history.ts` | 修改 | 新增 activeConversationId 跟踪 + syncActiveConversation() 重连补齐 |
| `components/independent/ConnectionStatusBadge.vue` | 新增 | 顶栏连接状态指示点（绿/黄/灰 + 离线态点击重试） |
| `components/AppSideBar/left/AppSidebarLeft.vue` | 修改 | 挂载 ConnectionStatusBadge；头像 reconnecting/offline 时 grayscale |
| `shared/config.ts` | 新增 | 本地开发配置（API_BASE_URL + WS_API_URL，gitignored） |
| `package.json` | 修改 | 新增 vitest + @vue/test-utils + jsdom；test 脚本 |
| `vitest.config.ts` | 新增 | vitest 配置（jsdom + @ alias） |

### 前端测试

| 文件 | 变更类型 | 说明 |
|------|----------|------|
| `utils/__tests__/reconnect-utils.test.ts` | 新增 | 26 个单测：退避公式 + ±25% 抖动边界 + 状态机迁移 + 常量校验 |
| `services/__tests__/websocket.service.test.ts` | 新增 | 7 个服务级测试：重复连接、旧连接事件、心跳判死、离线态重试、队列关联 ID、重连刷新、响应确认 |

### 文档

| 文件 | 变更类型 | 说明 |
|------|----------|------|
| `docs/ws-protocol-registry.md` | 新增 | v1 WebSocket 协议清单，登记 HEARTBEAT / HEARTBEAT_ACK 控制帧 |

## 验收结果

- [x] 后端 46 个单元与集成测试全绿（`./mvnw clean test`）
- [x] 前端 33 个单元与服务级测试全绿（`npm test`）
- [x] 后端编译通过（`./mvnw compile`）
- [x] 前端生产打包通过（`npx vite build`）
- [x] 本任务涉及前端文件无 TypeScript 错误
- [x] 真实 WebSocket 握手、心跳 ACK、超时关闭和路径拒绝验证通过
- [ ] 完整 UI 人工验证（断网、恢复、toast、颜色和头像灰度）为可选补充验证，未执行

## 遗留问题

1. 前端 `shared/config.ts` 为本地 gitignored 文件，需各开发者自行创建。
2. 前端全量 `npm run build` 仍被 88 个既有 TypeScript 错误阻断，分布于 Announcement、群组页面、手机端页面、认证服务和 `vite.config.ts` 等非本任务文件；本任务涉及文件已无类型错误，且 Vite 生产打包通过。
3. `UserSessionService.refreshActiveTime` 使用 read-modify-write 模式，高并发下可能有竞态（v1 单实例可接受）。
4. 完整浏览器断网与视觉状态人工检查尚未执行，但关键状态机、队列、心跳和真实 WebSocket 服务端链路已有自动化覆盖。

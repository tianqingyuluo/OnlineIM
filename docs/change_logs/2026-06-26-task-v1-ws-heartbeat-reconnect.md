# 2026-06-26 任务完成记录 — ws-heartbeat-reconnect

## 任务信息

- **任务 ID**：v1-ws-heartbeat-reconnect
- **标题**：WebSocket 心跳与断线重连
- **状态**：实现完成，待手动验证

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

### 文档

| 文件 | 变更类型 | 说明 |
|------|----------|------|
| `docs/ws-protocol-registry.md` | 新增 | v1 WebSocket 协议清单，登记 HEARTBEAT / HEARTBEAT_ACK 控制帧 |

## 验收结果

- [x] 后端 32 个单测全绿（`./mvnw test`）
- [x] 前端 26 个单测全绿（`npx vitest run`）
- [x] 后端编译通过（`./mvnw compile`）
- [x] 前端无新增类型错误（vue-tsc）
- [ ] 手动验证（UJ-1 ~ UJ-10）待执行

## 遗留问题

1. 前端 `shared/config.ts` 为本地 gitignored 文件，需各开发者自行创建
2. 前端已有预存类型错误（Announcement.vue、GroupMainPart.vue 等），非本任务引入
3. `UserSessionService.refreshActiveTime` 使用 read-modify-write 模式，高并发下可能有竞态（v1 单实例可接受）
4. 嵌入式 Vert.x 集成测试基类未创建（单测已覆盖核心逻辑，集成测试推迟到真正需要 E2E 时）

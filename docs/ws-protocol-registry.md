# v1 WebSocket 协议清单

> 所有 WS 消息类型和字段的唯一登记处。新增类型/字段须在此登记，保持 Web/Tauri/Flutter + 后端 + proxy 五端一致。

## 协议格式

所有帧统一信封：`{ "type": "<TYPE>", "message": {<payload>} }`

## 帧类型一览

| 类型 | 方向 | 分类 | 状态 | 登记任务 |
|------|------|------|------|----------|
| `PRIVATE_MESSAGE_REQUEST` | C→S | 业务·发送 | v1 | — |
| `PRIVATE_MESSAGE_RESPONSE` | S→C | 业务·接收 | v1 | — |
| `GROUP_MESSAGE_REQUEST` | C→S | 业务·发送 | v1 | — |
| `GROUP_MESSAGE_RESPONSE` | S→C | 业务·接收 | v1 | — |
| `RECALL_MESSAGE_REQUEST` | C→S | 业务·发送 | v1 | — |
| `MESSAGE_RECALLED` | S→C | 业务·接收 | v1 | — |
| `NOTICE_MESSAGE_REQUEST` | C→S | 业务·发送 | v1 | — |
| `NOTICE_MESSAGE` | S→C | 业务·接收 | v1 | — |
| `HEARTBEAT` | C→S | 控制·心跳 | v1 | ws-heartbeat-reconnect |
| `HEARTBEAT_ACK` | S→C | 控制·心跳 | v1 | ws-heartbeat-reconnect |
| `ERROR` | S→C | 控制·错误 | v1 | — |

## 控制帧详情

### HEARTBEAT（v1 必需帧）

- **方向**：客户端 → 服务端
- **载荷**：`{}`（空对象）
- **语义**：客户端每 30s 发送一次，用于应用层活性检测
- **处理**：服务端收到后刷新 `WebSocketSession.lastActiveAt` + Redis `user:sessions:{userId}:{deviceId}` 的 `activeTime`，回复 `HEARTBEAT_ACK`
- **不持久化**、**不进 Redis Stream**、**不进业务 registry**
- **兼容性**：旧客户端不发心跳 → 90s 后被服务端判死、强制断连 → 触发重连循环。v1 三端同步升级，可接受

### HEARTBEAT_ACK

- **方向**：服务端 → 客户端
- **载荷**：`{ "serverTime": <epoch_millis> }`
- **语义**：纯活性回执。`serverTime` 用于诊断/时钟偏差
- **客户端行为**：收到 ACK 重置"未收到 ACK 计数"。连续 2 周期（≈60s）未收到 → 判定连接已死，主动 `close()` 进入重连

## 配置参数

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `websocket.heartbeat.interval` | `30s` | 心跳发送间隔 / 服务端清理间隔 |
| `websocket.heartbeat.timeout` | `90s` | 心跳超时阈值（超过此时间无入站帧则判定死连接）|

# v1 WebSocket 协议清单

> 所有 WS 消息类型和字段的唯一登记处。新增类型/字段须在此登记，保持 Web/Tauri/Flutter + 后端 + proxy 五端一致。

## 协议格式

所有帧统一信封：`{ "type": "<TYPE>", "message": {<payload>} }`。

服务端不得信任载荷中的 `sender_id`、`reader_id` 等身份字段；身份统一从已认证 WebSocket session 推导。所有 `seq_id` 都按非负整数比较，客户端与服务端均不得使用字典序比较。

## 帧类型一览

| 类型 | 方向 | 分类 | 状态 | 登记任务 |
|------|------|------|------|----------|
| `PRIVATE_MESSAGE_REQUEST` | C→S | 业务·发送 | v1 | — |
| `PRIVATE_MESSAGE_RESPONSE` | S→C | 业务·接收·兼容 | v1 | — |
| `GROUP_MESSAGE_REQUEST` | C→S | 业务·发送 | v1 | — |
| `GROUP_MESSAGE_RESPONSE` | S→C | 业务·接收·兼容 | v1 | — |
| `MESSAGE_ACK` | S→C | 业务·发送确认 | v1 | message-ack-read |
| `RECEIPT` | C→S / S→C | 业务·送达回执 | v1 | message-ack-read |
| `READ_RECEIPT` | C→S / S→C | 业务·已读回执 | v1 | message-ack-read |
| `RECALL_MESSAGE_REQUEST` | C→S | 业务·发送 | v1 | — |
| `RECALL_MESSAGE_RESPONSE` | S→C | 业务·撤回确认·兼容 | v1 | message-reply |
| `MESSAGE_RECALLED` | S→C | 业务·撤回通知·兼容 | v1 | message-reply |
| `NOTICE_MESSAGE_REQUEST` | C→S | 业务·发送 | v1 | — |
| `NOTICE_MESSAGE` | S→C | 业务·接收 | v1 | — |
| `HEARTBEAT` | C→S | 控制·心跳 | v1 | ws-heartbeat-reconnect |
| `HEARTBEAT_ACK` | S→C | 控制·心跳 | v1 | ws-heartbeat-reconnect |
| `ERROR` | S→C | 控制·错误 | v1 | — |

## 引用回复协议

### 发送请求

`PRIVATE_MESSAGE_REQUEST` 与 `GROUP_MESSAGE_REQUEST` 增加可选 `reply_to_message_id`。客户端只提交直接被引用的消息 ID，不得提交或覆盖权威预览快照。

私聊示例：

```json
{
  "type": "PRIVATE_MESSAGE_REQUEST",
  "message": {
    "conversation_id": "conv_xxx",
    "receiver_id": "usr_xxx",
    "message_type": "text",
    "content": "这是回复正文",
    "client_message_id": "client_xxx",
    "reply_to_message_id": "msg_target"
  }
}
```

群聊示例：

```json
{
  "type": "GROUP_MESSAGE_REQUEST",
  "message": {
    "group_id": "grp_xxx",
    "message_type": "text",
    "content": "这是回复正文",
    "client_message_id": "client_xxx",
    "reply_to_message_id": "msg_target"
  }
}
```

HTTP 发送入口使用同一 snake_case 字段契约：

```http
POST /api/v1/messages/send
```

```json
{
  "target_id": "usr_xxx 或 grp_xxx",
  "message_type": "text",
  "content": "这是回复正文",
  "client_message_id": "client_xxx",
  "reply_to_message_id": "msg_target",
  "at_user_ids": ["usr_xxx"]
}
```

- `target_id`、`message_type`、`content`、`client_message_id` 为必填字段。
- `reply_to_message_id`、`at_user_ids` 和 `ext` 为可选字段；`at_user_ids` 仅对群聊有意义。
- 服务端为旧 Java 调用方保留 camelCase 反序列化别名，但 Web、Tauri、Flutter 等客户端必须发送 snake_case。
- 私聊会话 ID 由双方用户 ID 按字典序生成，HTTP 与会话创建、WebSocket 使用同一规则。

服务端在持久化前校验目标存在、属于同一会话、当前用户有访问权且目标未撤回；服务端根据权威原消息生成单层快照。拒绝路径不写 MongoDB、不发布 Redis Stream、不发送 `MESSAGE_ACK`。相同发送者重复使用同一 `client_message_id` 时，WebSocket 返回首次成功消息的 ACK，HTTP 返回首次成功消息的 `MessageResponse`，均不改变首次保存的引用目标。

HTTP 发送同样先校验私聊参与者或群成员身份，再执行幂等查询、引用快照生成、MongoDB 持久化和 Redis Stream 发布。重复 HTTP 请求返回首次成功消息的 `MessageResponse`，不重复落库或广播；HTTP 不产生 WebSocket `MESSAGE_ACK`。

### 消息响应 `reply_to`

`PRIVATE_MESSAGE_RESPONSE`、`GROUP_MESSAGE_RESPONSE`、历史分页、增量同步和上下文接口中的 `MessageResponse` 可包含：

```json
{
  "reply_to": {
    "message_id": "msg_target",
    "seq_id": "123456789",
    "sender_id": "usr_xxx",
    "sender_display_name": "小明",
    "message_type": "text",
    "preview_text": "发送回复时的摘要快照",
    "state": "active"
  }
}
```

| `state` | `preview_text` | 客户端展示 |
|---|---|---|
| `active` | 快照文本或类型占位 | 正常显示快照 |
| `edited` | 保留发送时快照 | 显示快照并标注“原消息已编辑” |
| `recalled` | 必须为 `null` | 仅显示“原消息已撤回” |
| `unavailable` | 必须为 `null` | 仅显示“原消息不可用” |

引用只保存当前选择消息的一层直接关系；即使目标消息自身含 `reply_to`，新快照也不得递归复制更早的引用链。文本摘要折叠空白并按 Unicode code point 最多保留 80 个字符；图片、语音、视频、表情、文件和未知类型使用固定占位文案。

### 引用错误

WebSocket 发送目标不存在、跨会话、无权限、已撤回或在发送竞态中变得不可用时，返回：

```json
{
  "type": "ERROR",
  "message": {
    "code": "REPLY_TARGET_UNAVAILABLE",
    "message": "原消息已不可用，请取消引用后重新发送",
    "client_message_id": "client_xxx"
  }
}
```

HTTP 发送入口对同一业务错误返回 `409 Conflict` 和 `code=REPLY_TARGET_UNAVAILABLE`。服务端不细分“不存在”和“无权限”，避免泄露其他会话消息是否存在。

## 消息发送确认

### MESSAGE_ACK（服务端 → 客户端）

仅在消息已经写入 MongoDB 且业务 Redis Stream 发布成功后发送。重复使用同一发送者和 `client_message_id` 时，服务端返回原消息的 ACK，不重复落库或广播。

```json
{
  "type": "MESSAGE_ACK",
  "message": {
    "client_message_id": "client_xxx",
    "message_id": "msg_xxx",
    "conversation_id": "conv_xxx",
    "seq_id": "123456789",
    "delivery_state": "sent",
    "server_time": 1710000000000
  }
}
```

`delivery_state` 初始值为 `sent`。客户端将本地临时消息按 `client_message_id` 合并为正式消息，不能因为 ACK 乱序或重复而降级状态。

## 送达回执

### C→S RECEIPT

接收端必须在业务消息成功写入 IndexedDB 后发送。`receipt_type` 当前只支持 `delivered`。

```json
{
  "type": "RECEIPT",
  "message": {
    "receipt_type": "delivered",
    "message_id": "msg_xxx",
    "conversation_id": "conv_xxx",
    "seq_id": "123456789"
  }
}
```

服务端从 session 推导回执用户，校验消息属于当前会话且当前用户确实是接收者/群成员，再以会话成员的 `delivered_seq = max(old, seq_id)` 幂等推进。重复、旧游标和越权回执不会产生新的广播事件。

### S→C RECEIPT

服务端将送达状态事件发送给私聊发送方，群聊发送给需要更新状态的会话成员。

```json
{
  "type": "RECEIPT",
  "message": {
    "receipt_type": "delivered",
    "message_id": "msg_xxx",
    "conversation_id": "conv_xxx",
    "seq_id": "123456789",
    "receiver_id": "usr_xxx"
  }
}
```

## 已读回执

### C→S READ_RECEIPT

客户端只上报会话连续已读游标，不为每条消息单独发送回执。游标应来自前台可视区域内连续停留约 300ms 的最高消息；打开会话不会自动把全部历史标为已读。

```json
{
  "type": "READ_RECEIPT",
  "message": {
    "conversation_id": "conv_xxx",
    "read_seq": "123456789"
  }
}
```

服务端从 session 推导 `reader_id`，校验会话成员关系和游标不超过会话最新消息，再以 `read_seq = max(old, read_seq)` 更新；已读游标推进时可同时补齐 `delivered_seq`。

### S→C READ_RECEIPT

```json
{
  "type": "READ_RECEIPT",
  "message": {
    "conversation_id": "grp_xxx",
    "reader_id": "usr_xxx",
    "read_seq": "123456789"
  }
}
```

群聊客户端按 `reader_id + read_seq` 更新成员阅读游标，并可调用 readers HTTP 接口得到完整昵称和头像列表。私聊客户端据此将对端发送的消息更新为 `read`。

## 消息响应新增字段

`PRIVATE_MESSAGE_RESPONSE` 和 `GROUP_MESSAGE_RESPONSE` 仍用于向接收端投递消息，新增字段保持 snake_case：

| 字段 | 类型 | 语义 |
|------|------|------|
| `delivery_state` | string | `sending`、`failed`、`sent`、`delivered`、`read` |
| `seq_id` | string | 非负整数消息游标 |
| `client_message_id` | string | 客户端幂等键 |
| `reply_to` | object/null | 服务端生成的单层引用快照；状态契约见“引用回复协议” |

旧 `status` 字段保留，用于兼容历史撤回语义；新客户端优先使用 `delivery_state`。

## HTTP 状态接口

- `GET /api/v1/conversations/{conversationId}/read-state`
- `GET /api/v1/conversations/{conversationId}/unread-count/{seqId}`
- `GET /api/v1/conversations/{conversationId}/messages/{messageId}/readers`

- `GET /api/v1/messages/{conversationId}/{messageId}/context?before=20&after=20`

消息上下文接口使用认证主体校验私聊参与者或群成员身份，`before`、`after` 默认 20 且分别限制为 0-50。目标前后窗口按数值型 `seq_id` 排序，一次返回目标及相邻消息：

```json
{
  "target_message_id": "msg_target",
  "messages": [],
  "has_more_before": true,
  "has_more_after": true
}
```

目标不存在或不可访问时统一返回 `404 Not Found` 和 `code=MESSAGE_CONTEXT_UNAVAILABLE`。已撤回目标可作为撤回占位消息返回。

会话响应同时返回 `delivered_seq`、`read_seq`、`latest_seq` 和权威 `unread_count`。readers 接口仅允许会话成员访问；群聊按成员 `read_seq >= message.seq_id` 推导阅读成员。同时返回可选的 `delivered_readers`，按成员 `delivered_seq >= message.seq_id` 推导，用于发送者离线重连后的送达状态恢复。

## 撤回事件与引用联动

当前撤回发送方确认 `RECALL_MESSAGE_RESPONSE` 与接收方通知 `MESSAGE_RECALLED` 沿用既有顶层字段格式，而不是标准 `message` 信封；Web 客户端兼容两种解析方式。

```json
{
  "type": "MESSAGE_RECALLED",
  "message_id": "msg_target",
  "conversation_id": "conv_xxx",
  "recall_user_id": "usr_xxx",
  "timestamp": 1710000000000
}
```

收到撤回事件后，客户端必须：将原消息改为撤回占位；将当前会话所有 `reply_to.message_id` 等于目标的引用改为 `recalled`；清空 `preview_text`；同步写回 IndexedDB。服务端同时批量清理 MongoDB 中的直接引用快照，并在历史/上下文响应时再次解析目标状态，保证错过实时事件的客户端刷新后也不会看到旧正文。

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
- **客户端行为**：收到 ACK 重置“未收到 ACK 计数”。连续 2 周期（约 60s）未收到 → 判定连接已死，主动 `close()` 进入重连

## 配置参数

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `websocket.heartbeat.interval` | `30s` | 心跳发送间隔 / 服务端清理间隔 |
| `websocket.heartbeat.timeout` | `90s` | 心跳超时阈值（超过此时间无入站帧则判定死连接）|

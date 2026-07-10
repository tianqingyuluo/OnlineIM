# 消息 ACK 与已读回执技术设计

## 1. 设计目标与边界

本设计将已确认的产品规则落到现有 OnlineIM 的 Spring/Vert.x WebSocket、MongoDB、Redis Stream、Vue/Pinia/IndexedDB 架构中。

目标：

- 将“服务端已接受”“接收端已收到”“接收端已阅读”拆成可恢复的状态链路；
- 私聊展示 `发送中 → 已发送 → 已送达 → 已读`，并支持明确的 `发送失败`；
- 群聊按成员维护 `deliveredSeq`/`readSeq`，从游标推导某条消息的阅读成员列表；
- 保持现有消息历史、`client_message_id`、seq 增量同步、心跳重连和 Redis Stream 多实例路由兼容；
- 回执处理具备身份校验、幂等、单调推进和重连恢复能力。

非目标：E2EE、离线推送渠道、多端登录策略、消息编辑/转发/回复/撤回等其他 v1 子任务。

## 2. 现状与主要问题

当前 WebSocket 发送链路为：客户端请求 → MongoDB 保存消息 → Redis Stream → 接收处理器 → 同时向发送者和在线接收者发送 `PRIVATE_MESSAGE_RESPONSE`/`GROUP_MESSAGE_RESPONSE`。该响应无法区分服务端持久化确认和接收端客户端实际收到消息。

当前 `WebSocketMessageRouter` 只把原始 payload 传给 `MessageSenderHandler`，处理器容易信任 payload 中的 `sender_id`；回执处理必须改为从已认证 `WebSocketSession.userId` 获取身份。当前 `Conversation` 只有 `unreadCount`，没有按用户的已读游标；`unread-count/{seqId}` 端点尚未实现。

## 3. 总体架构

```text
客户端发送消息
  → WebSocketMessageRouter（认证 session）
  → 消息发送处理器（校验/幂等/持久化）
  → MESSAGE_ACK 直接返回发送者
  → Redis Stream: PRIVATE_MESSAGE / GROUP_MESSAGE
  → 接收处理器推送消息
  → 客户端写入 IndexedDB 后发送 RECEIPT
  → 回执服务更新 ConversationReadState
  → Redis Stream: RECEIPT / READ_RECEIPT
  → 发送者或群成员收到状态更新

客户端可视区域检测
  → READ_RECEIPT(readSeq)
  → 服务端校验并单调更新 readSeq
  → 未读数投影刷新 + 状态广播

首次加载/重连
  → 会话列表 read_seq/unread_count
  → read-state 查询
  → 消息历史和必要的 readers 查询
```

### 3.1 服务端模块边界

新增或调整以下职责：

- `ReceiptService`：验证送达/已读回执、更新用户游标、计算状态变更、发布跨实例事件；
- `ConversationReadState` 文档与 Repository：保存每个 `(conversationId, userId)` 的送达和已读游标；
- `MessageDeliveryStateService`：根据消息方向和对端游标生成 `delivery_state`，不把“已读”混入现有撤回/兼容数值 `status`；
- `ReadReceiptReceiver`/`ReceiptReceiver`：处理 Redis Stream 中的回执事件并向本地 WebSocket 会话发送；
- `MessageAckSender` 或消息发送处理器内的 ACK 组件：消息成功持久化并发布事件后，向原发送 session 发送 `MESSAGE_ACK`；
- `WebSocketMessageRouter`：将认证 session 传入发送处理器，禁止回执处理信任客户端伪造的用户 ID。

### 3.2 前端模块边界

- `src/type/message.ts`：定义消息状态、WS envelope 和回执 payload；
- `websocket.service.ts`：解析 `MESSAGE_ACK`、`RECEIPT`、`READ_RECEIPT`，统一交给 history store；
- `stores/history.ts`：维护消息状态、会话游标、群成员 readSeq 缓存、出站回执队列；
- 新建可复用的消息可视检测 composable/工具：只上报最新连续可见 seq；
- `MessageService`/`conversation.service.ts`：实现未读数、read-state 和 readers 查询；
- 私聊/群聊消息组件：展示发送失败、送达/已读状态以及头像摘要和展开列表；
- `indexedDB.ts`：持久化待发送消息、未确认状态和待发送回执，复用现有出站队列机制。

## 4. 数据模型

### 4.1 ConversationReadState（MongoDB 新文档）

建议集合名：`conversation_read_states`。

```text
_id                 = conversationId + ":" + userId
conversationId      会话 ID
userId              游标所属用户
conversationType    private | group
deliveredSeq        客户端已接收并写入本地消息库的最新连续 seq，默认 "0"
readSeq             客户端已按可视规则阅读的最新连续 seq，默认 "0"
updatedAt           最近更新时间
```

建立唯一键 `(conversationId, userId)`，并建立 `conversationId`、`conversationId + readSeq` 查询索引。`readSeq`/`deliveredSeq` 对外保持字符串格式以兼容现有协议；服务端必须通过统一的 seq 比较器进行数值顺序比较，禁止使用序号相减计算未读数。

该文档是已读/送达游标的权威来源。`Conversation.unreadCount` 只作为会话列表的派生缓存，不得作为回执幂等判断依据。

### 4.2 消息文档兼容字段

保留现有 `status` 数值字段及其撤回语义，新增或在 DTO 层派生 `delivery_state`：

```text
sending | failed | sent | delivered | read
```

- 本地临时消息使用 `sending`；
- 服务端明确拒绝或客户端重试耗尽使用 `failed`；
- `MESSAGE_ACK` 后为 `sent`；
- 私聊接收者 `deliveredSeq >= message.seqId` 后为 `delivered`；
- 私聊接收者 `readSeq >= message.seqId` 后为 `read`；
- 群聊不把多个成员压缩成单一“已读”结论，使用成员阅读列表和游标摘要表达；发送者自身消息仍展示 `sent`/失败以及成员已读摘要。

`client_message_id` 的幂等查询必须至少结合发送者身份；若现有数据允许同一客户端 ID 跨发送者重复，新增复合索引 `(senderId, clientMessageId)`，不直接对历史数据执行破坏性唯一化迁移。

## 5. WebSocket 协议设计

所有新增帧继续使用 `{ "type": "<TYPE>", "message": { ... } }`，并登记到 `docs/ws-protocol-registry.md`。

### 5.1 MESSAGE_ACK（S→C）

服务端在消息持久化成功且 Redis Stream 事件发布成功后发送给原发送连接。发送者离线时不强制重放，重连通过状态同步恢复。

```json
{
  "type": "MESSAGE_ACK",
  "message": {
    "client_message_id": "cli_xxx",
    "message_id": "msg_xxx",
    "conversation_id": "conv_xxx",
    "seq_id": "1234567890",
    "delivery_state": "sent",
    "server_time": 1710000000000
  }
}
```

重复使用同一 `client_message_id` 时，服务端返回同一消息的 ACK，不创建第二条消息。

### 5.2 RECEIPT（C→S / S→C）

接收端在消息写入本地 IndexedDB 后发送 C→S；服务端校验后向消息发送方（私聊）或需要更新状态的群成员发布 S→C。

C→S 载荷：

```json
{
  "type": "RECEIPT",
  "message": {
    "receipt_type": "delivered",
    "message_id": "msg_xxx",
    "conversation_id": "conv_xxx",
    "seq_id": "1234567890"
  }
}
```

服务端从当前 WebSocket session 推导 `receiver_id`，不得信任 payload 中的用户身份。服务端按消息和接收者的 `deliveredSeq` 幂等更新，并在 S→C 载荷中补充 `receiver_id`。

### 5.3 READ_RECEIPT（C→S / S→C）

C→S 只提交当前用户可见范围内的最新连续游标：

```json
{
  "type": "READ_RECEIPT",
  "message": {
    "conversation_id": "grp_xxx",
    "read_seq": "1234567890"
  }
}
```

服务端从 session 推导 `reader_id`，校验会话成员关系、`read_seq <= deliveredSeq`（允许在同一同步事务中先推进 delivered）及 seq 所属会话，然后使用 max 语义更新 `readSeq`。S→C 事件补充 `reader_id`，群聊向需要实时更新阅读明细的成员广播，私聊只通知消息发送方。

### 5.4 HTTP 状态接口

保留并实现：

```text
GET /api/v1/conversations/{conversationId}/unread-count/{seqId}
```

响应至少包含：

```json
{
  "conversation_id": "conv_xxx",
  "from_seq_id": "123",
  "read_seq": "456",
  "latest_seq": "789",
  "unread_count": 12
}
```

新增：

```text
GET /api/v1/conversations/{conversationId}/read-state
GET /api/v1/conversations/{conversationId}/messages/{messageId}/readers
```

`read-state` 用于首次加载和重连，返回当前登录用户的 `delivered_seq`、`read_seq`、最新 seq 和必要的未读摘要。`readers` 仅允许会话成员访问；群聊根据消息 seq 和各成员 `readSeq` 推导阅读成员，返回用户 ID、昵称和头像等已有成员信息。私聊可以返回对端单成员状态，或复用消息状态字段。

## 6. 关键数据流与校验

### 6.1 发送与失败

1. 前端生成并持久化 `client_message_id`，本地插入 `sending` 临时消息；
2. WebSocket handler 从 session 取得发送者身份，校验目标会话成员关系；
3. 先按发送者和 `client_message_id` 查询已有消息；命中则返回原 ACK；
4. 未命中时保存消息、生成 seq，并发布业务 Redis Stream；
5. 两步成功后发送 `MESSAGE_ACK`；任一步明确失败则返回 `ERROR`，前端显示 `failed`；
6. 网络无响应时复用现有出站队列重试，最多 3 次；重试仍不确定时先保持“发送中”，最终失败才转为 `failed`。

### 6.2 送达

1. 接收端收到业务消息后先写 IndexedDB；
2. 写入成功后发送 `RECEIPT(delivered)`；
3. 服务端确认该用户是消息接收者/群成员，并以 max 更新 `deliveredSeq`；
4. 送达状态事件通过 Redis Stream 路由给发送方或群聊状态订阅者；
5. 发送方离线时不要求回执事件重放，重连通过 `read-state` 和消息状态查询恢复。

### 6.3 已读与群成员明细

1. 可视检测器只选择前台、停留约 300ms 且连续的最高 seq；
2. history store 对同一会话只发送更大的 `readSeq`，并可将待发回执放入 IndexedDB；
3. 服务端验证成员关系和游标边界后 max 更新；
4. 群聊广播 `reader_id + read_seq`，各客户端更新成员游标缓存；
5. 点击已读摘要时调用 readers 接口，按消息 seq 推导完整成员列表；
6. 旧事件、重复事件和乱序事件只能被忽略，不能回退状态。

## 7. 兼容性、迁移与安全

- 新增字段和新帧采用增量方式；现有消息响应继续用于接收端消息展示，发送者改由 `MESSAGE_ACK` 驱动发送确认；
- `MessageResponse.status` 继续保留，新增 `delivery_state` 避免破坏撤回和旧端解析；
- 历史消息缺少 read state 时按 `deliveredSeq=0/readSeq=0` 初始化；会话列表读取时按游标计算或刷新 `unreadCount` 缓存；
- 回执处理不能使用 payload 的 `sender_id`/`reader_id` 作为认证依据；所有身份由 WebSocket session/JWT 和会话成员关系决定；
- 发送者只能看到自己有权限的私聊或群聊回执；readers 接口必须校验群成员身份；
- seq 比较、最大游标更新和重复事件处理集中到一个服务/工具，禁止在 Controller、WS handler、前端组件中各自实现一套比较逻辑；
- 需要补充 Mongo 索引和数据初始化，但不得删除或重写既有消息/会话语义。

## 8. 重要取舍

1. **游标而非每消息成员数组**：用成员级 `readSeq` 推导 readers，写入成本和广播体积可控；代价是展开列表需要一次查询或缓存计算。
2. **客户端写入本地库后才送达**：比服务端写入即送达更准确；代价是客户端必须可靠执行 IndexedDB 写入和回执重试。
3. **网络超时不立即失败**：避免服务端已落库但 ACK 丢失时误报；代价是失败 UI 出现稍晚。
4. **HTTP 同步 + WS 实时事件**：实时体验和断线恢复各自使用合适通道；代价是必须保证两条通道都引用同一个 read state 权威数据。
5. **兼容保留数值 status**：降低历史客户端/撤回逻辑风险；代价是前端需要优先读取新的 `delivery_state`。

## 9. 回滚与发布

- 先合入协议登记、数据模型和只读同步接口，再合入 WS 写入路径和前端 UI，保持每个提交可单独回滚；
- 不执行破坏性 Mongo 数据迁移；新 read state 采用懒创建和默认游标；
- 若实时回执异常，可暂时关闭新回执处理器/前端 UI，保留历史消息收发和 HTTP 历史同步；
- 发布前必须完成单实例、Redis 多实例、离线重连和 Web 前端旅程回归。

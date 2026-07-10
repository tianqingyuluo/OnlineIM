# 消息回复（引用回复）技术设计

## 1. 设计目标

在不破坏现有消息发送、ACK、送达/已读回执和历史同步语义的前提下，为私聊与群聊增加可持久化、可离线展示、可跨分页定位的单层引用关系。服务端是引用快照和目标状态的权威来源，客户端只提交目标消息 ID。

## 2. 系统边界

### 2.1 涉及层次

```text
Web 消息操作与输入框
  → history Pinia store / IndexedDB / 离线出站队列
  → WebSocket 或 HTTP 发送请求
  → MessageReplyService 校验与快照生成
  → MongoDB private_messages / group_messages
  → Redis Stream
  → MessageResponse.reply_to
  → Web 实时接收、历史加载与上下文定位
```

### 2.2 不改变的既有契约

- `PRIVATE_MESSAGE_REQUEST`、`GROUP_MESSAGE_REQUEST` 消息类型不变。
- `client_message_id` 幂等键和 `MESSAGE_ACK` 载荷不变。
- `delivery_state`、`status`、`seq_id`、送达回执和已读游标语义不变。
- 旧消息的引用字段缺失时按普通消息处理。

## 3. 数据模型

### 3.1 MongoDB 嵌入值对象

新增 `MessageReplySnapshot`，作为 `PrivateMessage.replyTo` 与 `GroupMessage.replyTo` 的可空嵌入字段：

| 字段 | 类型 | 说明 |
|---|---|---|
| `messageId` | string | 直接被引用消息 ID |
| `seqId` | string | 直接被引用消息序列号 |
| `senderId` | string | 原发送者 ID |
| `senderDisplayName` | string | 回复发送时的显示名快照 |
| `messageType` | string | 原消息类型 |
| `previewText` | string/null | 服务端生成的预览；撤回/不可用后清空 |
| `contentRevision` | integer | 生成快照时的原消息内容版本，默认 1 |
| `state` | string | `active`、`edited`、`recalled`、`unavailable` |

`PrivateMessage` 与 `GroupMessage` 新增可空 `contentRevision`。历史文档缺失时按 `1` 处理；新消息初始化为 `1`。后续消息编辑任务更新正文时递增该字段，并调用引用状态更新入口。

不把被引用消息已有的 `replyTo` 嵌入新快照，因此数据和 UI 始终只有一层。

### 3.2 索引

为两个消息集合确保以下索引：

- `replyTo.messageId`：原消息撤回/不可用时批量更新直接引用。
- 继续复用现有会话和消息 ID 查询；不改变旧索引语义。

通过独立 Mongo 索引初始化组件使用 `MongoTemplate.indexOps(...).ensureIndex(...)` 幂等创建，不依赖手工环境操作。

## 4. 协议契约

### 4.1 发送请求

私聊与群聊请求新增可选字段：

```json
{
  "reply_to_message_id": "msg_123"
}
```

HTTP `MessageSendRequest` 将废弃未使用的 `quoteMessageId`，统一映射为 `replyToMessageId` / `reply_to_message_id`。服务端忽略客户端可能额外提交的预览内容，只按消息 ID读取权威目标。

### 4.2 消息响应

`MessageResponse` 新增可选 `reply_to`：

```json
{
  "reply_to": {
    "message_id": "msg_123",
    "seq_id": "987654321",
    "sender_id": "usr_1",
    "sender_display_name": "小明",
    "message_type": "text",
    "preview_text": "发送时的消息摘要",
    "state": "active"
  }
}
```

- `state=edited`：保留 `preview_text`。
- `state=recalled|unavailable`：`preview_text` 必须为 `null`，客户端只显示状态文案。
- 无引用消息不返回 `reply_to` 或返回 `null`，前端字段为可选。

### 4.3 错误契约

新增稳定业务错误码：

| 错误码 | HTTP 状态 | 场景 |
|---|---:|---|
| `REPLY_TARGET_UNAVAILABLE` | 409 | 目标不存在、跨会话、无权限、已撤回或在发送竞态中变得不可用 |
| `MESSAGE_CONTEXT_UNAVAILABLE` | 404 | 上下文目标不可访问或不存在 |

为避免泄露其他会话消息是否存在，发送链路统一返回 `REPLY_TARGET_UNAVAILABLE`，不向客户端细分“存在但无权访问”。WebSocket `ERROR.message` 保持 `{code,message,client_message_id}` 结构。

新增 `ReplyTargetUnavailableException`，由 HTTP 全局异常处理器和 WebSocket 路由器分别映射为同一业务错误码。私聊/群聊 sender 不吞掉该异常；其他格式错误仍走现有通用拒绝逻辑。

## 5. 后端组件设计

### 5.1 `MessageReplyService`

职责集中在一个服务，避免 HTTP、私聊 WS、群聊 WS 和历史转换重复实现：

- `createSnapshot(conversationId, targetMessageId, userId)`
  - 校验当前用户可访问会话。
  - 按会话类型在对应集合查询 `targetMessageId + conversationId`。
  - 拒绝缺失、跨会话、已撤回目标。
  - 读取发送者显示名和内容版本，生成单层快照。
- `resolveForResponse(conversationId, snapshot)`
  - 读取当前目标状态，纠正旧快照状态。
  - 目标撤回/缺失时清空预览。
  - 当前版本大于快照版本时返回 `edited`。
- `resolveBatchForResponses(messages)`
  - 历史列表按目标 ID 批量读取，避免逐条 N+1 查询。
- `markTargetRecalled(conversationId, messageId)`
  - 在对应集合批量将直接引用的 `state` 更新为 `recalled` 并清空 `previewText`。
- 预留 `markTargetEdited(...)` 与 `markTargetUnavailable(...)`，供后续编辑/删除任务调用。

### 5.2 预览生成

后端预览函数是持久化快照的权威实现：

- 文本：Unicode code point 安全截断 80 字符，空白折叠为单个空格。
- 图片、语音、视频、表情和未知类型使用 PRD 固定占位文案。
- 文件名只从受信任的结构化 `ext.file_name` 读取，不从 URL 猜测；不存在时显示 `[文件]`。
- 预览不得包含被引用消息自己的 `replyTo`。

前端提供同规则的本地预览工具，仅用于选中后的即时引用条和乐观消息；服务端响应覆盖本地临时值。

### 5.3 私聊与群聊发送

`PrivateMessageSender`、`GroupMessageSender` 和 HTTP `MessageService.sendMessage` 在持久化前执行：

1. 既有身份、会话、成员和幂等校验。
2. 若 `reply_to_message_id` 存在，调用 `MessageReplyService.createSnapshot`。
3. 将快照写入新消息文档。
4. 保存 MongoDB。
5. 发布 Redis Stream。
6. 成功后才发送既有 `MESSAGE_ACK`。

若引用校验失败，步骤 3-6 均不执行。重复 `client_message_id` 仍返回第一次成功消息的 ACK，不重新解析或替换引用目标。

### 5.4 历史与实时响应

- `MessageServiceImpl.convertPrivateMessageToResponse` 与 `convertGroupMessageToResponse` 输出 `reply_to`。
- 单条实时投递可调用单目标状态解析。
- 历史分页、增量同步和上下文窗口使用批量解析，避免 N+1。
- 原消息撤回时，持久化快照批量清除是第一道保护；响应解析再次校验是第二道保护，避免旧数据或漏更新泄露预览。

### 5.5 撤回联动

`MessageServiceImpl.recallMessage` 在原消息状态成功更新为撤回后调用 `markTargetRecalled`。操作幂等：重复撤回不会恢复或重复写入预览。

Redis 撤回事件到达客户端后：

- 标记原消息为撤回占位。
- 将当前会话中所有 `reply_to.message_id` 等于目标的引用状态更新为 `recalled` 并清空预览。
- 将变化写回 IndexedDB。

服务端历史读取仍是最终权威，确保错过实时事件的客户端重连后得到正确状态。

## 6. 消息上下文接口

### 6.1 HTTP 接口

```http
GET /api/v1/messages/{conversationId}/{messageId}/context?before=20&after=20
```

响应：

```json
{
  "target_message_id": "msg_123",
  "messages": [],
  "has_more_before": true,
  "has_more_after": true
}
```

约束：

- `before`、`after` 默认 20，分别限制为 0-50。
- 使用认证主体获得用户 ID，不信任请求中的用户字段。
- 私聊要求用户属于会话；群聊要求当前仍是群成员。
- 目标必须属于 `conversationId`。
- 以数值型 `seq_id` 语义取目标前后窗口并按升序返回，不使用字符串词法比较。
- 已撤回目标作为占位消息返回；缺失或无权限统一返回 `MESSAGE_CONTEXT_UNAVAILABLE`。

### 6.2 查询实现

新增上下文查询方法，复用目标消息的 `seqId`，分别查询较小和较大的序列区间。Mongo 查询使用 `$toDecimal` 比较字符串形式的 Snowflake ID；结果最终使用 `SeqIdComparator` 排序。

## 7. 前端设计

### 7.1 类型

`@/type/message.ts` 新增：

```ts
type ReplyState = 'active' | 'edited' | 'recalled' | 'unavailable'

interface ReplyReference {
  message_id: string
  seq_id: string
  sender_id: string
  sender_display_name: string
  message_type: string
  preview_text?: string | null
  state: ReplyState
}
```

`MessageResponse.reply_to?: ReplyReference`。所有 API 字段保持 snake_case。

### 7.2 Store 状态

`useHistoryStore` 增加：

- 当前会话的选中回复目标。
- 待发送项中的 `replyToMessageId` 和本地 `replyTo` 快照。
- 发送前作文快照，用于服务端拒绝后的正文与引用恢复。
- `jumpToMessage(conversationId, messageId)`：内存 → IndexedDB → 上下文接口的三级查找。
- `handleMessageRecalled`：更新原消息和全部直接引用，并持久化缓存。

发送 payload、自动重试、手动重试和离线出站队列都必须保留 `reply_to_message_id`。ACK 后删除发送前恢复快照；特定错误到达时恢复正文和引用状态，不覆盖用户随后输入的新内容。

### 7.3 IndexedDB

- `history` 存储对象可直接增加 `reply_to`，无需数据库版本升级。
- 新增按 `message_id` 主键读取并校验 `user_id + conversation_id` 的方法。
- 上下文接口返回的消息使用既有 `putHistory` 缓存。
- 撤回事件更新引用状态并清空预览后覆盖写回。
- 出站队列 payload 本身持久化 `reply_to_message_id`，不新增独立字段。

### 7.4 组件

新增可复用组件，私聊与群聊共用：

- `MessageReplyPreview.vue`：渲染消息内引用气泡，触发定位。
- `ReplyComposerBar.vue`：渲染输入框上方引用条和取消按钮。
- `MessageActions.vue`：悬停快捷按钮和右键菜单中的“回复”。

`UserMainPart.vue` 与 `GroupMainPart.vue` 负责组合组件、维护受控输入文本、执行滚动定位。消息元素保留 `data-message-id`，定位后添加约两秒高亮 class，再自动移除。

已撤回或不可用消息不渲染回复入口。引用为 `recalled/unavailable` 时组件不读取或展示 `preview_text`。

### 7.5 跨分页定位

1. 在当前 Pinia 列表按 `message_id` 查找。
2. 未命中时从 IndexedDB 主键读取并校验会话。
3. 仍未命中时调用上下文接口，将窗口消息经现有归一化和去重逻辑合并、缓存。
4. `nextTick` 后查找 `[data-message-id="..."]`，使用 `scrollIntoView({block:'center'})`。
5. 设置两秒高亮；失败时保持当前 scrollTop 并使用 `vue-sonner` 提示。

上下文窗口与当前列表之间可能存在未加载区间；合并逻辑保留现有 pending range 信息，不宣称中间消息已经完整加载。

## 8. 兼容性、迁移与发布

- Mongo 字段全部可空，旧文档无需批量迁移。
- `contentRevision` 缺失按 1 处理。
- 旧客户端不发送 `reply_to_message_id`，行为不变。
- 新客户端可读取缺少 `reply_to` 的旧响应。
- 发布顺序：后端先部署，再部署 Web 客户端。回滚时优先回滚客户端；仅回滚后端会导致新客户端提交的引用字段被旧服务忽略，因此不允许单独回滚后端而保留新客户端。
- 新增索引为幂等创建，可独立保留；回滚无需删除索引。

## 9. 风险与缓解

| 风险 | 缓解措施 |
|---|---|
| 历史列表逐条解析造成 N+1 | 批量读取所有目标 ID，按 ID 映射 |
| 撤回后旧预览泄露 | 持久化批量清除 + 实时缓存清除 + 读取时二次校验 |
| 发送重试丢失引用 | pending、离线队列和手动重试统一保存目标 ID |
| 跨会话 ID 探测 | 统一 `REPLY_TARGET_UNAVAILABLE`，不暴露目标是否存在 |
| `seq_id` 字符串排序错误 | Mongo `$toDecimal` + `SeqIdComparator` |
| 私聊/群聊 UI 分叉 | 共用三个引用组件和一个预览工具 |
| 原消息状态在多端不一致 | 实时事件更新 + IndexedDB 覆盖 + 历史响应权威纠正 |

## 10. 设计验收点

- 请求只信任目标 ID，快照只能由服务端生成。
- 无引用路径不增加额外目标查询。
- 历史批量解析无 N+1。
- 拒绝路径不落库、不发布、无 ACK。
- 撤回后任意服务端响应均不包含旧预览正文。
- 前端所有发送与重试路径均携带相同目标 ID。
- 上下文接口一次请求完成远距离定位，并严格鉴权。

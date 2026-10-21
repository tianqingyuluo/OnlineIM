# 会话记录模型：每个参与者一条记录

> 适用版本：v1（审计项 Q3）。对外 API 形状不变，`conversation_id` 依旧是 `conv_...`（私聊）或 `grp_...`（群聊）。

## 1. 为什么改

旧模型中一个会话只有一条共享文档（`_id = 会话ID`，`userId` 是创建者），导致：

- 只有创建者能置顶/免打扰/删除，且这些操作会影响对方；
- 对方或其他群成员调用 `POST /conversations/create` 会覆盖整条文档（重置置顶、免打扰、未读）；
- 群会话只有“点开过”的成员才看得到，非成员也能创建群会话；
- `lastMessage` 从不更新，未读缓存不区分人。

## 2. 集合结构（MongoDB `conversations`）

| 字段 | 说明 |
| --- | --- |
| `_id` | `conversationId + ":" + userId`，确定性主键，并发 upsert 也只会有一条 |
| `conversationId` | 对外会话 ID：私聊 `conv_` + 两个用户 ID 按字典序拼接；群聊即群 ID |
| `userId` | 记录所有者 |
| `targetId` | 私聊为对方用户 ID，群聊为群 ID |
| `conversationType` | `private` / `group` |
| `top` / `mute` | 所有者自己的置顶、免打扰 |
| `status` | 所有者视角：`1` 显示，`0` 已删除（隐藏） |
| `unreadCount` | 所有者的未读缓存；权威值仍由 `conversation_read_states` 游标计算 |
| `lastMessage` | `{messageId, content(预览), messageType, timestamp}` |
| `updatedAt` | 最近活动时间，列表排序用 |

索引（启动时 `ConversationRecordMigration` 确保存在）：

- `uk_user_conversation`：`{userId: 1, conversationId: 1}` 唯一（partial：`conversationId` 存在）；
- `idx_user_status_top_updated`：`{userId: 1, status: 1, top: -1, updatedAt: -1}`，会话列表查询；
- `idx_conversation_id`：`{conversationId: 1}`，群消息/解散/隐藏时按会话批量更新。

## 3. 生命周期

| 事件 | 记录变化 |
| --- | --- |
| `POST /conversations/create`（打开会话） | 群：必须是群成员，否则 403。私聊：必须是好友。只 upsert 调用者自己的记录并恢复显示，已有的置顶/免打扰/未读不变；私聊时对方缺记录则以隐藏状态补建（不改动对方已有记录） |
| 置顶 / 免打扰 / 删除 / 清空 | 只修改调用者自己的记录；群会话还要求调用者当前仍是群成员，否则 404 |
| 新私聊消息（WS / HTTP） | 双方记录更新 `lastMessage`、`updatedAt`，恢复显示；只有接收方 `unreadCount + 1` |
| 新群消息（WS / HTTP） | 所有当前成员记录更新 `lastMessage` 并恢复显示；除发送者外 `unreadCount + 1` |
| 已读回执推进 | 只把读者自己的 `unreadCount` 同步为游标计算值 |
| 撤回 | 若被撤回的是最后一条消息，各记录的预览替换为 `[消息已撤回]` |
| 建群 / 免审核入群 / 邀请直进 / 审批通过 | 为新成员创建（或恢复显示）群记录 |
| 退群 / 被移出 | 删除该成员的群记录（消息和已读游标保留） |
| 解散群 | 删除该群所有记录 |
| 删除好友 | 双方的私聊记录都隐藏（`status=0`），置顶/免打扰保留；再次打开或有新消息时恢复 |

成员关系校验：私聊以“调用者在该会话上有自己的记录且 `targetId` 是对方”为准，群聊以 MySQL 群成员表为准。

## 4. 数据迁移

`config/ConversationRecordMigration` 在启动时执行，可重复运行：

1. 找出没有 `conversationId` 字段的旧文档：
   - 私聊：为双方各写一条记录，复制旧文档的 `top/mute/status/lastMessage/updatedAt`（旧文档双方共享，迁移后每人看到的和以前一致）；
   - 群聊：为该群所有现存成员写记录，旧文档的偏好只给原创建者；群已解散则直接清理；
   - 写入一律 `$setOnInsert`，不会覆盖已经存在的新记录；转换后删除旧文档。
2. 建立上面的索引。
3. 一次性（`schema_migrations` 中 `_id = conversation-per-user-v1-group-backfill` 为标记）为所有现存群的所有成员补建记录。

## 5. 前端

- `conversation.service.ts`：置顶/免打扰改为 `POST`（与后端一致，原来的 `PUT` 返回 405），成功后同步本地列表的 `is_pinned/is_muted`；
- 会话列表用服务端的每人 `unread_count` 初始化侧栏未读角标 `notreadednumber`。

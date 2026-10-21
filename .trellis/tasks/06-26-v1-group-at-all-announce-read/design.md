# 群 @全员 与 群公告已读

## @所有人

- 协议保留值 `at_users` 中的 `"all"`（`MentionUtil.MENTION_ALL` / 前端 `MENTION_ALL_ID`）。
- `MentionUtil.sanitize(..., canMentionAll)`：`canMentionAll` 来自 `GroupMemberService.isAdminOrOwner`。允许时 `all` 放在列表最前，不计入 50 人上限；不允许时丢弃，其余 @单人照旧。
- 投递：`MentionUtil.notifiedUserIds` 在含 `all` 时覆盖除发送者外的当前成员。`Conversation.PendingMention.atAll=true`。会话响应增加 `at_all`、`at_all_count`、`at_all_message_id`。`at_me` 仍表示“有未读提及”（含 @全员），兼容旧客户端。
- 编辑：提及集合变化时 `ConversationRecordService.replaceMentions` 先拔掉该消息的旧标记，再按新列表重写，避免 `all` 与具体用户 ID 做差集时把标记写到不存在的用户 `"all"` 上。
- 帧：`GROUP_MESSAGE_RESPONSE` 与 `MESSAGE_EDITED` 顶层增加 `at_all`。接收者的 `at_me` 在 `all` 或自己的 ID 命中时为 true，发送者本人不为 true。

## 群公告已读

- 表 `group_announcement_reads`（`announcement_id + user_id` 唯一）。启动时 `CREATE TABLE IF NOT EXISTS`，并写入 `onlineIM.sql`。
- `POST /api/v1/groups/{groupId}/announcements/{announcementId}/read`：成员确认，重复确认保持首次 `read_at`。
- `GET .../announcements` 与详情增加 `read_by_me`；群主/管理员另有 `read_count`、`unread_count`（当前 `status=1` 的成员）。
- `GET .../announcements/{announcementId}/reads`：仅群主/管理员，返回已读/未读名单。
- 发布者在发布时写入自己的已读；标题或正文变化时清空该公告的已读再记操作者已读；只改置顶不清空。
- Redis Stream：`GROUP_ANNOUNCEMENT`（published/updated/deleted，全体在群成员）、`GROUP_ANNOUNCEMENT_READ`（群主/管理员 + 确认者）。非管理员的已读帧去掉人数字段。
- Web：群聊顶栏公告条 + 弹层，未确认不消失；管理页展示人数和名单。候选 @ 列表仅管理员看到“所有人”。

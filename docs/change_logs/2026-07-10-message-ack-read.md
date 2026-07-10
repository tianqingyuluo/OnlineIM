# 消息 ACK 与已读回执变更记录

- 日期：2026 年 7 月 10 日
- 关联任务：`06-26-v1-message-ack-read`

## 变更摘要

- 修正私聊 WebSocket 发送处理器：发送者身份改为从认证 session 获取，并校验私聊会话双方关系；重复 `client_message_id` 只返回原消息 `MESSAGE_ACK`。
- 修正群聊发送处理器：校验群成员身份，保持按发送者和 `client_message_id` 幂等，并在落库及 Redis 发布成功后返回 `MESSAGE_ACK`。
- 强化送达/已读回执：使用统一的 seq 数值比较，校验回执消息、会话成员关系和已读游标不能超过会话最新序列号。
- 修正消息增量同步和未读统计的 MongoDB seq 查询，使用数值转换避免 Snowflake 字符串字典序错误；同步结果在服务层再次按 seq 数值排序。
- 新增发送器安全与幂等单元测试，覆盖身份伪造、重复客户端 ID、群成员权限和 ACK 行为。
- 登记 `MESSAGE_ACK`、`RECEIPT`、`READ_RECEIPT`、`delivery_state`、`read_seq`、`delivered_seq` 协议字段和方向。
- 完成前端主链路：新增消息五态类型与状态迁移，统一处理 `MESSAGE_ACK`、`RECEIPT`、`READ_RECEIPT` 和结构化 `ERROR`。
- 扩展 IndexedDB v3：新增送达/已读回执离线队列，出站消息按 `client_message_id` 更新而不是重复插入。
- history store 现在负责 ACK 合并、三次超时重试、送达后回执、单调 readSeq、重连同步和群成员 readers 缓存。
- 私聊和群聊 UI 增加发送失败重试、发送/送达/已读状态、前台可视 300ms 已读触发、“全部标为已读”和群聊头像摘要/完整阅读成员列表。
- WebSocket 路由错误帧补充 `client_message_id`，便于明确拒绝消息直接显示发送失败。
- 统一历史消息 HTTP 响应为 `{ messages, has_more_before, has_more_after }`，同时前端保留对旧数组响应的兼容归一化；历史分页参考值按 `seq_id` 查询而不是误当作 Mongo `_id`。
- 未读统计改为只计算当前用户收到的消息，群聊排除当前用户自己发送的消息。
- 为 `conversation_read_states` 增加会话/用户索引与唯一复合索引，保证每个会话成员只有一条游标记录。
- 修正前端收到其他成员的送达/已读回执时误写当前用户 read state 的问题，并按回执游标批量推进当前用户自己发送的消息状态。
- 统一 Web 与移动端会话入口使用具备回执和群成员阅读明细的群聊组件；前端 seq 比较改为 BigInt，避免 Snowflake 超过 Number 安全整数范围。
- 修正 IndexedDB 回执队列的并发去重：较旧的 READ_RECEIPT 不覆盖较新游标，发送后仅在队列记录仍匹配时删除，避免丢失最新回执。
- 为会话成员阅读状态增加 `conversationId + readSeq` 查询索引。
- readers 查询同时返回 `delivered_readers`，使发送者离线重连时可以从持久化送达游标恢复“已送达”状态，再由 `readers` 恢复“已读”状态。

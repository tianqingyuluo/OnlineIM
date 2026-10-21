# 实现顺序

1. `MentionUtil` 识别 `all` 与权限参数；发送（WS/HTTP）和编辑接入 `isAdminOrOwner`。
2. 会话 `pendingMentions.atAll`、会话响应 `at_all*`、群消息/编辑帧 `at_all`。
3. 表 `group_announcement_reads`、已读服务、确认/名单接口、公告帧。
4. 前端候选、会话高亮、公告条/弹层、协议登记、PRD。
5. `mvn test` 与 `npm test`（vitest）。

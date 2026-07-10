# 消息回复功能变更日志

## 日期

2026-07-10

## 变更摘要

### 后端模型与协议

- 新增 `MessageReplySnapshot`、`ReplyReferenceResponse`、`MessageContextResponse`，并为私聊、群聊消息文档增加可空 `replyTo` 与 `contentRevision`。
- 私聊、群聊 WebSocket 请求和 HTTP 发送请求统一使用可选字段 `reply_to_message_id`；消息响应统一输出可选 `reply_to`。
- HTTP `MessageSendRequest` 的 `target_id`、`message_type`、`client_message_id`、`at_user_ids` 补齐 snake_case 映射，并保留 camelCase 兼容别名。
- 新增 `ReplyTargetUnavailableException` 与 `MessageContextUnavailableException`，分别稳定映射为 `REPLY_TARGET_UNAVAILABLE` 和 `MESSAGE_CONTEXT_UNAVAILABLE`。
- 新增 `MessageReplyMongoIndexInitializer`，幂等创建私聊、群聊集合的 `replyTo.messageId` 稀疏索引。

### 后端业务链路

- 新增 `MessageReplyService`，集中处理同会话/权限/撤回校验、服务端权威快照生成、Unicode 80 字摘要、非文本占位、批量状态解析与撤回清理。
- 私聊、群聊和 HTTP 发送链路在落库前生成引用快照；HTTP 入口补齐会话/群成员权限、按发送者与 `client_message_id` 幂等、MongoDB 持久化和 Redis Stream 实时投递；引用目标拒绝时不保存、不发布 Redis Stream、不发送成功 ACK。
- 新增 `ConversationIdUtil` 并让会话创建与 HTTP 私聊发送复用同一 ID 规则，修复 HTTP 发送曾额外插入下划线、无法命中真实会话的问题。
- 历史分页、增量同步和上下文窗口使用批量引用解析，避免逐条 N+1 查询；撤回后清空所有直接引用的旧预览，读取响应时再次校验目标状态。
- 新增 `GET /api/v1/messages/{conversationId}/{messageId}/context`，使用认证主体鉴权，按数值型 `seq_id` 获取目标前后窗口。
- 将消息时间格式化从共享 `SimpleDateFormat` 调整为线程安全 `DateTimeFormatter`。

### 前端类型、缓存与状态

- 新增 `ReplyState`、`ReplyReference`、`MessageContextResponse`、撤回事件载荷及共享引用预览工具。
- `MessageService` 增加消息上下文请求，并兼容历史/同步接口的数组与信封响应。
- `MessageService.putMessage` 发送稳定的 `client_message_id`，支持调用方在 HTTP 重试时沿用同一幂等键；同步修复移动端群消息将消息类型传成数字的问题。
- IndexedDB 增加按用户、会话、消息 ID 读取以及撤回联动更新；撤回时同步清空直接引用的旧预览。
- `historyStore` 增加选择/取消引用、发送失败恢复、上下文三级补载、撤回联动，并保证初发、自动重试、手动重试和离线队列保留同一引用目标。
- ACK 仅清除本次发送对应的引用条，避免旧 ACK 清掉用户随后选择的新目标；`REPLY_TARGET_UNAVAILABLE` 会恢复正文与引用上下文且不覆盖用户新输入。
- 修复 Pinia 中 `isMessagePending`、`isMessageTimeout` action/getter 重名导致真实页面无法挂载的问题，保留 getter 作为唯一公开实现。
- 修复群聊离线出站队列未识别 `group_id` 的问题，保证群聊引用消息可以入队和重试。

### Web UI

- 新增共享组件 `MessageReplyPreview.vue`、`ReplyComposerBar.vue`、`MessageActions.vue`，并重构 `UserTextArea.vue` 供私聊和群聊复用。
- 支持悬停快捷回复、右键回复、输入框引用条、关闭按钮、`Esc` 取消、会话切换取消以及撤回消息隐藏回复入口。
- 引用气泡最多展示两行；撤回/不可用状态不读取旧预览；点击引用按内存、IndexedDB、上下文接口三级补载并居中高亮约两秒。
- 定位失败恢复原滚动位置并提示“原消息不可用”；竞态拒绝时在输入框为空的前提下恢复原正文。
- WebSocket 客户端兼容既有顶层 `MESSAGE_RECALLED`、`RECALL_MESSAGE_RESPONSE` 撤回帧。

### 测试与真实验证

- 新增或扩展后端引用服务、消息服务、Controller、索引初始化、私聊/群聊 sender 和 WebSocket Router 测试。
- 新增或扩展前端预览工具、消息服务、history store、WebSocket 服务和共享组件测试。
- 加入 Playwright 配置、Mock HTTP/WS 浏览器夹具和 6 条 UI 用户旅程，覆盖私聊、群聊单层引用、撤回竞态、跨分页定位及幂等重试。
- 加入可通过 `ONLINEIM_E2E_REAL=1` 启用的真实集成测试，验证 MySQL 用户/会话、MongoDB 引用快照与索引、HTTP 引用发送与幂等响应、Redis Stream 实时投递、HTTP 历史/上下文、双 WebSocket、撤回清理、拒绝路径和幂等 ACK。

### 本轮质量补强涉及文件

- 新增 `onlineIM-server/src/main/java/icu/tianqingyuluo/onlineim/util/ConversationIdUtil.java` 与对应单元测试，统一私聊会话 ID 生成。
- 修改 `MessageSendRequest.java`、`MessageController.java`、`MessageServiceImpl.java`、`GlobalExceptionHandler.java`，完成 HTTP 请求映射、认证主体、权限、幂等、Redis 发布和 403 错误处理。
- 修改 `ConversationController.java`，复用统一私聊会话 ID 工具。
- 修改 `OnlineIM-Vue/src/services/message.service.ts` 与 `phoneGroupMainPart.vue`，补齐 HTTP 幂等键和字符串消息类型。
- 扩展 `MessageControllerTest.java`、`MessageServiceImplTest.java`、`message.service.test.ts` 与 `message-reply.real.spec.ts`，覆盖 snake_case、私聊/群聊权限、重复请求、拒绝路径和真实 HTTP 实时投递。

### 验证结果

- 后端全量测试：90 个测试全部通过。
- 前端 Vitest：8 个测试文件、73 个测试全部通过。
- Playwright Mock E2E：6 个通过，真实集成默认跳过。
- Playwright 真实集成：启用 `ONLINEIM_E2E_REAL=1` 后 1 个场景通过。
- 生产构建：仍被 74 条既有 TypeScript 基线错误阻塞；本任务相关文件无新增类型错误，相比实施前基线减少 1 条移动端消息类型错误。

### 规范固化

- 更新 `.trellis/spec/backend/quality-guidelines.md`，记录 HTTP/WebSocket 发送入口的 snake_case、认证身份、会话 ID、幂等、MongoDB 与 Redis Stream 顺序及测试矩阵。
- 更新 `.trellis/spec/frontend/state-management.md`，记录回复消息在乐观态、自动/手动重试、离线队列、ACK 恢复中的字段不变量，并明确 Pinia action/getter 不得重名、群聊队列必须识别 `group_id`。

## 关联任务

- Trellis 任务：`06-26-v1-message-reply`

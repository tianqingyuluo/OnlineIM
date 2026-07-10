# 消息回复（引用回复）实施计划

## 0. 执行原则

- 严格按测试先行推进：先写用户旅程 e2e/契约测试，再写实现。
- 每完成一个层次立即运行聚焦测试；最后运行全量后端、前端、构建和 e2e。
- 不在本任务实现消息编辑、媒体缩略图、Tauri 或 Flutter。
- 任一阶段发现协议或产品语义不成立，回到 `prd.md` / `design.md` 修订后再继续。

## 1. 建立失败测试与测试基线

- [x] 为 Web 端加入 Playwright e2e 配置和消息回复核心旅程用例，先覆盖：私聊回复、群聊单层引用、目标撤回竞态、跨分页定位。
- [x] 为后端增加 `MessageReplyServiceTest`、私聊/群聊 sender 引用场景测试和消息上下文服务测试骨架。
- [x] 为前端增加预览工具、history store 和引用组件测试骨架。
- [x] 运行现有测试，记录基线；确认新增测试在未实现功能时按预期失败，而不是因环境配置失败。

验证命令：

```bash
cd onlineIM-server && ./mvnw test
cd OnlineIM-Vue && npm test
cd OnlineIM-Vue && npm run build
```

回滚点：仅新增测试与测试配置，可整体撤销，不影响生产代码。

## 2. 冻结后端引用模型与协议 DTO

- [x] 新增 `MessageReplySnapshot`、`ReplyReferenceResponse`、`MessageContextResponse` 和引用状态枚举/常量。
- [x] `PrivateMessage`、`GroupMessage` 增加可空 `replyTo` 与 `contentRevision`。
- [x] 私聊/群聊 WebSocket request 和 HTTP `MessageSendRequest` 统一增加 `reply_to_message_id`；删除或兼容迁移未使用的 `quoteMessageId`。
- [x] `MessageResponse` 增加可空 `reply_to`。
- [x] 增加 Mongo `replyTo.messageId` 索引初始化。
- [x] 更新序列化和旧文档兼容测试。

验证：运行模型、ObjectMapper 和 Mongo 往返测试。

回滚点：字段均可空，撤销 DTO/文档字段不会要求数据迁移；已创建索引可安全保留。

## 3. 实现服务端快照、校验与错误契约

- [x] 实现 `MessageReplyService` 的会话访问校验、目标查询、快照生成和批量状态解析。
- [x] 实现 Unicode 安全 80 字预览与消息类型占位规则。
- [x] 新增 `ReplyTargetUnavailableException`，统一 HTTP 与 WebSocket 错误码。
- [x] 调整 WebSocket 路由器，使业务拒绝保留 `client_message_id` 并返回具体错误码。
- [x] 增加不存在、跨会话、无权限、已撤回、自引用和回复引用消息的单元测试。

验证：

```bash
cd onlineIM-server && ./mvnw -Dtest=MessageReplyServiceTest,WebSocketMessageRouterTest test
```

回滚点：`MessageReplyService` 尚未接入发送链路，可独立撤销。

## 4. 接入私聊、群聊与 HTTP 发送链路

- [x] `PrivateMessageSender` 在持久化前创建引用快照。
- [x] `GroupMessageSender` 在持久化前创建引用快照。
- [x] HTTP `MessageService.sendMessage` 使用同一服务和同一字段语义。
- [x] 设置新消息 `contentRevision=1`。
- [x] 确认幂等重复请求返回原 ACK，不改变第一次引用目标。
- [x] 拒绝路径断言无 Mongo 保存、无 Redis Stream 发布、无 ACK。
- [x] 更新 sender 单元测试和 Redis 实时投递集成测试。

验证：

```bash
cd onlineIM-server && ./mvnw -Dtest=PrivateMessageSenderTest,GroupMessageSenderTest,WebSocketMessageRouterTest test
```

回滚点：移除三个发送入口的服务调用即可恢复普通消息发送；可空模型字段保留无影响。

## 5. 实现历史响应和原消息状态联动

- [x] 私聊/群聊响应转换输出 `reply_to`。
- [x] 历史分页、增量同步和上下文窗口使用批量状态解析，避免 N+1。
- [x] 原消息撤回成功后批量更新直接引用为 `recalled` 并清空预览。
- [x] 读取时再次校验目标状态，确保旧数据不会泄露撤回内容。
- [x] 为未来编辑任务提供基于 `contentRevision` 的 `edited` 状态入口。
- [x] 增加撤回、缺失目标、版本变化和旧文档测试。

验证：

```bash
cd onlineIM-server && ./mvnw -Dtest=MessageReplyServiceTest,MessageServiceImplTest test
```

回滚点：先关闭响应状态解析，再移除批量更新调用；保留快照字段不会破坏旧客户端。

## 6. 实现消息上下文接口

- [x] repository 增加按数值型 `seq_id` 查询目标前后窗口的方法。
- [x] service 增加鉴权、窗口限制、排序与响应组装。
- [x] controller 新增 `GET /api/v1/messages/{conversationId}/{messageId}/context`，使用认证主体获取用户 ID。
- [x] 统一 `MESSAGE_CONTEXT_UNAVAILABLE` 错误，不泄露其他会话消息。
- [x] 增加私聊、群聊、撤回目标、无权限、边界窗口和数值排序测试。

验证：

```bash
cd onlineIM-server && ./mvnw -Dtest=MessageContextServiceTest,MessageControllerTest test
```

回滚点：该接口为新增接口，可独立删除；发送和历史回复仍可工作，只失去远距离跳转。

## 7. 实现前端类型、预览工具和缓存能力

- [x] 在 `type/message.ts` 增加 `ReplyState`、`ReplyReference`、上下文响应类型。
- [x] 新增共享引用预览工具，覆盖文本规范化、80 字截断和类型占位。
- [x] `message.service.ts` 增加上下文请求并统一现有历史返回类型。
- [x] IndexedDB 增加按消息 ID + 用户 + 会话校验读取的方法；上下文消息继续使用 `putHistory`。
- [x] 为预览、API 解包和 IndexedDB 读写添加测试。

验证：

```bash
cd OnlineIM-Vue && npm test -- --run src/utils src/services
cd OnlineIM-Vue && npm run build
```

回滚点：新增类型、工具和服务方法尚未接入 UI，可独立撤销。

## 8. 扩展 history store 的发送、重试与定位状态

- [x] 增加当前回复目标和选择/取消 action。
- [x] 临时消息、pending 项、自动重试、手动重试和离线队列全部携带 `reply_to_message_id`。
- [x] 保存待 ACK 的作文快照；ACK 成功后清理，特定错误时恢复正文和引用上下文。
- [x] `handleServerError` 按错误码区分普通发送失败与目标不可用。
- [x] 实现内存 → IndexedDB → 上下文接口的三级定位与消息合并。
- [x] 实现撤回事件对原消息、直接引用和 IndexedDB 的同步更新。
- [x] 增加 store 测试：ACK、超时、重试、离线队列、不可用目标、上下文合并、撤回预览清除。

验证：

```bash
cd OnlineIM-Vue && npm test -- --run src/stores/__tests__/history.test.ts src/services/__tests__/websocket.service.test.ts
```

回滚点：store 新状态均为瞬态；移除 action 与 payload 扩展即可恢复旧发送流程。

## 9. 实现共享 UI 与私聊/群聊接入

- [x] 新增 `MessageReplyPreview.vue`、`ReplyComposerBar.vue`、`MessageActions.vue`。
- [x] 私聊和群聊消息气泡接入悬停按钮与右键菜单。
- [x] 输入区接入引用条、关闭按钮、`Esc` 和切换会话清理。
- [x] 已撤回/不可用消息隐藏回复入口。
- [x] 点击引用执行定位、居中滚动、两秒高亮和失败 toast。
- [x] 引用区域限制两行；`recalled/unavailable` 不访问旧预览。
- [x] 增加组件测试和键盘交互测试。

验证：

```bash
cd OnlineIM-Vue && npm test
cd OnlineIM-Vue && npm run build
```

回滚点：共享组件可从两个 MainPart 移除，协议和 store 保持向后兼容。

## 10. 完成真实集成与 e2e

- [x] 使用本地 MySQL、MongoDB、Redis 启动后端和前端测试环境。
- [x] 先运行并修复步骤 1 创建的 Playwright 用户旅程。
- [x] 验证私聊、群聊、撤回竞态、断线重试和跨分页上下文。
- [x] 验证原消息撤回后，实时客户端、刷新客户端和 IndexedDB 缓存均不显示旧预览。
- [x] 验证浏览器只发起一次上下文请求。

建议命令：

```bash
docker compose -f local-dev.middleware.yml up -d
cd onlineIM-server && ./mvnw spring-boot:run
cd OnlineIM-Vue && npm run dev
cd OnlineIM-Vue && npx playwright test
```

回滚点：e2e 失败不得通过降低断言绕过；回到对应实现步骤修复。

## 11. 文档、全量质量门和发布检查

- [x] 更新 `docs/ws-protocol-registry.md`：请求字段、响应字段、状态枚举、错误码和上下文接口。
- [x] 为所有代码文件变更维护中文 `docs/change_logs/` 记录。
- [x] 运行后端全量测试。
- [x] 运行前端全量 Vitest、TypeScript 检查和生产构建。
- [x] 运行 Playwright e2e。
- [x] 检查 `git diff --check`、无调试日志、无 `any` 扩散、无旧 `quoteMessageId` 遗漏。
- [x] 检查服务端先于客户端发布以及后端不能单独回滚的发布约束。

最终验证命令：

```bash
cd onlineIM-server && ./mvnw test
cd OnlineIM-Vue && npm test
cd OnlineIM-Vue && npm run build
cd OnlineIM-Vue && npx playwright test
git diff --check
git status --short
```

> 质量门说明（2026-07-10）：后端 90/90、前端 Vitest 73/73、Mock E2E 6 条、真实集成 1 条均通过；`npm run build` 已执行，但仍被 74 条与本任务无关的既有 TypeScript 基线错误阻塞。本任务相关文件无新增构建错误。

## 12. 评审门

实施完成后必须确认：

- [x] PRD 所有验收标准逐项有测试或人工证据（构建基线例外见上方质量门说明）。
- [x] 私聊、群聊、WS、HTTP、Mongo、Redis、IndexedDB、UI 数据流均已覆盖。
- [x] 撤回后无旧预览泄露。
- [x] 拒绝路径无持久化、无发布、无 ACK。
- [x] 旧消息和无引用消息回归通过。

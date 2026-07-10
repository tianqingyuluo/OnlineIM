# 消息回复（引用回复）任务完成记录

## 任务信息

- **任务 ID**：`v1-message-reply`
- **任务路径**：`.trellis/tasks/06-26-v1-message-reply`
- **任务标题**：消息回复（引用回复）
- **完成日期**：2026 年 7 月 10 日
- **状态**：功能实现、真实链路验证、协议文档与工程规范同步完成；前端生产构建仍受既有 TypeScript 基线错误阻塞

## 交付内容

### 后端引用回复链路

- 私聊、群聊的 WebSocket 与 HTTP 发送统一支持 `reply_to_message_id`，由服务端校验引用目标并生成权威引用快照。
- Mongo 消息文档保存单层直接引用、`contentRevision` 与必要索引；旧消息和无引用消息保持兼容。
- 引用状态统一为 `active`、`edited`、`recalled`、`unavailable`，原消息撤回后清空直接引用中的旧预览，避免内容泄露。
- 文本预览按 Unicode code point 截断为最多 80 个字符；图片、语音、视频、表情、文件及未知类型使用统一占位摘要。
- 历史、同步和上下文查询批量解析引用状态，避免逐条查询造成 N+1 问题。
- 新增消息上下文接口，按原消息前后窗口返回可定位消息；不存在、越权或不可访问目标返回统一错误且不泄露其他会话信息。
- HTTP 发送补齐私聊参与者/群成员权限、按发送者与 `client_message_id` 幂等、Mongo 持久化及 Redis Stream 发布。
- 私聊会话 ID 生成收敛到 `ConversationIdUtil`，消除 HTTP 与既有 WebSocket/会话查询规则漂移。
- `ForbiddenException` 统一映射为 HTTP 403；引用目标竞态失败返回冲突错误，并保证不落库、不发布。

### 前端交互、恢复与定位

- 私聊和群聊消息气泡新增悬停回复入口、统一右键消息操作菜单和引用预览气泡。
- 输入区新增引用条，支持关闭、`Esc`、切换会话清理；发送成功后清除，失败或重试期间保留正文与引用关系。
- 临时消息、待 ACK 项、自动重试、手动重试及离线队列均携带稳定的 `client_message_id` 和 `reply_to_message_id`。
- 特定目标不可用错误可恢复待发送正文与引用上下文，提示用户取消引用后重新发送。
- 点击引用采用“当前列表 → IndexedDB → 上下文接口”三级定位，合并窗口消息后居中滚动并短暂高亮。
- IndexedDB 缓存、实时撤回事件与历史刷新同步更新引用状态；撤回或不可用引用不再展示旧摘要。
- `MessageService.putMessage` 支持 snake_case HTTP 契约并自动生成或复用稳定客户端消息 ID。
- 修复移动端群聊发送数字消息类型的问题，使其复用统一字符串消息类型与回复链路。

### 协议、规范与记录

- 更新 `docs/ws-protocol-registry.md`，登记请求字段、响应引用结构、状态枚举、错误码与上下文 HTTP 接口。
- 新增 HTTP 消息发送契约文档，明确 HTTP/WS 在鉴权、会话 ID、幂等、引用校验、持久化和 Redis 发布方面的一致性。
- 更新后端质量规范，固化 HTTP/WS 发送一致性的七段式契约。
- 更新前端状态管理规范，固化回复发送、ACK 恢复、重试、离线队列、Pinia 命名与群聊队列字段约束。
- 完成 PRD、技术设计、实施清单及变更日志同步。

## 验收结果

### 已通过

- 后端全量测试：`cd onlineIM-server && ./mvnw test`，90 项测试全部通过。
- 前端 Vitest：`cd OnlineIM-Vue && npm test`，8 个测试文件、73 项测试全部通过。
- Mock Playwright：`cd OnlineIM-Vue && npx playwright test`，6 项通过，1 项默认跳过的真实集成测试未在该命令中启用。
- 真实 HTTP/WS/Mongo/Redis 集成：`ONLINEIM_E2E_REAL=1 npx playwright test e2e/message-reply.real.spec.ts`，1 项通过。
- 真实集成覆盖 HTTP snake_case 引用发送、Mongo 快照、Redis 到 WebSocket 投递、HTTP 幂等、撤回后预览清除、已撤回目标拒绝及拒绝后无新增历史。
- 静态检查：`git diff --check` 通过，旧 `quoteMessageId` / `quote_message_id` 搜索为空。
- 新增代码未引入调试日志、`printStackTrace` 或前端 `any` 扩散。

### 既有基线例外

- `cd OnlineIM-Vue && npm run build` 已执行，但仍被 74 条既有 TypeScript 错误阻塞。
- 实施前记录为 75 条；本任务修复移动端数字消息类型后减少 1 条。
- 本任务相关文件未新增 TypeScript 构建错误，详细日志保存在执行环境的 `/tmp/onlineim-reply-build.log`。

## 发布与回滚说明

- 发布顺序应为服务端先于 Web 客户端，以确保服务端已理解 `reply_to_message_id` 和新响应字段；旧客户端可忽略新增字段继续工作。
- 若需回滚前端，可移除回复 UI、定位状态及发送字段，后端仍兼容普通消息。
- 若需回滚后端，应与已发布客户端协调，不能在客户端仍发送引用字段时单独回退到不支持引用校验的版本。
- Mongo 新增引用字段与索引均为向后兼容结构，旧消息无需数据迁移。

## 后续事项

1. 在独立基线治理任务中清理剩余 74 条既有 TypeScript 错误，恢复前端生产构建质量门。
2. 后续消息编辑功能可通过 `contentRevision` 将历史引用标记为 `edited`，无需改变当前引用快照协议。
3. 后续文件与图片缩略图功能可增强引用视觉展示，但不应改变当前服务端权威摘要和单层引用语义。

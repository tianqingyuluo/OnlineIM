# HTTP 消息发送契约补强技术方案变更记录

## 日期

2026-07-10

## 变更背景

消息回复实施后的跨层质量检查发现，HTTP `/api/v1/messages/send` 与 WebSocket 发送链路存在契约漂移：HTTP DTO 仅映射了 `reply_to_message_id`，其余字段仍依赖 camelCase；私聊会话 ID 比会话创建规则多一个下划线；HTTP 入口缺少会话权限、`client_message_id` 幂等和 Redis Stream 实时投递。该状态会导致 Web 客户端发送的 snake_case 请求无法完整反序列化，并使 HTTP 引用消息无法达到与 WebSocket 一致的可靠发送语义。

## 方案对比与决策

### 方案一：本任务内抽取全新的统一消息派发服务

优点是 HTTP 与 WebSocket 可以共享全部构建、持久化和发布代码。缺点是需要同时重构两个已稳定的 WebSocket sender、ACK 处理和现有测试，扩大本次引用回复任务的回归面。

### 方案二：冻结统一契约，在现有 HTTP 服务中补齐相同语义

保留 WebSocket sender 结构，在 `MessageServiceImpl.sendMessage` 中按同一顺序执行权限校验、幂等查询、引用快照、MongoDB 保存和 Redis Stream 发布；通过单元测试与真实集成测试锁定两条入口的一致行为。同时抽取独立的 `ConversationIdUtil`，消除会话创建与 HTTP 发送之间的 ID 规则重复。

本次选择方案二。它能够修复已确认的协议和数据流缺口，同时避免在消息回复任务中引入发送架构的大范围重构。后续若统一普通发送管线，可在不改变本次冻结协议的前提下再抽取共享派发服务。

## 影响评估

- HTTP 请求字段统一为 snake_case：`target_id`、`message_type`、`client_message_id`、`reply_to_message_id`、`at_user_ids`；服务端保留 camelCase 别名兼容 Java/旧调用方。
- HTTP `client_message_id` 成为必填幂等键；前端 `MessageService.putMessage` 默认生成 UUID，也允许重试时显式复用。
- HTTP 私聊和群聊发送均在持久化前校验当前认证用户的会话访问权；无权访问返回 403。
- HTTP 成功发送会写入 MongoDB 并发布 Redis Stream，在线接收端可以通过现有 WebSocket 实时收到消息。
- 引用目标不可用继续统一返回 409 和 `REPLY_TARGET_UNAVAILABLE`，拒绝时无持久化、无 Redis 发布。
- 私聊会话 ID 生成规则由 `ConversationIdUtil` 单点维护，会话创建与 HTTP 发送保持一致。

## 关联任务

- Trellis 任务：`06-26-v1-message-reply`

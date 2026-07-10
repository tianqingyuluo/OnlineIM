# 消息 ACK 与已读回执任务完成记录

## 任务信息

- **任务 ID**：`v1-message-ack-read`
- **任务路径**：`.trellis/tasks/06-26-v1-message-ack-read`
- **任务标题**：消息 ACK 与已读回执
- **完成日期**：2026 年 7 月 10 日
- **状态**：实现、分层验证与协议/规范同步完成；真实基础设施集成测试待具备 Docker 和网络 socket 权限的环境补跑

## 交付内容

### 后端协议与可靠性

- 新增 `MESSAGE_ACK`、`RECEIPT`、`READ_RECEIPT` 的 WebSocket 请求/响应载荷及统一路由。
- 私聊和群聊发送成功后，仅在消息落库并发布 Redis Stream 成功时发送 `MESSAGE_ACK`；失败返回结构化 `ERROR`，并保留 `client_message_id` 便于客户端定位失败消息。
- 发送者身份统一从认证 `WebSocketSession` 获取，不信任客户端 payload 中的 `sender_id`。
- 发送链路按发送者与 `client_message_id` 幂等，重复发送返回原消息 ACK，不重复写入消息。
- 新增送达/已读回执服务、接收器和 Redis Stream 跨实例路由，校验会话成员、消息方向、消息归属及 seq 边界。
- 新增 `ConversationReadState` 持久化模型、Repository、唯一复合索引和 `conversationId + readSeq` 查询索引；已读游标只允许单调推进。
- 新增 `SeqIdComparator`，所有 Snowflake seq 均按数值比较，避免字符串字典序、`Number` 精度和减法溢出问题。
- 新增 `read-state`、`unread-count/{seqId}` 和 readers HTTP 查询；群聊阅读成员由成员 readSeq 推导，不按消息持久化成员数组。
- readers 响应增加可选 `delivered_readers`，支持发送者离线重连后恢复“已送达”状态。

### 前端状态与交互

- 消息状态统一为 `sending`、`failed`、`sent`、`delivered`、`read` 五态，并集中处理状态迁移。
- WebSocket service 统一解析 ACK、送达回执、已读回执和结构化错误，组件不再重复解析 envelope。
- 出站消息持久化到 IndexedDB，支持原 `client_message_id` 重试、ACK 去重和最多三次确认重试。
- IndexedDB v3 新增离线回执队列；发送后仅在队列记录仍与当前 `id/dedupe/payload` 匹配时删除，避免并发更新导致最新回执丢失。
- history store 分离当前用户 read state 与远端成员游标；远端回执只推进当前用户自己发送的消息，群成员阅读进度与 readers 查询结果独立缓存。
- 前台连续可视约 300ms 的消息触发单调 `READ_RECEIPT`；支持“全部标为已读”和断线恢复。
- 私聊/群聊 UI 展示发送中、失败/重试、已发送、已送达、已读状态；群聊展示已读头像摘要并可展开完整阅读成员列表。
- 移动端群聊入口复用统一 `GroupMainPart`，避免绕过新回执链路。
- 前端 seq 比较统一使用 BigInt，兼容 Snowflake 超过 JavaScript `Number.MAX_SAFE_INTEGER` 的情况。

### 协议、规范与记录

- 更新 `docs/ws-protocol-registry.md`，登记新增消息类型、方向和字段：`delivery_state`、`read_seq`、`delivered_seq`、`delivered_readers` 等。
- 更新后端质量规范和前端状态管理规范，固化 ACK/回执跨层契约、游标隔离和 IndexedDB 并发删除约束。
- 更新任务 PRD、设计文档、实施清单、检查清单及变更记录。

## 验收结果

### 通过

- `onlineIM-server`: `./mvnw -q -DskipTests compile`
- `onlineIM-server`: 定向单元测试（seq 比较、read state、receipt service、发送幂等、WebSocket router、receipt receivers）
- `OnlineIM-Vue`: `npm test`，5 个测试文件、49 个测试通过
- `git diff --check`
- Trellis task validation：`task.py validate 06-26-v1-message-ack-read`

### 环境或既有问题阻塞

- `onlineIM-server`: `./mvnw -q test` 共 64 个测试，0 个 assertion failure，7 个环境错误：4 个 Testcontainers 测试缺少 Docker；3 个 Vert.x WebSocket 集成测试受当前环境禁止创建网络 socket 影响。
- `OnlineIM-Vue`: `vue-tsc -b` / `npm run build` 被 75 个既有 TypeScript 错误阻塞；错误位于本任务未修改的其他文件，针对本任务修改文件过滤结果为 0 个错误。
- 真实 MongoDB/Redis 多实例、Docker 及核心 WebSocket e2e 尚未在当前受限环境执行，需在具备 Docker 与网络 socket 权限的 CI 或开发环境补跑。

## 遗留与后续

1. 在具备 Docker、MongoDB、Redis 和网络 socket 权限的环境补跑后端 Testcontainers、真实 WebSocket、多实例 Redis Stream 及核心端到端旅程。
2. 清理或修复项目中与本任务无关的既有前端 TypeScript 错误，再执行完整生产构建。
3. 后续 Tauri/Flutter 客户端接入时，按 `docs/ws-protocol-registry.md` 复用 ACK、回执和 read-state 契约。

## 回滚说明

- 新增 read state 集合和索引可独立保留，不影响旧消息历史；必要时可先回滚 WebSocket 回执路由和前端 UI，再按兼容接口继续使用消息同步。
- 新增协议字段保持可选/可忽略，旧客户端可继续处理既有消息 envelope。

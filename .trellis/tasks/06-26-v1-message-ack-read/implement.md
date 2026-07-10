# 消息 ACK 与已读回执实施计划

## 实施前置门槛

- [x] 用户已审阅并批准 `prd.md`、`design.md`、本实施计划；
- [x] 任务已从 `planning` 切换为 `in_progress` 后实施；
- [x] 未修改其他 v1 子任务的业务协议；新增字段已登记到本任务协议契约；
- [x] 已先建立后端/前端定向旅程测试骨架，再补齐实现；

## 阶段 1：协议与测试契约

1. [x] 将 `MESSAGE_ACK`、`RECEIPT`、`READ_RECEIPT` 及 `delivery_state`、`read_seq`、`delivered_seq` 字段登记到 `docs/ws-protocol-registry.md`。
2. [x] 固化 JSON envelope、方向、身份来源、幂等键和错误语义；明确旧 `PRIVATE_MESSAGE_RESPONSE`/`GROUP_MESSAGE_RESPONSE` 的接收端兼容行为。
3. [x] 编写后端 WebSocket/回执定向测试骨架：私聊成功、失败重试/重复 client ID、已读推进、群成员 readers、回执本地路由；真实多实例测试保留在环境可用时执行。
4. [x] 编写前端 Vitest 旅程测试：五态状态机、回执重复/乱序、断线恢复、连续 readSeq 推进、远端游标不污染本地状态。

## 阶段 2：服务端数据与领域服务

5. [x] 新增 `ConversationReadState` Mongo 文档、Repository、复合索引和默认游标初始化逻辑。
6. [x] 新增统一后端/前端 seq 数值比较和单调 max 工具，补齐边界测试；任务新增路径不再使用字符串字典序或 Number 比较。
7. [x] 实现 `ReceiptService`：
   - [x] 校验当前 session 用户、会话成员关系和消息方向；
   - [x] 按消息/会话成员游标幂等更新 `deliveredSeq`/`readSeq`；
   - [x] 拒绝跨会话、越权、回退游标和超过会话最新范围的回执；
   - [x] 以权威 read state 计算未读摘要并发布变更回执事件；
8. [x] 实现消息发送幂等：按发送者 + `client_message_id` 查重，重复请求返回原消息 ACK；不信任客户端 payload 中的 sender ID。

## 阶段 3：WebSocket ACK/回执链路

9. [x] 扩展 `MessageSenderHandler`/`WebSocketMessageRouter`，把认证 `WebSocketSession` 传入业务 handler；同步修复现有 handler 与 router 单测。
10. [x] 在私聊/群聊发送成功路径发送 `MESSAGE_ACK`，失败路径发送结构化 `ERROR`；ACK 只在 Mongo 保存和 Redis Stream 发布成功后发出。
11. [x] 新增 C→S `RECEIPT`/`READ_RECEIPT` handler 和 S→C 回执事件 handler；复用现有 Redis Stream/消费者组路由。
12. [x] 修改接收端消息处理，使客户端本地持久化成功后才发送送达回执；重连恢复复用出站回执队列、read-state 和 readers 查询。
13. [x] 增加 HTTP `read-state`、`unread-count/{seqId}` 和 readers 查询接口；会话列表返回权威 `read_seq`/`unread_count`。

## 阶段 4：前端状态与同步

14. [x] 扩展消息、会话和 WS payload 类型，回执入口统一按结构化 payload 处理。
15. [x] 在 `websocket.service.ts` 统一处理 `MESSAGE_ACK`、`RECEIPT`、`READ_RECEIPT`、结构化 `ERROR`，组件不重复解析 envelope。
16. [x] 在 `history` store 实现：
   - [x] `sending/failed/sent/delivered/read` 状态迁移；
   - [x] client ID 重试和 ACK 去重；
   - [x] 当前用户游标与远端成员 delivered/read 游标分离；
   - [x] group member readSeq 缓存和 readers 查询缓存；
   - [x] IndexedDB 中断线待发回执的恢复，并修正并发去重删除边界。
17. [x] 实现消息可视检测：前台 + 约 300ms 可见 + 连续最高 seq，发送单调 `READ_RECEIPT`；支持“全部标为已读”。
18. [x] 更新私聊/群聊消息 UI：发送失败重试、送达/已读状态、群聊头像摘要、点击展开成员列表；移动端聊天入口复用同一群聊组件。

## 阶段 5：分层验证

19. [x] 后端单元测试：seq 比较、幂等、权限、未读计算、readers 推导和回执 receiver 路由。
20. [ ] 后端真实集成测试待具备 Docker 和网络 socket 权限的环境重跑；当前相关失败均为环境错误。
21. [x] 前端测试：WebSocket service、history store、状态机、回执解析和可视检测。
22. [x] 已运行 Web 端 `npm test`、`npm run build` 和后端 `./mvnw test`；构建/全集成测试的既有或环境阻塞见下方记录。
23. [ ] 核心真实 e2e 和多实例旅程待 Docker、Mongo/Redis 与网络 socket 环境可用后执行。
24. [x] 已执行 `git diff --check`，并复核协议登记、字段命名、旧消息历史兼容和心跳定向测试。

## 实际验证记录（2026-07-10）

- `cd onlineIM-server && ./mvnw -q -DskipTests compile`：通过。
- 后端定向单元测试（seq、read state、receipt service、发送幂等、router）以及新增 `ReceiptMessageReceiverTest`、`ReadReceiptMessageReceiverTest`：通过。
- `cd OnlineIM-Vue && npm test`：通过，5 个测试文件、49 个测试通过。
- `cd OnlineIM-Vue && npm run build`：未通过；`vue-tsc` 报告 75 个既有错误，错误均位于本任务未修改的其他文件，任务修改文件过滤结果为 0。
- `cd onlineIM-server && ./mvnw -q test`：64 个测试、0 个 assertion failure、7 个环境错误；4 个 Testcontainers 测试缺少 Docker，3 个 Vert.x 集成测试受当前环境禁止创建网络 socket 影响。
- `git diff --check`：通过。

## 风险文件与回滚点

- **高风险接口**：`WebSocketMessageRouter`、`MessageSenderHandler`、现有消息 sender/receiver 及其测试；先改接口和测试，再逐个迁移实现。
- **高风险数据**：`PrivateMessage`、`GroupMessage`、`Conversation` 以及 Mongo Repository；只新增字段/集合，不删除旧字段，不直接唯一化历史数据。
- **高风险前端**：`websocket.service.ts`、`stores/history.ts`、私聊/群聊消息组件、IndexedDB schema；先写状态机和回执解析测试再改渲染。
- **协议回滚**：协议登记与新增字段保持向后可忽略；若回执链路异常，可回滚 WS handler/UI 提交，保留消息历史和旧接收响应。
- **数据回滚**：read state 为新增集合，回滚代码不删除数据；恢复后按默认游标或兼容同步继续运行。

## 交付检查清单

- [x] `prd.md`、`design.md`、`implement.md` 与实际实现和验证限制一致；
- [x] `docs/ws-protocol-registry.md` 已登记所有新增类型和字段；
- [x] 变更后的代码文件均有对应中文 `docs/change_logs/YYYY-MM-DD-<description>.md`；
- [x] 后端、前端、集成和 e2e 验证均有命令输出或环境阻塞报告；
- [x] 已运行 Trellis quality check；任务归档时由 Trellis 生成 task-summary。

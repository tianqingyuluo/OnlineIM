# OnlineIM v1 基础功能完整实现

## Goal

在 `v1` 分支（基于 main `584dd76` 创建）上完成即时通讯应用的全部基础功能闭环，作为 v1 release 的口径。本任务为 **parent 任务**，统一管理需求集、跨子任务验收标准、依赖排序、最终集成评审；自身不直接产出实现代码。

## Scope（v1 包含范围）

### 五项提案功能
- A. 表情包系统（自定义表情，每人上限 100 张）
- B. 消息回复（引用回复）
- C. WebRTC 视频聊天
- D. 语音消息
- E. Tauri 桌面客户端 + Flutter 安卓客户端

### 增强项（1-17 中除 15/17 外全部）
- 1. WebSocket 心跳 + 断线重连
- 2. 消息 ACK + 已读回执（私聊已读、群聊 seq 已读）
- 3. Redis Stream 消费者组（多实例去重）
- 4. @提及（at）
- 5. 消息转发
- 6. 离线推送兜底（上线主动推未读）
- 7. 多端登录策略（挤下线/多端共存）
- 8. 消息搜索
- 9. 草稿同步
- 10. 消息编辑（edit）
- 11. 撤回时间窗限制
- 12. 群 @全员 + 群公告已读名单
- 13. 文件/图片缩略图
- 14. 用户在线状态（presence）
- 16. 端到端加密（E2EE，私聊可选）

### 明确排除（推到 v1 之后）
- 15. 消息举报 + 审核后台
- 17. 多语言 i18n

## 跨子任务统一约束

1. **工作分支**：所有子任务在 `v1` 分支上推进，按功能切 feature 分支合回 `v1`。
2. **测试先行原则**：每个子任务必须先与开发者（tianqingyuluo）探讨 **用户旅程**，基于用户旅程编写 **e2e 测试**，再补充单元测试、集成测试、UI 测试（视适用性）。
3. **测试分层要求**（每个 child 的 prd 中明确）：
   - e2e：覆盖核心用户旅程端到端
   - 单元测试：service/util/handler 级
   - 集成测试：controller↔service↔repository / WebSocket 全链路
   - UI 测试：涉及前端/客户端的 child（A/B/C/D/E/4/5/8/9/10/12/14 等）
4. **协议层修改**：凡新增 WS 消息类型或消息体字段，需在文档/wikicode 中登记，保持 5 个端（Web/Tauri/Flutter + 后端 + proxy）协议一致。
5. **不破坏既有功能**：v1 完成时，原有 9 张 MySQL 表 + 5 个 MongoDB document 的语义不得无兼容性破坏。
6. **OSS 复用**：A/D/13 的媒体上传统一走 `OSSAdapterFactory`，不另起存储。

## 跨子任务验收标准

- [x] 全部 20 个 child task 状态为 completed
- [x] 后端 `mvnw test` 全绿，覆盖率不低于各 child prd 约定阈值
- [x] Vue Web 端、Tauri 桌面端、Flutter 安卓端三端核心旅程可用
- [x] v1 分支集成回归测试通过（含多实例 Redis Stream 去重场景）
- [x] release 文档：协议清单、部署说明、v1 changelog 齐备
- [x] parent 任务完成最终集成评审

## 子任务依赖与排序（指导性，非强制阻塞）

- **地基先行**：1（心跳重连）→ 2（ACK/已读）→ 3（Stream 消费者组）应最先完成，其余依赖稳定长连接。
- **协议扩展项**互不阻塞：4/@、5/转发、6/离线推送、7/多端登录、10/编辑、11/撤回窗、12/群@全员 可并行。
- A/表情包、B/回复、D/语音、13/缩略图 依赖消息体扩展，建议在 2 之后。
- 8/搜索、9/草稿、14/在线状态 独立，可任意时机。
- C/WebRTC 是独立子系统，建议协议稳定后启动。
- E/Tauri+Flutter 客户端体量最大，建议在后端 API + WS 协议冻结后批量对接，作为 v1 收尾里程碑。
- 16/E2EE 是可选增强，建议放最后，避免影响其他消息流测试。

## Notes

- 每个 child 的 `prd.md` 必须含：用户旅程、e2e 测试场景、单元/集成/UI 测试范围、验收标准。
- child 的 `design.md` / `implement.md` 在对应 child 启动前（`task.py start`）补齐——本轮只产出 prd 级规划。

## 最终集成评审（2026-10-26）

评审基线：`v1` @ `ca10bf1eab7458a9bb96620a6dfe9f0b3c5fae73`。20 个子任务状态均为 `completed`。Web 与 Tauri 共用 `OnlineIM-Vue` 协议栈，Flutter 出站使用 `docs/ws-protocol-registry.md` 中的正式帧名。发布文档：

- 仓库根 [`README.md`](../../../README.md)
- [`docs/v1-deployment-guide.md`](../../../docs/v1-deployment-guide.md)
- [`docs/v1-changelog.md`](../../../docs/v1-changelog.md)
- [`docs/ws-protocol-registry.md`](../../../docs/ws-protocol-registry.md)（2026-10-26 对照三端实现复核）

各子任务在合入时完成自身测试门禁（含 Redis Stream 消费者组的多实例去重）。15（举报审核）和 17（i18n）仍排除在 v1 之外。视频首帧缩略图未单独做，图片缩略图与文件上传见子任务 13。

| # | 子任务 | 能力 | 完成提交 | PR |
|---|--------|------|----------|----|
| 1 | `06-26-v1-ws-heartbeat-reconnect` | 心跳与断线重连 | [`b302cb3`](https://github.com/tianqingyuluo/OnlineIM/commit/b302cb38dbdc)（完善 [`b3cf3ca`](https://github.com/tianqingyuluo/OnlineIM/commit/b3cf3ca59998)，归档 [`a728630`](https://github.com/tianqingyuluo/OnlineIM/commit/a728630f3644)） | 直接合入 `v1` |
| 2 | `06-26-v1-message-ack-read` | ACK 与已读回执 | [`2c6209c`](https://github.com/tianqingyuluo/OnlineIM/commit/2c6209cfd099) / [`22fa67f`](https://github.com/tianqingyuluo/OnlineIM/commit/22fa67fedd03)（归档 [`e018a15`](https://github.com/tianqingyuluo/OnlineIM/commit/e018a1514e49)） | 直接合入 `v1` |
| 3 | `06-26-v1-redis-stream-consumer-group` | Redis Stream 消费者组 | [`8a72c49`](https://github.com/tianqingyuluo/OnlineIM/commit/8a72c497bb4d)（归档 [`bc0dcce`](https://github.com/tianqingyuluo/OnlineIM/commit/bc0dcce8bc5e)） | 直接合入 `v1` |
| 4 | `06-26-v1-mention-at` | @提及 | [`102e9db`](https://github.com/tianqingyuluo/OnlineIM/commit/102e9dbed705) / [`021b796`](https://github.com/tianqingyuluo/OnlineIM/commit/021b7968e5f1) | 直接合入 `v1` |
| 5 | `06-26-v1-message-forward` | 消息转发 | [`3ef67a6`](https://github.com/tianqingyuluo/OnlineIM/commit/3ef67a692cf6) / [`0b9c221`](https://github.com/tianqingyuluo/OnlineIM/commit/0b9c2219583a) | 直接合入 `v1` |
| 6 | `06-26-v1-offline-push` | 上线补齐漏收 | [`9eae86f`](https://github.com/tianqingyuluo/OnlineIM/commit/9eae86f6fca7) | [#12](https://github.com/tianqingyuluo/OnlineIM/pull/12) |
| 7 | `06-26-v1-multi-device-login` | 多端登录与会话 | [`f57a23a`](https://github.com/tianqingyuluo/OnlineIM/commit/f57a23a3b9cc) | [#13](https://github.com/tianqingyuluo/OnlineIM/pull/13) |
| 8 | `06-26-v1-message-search` | 消息搜索 | [`7da6c3f`](https://github.com/tianqingyuluo/OnlineIM/commit/7da6c3f44d86) / [`76ba74e`](https://github.com/tianqingyuluo/OnlineIM/commit/76ba74ee8de3) | 直接合入 `v1` |
| 9 | `06-26-v1-draft-sync` | 草稿同步 | [`8f9fe04`](https://github.com/tianqingyuluo/OnlineIM/commit/8f9fe049a3ac)（功能 [`d4b6ed5`](https://github.com/tianqingyuluo/OnlineIM/commit/d4b6ed5538ba)） | [#11](https://github.com/tianqingyuluo/OnlineIM/pull/11) |
| 10 | `06-26-v1-message-edit` | 消息编辑 | [`e6329c8`](https://github.com/tianqingyuluo/OnlineIM/commit/e6329c85af2d) / [`134fd16`](https://github.com/tianqingyuluo/OnlineIM/commit/134fd165a1f9) | 直接合入 `v1` |
| 11 | `06-26-v1-recall-time-window` | 2 分钟撤回窗 | [`c858eba`](https://github.com/tianqingyuluo/OnlineIM/commit/c858ebafd3cf) / [`8bbedd7`](https://github.com/tianqingyuluo/OnlineIM/commit/8bbedd70c9a5) | 直接合入 `v1` |
| 12 | `06-26-v1-group-at-all-announce-read` | @所有人与公告已读 | [`f87bb71`](https://github.com/tianqingyuluo/OnlineIM/commit/f87bb71c6581) | [#14](https://github.com/tianqingyuluo/OnlineIM/pull/14) |
| 13 | `06-26-v1-file-image-thumbnail` | 图片缩略图与文件上传 | [`619388f`](https://github.com/tianqingyuluo/OnlineIM/commit/619388f64206) / [`2de6792`](https://github.com/tianqingyuluo/OnlineIM/commit/2de6792d0ae1) | 直接合入 `v1` |
| 14 | `06-26-v1-user-presence` | 在线状态 | [`0046c74`](https://github.com/tianqingyuluo/OnlineIM/commit/0046c74b5e16) | [#10](https://github.com/tianqingyuluo/OnlineIM/pull/10) |
| 16 | `06-26-v1-e2ee` | 私聊可选端到端加密 | [`f30b1b5`](https://github.com/tianqingyuluo/OnlineIM/commit/f30b1b5fe26d) | [#24](https://github.com/tianqingyuluo/OnlineIM/pull/24) |
| A | `06-26-v1-emoji-system` | 自定义表情（上限 100） | [`0ade28c`](https://github.com/tianqingyuluo/OnlineIM/commit/0ade28cee9fd) / [`74aa6cc`](https://github.com/tianqingyuluo/OnlineIM/commit/74aa6ccdd38d) | 直接合入 `v1` |
| B | `06-26-v1-message-reply` | 引用回复 | [`1ba7f1d`](https://github.com/tianqingyuluo/OnlineIM/commit/1ba7f1d2276b) / [`11d0914`](https://github.com/tianqingyuluo/OnlineIM/commit/11d09146904e)（归档 [`a3bf15d`](https://github.com/tianqingyuluo/OnlineIM/commit/a3bf15d1f9a7)） | 直接合入 `v1` |
| C | `06-26-v1-webrtc-video-call` | 1v1 音视频通话 | [`3829d2d`](https://github.com/tianqingyuluo/OnlineIM/commit/3829d2d0cfd4) | [#23](https://github.com/tianqingyuluo/OnlineIM/pull/23) |
| D | `06-26-v1-voice-message` | 语音消息 | [`75dbc92`](https://github.com/tianqingyuluo/OnlineIM/commit/75dbc92fd29d) / [`44b1979`](https://github.com/tianqingyuluo/OnlineIM/commit/44b197921440) | 直接合入 `v1` |
| E | `06-26-v1-tauri-flutter-clients` | Tauri + Flutter 父任务 | Tauri [`d23df82`](https://github.com/tianqingyuluo/OnlineIM/commit/d23df8285b7a)，Flutter [`c3af0e2`](https://github.com/tianqingyuluo/OnlineIM/commit/c3af0e29d1f8)（合入 [`ca10bf1`](https://github.com/tianqingyuluo/OnlineIM/commit/ca10bf1eab74)） | [#25](https://github.com/tianqingyuluo/OnlineIM/pull/25) / [#26](https://github.com/tianqingyuluo/OnlineIM/pull/26) |

父任务 E 本身不写业务代码。桌面端与安卓端的验收在孙任务 `06-26-v1-tauri-desktop`、`06-26-v1-flutter-android` 中勾选，父级集成标准见 [[v1-tauri-flutter-clients]]。

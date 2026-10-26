# OnlineIM v1 更新日志

发布分支：`v1`。集成基线 `ca10bf1eab7458a9bb96620a6dfe9f0b3c5fae73`（2026-10-26），相对 `main` 的 `584dd76`。

v1 把即时通讯的基础闭环做完：网页、Windows / Linux 桌面、Android 使用同一套 HTTP（`:8080/api/v1`）和 WebSocket（`:8081/api/v1/chat`）。帧格式、字段和错误码登记在 [ws-protocol-registry.md](./ws-protocol-registry.md)。子任务与提交的对照见 `.trellis/tasks/06-26-v1-release/prd.md` 的集成评审。

明确不做：消息举报与审核后台（增强项 15）、多语言 i18n（增强项 17）、iOS、macOS、群通话、视频首帧缩略图、开机自启、应用内更新。

## 提案

### A. 自定义表情

每人一份「我的表情」，默认最多 100 个。支持上传 PNG / JPG / GIF，以及把会话里的图片或表情收藏过来。GIF 保留动画；静态图过大时服务端等比缩小。发送 `message_type=emoji`，服务端校验表情属于发送者并填入 URL 与尺寸。删除收藏不影响已经发出的消息。

### B. 引用回复

私聊和群聊发送时可带 `reply_to_message_id`。服务端校验目标属于同一会话且未撤回，并写入一层快照（原文、已编辑、已撤回、不可用）。客户端不提交预览正文。引用目标后来被编辑或撤回时，历史和实时帧都会把快照状态改掉。

### C. 1v1 WebRTC 语音 / 视频

仅私聊。媒体走 `RTCPeerConnection`，服务端只转发信令：`CALL_INVITE` / `CALL_OFFER`、`CALL_RINGING`、`CALL_ANSWER`、`CALL_CANDIDATE`、`CALL_REJECT`、`CALL_CANCEL`、`CALL_BUSY`、`CALL_END` / `CALL_HANGUP`、`CALL_HANDLED_ELSEWHERE`。同一用户同时只有一路通话。响铃默认 45 秒。ICE 服务器由 `GET /api/v1/calls/ice-servers` 下发，可选 coturn 限时凭证。终态写入一条 `message_type=call` 的会话消息。

### D. 语音消息

先 `POST /api/v1/messages/upload`（`type=voice`），再以 `message_type=voice` 发送。格式按文件内容识别 webm / ogg / mp4 / mp3 / wav，默认最长 60 秒、最短 500 毫秒、最大 5MB。时长优先由服务端解析。播放支持单段 Range。会话预览为 `[语音]`。

### E. Tauri 桌面端与 Flutter Android

- 桌面端（`d23df82`，[PR #25](https://github.com/tianqingyuluo/OnlineIM/pull/25)）用 Tauri 2 包装 `OnlineIM-Vue`。Windows 安装包为 `msi` 和 `nsis`，Linux 为 `deb` 和 `AppImage`。带系统通知、托盘和单实例锁。
- Android（`c3af0e2`，[PR #26](https://github.com/tianqingyuluo/OnlineIM/pull/26)，合入 `ca10bf1`）是独立 Flutter 工程，`minSdk` 24。出站帧使用协议清单里的正式名称。私钥放在安全存储。前台服务用于保活，通知 / 麦克风 / 摄像头 / 存储按运行时权限申请。

父任务的集成标准在 `.trellis/tasks/06-26-v1-tauri-flutter-clients/prd.md`。

## 增强项

### 1. WebSocket 心跳与断线重连

客户端发送 `HEARTBEAT`，服务端回复 `HEARTBEAT_ACK`（`serverTime`）。Web / Tauri 间隔 30 秒，Flutter 间隔 25 秒。90 秒没有入站帧，服务端断开连接。连续两个本端周期没有 ACK，客户端主动重连。`SESSION_KICKED` 和关闭码 `4001` 停止重连。

### 2. 消息 ACK 与已读回执

消息写入 MongoDB 并发布 Redis Stream 之后，发送方收到 `MESSAGE_ACK`（`delivery_state=sent`）。接收方落本地缓存后发 `RECEIPT`。前台连续可见的消息用会话级 `READ_RECEIPT` 推进 `read_seq`，而不是逐条已读。群聊可查询某条消息的读者列表。

### 3. Redis Stream 消费者组

业务事件进入 `im:message:stream`。每个后端实例以自己的身份加入消费者组，从流尾消费，处理完成后 ACK。多实例不会因为重启把同一段历史再投递给在线用户。心跳、上线补齐和会话踢下线不进这条流。

### 4. @提及

群文本消息可带 `at_users`。服务端只保留当前群成员，去掉发送者自己，最多 50 人。实时帧用顶层 `at_me` 告诉接收者。会话列表显示 `[有人@我]`，已读游标推进后清除。

### 5. 消息转发

`POST /api/v1/messages/forward` 支持逐条（`separate`）和合并（`merged`，生成 `chat_record`）。一次最多 9 个目标。逐条转发保留 `forwarded_from`，不复制对象存储里的文件，也不带原来的 @ 和引用。加密消息不能转发。

### 6. 离线同步

每次 WebSocket 连上（含重连）后，客户端发送 `SYNC_REQUEST`，带上各会话本地最大 `seq`。服务端只向这条连接回复 `OFFLINE_SYNC`：权威未读数、缺口消息、已加载消息在离线期间的编辑和撤回。条数不够时 `truncated=true`，客户端再拉 `GET /api/v1/messages/sync/{conversationId}`。这一帧不表示已读。

### 7. 多端登录与会话管理

设备类型只有 `web`、`desktop`、`phone`。不同类型可以同时在线；同类型新登录使旧会话收到 `SESSION_KICKED` 并作废其 JWT。同一 `device_id` 的多个标签页可以共存。`GET /api/v1/devices` 列出设备名称和在线情况，`POST /api/v1/devices/{deviceId}/logout` 远程登出。消息、已读和草稿推到该用户剩下的每条连接。

### 8. 消息搜索

`GET /api/v1/messages/search` 在当前用户可见的会话里搜索文本消息，忽略大小写，按字面量匹配。支持全局和指定会话，游标分页。编辑后的内容按新正文搜索；已撤回和加密消息不出现。网页在会话列表和聊天窗口里都能跳到命中消息。

### 9. 草稿同步

每个用户在每个会话最多一份未发送文本草稿，可带回复目标和群 @。`PUT/GET/DELETE /api/v1/conversations/{id}/draft`。保存后用 `DRAFT_UPDATED` 推给自己的其他在线设备，不推给对方，也不产生未读。会话列表用 `[草稿]` 代替最后一条预览。

### 10. 消息编辑

发送者可在时间窗内（默认 120 秒，与撤回相同）编辑自己的文本。`PUT /api/v1/messages/{id}` 或 `EDIT_MESSAGE_REQUEST`。不新开消息、不改 `seq_id`。成功后会话成员收到 `MESSAGE_EDITED`，气泡显示「已编辑」。非文本、超时、并发冲突会返回明确错误码。

### 11. 两分钟撤回

`DELETE /api/v1/messages/{id}` 与 `RECALL_MESSAGE_REQUEST` 共用规则：只能撤回时间窗内的消息（默认 120 秒）。私聊只能撤回自己的；群主和管理员可以撤回他人的，并带上 `recall_operator_role`。撤回后原文不再从任何读取接口返回，未读数下降，引用变为「原消息已撤回」。自己撤回的文本在当前页面可以重新编辑。

### 12. 群 @所有人与公告已读

`at_users` 中的 `all` 表示 @所有人，只有群主或管理员能留下这个值，不占 50 人额度。会话列表显示 `[@所有人]`。公告仍走原来的 HTTP 管理。成员 `POST .../announcements/{id}/read` 确认；群主和管理员可以拉已读 / 未读名单。实时帧为 `GROUP_ANNOUNCEMENT` 和 `GROUP_ANNOUNCEMENT_READ`。

### 13. 图片、文件与缩略图

`POST /api/v1/messages/upload` 接收 `image` 或 `file`。图片按内容识别，同步生成长边不超过 320px 的缩略图（透明通道用 PNG，否则 JPEG；GIF 缩略图取第一帧）。发送时只认发送者自己的 `file_id`，服务端改写 `content`。列表用 `thumbnail_url`，点击看原图；文件显示卡片。对象存在 RustFS（或所配置的 S3 兼容存储）中，经 `/onlineim/**` 代理，且仅会话参与者可下载。

### 14. 在线状态

只要该用户还有一条 WebSocket 就显示在线；最后一条连接关闭或心跳超时后变为离线，并记录 `last_seen_at`。`GET /api/v1/presence` 供打开页面时拉取，变更用 `PRESENCE_UPDATE` 推给有权看到的好友和同群成员。用户可以隐藏自己的在线状态。

### 16. 私聊可选端到端加密

仅私聊文本。双方上传 P-256 身份公钥和签名公钥，服务端只存公钥。会话开启后，正文是 AES-256-GCM 信封（静态 ECDH + HKDF，每条消息新的 salt、IV 和临时公钥）。群聊、图片、文件、语音和表情不加密。搜索跳过密文，转发被拒绝。预览显示 `[加密消息]`。本机无私钥时提示导入口令备份。Web 与 Tauri 使用 IndexedDB（Tauri 另有本机文件），Flutter 使用安全存储。开启和关闭通过 `E2EE_STATE_CHANGED` 同步到双方在线设备。

## 客户端与构建

| 端 | 构建 |
|----|------|
| Web | `OnlineIM-Vue` 下 `npm run build` |
| Windows / Linux | `OnlineIM-Vue` 下 `npm run tauri:build` |
| Android | `OnlineIM-Flutter` 下 `flutter build apk --release` |

步骤、反向代理和 TURN 见 [v1-deployment-guide.md](./v1-deployment-guide.md)。

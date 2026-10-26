# v1 - Flutter 安卓客户端 执行计划

> 有序检查清单。**第一段为不依赖协议的可先行步骤**；第二段为依赖协议冻结的待启动步骤（开放项），待对应任务冻结后补齐执行细节。

## 可先行步骤（不依赖协议冻结）

- [x] **S1 工程脚手架 + 依赖**
  - `flutter create OnlineIM-Flutter`，配 `pubspec.yaml`：riverpod、go_router、dio、web_socket_channel、drift、flutter_secure_storage、flutter_local_notifications、flutter_foreground_task、permission_handler、freezed、json_serializable
  - `android/app/build.gradle.kts`：minSdk 24、targetSdk 最新
  - `AndroidManifest.xml`：声明权限（INTERNET、FOREGROUND_SERVICE、POST_NOTIFICATIONS、RECORD_AUDIO、CAMERA、存储/媒体）
  - 验证：`flutter run` 可在 minSdk 24 真机启动空白应用
  - 回滚点：移除 `OnlineIM-Flutter/`，不影响其他工程

- [x] **S2 协议层基础**
  - `api_service.dart`：dio 实例 + 拦截器（注入 Bearer token、401 处理）
  - `ws_service.dart`：web_socket_channel 连接 `ws://.../api/v1/chat?token=...`，单例，基础连接+认证+收发
  - models：freezed + json_serializable，snake_case 字段对齐后端
  - 验证：可连接后端 WS、收发一条测试消息
  - 开放项：心跳/重连器留接口，待 [[v1-ws-heartbeat-reconnect]] 冻结

- [x] **S3 本地缓存（SQLite / sqflite）**
  - drift schema：会话表、消息表、草稿表、用户/群缓存表（字段对齐 Vue 端 idb store）
  - DAO + 迁移脚手架
  - 上线先读本地展示再 sync API
  - 验证：写入→读取→重启后仍在

- [x] **S4 token 安全存储**
  - `secure_storage.dart`：flutter_secure_storage 封装 access/refresh token
  - 启动读取、登录写入、登出清除
  - 验证：登录后重启免登录；登出后 token 清除

- [x] **S5 前台服务保活**
  - `flutter_foreground_task`：切后台启动前台服务，WS 保活
  - 静默持久通知通道（与消息通知通道区分）
  - 验证：切后台 WS 仍在线收消息；前台服务通知展示

- [x] **S6 通知权限 + 本地通知**
  - permission_handler 申请 POST_NOTIFICATIONS（Android 13+）
  - flutter_local_notifications 消息通知通道；点击跳转会话
  - 验证：授权后收消息展示系统通知；点击跳转

- [x] **S7 登录-会话核心旅程**
  - 登录页（账号密码）→ 会话列表页 → 聊天页
  - 文本消息收发全链路（HTTP + WS + 本地缓存 + UI）
  - 验证：登录→收发文本→退出登录 全链路真机跑通

- [x] **S8 构建签名 APK**
  - 生成 release keystore（不入库），`key.properties` 配置
  - `flutter build apk --release`
  - 验证：签名 APK 可在干净 minSdk 24 真机安装启动

## 验证命令

```bash
cd OnlineIM-Flutter
flutter pub get
flutter run                    # 真机调试
flutter test                   # 单元测试
flutter build apk --release    # 构建签名 APK
```

## 协议冻结后的步骤

- [x] S9 WS 心跳/重连器（[[v1-ws-heartbeat-reconnect]]）
- [x] S10 多端登录：被挤下线、设备管理、多端投递（[[v1-multi-device-login]]）
- [x] S11 离线推送：上线未读补齐（[[v1-offline-push]]）
- [x] S12 presence 订阅与展示（[[v1-user-presence]]）
- [x] S13 WebRTC 信令、通话 UI、权限、多端响铃（[[v1-webrtc-video-call]]）
- [x] S14 E2EE 密钥 Keystore 存储、加解密（[[v1-e2ee]]）
- [x] S15 ACK/已读回执上报（[[v1-message-ack-read]]）
- [x] S16 消息操作：回复/编辑/撤回/转发/@提及/搜索（依赖对应任务冻结）
- [x] S17 文件/图片/语音消息（依赖 [[v1-file-image-thumbnail]]、[[v1-voice-message]] 冻结）

## S9–S17 冻结后的协议口径

以 `docs/ws-protocol-registry.md` 与 `OnlineIM-Vue/src/crypto/e2ee.ts` 为准，出站只用登记表里的正式帧名。

- **S9**：客户端每 25 秒发送 `{type: HEARTBEAT, message: {}}`。服务端回 `HEARTBEAT_ACK`（入站同时接受 `PONG` / `HEARTBEAT_PONG`）。连续 2 个周期没有回执则主动断开。退避 1s、2s、4s、8s、16s、30s。关闭码 `4001` 与 `SESSION_KICKED` 停止重连；`4000` 停在离线。连接没建立就断开时先 `GET /users/me`，401 则清会话。
- **S10**：登录体 `device_id` + `device_type: phone` + `device_name`（默认 `Flutter Android`）。同类型设备互斥。`SESSION_KICKED.reason` 为 `SAME_DEVICE_TYPE_LOGIN` / `SESSION_REPLACED` / `REMOTE_LOGOUT`。设备列表 `GET /api/v1/devices`，远程登出 `POST /api/v1/devices/{deviceId}/logout`。
- **S11**：每次连接成功发送 `SYNC_REQUEST`（`conversations[].last_seq` 与可选 `watch`）。`OFFLINE_SYNC` 的未读数以 `conversations` 快照为准，不把 `messages` 再逐条加未读。`truncated == true` 时对当前会话再拉 `GET /api/v1/messages/sync/{conversationId}?seq_id=`。
- **S12**：没有单独订阅帧。打开页面 `GET /api/v1/presence?user_ids=`，变更走 `PRESENCE_UPDATE`（入站别名 `PRESENCE_EVENT`）。`visible=false` 按离线且不展示 `last_seen_at`。隐私开关 `PUT /api/v1/presence/settings`。
- **S13**：`GET /api/v1/calls/ice-servers`。信令 `CALL_INVITE` / `CALL_OFFER`、`CALL_RINGING`、`CALL_ANSWER`、`CALL_CANDIDATE`、`CALL_REJECT`、`CALL_CANCEL`、`CALL_BUSY`、`CALL_END` / `CALL_HANGUP`、`CALL_HANDLED_ELSEWHERE`。同一用户同时一路通话。`CALL_HANDLED_ELSEWHERE` 停止本机响铃。历史气泡 `message_type: call`，点击按 `call_type` 重拨。媒体面为 `flutter_webrtc`，状态机可独立测试。
- **S14**：算法名 `ECDH-P256+ECDSA-P256`。P-256 的 ECDH / ECDSA 用 pointycastle 实现，因为 `cryptography` 的纯 Dart 后端没有这两类运算；AES-GCM、HKDF、PBKDF2 与 SHA-256 仍走 `cryptography`，线格式与 Web Crypto 一致。HKDF-SHA256 的 info 是 UTF-8 `OnlineIM-E2EE-v1|{counter}|` 再拼接临时公钥 SPKI，不是会话 ID 拼接。信封字段为 `e2ee/version/ciphertext/iv/salt/sender_key_id/recipient_key_id/ephemeral_public_key/counter`。指纹是身份公钥 SPKI 的 SHA-256 十六进制。安全码为双方指纹小写字典序 `|` 拼接后再 SHA-256，对 10^60 取模并补零到 60 位。备份为 PBKDF2-SHA256 100000 次 + AES-256-GCM。私钥只进 `flutter_secure_storage`。公钥 `PUT /api/v1/e2ee/keys`，查询 `GET /api/v1/e2ee/keys/{userId}`，开关 `PUT /api/v1/conversations/{conversationId}/e2ee`，推送 `E2EE_STATE_CHANGED`。解不开时显示 `🔒 [加密消息 · 当前设备无密钥，请导入密钥恢复]`。
- **S15**：`MESSAGE_ACK` 按 `client_message_id` 合并，`delivery_state` 只升不降。本地落库后发 `RECEIPT`（`receipt_type: delivered`）。已读只上报会话游标 `READ_RECEIPT.read_seq`，打开会话不会把全部历史标已读。
- **S16**：引用只提交 `reply_to_message_id`。撤回窗与编辑窗默认 120 秒；群主/管理员可撤回他人消息；自己撤回的文本可重新编辑。`MESSAGE_RECALLED` 兼容顶层字段。编辑走 `EDIT_MESSAGE_REQUEST`，成功帧是 `MESSAGE_EDITED`，气泡显示「已编辑」；加密消息编辑时重新加密。引用目标撤回后 `reply_to.state = recalled` 且 `preview_text = null`；编辑后 state 变为 `edited` 但保留快照。转发 `POST /api/v1/messages/forward`。@ 使用 `at_users`，`all` 表示 @所有人。会话列表显示 `[有人@我]` 与 `[@所有人]`。搜索 `GET /api/v1/messages/search`。
- **S17**：图片/文件/语音先 `POST /api/v1/messages/upload`（`type=image|file|voice`），再以 `file_id` 发送。表情收藏上限 100，`GET/POST/DELETE /api/v1/emojis`。草稿 `PUT/GET/DELETE /api/v1/conversations/{id}/draft`，停止输入约 500ms 保存，列表用红色 `[草稿]`。群公告确认 `POST /api/v1/groups/{groupId}/announcements/{announcementId}/read`，实时帧 `GROUP_ANNOUNCEMENT` 与 `GROUP_ANNOUNCEMENT_READ`。

## start 前检查（Review Gate）

- [x] prd.md / design.md / implement.md 已按冻结协议回填
- [x] S1-S8 已在 `OnlineIM-Flutter/` 落地
- [x] S9-S17 已按 `docs/ws-protocol-registry.md` 实现，未再保留“待冻结”占位
- [x] 父任务 [[v1-tauri-flutter-clients]] 的安卓交付物已可独立验收；桌面端不在本分支修改 `OnlineIM-Vue/`

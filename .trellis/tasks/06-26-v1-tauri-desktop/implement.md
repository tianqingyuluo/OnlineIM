# v1 - Tauri 桌面端 执行计划

> 有序检查清单。**第一段为不依赖协议的可先行步骤**；第二段为依赖协议冻结的待启动步骤（开放项），待对应任务冻结后补齐执行细节。

## 可先行步骤（不依赖协议冻结）

- [x] **S1 Tauri 工程脚手架**
  - 在 `OnlineIM-Vue/` 内新增 `src-tauri/`（Cargo.toml、tauri.conf.json、main.rs、icons）
  - 注册插件：single-instance、notification、tray-icon
  - `beforeBuildCommand: npm run build`，devPath/distPath 指向 Vite 产物
  - 验证：`npx tauri dev` 能启动窗口并加载 Vue 页面
  - 回滚点：移除 `src-tauri/`，Vue 工程不受影响

- [x] **S2 配置可配置化**
  - `tauri.conf.json` 增后端地址配置字段（API_BASE_URL / WS_BASE_URL）
  - `shared/config.ts` 增 Tauri 环境检测：`window.__TAURI__` 存在则读注入值，否则回退 Web 端原值
  - 验证：Tauri 内请求打到配置的后端；Web 端 `npm run dev` 行为不变（回退原值）
  - 风险文件：`shared/config.ts`（改动需保证 Web 回退路径）

- [x] **S3 单实例锁**
  - `tauri-plugin-single-instance` 注册回调：唤起已有窗口（show + set_focus），退出新实例
  - 验证：启动二实例时不新开窗口，已有窗口前置

- [x] **S4 系统通知**
  - Rust 侧 `notification.rs` 暴露 `notify(title, body, conversation_id)` invoke 命令
  - Vue 侧封装 `notifyService`：Tauri 内走 invoke + 点击跳转，Web 内走 vue-sonner
  - 消息到来时按窗口聚焦态分流（失焦/最小化→系统通知，聚焦→toast）
  - 验证：失焦态收消息触发系统通知；点击通知唤起窗口跳会话

- [x] **S5 系统托盘 + 未读红点**
  - `tray.rs`：托盘图标 + 右键菜单（显示/退出）
  - 拦截窗口 `close-requested`：隐藏窗口 + 托盘而非退出
  - 未读数：Vue list store 通过 IPC event 推送到 Rust，更新托盘图标 overlay/红点图标
  - 验证：关闭窗口→托盘可见；收到消息→托盘红点；托盘"显示"→窗口恢复；托盘"退出"→应用退出

- [x] **S6 webview 内基础链路验证**
  - 在 Tauri webview 内验证：IndexedDB(idb) 读写、localStorage、axios HTTP、原生 WebSocket 全部正常
  - Win(WebView2) + Linux(WebKitGTK) 各至少一机
  - 验证：登录→收发消息→本地缓存→重启免登录 全链路跑通
  - 回滚点：若 webview 某能力异常，回退 S2 配置或加 polyfill

- [x] **S7 构建打包**
  - `tauri.conf.json` 配 bundle targets：Win `msi`/`nsis`，Linux `deb` + `AppImage`
  - Win 安装包处理 WebView2（bootstrapper 捆绑或引导）
  - Linux deb 声明 WebKitGTK 依赖
  - CI 矩阵：windows-latest + ubuntu-latest 各跑 `npx tauri build`
  - 验证：产出安装包可在干净环境安装启动

## 验证命令

```bash
# 开发
cd OnlineIM-Vue && npx tauri dev

# 构建
cd OnlineIM-Vue && npx tauri build

# Vue 工程自身回归（确保 Tauri 集成未破坏 Web 端）
cd OnlineIM-Vue && npm run build
```

## 协议步骤（已按冻结协议落地，桌面端复用 Vue 实现）

桌面端不另写一套协议客户端。Tauri WebView 跑同一份 `OnlineIM-Vue`，下列行为与 `docs/ws-protocol-registry.md` 一致。

- [x] **S8 WS 心跳/重连**（[[v1-ws-heartbeat-reconnect]]）
  - 客户端每 30s 发送 `HEARTBEAT`，载荷 `{}`。
  - 收到 `HEARTBEAT_ACK` 清零未应答计数。连续 2 个周期（约 60s）没有 ACK 则主动断开并重连。
  - 服务端超过 `websocket.heartbeat.timeout`（默认 90s）无入站帧则清理连接。
  - 断线用指数退避重连；重连成功后补发队列中的消息和回执，并再发一次 `SYNC_REQUEST`。
  - 关闭码 `4000` 表示同一设备的新页面接过了连接，停在离线，不再互抢。

- [x] **S9 多端登录**（[[v1-multi-device-login]]）
  - Tauri 登录上报 `device_type: "desktop"`（后端把 `tauri` 也归一成 `desktop`）。
  - 默认设备名：`Tauri Desktop (Linux)` / `Tauri Desktop (Windows)`。`onlineim.device_name` 有值时用用户指定的名称。
  - 同一账号的 `web` / `desktop` / `phone` 可以同时在线；同类型再登录会挤掉旧会话。
  - 旧连接先收到 `SESSION_KICKED`（`SAME_DEVICE_TYPE_LOGIN` / `SESSION_REPLACED` / `REMOTE_LOGOUT`），随后关闭码 `4001`。客户端停止重连、清本地登录态，不再发 `SYNC_REQUEST`。
  - 设备列表仍走 `GET /api/v1/devices` 与 `POST /api/v1/devices/{deviceId}/logout`。

- [x] **S10 离线未读补齐**（[[v1-offline-push]]）
  - 每次 WebSocket 连接成功（含首次和重连）立即发送 `SYNC_REQUEST`。
  - `conversations[].last_seq` 取本地历史、当前消息列表和上次同步游标的较大值；都没有时用会话列表的 `latest_seq`。
  - `OFFLINE_SYNC` 只回给发出请求的那条连接。未读数以帧里的 `conversations` 为准，不把本帧消息再逐条加未读。
  - `truncated: true` 时，正在查看的会话再拉 `GET /api/v1/messages/sync/{conversationId}`。

- [x] **S11 presence**（[[v1-user-presence]]）
  - 没有单独的订阅帧。连接建立后用 `GET /api/v1/presence` 拉当前可见用户，之后吃 `PRESENCE_UPDATE`。
  - 只要该用户还有任意一条连接就显示在线；最后一条断开或心跳超时后才离线，并带 `last_seen_at`。
  - 自己的在线状态以本机 WebSocket 为准。隐藏在线状态走 `GET` / `PUT /api/v1/presence/settings`。

- [x] **S12 WebRTC**（[[v1-webrtc-video-call]]）
  - 信令帧与 Web 端相同：`CALL_INVITE` / `CALL_OFFER` / `CALL_RINGING` / `CALL_ANSWER` / `CALL_CANDIDATE` / `CALL_REJECT` / `CALL_CANCEL` / `CALL_BUSY` / `CALL_END` / `CALL_HANGUP` / `CALL_HANDLED_ELSEWHERE`。
  - ICE 来自 `GET /api/v1/calls/ice-servers`。媒体仍走 WebView 里的 `RTCPeerConnection`，不做独立通话窗口。
  - 来电 `CALL_INVITE`：窗口聚焦时用 vue-sonner；窗口隐藏或失焦时调用 `send_native_notification`，点击通知后聚焦窗口并打开 `conversation_id`。
  - 响铃超时默认 45 秒。终态通话记录仍是 `message_type: "call"` 的普通私聊消息。

- [x] **S13 E2EE**（[[v1-e2ee]]）
  - 算法、信封、指纹、安全码、备份 JSON 与 Web 端相同。私钥不上服务器。
  - 桌面端同时写入 IndexedDB（`onlineim-e2ee` / `identity` / `current`）和应用数据目录 `e2ee/{user_id}.json`。
  - Unix 上密钥目录 `0700`、密钥文件 `0600`。WebView 重置后 IndexedDB 为空时，从本机文件恢复再写回 IndexedDB。
  - 命令：`save_e2ee_key_bundle`、`load_e2ee_key_bundle`、`delete_e2ee_key_bundle`。换机仍使用口令备份。

- [x] **S14 ACK / 已读**（[[v1-message-ack-read]]）
  - 发送成功后等 `MESSAGE_ACK`，按 `client_message_id` 合并，状态只升不降。
  - 消息写入 IndexedDB 后发送 `RECEIPT`（`receipt_type: delivered`）。
  - 用户看到消息后发送 `READ_RECEIPT`。自己另一台设备的已读只清本机未读，不把自己发出的消息标成对方已读。
  - 未读总数变化后调用 `set_unread_badge`，托盘提示改为 `OnlineIM (N 条未读)` 并切换红点图标。

## 本环境验证

- `cargo test`：13 项通过（配置、未读红点、通知辅助、E2EE 文件权限）。
- `cargo check`：通过。
- `npx vue-tsc --noEmit -p tsconfig.app.json`：0 错误。
- `npm run test:unit`：57 个文件、291 项通过。
- `npm run build`：通过。
- 无图形会话，未在本机启动 `tauri dev` 窗口，也未在干净的 Windows / Linux 桌面上安装 msi/deb。安装包由 `.github/workflows/tauri-desktop.yml` 在 `windows-latest` 与 `ubuntu-latest` 上构建。

## start 前检查（Review Gate）

- [x] prd.md / design.md / implement.md 已就绪并经用户 review
- [x] S1-S7 步骤清晰可执行
- [x] 开放项（S8-S14）已按冻结协议回填
- [x] 父任务 [[v1-tauri-flutter-clients]] 集成验收口径已知悉

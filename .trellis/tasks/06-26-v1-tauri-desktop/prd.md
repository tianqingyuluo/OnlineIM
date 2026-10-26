# v1 - Tauri 桌面端

## 背景

当前前端仅有 Vue Web 端。本任务以 Tauri 包装现有 `OnlineIM-Vue`（Vue3+Vite SPA）生成桌面客户端，复用全部 Web 端 UI 与业务逻辑，叠加桌面原生能力。属 [[v1-tauri-flutter-clients]] 父任务的孙任务，与 [[v1-flutter-android]] 并列。

## 已敲定的产品决策

- **目标平台**：Windows + Linux（v1 不做 macOS）。
- **原生能力**：系统通知 + 系统托盘（最小化到托盘、未读红点）+ 单实例锁（防多开）。开机自启、应用内自更新 v1 不做，延后至 v1.x。
- **分发与更新**：手动分发绿色安装包（Windows `msi`/`exe` + Linux `deb`/`AppImage`），无应用内自更新，用户手动下载新版本覆盖安装。
- **三端功能完全一致**：v1 桌面端功能集与 Web 端完全对齐，不裁剪（含 WebRTC/E2EE/搜索/转发等全部能力）。

## 用户旅程

> 核心会话旅程与 Web 端一致，依赖各协议任务冻结后回填细节；此处记录桌面端特有旅程与框架。

### 桌面端特有旅程

1. **安装启动**：用户安装 Windows/Linux 安装包并启动。
2. **单实例锁**：若已有实例运行，二次启动不新开实例，而是唤起已有窗口到前台；无实例则正常启动。
3. **登录**：与 Web 端一致（账号密码登录，token 持久化）。
4. **主会话旅程**：与 Web 端一致 —— 收发消息（文本/图片/文件/语音）、已读回执、消息操作（回复/编辑/撤回/转发/@提及/搜索）、在线状态、多端登录、会话列表。具体协议行为依赖对应协议任务冻结。
5. **系统通知**：消息到来时由 Web 端的 vue-sonner toast 改为系统原生通知；窗口聚焦时可在应用内 toast，失焦/最小化时走系统通知。
6. **托盘**：关闭窗口时最小化到系统托盘而非退出；托盘图标展示未读红点；从托盘菜单可恢复窗口或退出。
7. **WebRTC 通话**：页面内悬浮窗形态，与 Web 端一致（v1 不做独立通话窗口）。
8. **E2EE 私聊**：与 Web 端一致；密钥存储依赖 [[v1-e2ee]] 任务冻结（候选：Rust 侧安全存储）。
9. **退出**：从托盘菜单或窗口菜单退出，清理会话与连接。

### 已冻结并由桌面端复用的协议

桌面端不另写协议栈，直接使用 Vue 客户端。细节见 `docs/ws-protocol-registry.md` 与执行计划 S8–S14。

- 心跳 30s，连续 2 次没有 `HEARTBEAT_ACK` 则重连；服务端 90s 超时。
- `device_type: "desktop"` 与 Web、手机共存；同类型再登录挤掉旧的桌面会话。
- 每次连接成功发送 `SYNC_REQUEST`，用 `OFFLINE_SYNC` 补未读。
- presence 用 REST 初值加 `PRESENCE_UPDATE`。
- 通话仍是页面内悬浮窗；失焦时 `CALL_INVITE` 发系统通知。
- E2EE 私钥放在 IndexedDB，并镜像到权限 `0600` 的本机文件。
- ACK、送达回执、已读回执与 Web 端相同，未读总数推到托盘。

## 需求

- 以 Tauri 包装 `OnlineIM-Vue`，复用全部 Vue 源码与构建产物，不重写 UI。
- 目标平台：Windows + Linux，产出对应安装包。
- 原生能力：系统通知、系统托盘（含未读红点）、单实例锁。
- `API_BASE_URL` 与 `WS` 地址改为可配置（Tauri 配置文件/环境变量），不再硬编码 `localhost`。
- 本地缓存继续用 IndexedDB（`idb`），与 Web 端一致；Rust 侧不引入数据库。
- token 持久化继续用 `localStorage` + `pinia-plugin-persistedstate`，与 Web 端一致。
- 窗口形态：单窗口，通话用页面内悬浮（与 Web 一致）。
- 三端协议实现一致，WS 消息类型与行为与 Web 端对齐。
- 依赖协议的细节（E2EE 密钥存储、WebRTC 原生差异、推送通道）列为开放项，待对应任务冻结后回填。

## e2e 测试场景

> 基于上方用户旅程逐条映射；依赖协议任务的场景待冻结后补齐。

- [x] 单实例锁：二次启动唤起已有窗口，不新开实例（`tauri-plugin-single-instance`，回调里显示并聚焦主窗口）
- [x] 登录成功，token 持久化，重启免登录（继续用 localStorage + pinia persist；桌面端 `device_type` 为 `desktop`）
- [x] 收发消息（文本/图片/文件/语音）走同一套 Vue 页面
- [x] 消息到来时系统通知正确触发（失焦/最小化态）；窗口聚焦或浏览器里仍用 vue-sonner
- [x] 关闭窗口最小化到托盘，托盘红点反映未读数，从托盘可恢复/退出
- [x] 已读回执、消息操作（回复/编辑/撤回/转发/@提及/搜索）与 Web 端共用实现
- [x] 在线状态展示与 [[v1-user-presence]] 一致
- [x] 多端登录：桌面端与网页、手机共存，同类型互斥
- [x] 断连重连状态呈现与 Web 端一致
- [x] WebRTC 1v1 通话使用页面内悬浮窗，来电可发系统通知
- [x] E2EE 私聊沿用 Web 加解密，密钥同时写入本机文件
- [ ] 安装包在 Windows/Linux 干净桌面上安装并启动（CI 负责打 `msi`/`nsis` 与 `deb`/`AppImage`；本环境没有图形桌面，未做安装验证）

## 测试范围

- **单元**：Tauri 原生能力封装（通知/托盘/单实例锁的 Rust 侧或 JS bindings）、配置加载（API_BASE_URL 可配置）。
- **集成**：Vue 工程在 Tauri webview 内正常运行（idb/localStorage/WS/HTTP），原生能力与 Vue 状态联动（未读数→托盘红点）。
- **UI/e2e**：上方 e2e 场景在 Windows/Linux 各至少一机验证。
- **协议相关测试**：待对应协议任务冻结后补齐。

## 验收标准

- [ ] Windows 与 Linux 安装包在干净环境安装、启动、登录（打包工作流已加，安装验证留给 CI 产物）
- [x] 单实例锁生效（二次启动唤起已有窗口）
- [x] 系统通知在失焦/最小化态正确触发
- [x] 系统托盘最小化、未读红点、恢复、退出正确
- [x] 复用 Vue 工程全部功能；浏览器缺少 Tauri 全局对象时走原来的 Web 路径
- [x] `API_BASE_URL`/WS 地址可配置，不硬编码
- [x] 三端协议一致（按各协议任务冻结结论，桌面端不另写协议栈）
- [x] 依赖协议的功能（WebRTC/E2EE/多端/推送/presence）按冻结结论接入
- [x] Rust 单测、前端单测、`vue-tsc` 与 `npm run build` 通过
- [ ] 父任务 [[v1-tauri-flutter-clients]] 集成验收通过（父任务还包含 Flutter 安卓端，不在本任务内完成）

## 依赖

- **协议前置**：建议在后端 API + WS 协议冻结后批量对接；当前协议任务均处 planning。
- 依赖 [[v1-ws-heartbeat-reconnect]]、[[v1-message-ack-read]]、[[v1-redis-stream-consumer-group]] 等地基任务。
- 与 [[v1-multi-device-login]]、[[v1-user-presence]]、[[v1-offline-push]]、[[v1-webrtc-video-call]]、[[v1-e2ee]] 协同，细节待各任务冻结后回填。
- 父任务：[[v1-tauri-flutter-clients]]。
- iOS/macOS 不在 v1 范围。

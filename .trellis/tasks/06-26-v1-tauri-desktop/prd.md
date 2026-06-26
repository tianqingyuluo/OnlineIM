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

### 依赖协议任务冻结的开放项

以下旅程细节待对应协议任务冻结后回填，当前不臆造：
- 心跳/断连重连的具体周期与状态呈现（[[v1-ws-heartbeat-reconnect]]）
- 多端登录策略：桌面端与 Web/安卓端的共存/互斥口径（[[v1-multi-device-login]]）
- 离线推送兜底：上线未读补齐行为（[[v1-offline-push]]）
- presence 在线态粒度与展示（[[v1-user-presence]]）
- WebRTC 信令流程与异常分支（[[v1-webrtc-video-call]]）
- E2EE 密钥在三端的存储（[[v1-e2ee]]）
- ACK/已读回执协议字段（[[v1-message-ack-read]]）

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

- [ ] 安装包在 Windows/Linux 可正常安装并启动
- [ ] 单实例锁：二次启动唤起已有窗口，不新开实例
- [ ] 登录成功，token 持久化，重启免登录
- [ ] 收发消息（文本/图片/文件/语音）端到端可用
- [ ] 消息到来时系统通知正确触发（失焦/最小化态）
- [ ] 关闭窗口最小化到托盘，托盘红点反映未读数，从托盘可恢复/退出
- [ ] 已读回执、消息操作（回复/编辑/撤回/转发/@提及/搜索）可用（待协议冻结细化）
- [ ] 在线状态展示正确（待 [[v1-user-presence]] 冻结）
- [ ] 多端登录共存/互斥按结论生效（待 [[v1-multi-device-login]] 冻结）
- [ ] 断连重连状态呈现正确（待 [[v1-ws-heartbeat-reconnect]] 冻结）
- [ ] WebRTC 1v1 通话可用（待 [[v1-webrtc-video-call]] 冻结）
- [ ] E2EE 私聊可开启加解密（待 [[v1-e2ee]] 冻结）

## 测试范围

- **单元**：Tauri 原生能力封装（通知/托盘/单实例锁的 Rust 侧或 JS bindings）、配置加载（API_BASE_URL 可配置）。
- **集成**：Vue 工程在 Tauri webview 内正常运行（idb/localStorage/WS/HTTP），原生能力与 Vue 状态联动（未读数→托盘红点）。
- **UI/e2e**：上方 e2e 场景在 Windows/Linux 各至少一机验证。
- **协议相关测试**：待对应协议任务冻结后补齐。

## 验收标准

- [ ] Windows 与 Linux 安装包可正常安装、启动、登录
- [ ] 单实例锁生效（二次启动唤起已有窗口）
- [ ] 系统通知在失焦/最小化态正确触发
- [ ] 系统托盘最小化、未读红点、恢复、退出正确
- [ ] 复用 Vue 工程全部功能，与 Web 端行为一致
- [ ] `API_BASE_URL`/WS 地址可配置，不硬编码
- [ ] 三端协议一致（按各协议任务冻结结论）
- [ ] 依赖协议的功能（WebRTC/E2EE/多端/推送/presence）按结论生效
- [ ] 单元/集成/UI 测试全绿
- [ ] 父任务 [[v1-tauri-flutter-clients]] 集成验收通过

## 依赖

- **协议前置**：建议在后端 API + WS 协议冻结后批量对接；当前协议任务均处 planning。
- 依赖 [[v1-ws-heartbeat-reconnect]]、[[v1-message-ack-read]]、[[v1-redis-stream-consumer-group]] 等地基任务。
- 与 [[v1-multi-device-login]]、[[v1-user-presence]]、[[v1-offline-push]]、[[v1-webrtc-video-call]]、[[v1-e2ee]] 协同，细节待各任务冻结后回填。
- 父任务：[[v1-tauri-flutter-clients]]。
- iOS/macOS 不在 v1 范围。

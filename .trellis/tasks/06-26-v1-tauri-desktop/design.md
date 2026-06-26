# v1 - Tauri 桌面端 技术设计

> 本设计只覆盖**不依赖协议冻结**的部分；WS 重连策略、多端登录口径、WebRTC 信令、E2EE 密钥存储等依赖协议任务的细节标注为开放项，待对应任务冻结后回填。

## 架构与边界

```
┌─────────────────────────────────────────────┐
│  Tauri 壳 (src-tauri/, Rust)                 │
│  ├─ 单实例锁 (tauri-plugin-single-instance)  │
│  ├─ 系统通知 (tauri notification API)        │
│  ├─ 系统托盘 (tray-icon + overlay 红点)      │
│  └─ 配置注入 (API_BASE_URL / WS 地址)        │
├─────────────────────────────────────────────┤
│  WebView (Win: WebView2 / Linux: WebKitGTK)  │
│  └─ OnlineIM-Vue SPA (复用全部源码)           │
│     ├─ HTTP (axios) + WS (原生 WebSocket)    │
│     ├─ 本地缓存 (idb / IndexedDB)            │
│     └─ token (localStorage + pinia-persist)  │
└─────────────────────────────────────────────┘
```

**边界原则**：Rust 侧只做原生能力与配置注入，不碰业务逻辑；所有业务逻辑留在 Vue SPA 内，与 Web 端共用源码。Rust ↔ Vue 通过 Tauri IPC（invoke / event）交互。

## 工程结构

在 `OnlineIM-Vue/` 内新增 `src-tauri/` 目录，不新建独立工程：

```
OnlineIM-Vue/
├── src-tauri/
│   ├── Cargo.toml
│   ├── tauri.conf.json        # Tauri 配置：窗口、bundle、插件
│   ├── build.rs
│   ├── icons/                 # 应用图标 + 托盘图标
│   └── src/
│       ├── main.rs            # Tauri 入口、插件注册、单实例锁
│       ├── tray.rs            # 托盘 + 菜单 + 红点 overlay
│       ├── notification.rs    # 系统通知桥接
│       └── config.rs          # 从 tauri.conf.json/env 读取后端地址注入前端
├── src/                       # Vue 源码（复用，不改业务）
├── shared/config.ts           # 改为读取 Tauri 注入值，Web 端回退原硬编码
└── vite.config.ts             # 增加 Tauri 构建适配
```

Tauri 构建时 `beforeBuildCommand` 调 `npm run build` 产出 Vue dist，Tauri 打包 dist 为 webview 资源。

## 配置可配置化

**现状**：`shared/config.ts` 硬编码 `API_BASE_URL`（Web 开发用 proxy-server.js 8000→后端 8080）。

**改造**：
- `tauri.conf.json` 增 `app.env` 或独立配置字段存 `API_BASE_URL` / `WS_BASE_URL`（默认指向生产后端）。
- Vue 侧 `shared/config.ts` 增加 Tauri 运行时检测：若在 Tauri webview 内（`window.__TAURI__` 存在），从 Tauri 注入值读取；否则回退 Web 端原值（保持 Web 端开发不受影响）。
- 打包时通过构建参数/配置文件覆盖后端地址，无需改源码。

## 原生能力实现

### 单实例锁
- `tauri-plugin-single-instance`：二次启动时触发回调，Rust 侧唤起已有窗口（show + set_focus），退出新实例。
- 与托盘配合：最小化到托盘时窗口隐藏，二次启动从托盘恢复。

### 系统通知
- 消息到来时：窗口失焦或最小化 → 走 Tauri notification API（系统通知）；窗口聚焦 → 保留应用内 vue-sonner toast。
- Vue 侧封装 `notifyService`，运行时判断环境分流：Tauri 内调用 `invoke('notify', {...})`，Web 内调用 vue-sonner。
- 通知点击 → 唤起窗口到前台并跳转对应会话。

### 系统托盘
- `tray-icon`：托盘图标 + 右键菜单（显示主窗口 / 退出）。
- 未读红点：托盘图标 overlay 或切换带红点图标；未读数由 Vue 侧 list store 维护，通过 IPC event 推送到 Rust 侧更新托盘。
- 关闭窗口行为：拦截 `close-requested` 事件，改为隐藏窗口 + 托盘（不退出）；退出仅从托盘菜单或应用内退出按钮触发。

## 本地缓存与 token

- **本地缓存**：继续用 `idb`（IndexedDB）。Tauri webview（WebView2 / WebKitGTK）支持 IndexedDB，与 Web 端零迁移。Rust 侧不引入数据库；托盘未读数通过 IPC 从 Vue 侧读取。
- **token 持久化**：继续用 `localStorage` + `pinia-plugin-persistedstate`，与 Web 端一致。
- **E2EE 密钥存储（开放项）**：依赖 [[v1-e2ee]] 冻结。候选方案：Rust 侧安全存储（Windows Credential Manager / Linux secret service），通过 IPC 供前端调用。v1 桌面端若 E2EE 未冻结则先不实现密钥存储。

## 构建与分发

- **Windows**：`msi`（或 `nsis`）安装包，需 WebView2（Win11 内置；Win10 引导安装或捆绑 bootstrapper）。
- **Linux**：`deb` + `AppImage`，依赖 WebKitGTK（打包时声明依赖）。
- **CI 矩阵**：GitHub Actions（或本地）在 windows-latest + ubuntu-latest 各跑一次 `tauri build`。
- **签名**：v1 暂不做代码签名（手动分发，用户可能遇 SmartScreen 警告）；v1.x 补 Windows 代码签名。
- **无应用内自更新**：v1 不集成 Tauri updater。

## 兼容性与风险

| 风险 | 缓解 |
|------|------|
| Win10 无 WebView2 | 安装包捆绑 WebView2 bootstrapper 或引导下载 |
| Linux WebKitGTK 版本差异 | deb 声明依赖最低版本；AppImage 打包所需库 |
| webview 内 idb/localStorage 行为差异 | 构建后在 Win/Linux 各真机验证基础存储链路 |
| 关闭窗口 vs 退出语义混淆 | 明确：关闭=最小化到托盘，退出=托盘菜单/应用内按钮 |
| 未签名 SmartScreen 警告 | v1 文档说明；v1.x 补签名 |

## 协议对接（开放项，待冻结）

以下设计待对应协议任务冻结后回填，当前不臆造：
- WS 心跳/重连：客户端重连器周期/退避/状态呈现（[[v1-ws-heartbeat-reconnect]]）
- 多端登录：被挤下线通知处理、设备管理 UI（[[v1-multi-device-login]]）
- 离线推送：上线未读补齐的客户端处理（[[v1-offline-push]]）
- presence：在线态订阅与展示（[[v1-user-presence]]）
- WebRTC：信令消息处理、通话 UI、悬浮窗（[[v1-webrtc-video-call]]）
- E2EE：密钥存储、加解密流程（[[v1-e2ee]]）
- ACK/已读：协议字段与客户端上报（[[v1-message-ack-read]]）

## 迁移 / 回滚

- Tauri 集成在 `OnlineIM-Vue` 内新增 `src-tauri/`，不改动 Vue 业务源码（仅 `shared/config.ts` 增加环境检测分支）。回滚即移除 `src-tauri/` 并还原 config.ts，Web 端不受影响。
- 配置可配置化改造保持 Web 端回退路径，Web 端开发行为不变。

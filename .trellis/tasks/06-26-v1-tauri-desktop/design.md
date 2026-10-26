# v1 - Tauri 桌面端 技术设计

> 桌面端复用 `OnlineIM-Vue`。协议行为以 `docs/ws-protocol-registry.md` 为准；本文件记录 Tauri 壳和它与 Vue 的边界。S8–S14 已按冻结协议回填。

## 架构与边界

```
┌─────────────────────────────────────────────┐
│  Tauri 壳 (src-tauri/, Rust)                 │
│  ├─ 单实例锁 (tauri-plugin-single-instance)  │
│  ├─ 系统通知 (tauri notification API)        │
│  ├─ 系统托盘 (tray-icon + 未读红点图标)      │
│  ├─ 配置注入 (server-config.json / 环境变量) │
│  └─ E2EE 密钥文件 (0600，与 IndexedDB 双写)  │
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
│       ├── config.rs          # 环境变量 + server-config.json
│       ├── e2ee_store.rs      # 本机密钥文件
│       └── state.rs           # 退出 / 静音 / 未读状态
├── src/                       # Vue 源码（复用，不改业务）
├── shared/config.ts           # 改为读取 Tauri 注入值，Web 端回退原硬编码
└── vite.config.ts             # 增加 Tauri 构建适配
```

Tauri 构建时 `beforeBuildCommand` 调 `npm run build` 产出 Vue dist，Tauri 打包 dist 为 webview 资源。

## 配置可配置化

**现状**：`shared/config.ts` 硬编码 `API_BASE_URL`（Web 开发用 proxy-server.js 8000→后端 8080）。

**改造**：
- 默认值仍是 `http://localhost:8080/api/v1` 与 `ws://localhost:8081/api/v1/chat`。
- 读取顺序：内置默认值 → `ONLINEIM_API_BASE_URL` / `ONLINEIM_WS_BASE_URL` → 应用配置目录里的 `server-config.json`。用户在设置页保存后，文件覆盖环境变量。
- 命令：`get_server_config`、`set_server_config(api_base_url, ws_base_url)`。
- 浏览器没有 `window.__TAURI_INTERNALS__` / `window.__TAURI__` 时不读覆盖值，Web 端行为不变。
- 登录页在 Tauri 里显示齿轮，打开服务器地址对话框。保存后 axios 拦截器改用新的 API 地址，下一次 `connect()` 使用新的 WebSocket 地址。

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
- **E2EE 密钥存储**：IndexedDB 继续保存身份密钥。Tauri 额外把同一份 JSON 写到应用数据目录 `e2ee/{user_id}.json`，Unix 权限 `0600`（目录 `0700`）。WebView 被清空时从该文件恢复。换机使用既有口令备份，不把私钥上传到服务器。

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

## 协议对接

桌面端直接跑 Vue 客户端，不复制协议实现。冻结后的口径：

- 心跳：30s `HEARTBEAT`，连续 2 次没有 `HEARTBEAT_ACK` 视为断线；服务端 90s 无入站帧清理连接。
- 多端：Tauri 上报 `device_type: "desktop"`。`web` / `desktop` / `phone` 可同时在线。`SESSION_KICKED` 或关闭码 `4001` 停止重连并清登录态。
- 上线补齐：每次连接成功发送 `SYNC_REQUEST`，未读以 `OFFLINE_SYNC.conversations` 为准。
- presence：连接后 REST 拉初始状态，之后处理 `PRESENCE_UPDATE`。
- WebRTC：页面内悬浮窗。失焦或隐藏时，`CALL_INVITE` 走系统通知。
- E2EE：同一信封和备份格式。桌面端再加本机 `0600` 密钥文件。
- ACK / 已读：`MESSAGE_ACK`、`RECEIPT`、`READ_RECEIPT` 与 Web 端相同。未读总数同步到托盘。

## 迁移 / 回滚

- Tauri 集成在 `OnlineIM-Vue` 内新增 `src-tauri/`，不改动 Vue 业务源码（仅 `shared/config.ts` 增加环境检测分支）。回滚即移除 `src-tauri/` 并还原 config.ts，Web 端不受影响。
- 配置可配置化改造保持 Web 端回退路径，Web 端开发行为不变。

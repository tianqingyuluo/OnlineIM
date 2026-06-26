# v1 - Flutter 安卓客户端 技术设计

> 本设计只覆盖**不依赖协议冻结**的部分；WS 重连策略、多端登录口径、WebRTC 信令、E2EE 密钥存储、推送通道等依赖协议任务的细节标注为开放项，待对应任务冻结后回填。

## 架构与边界

```
┌──────────────────────────────────────────────┐
│  Flutter App (lib/, 独立工程)                  │
│  ├─ UI 层 (pages/widgets)                     │
│  ├─ 状态层 (Riverpod providers)               │
│  ├─ 协议层 (HTTP dio + WS web_socket_channel) │
│  ├─ 本地缓存 (drift / SQLite)                 │
│  ├─ token (flutter_secure_storage / Keystore) │
│  └─ 原生桥接 (platform channels)              │
│     ├─ 前台服务保活 (flutter_foreground_task) │
│     ├─ 本地通知 (flutter_local_notifications) │
│     └─ 权限 (permission_handler)              │
├──────────────────────────────────────────────┤
│  Android 原生                                 │
│  ├─ Foreground Service (WS 保活 + 持久通知)    │
│  ├─ Keystore (token / E2EE 密钥)              │
│  └─ 权限 (通知/麦克风/摄像头/存储)             │
└──────────────────────────────────────────────┘
        │ HTTP(8080) + WS(8081) 复用后端协议
        ▼
   onlineIM-server (Spring Boot + Vert.x)
```

**边界原则**：Flutter 工程独立，不复用 Vue 源码；仅复用后端 HTTP + WS 协议（消息格式、字段、端点与 Web 端一致）。原生能力通过 platform channel / 插件桥接。

## 工程结构

新建 `OnlineIM-Flutter/` 独立工程（与 `OnlineIM-Vue/`、`onlineIM-server/` 平级）：

```
OnlineIM-Flutter/
├── pubspec.yaml
├── android/
│   ├── app/
│   │   ├── build.gradle.kts     # minSdk 24, targetSdk 最新
│   │   └── src/main/
│   │       ├── AndroidManifest.xml  # 权限 + 前台服务声明
│   │       └── kotlin/.../        # Foreground Service 等
│   └── ...
└── lib/
    ├── main.dart
    ├── app.dart                 # MaterialApp + 路由
    ├── router/                  # go_router 配置
    ├── pages/                   # 页面（登录、会话列表、聊天、设置）
    ├── widgets/                 # 可复用组件
    ├── providers/               # Riverpod providers（状态层）
    ├── services/
    │   ├── api_service.dart     # dio 封装，HTTP service
    │   ├── ws_service.dart      # web_socket_channel，WS service
    │   ├── notify_service.dart  # 本地通知桥接
    │   └── permission_service.dart
    ├── data/
    │   ├── database/            # drift 数据库 + tables + daos
    │   └── secure_storage.dart  # flutter_secure_storage 封装
    ├── models/                  # 数据模型（对应后端 snake_case 字段）
    └── utils/
```

## 技术栈选型

| 层 | 选型 | 理由 |
|----|------|------|
| 状态管理 | Riverpod 2 | 类型安全、编译期检查、测试友好 |
| 路由 | go_router | 声明式、深链接、与 Riverpod 配合好 |
| HTTP | dio | 拦截器（token 刷新）、与 axios 模型对应 |
| WebSocket | web_socket_channel | 官方维护，轻量 |
| 本地缓存 | drift (SQLite) | 类型安全 schema、迁移、响应式查询，适合 IM 复杂数据 |
| token | flutter_secure_storage | Android Keystore，非明文 |
| 本地通知 | flutter_local_notifications | 通知通道、前台服务通知 |
| 前台服务 | flutter_foreground_task | WS 保活 + 持久通知 |
| 权限 | permission_handler | 统一运行时权限申请 |
| 序列化 | freezed + json_serializable | 不可变模型、JSON 编解码 |

**API 字段**：models 用 `snake_case` 字段名匹配后端 JSON（与 Vue 端 type/ 一致，避免转换层）。

## 协议层

- **HTTP**：dio 实例 + 拦截器（注入 `Authorization: Bearer <jwt>`、401 触发 token 刷新或登出）；按资源拆 service（auth/message/group/...），与 Vue 端 service 对应。
- **WS**：`ws://<host>/api/v1/chat?token=Bearer%20<jwt>`；消息格式 `{type, message}`；WS service 为单例，状态暴露给 Riverpod。
- **WS 生命周期（开放项）**：心跳/重连策略依赖 [[v1-ws-heartbeat-reconnect]] 冻结，当前先实现基础连接+认证+收发，重连器留接口。

## 前台服务保活

- `flutter_foreground_task`：应用切后台时启动前台服务，WS 在服务内保活，前台服务带持久通知（API 26+ 要求通知通道）。
- 前台服务通知与消息通知区分通道（保活通知静默持久，消息通知可交互跳转）。
- 国产 ROM 保活限制（开放项）：各厂商系统对前台服务杀进程策略不同，v1 尽力保活；被杀后靠 [[v1-offline-push]] 上线补齐。

## 权限申请流程

| 权限 | 触发时机 | 说明 |
|------|----------|------|
| 通知 | 首次启动登录后 / Android 13+ | 运行时申请，拒绝则不收系统通知（应用内仍可提示） |
| 麦克风 + 摄像头 | 发起/接听 WebRTC 通话时 | 拒绝则通话降级或提示 |
| 存储/相册 | 首次选择文件/图片时 | scoped storage（API 29+），Photo Picker 优先 |
| 前台服务 | 启动前台服务时 | manifest 声明 + 运行时启动 |

## 本地缓存与 token

- **本地缓存（drift/SQLite）**：对应 Web 端 idb 结构 —— 会话表、消息表（按会话分区/分页）、草稿、用户/群信息缓存。drift schema 与 Vue 端 idb store 对齐字段。上线先从本地缓存展示，再 sync API。
- **token（Keystore）**：`flutter_secure_storage` 存 access/refresh token；启动读取，登出清除。
- **E2EE 密钥（开放项）**：依赖 [[v1-e2ee]] 冻结。候选：Keystore 存私钥；v1 若 E2EE 未冻结则先不实现。

## 构建与分发

- **minSdk 24 / targetSdk 最新稳定**：`build.gradle.kts` 配置。
- **签名**：生成 release keystore，`key.properties` 配置签名（keystore 不入库，gitignore）。
- **构建**：`flutter build apk --release` 产出自分发 APK。
- **无应用商店上架**：v1 自分发；v1.x 再考虑国内应用商店。
- **无应用内自更新**：v1 用户手动下载新版覆盖安装。

## 兼容性与风险

| 风险 | 缓解 |
|------|------|
| 国产 ROM 前台服务被杀 | 尽力保活 + 上线补齐兜底；文档说明各家电池优化白名单引导 |
| WebSocket 在移动网络/省电态断连 | 重连器（开放项，待心跳任务冻结）+ 上线补齐 |
| scoped storage 兼容 | 优先 Photo Picker；存储权限降级处理 |
| Keystore 在某些厂商定制 ROM 异常 | flutter_secure_storage 降级 + 文档说明 |
| 自分发 APK "未知来源"提示 | 首次启动文档引导 |
| minSdk 24 边缘设备 | 覆盖 95%+，低版本不支持 |

## 协议对接（开放项，待冻结）

以下设计待对应协议任务冻结后回填，当前不臆造：
- WS 心跳/重连：移动端切后台/网络切换/省电态重连策略（[[v1-ws-heartbeat-reconnect]]）
- 多端登录：被挤下线处理、设备管理、多端消息投递（[[v1-multi-device-login]]）
- 离线推送：上线未读补齐客户端处理（[[v1-offline-push]]）
- presence：在线态订阅与展示（[[v1-user-presence]]）
- WebRTC：信令处理、通话 UI、权限、多端响铃（[[v1-webrtc-video-call]]）
- E2EE：密钥分发、Keystore 存储、加解密流程（[[v1-e2ee]]）
- ACK/已读：协议字段与客户端上报（[[v1-message-ack-read]]）

## 迁移 / 回滚

- 新建独立工程 `OnlineIM-Flutter/`，不影响 Vue/后端。回滚即移除该工程目录。
- 后端协议复用，无后端改动（原生推送通道除外，v1 不做系统推送故无后端推送适配）。

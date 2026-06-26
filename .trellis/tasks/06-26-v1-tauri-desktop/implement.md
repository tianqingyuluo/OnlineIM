# v1 - Tauri 桌面端 执行计划

> 有序检查清单。**第一段为不依赖协议的可先行步骤**；第二段为依赖协议冻结的待启动步骤（开放项），待对应任务冻结后补齐执行细节。

## 可先行步骤（不依赖协议冻结）

- [ ] **S1 Tauri 工程脚手架**
  - 在 `OnlineIM-Vue/` 内新增 `src-tauri/`（Cargo.toml、tauri.conf.json、main.rs、icons）
  - 注册插件：single-instance、notification、tray-icon
  - `beforeBuildCommand: npm run build`，devPath/distPath 指向 Vite 产物
  - 验证：`npx tauri dev` 能启动窗口并加载 Vue 页面
  - 回滚点：移除 `src-tauri/`，Vue 工程不受影响

- [ ] **S2 配置可配置化**
  - `tauri.conf.json` 增后端地址配置字段（API_BASE_URL / WS_BASE_URL）
  - `shared/config.ts` 增 Tauri 环境检测：`window.__TAURI__` 存在则读注入值，否则回退 Web 端原值
  - 验证：Tauri 内请求打到配置的后端；Web 端 `npm run dev` 行为不变（回退原值）
  - 风险文件：`shared/config.ts`（改动需保证 Web 回退路径）

- [ ] **S3 单实例锁**
  - `tauri-plugin-single-instance` 注册回调：唤起已有窗口（show + set_focus），退出新实例
  - 验证：启动二实例时不新开窗口，已有窗口前置

- [ ] **S4 系统通知**
  - Rust 侧 `notification.rs` 暴露 `notify(title, body, conversation_id)` invoke 命令
  - Vue 侧封装 `notifyService`：Tauri 内走 invoke + 点击跳转，Web 内走 vue-sonner
  - 消息到来时按窗口聚焦态分流（失焦/最小化→系统通知，聚焦→toast）
  - 验证：失焦态收消息触发系统通知；点击通知唤起窗口跳会话

- [ ] **S5 系统托盘 + 未读红点**
  - `tray.rs`：托盘图标 + 右键菜单（显示/退出）
  - 拦截窗口 `close-requested`：隐藏窗口 + 托盘而非退出
  - 未读数：Vue list store 通过 IPC event 推送到 Rust，更新托盘图标 overlay/红点图标
  - 验证：关闭窗口→托盘可见；收到消息→托盘红点；托盘"显示"→窗口恢复；托盘"退出"→应用退出

- [ ] **S6 webview 内基础链路验证**
  - 在 Tauri webview 内验证：IndexedDB(idb) 读写、localStorage、axios HTTP、原生 WebSocket 全部正常
  - Win(WebView2) + Linux(WebKitGTK) 各至少一机
  - 验证：登录→收发消息→本地缓存→重启免登录 全链路跑通
  - 回滚点：若 webview 某能力异常，回退 S2 配置或加 polyfill

- [ ] **S7 构建打包**
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

## 待启动步骤（开放项，待协议冻结）

以下步骤依赖对应协议任务冻结后回填执行细节，当前仅登记占位：

- [ ] S8 WS 心跳/重连客户端对接（[[v1-ws-heartbeat-reconnect]]）
- [ ] S9 多端登录：被挤下线通知、设备管理 UI（[[v1-multi-device-login]]）
- [ ] S10 离线推送：上线未读补齐客户端处理（[[v1-offline-push]]）
- [ ] S11 presence 订阅与展示（[[v1-user-presence]]）
- [ ] S12 WebRTC 信令处理、通话 UI、悬浮窗（[[v1-webrtc-video-call]]）
- [ ] S13 E2EE 密钥存储（Rust 安全存储）、加解密流程（[[v1-e2ee]]）
- [ ] S14 ACK/已读回执客户端上报（[[v1-message-ack-read]]）

## start 前检查（Review Gate）

- [ ] prd.md / design.md / implement.md 已就绪并经用户 review
- [ ] S1-S7 步骤清晰可执行
- [ ] 开放项（S8-S14）已登记对应协议任务，未臆造细节
- [ ] 父任务 [[v1-tauri-flutter-clients]] 集成验收口径已知悉

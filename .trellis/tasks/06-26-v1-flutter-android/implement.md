# v1 - Flutter 安卓客户端 执行计划

> 有序检查清单。**第一段为不依赖协议的可先行步骤**；第二段为依赖协议冻结的待启动步骤（开放项），待对应任务冻结后补齐执行细节。

## 可先行步骤（不依赖协议冻结）

- [ ] **S1 工程脚手架 + 依赖**
  - `flutter create OnlineIM-Flutter`，配 `pubspec.yaml`：riverpod、go_router、dio、web_socket_channel、drift、flutter_secure_storage、flutter_local_notifications、flutter_foreground_task、permission_handler、freezed、json_serializable
  - `android/app/build.gradle.kts`：minSdk 24、targetSdk 最新
  - `AndroidManifest.xml`：声明权限（INTERNET、FOREGROUND_SERVICE、POST_NOTIFICATIONS、RECORD_AUDIO、CAMERA、存储/媒体）
  - 验证：`flutter run` 可在 minSdk 24 真机启动空白应用
  - 回滚点：移除 `OnlineIM-Flutter/`，不影响其他工程

- [ ] **S2 协议层基础**
  - `api_service.dart`：dio 实例 + 拦截器（注入 Bearer token、401 处理）
  - `ws_service.dart`：web_socket_channel 连接 `ws://.../api/v1/chat?token=...`，单例，基础连接+认证+收发
  - models：freezed + json_serializable，snake_case 字段对齐后端
  - 验证：可连接后端 WS、收发一条测试消息
  - 开放项：心跳/重连器留接口，待 [[v1-ws-heartbeat-reconnect]] 冻结

- [ ] **S3 本地缓存（drift）**
  - drift schema：会话表、消息表、草稿表、用户/群缓存表（字段对齐 Vue 端 idb store）
  - DAO + 迁移脚手架
  - 上线先读本地展示再 sync API
  - 验证：写入→读取→重启后仍在

- [ ] **S4 token 安全存储**
  - `secure_storage.dart`：flutter_secure_storage 封装 access/refresh token
  - 启动读取、登录写入、登出清除
  - 验证：登录后重启免登录；登出后 token 清除

- [ ] **S5 前台服务保活**
  - `flutter_foreground_task`：切后台启动前台服务，WS 保活
  - 静默持久通知通道（与消息通知通道区分）
  - 验证：切后台 WS 仍在线收消息；前台服务通知展示

- [ ] **S6 通知权限 + 本地通知**
  - permission_handler 申请 POST_NOTIFICATIONS（Android 13+）
  - flutter_local_notifications 消息通知通道；点击跳转会话
  - 验证：授权后收消息展示系统通知；点击跳转

- [ ] **S7 登录-会话核心旅程**
  - 登录页（账号密码）→ 会话列表页 → 聊天页
  - 文本消息收发全链路（HTTP + WS + 本地缓存 + UI）
  - 验证：登录→收发文本→退出登录 全链路真机跑通

- [ ] **S8 构建签名 APK**
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

## 待启动步骤（开放项，待协议冻结）

以下步骤依赖对应协议任务冻结后回填执行细节，当前仅登记占位：

- [ ] S9 WS 心跳/重连器（[[v1-ws-heartbeat-reconnect]]）
- [ ] S10 多端登录：被挤下线、设备管理、多端投递（[[v1-multi-device-login]]）
- [ ] S11 离线推送：上线未读补齐（[[v1-offline-push]]）
- [ ] S12 presence 订阅与展示（[[v1-user-presence]]）
- [ ] S13 WebRTC 信令、通话 UI、权限、多端响铃（[[v1-webrtc-video-call]]）
- [ ] S14 E2EE 密钥 Keystore 存储、加解密（[[v1-e2ee]]）
- [ ] S15 ACK/已读回执上报（[[v1-message-ack-read]]）
- [ ] S16 消息操作：回复/编辑/撤回/转发/@提及/搜索（依赖对应任务冻结）
- [ ] S17 文件/图片/语音消息（依赖 [[v1-file-image-thumbnail]]、[[v1-voice-message]] 冻结）

## start 前检查（Review Gate）

- [ ] prd.md / design.md / implement.md 已就绪并经用户 review
- [ ] S1-S8 步骤清晰可执行
- [ ] 开放项（S9-S17）已登记对应协议任务，未臆造细节
- [ ] 父任务 [[v1-tauri-flutter-clients]] 集成验收口径已知悉

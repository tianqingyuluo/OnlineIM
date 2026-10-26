# OnlineIM Flutter 安卓客户端

独立 Flutter 工程，与 Web（`OnlineIM-Vue`）、Tauri 共用 v1 HTTP + WebSocket 协议。协议以仓库根目录 `docs/ws-protocol-registry.md` 为准，端到端加密与 `OnlineIM-Vue/src/crypto/e2ee.ts` 同一套线格式。

## 默认地址

| 用途 | 地址 |
|------|------|
| HTTP | `http://10.0.2.2:8080/api/v1`（模拟器访问宿主机） |
| WebSocket | `ws://10.0.2.2:8081/api/v1/chat` |

登录页右上角可以改成电脑的局域网地址，并写入 Android Keystore（`flutter_secure_storage`）。连接参数为 `token=Bearer%20<jwt>&device_id=<id>&device_type=phone`。登录体固定带 `device_type: phone` 与 `device_name: Flutter Android`。

## 本地运行

```bash
cd OnlineIM-Flutter
flutter pub get
flutter analyze
flutter test
flutter build apk --release
```

`android/key.properties` 存在时使用其中的 release 签名；没有该文件时 release 构建回退到 debug 签名，因此 `flutter build apk --release` 开箱可用。密钥库不要提交。

`minSdk` 为 24，`targetSdk` 为 34。

## 与登记表对齐的行为

- 心跳帧是 `HEARTBEAT` / `HEARTBEAT_ACK`（入站同时接受 `PING` / `PONG`）。客户端每 25 秒发送一次，连续两个周期没有回执就断开并按 1s、2s、4s、8s、16s、30s 退避重连。`SESSION_KICKED` 与关闭码 `4001` 停止重连并清空登录态。
- 消息帧使用 `PRIVATE_MESSAGE_REQUEST` / `GROUP_MESSAGE_REQUEST` 与对应 RESPONSE，而不是另一套别名。入站别名会归一到登记表名称。
- 增量补齐是 `SYNC_REQUEST` + `OFFLINE_SYNC`，缺口再请求 `GET /api/v1/messages/sync/{conversationId}?seq_id=`。
- 设备列表是 `GET /api/v1/devices`，远程登出是 `POST /api/v1/devices/{deviceId}/logout`。
- E2EE：ECDH P-256 + ECDSA P-256，HKDF info 为 `OnlineIM-E2EE-v1|{counter}|` 拼接临时公钥 SPKI，AES-256-GCM，60 位安全码，PBKDF2-SHA256（100000 次）口令备份。解不开时显示 `🔒 [加密消息 · 当前设备无密钥，请导入密钥恢复]`。

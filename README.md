# OnlineIM v1

OnlineIM 是一套即时通讯系统。v1 在同一套 HTTP 与 WebSocket 协议上提供网页、Windows / Linux 桌面和 Android 客户端。

协议字段以 [docs/ws-protocol-registry.md](docs/ws-protocol-registry.md) 为准。部署与打包见 [docs/v1-deployment-guide.md](docs/v1-deployment-guide.md)，功能清单见 [docs/v1-changelog.md](docs/v1-changelog.md)。

## 架构

```
浏览器 / Tauri WebView / Flutter
        │  HTTP :8080 /api/v1
        │  WebSocket :8081 /api/v1/chat
        ▼
onlineIM-server
  Spring Boot 3.4.4（Java 21）  ── REST、鉴权、业务
  Vert.x 4.5.11                 ── WebSocket
        │
        ├── MySQL        用户、好友、群、公告已读
        ├── MongoDB      消息、会话、表情收藏、上传记录
        ├── Redis        会话、在线状态、跨节点 Stream（im:message:stream）
        └── RustFS / S3  图片、缩略图、文件、语音、表情
```

| 组件 | 目录 | 说明 |
|------|------|------|
| 后端 | `onlineIM-server` | Spring MVC `:8080`，Vert.x WebSocket `:8081` `/api/v1/chat` |
| Web | `OnlineIM-Vue` | Vue 3.5 + Vite 6 + Pinia，开发服务器 `:3000` |
| 桌面 | `OnlineIM-Vue/src-tauri` | Tauri 2，复用 Vue。Windows `msi` / `nsis`，Linux `deb` / `AppImage` |
| Android | `OnlineIM-Flutter` | Flutter，`minSdk` 24，与 Web 共用同一套帧和 REST |

WebSocket 帧一律是 `{ "type": "<TYPE>", "message": { ... } }`。查询串为 `token=Bearer%20<jwt>`。登录按设备类型区分 `web`、`desktop`、`phone`：三种类型可以同时在线，同一类型再次登录会挤下线。

对象存储默认是 RustFS（S3 兼容）。也可以换成 MinIO、Amazon S3、阿里云 OSS 或腾讯云 COS，由 `oss.type` 选择。

## v1 功能

- 私聊与群聊：文本、图片（缩略图）、文件、语音、自定义表情（每人最多 100 个）
- 引用回复、2 分钟内编辑、2 分钟内撤回、逐条 / 合并转发
- 群 @ 成员、群主 / 管理员 @所有人、群公告已读名单
- 送达回执与已读回执，上线后补齐漏收消息
- 心跳与断线重连，Redis Stream 跨节点投递
- 多端登录、设备列表与远程登出，好友 / 群成员在线状态
- 草稿同步、文本消息搜索
- 1v1 WebRTC 语音 / 视频通话（仅私聊）
- 私聊可选端到端加密（静态 ECDH P-256 + AES-256-GCM）

不在 v1：消息举报与审核后台、多语言、iOS、macOS、群通话、开机自启和应用内更新。

## 本地启动

中间件（MySQL 8、Redis 7 映射到本机 `7000`、MongoDB 7、RustFS `9000` / 控制台 `9001`）：

```bash
docker compose -f local-dev.middleware.yml up -d
```

准备 MySQL 账号文件（该文件已在 `.gitignore` 中）和库表：

```bash
cat > onlineIM-server/src/main/resources/db.properties << 'EOF'
db.username=root
db.password=root123
EOF

docker compose -f local-dev.middleware.yml exec -T mysql \
  mysql -uroot -proot123 online_im \
  < onlineIM-server/src/main/resources/onlineIM.sql
```

`onlineIM.sql` 前 142 行是以 `#` 开头的旧稿，`mysql` 客户端会跳过这些注释，直接执行后面的 `CREATE TABLE`。整文件导入即可。

启动后端与网页：

```bash
cd onlineIM-server && ./mvnw spring-boot:run

cd OnlineIM-Vue && npm ci && npm run dev
```

默认地址：

| 用途 | 地址 |
|------|------|
| HTTP API | `http://localhost:8080/api/v1` |
| WebSocket | `ws://localhost:8081/api/v1/chat` |
| Web | `http://localhost:3000` |
| RustFS S3 | `http://localhost:9000`（`rustfsadmin` / `rustfsadmin`，桶 `onlineim`） |

生产环境、HTTPS / WSS、coturn、桌面安装包和 Android APK 见 [部署指南](docs/v1-deployment-guide.md)。

## 常用命令

```bash
# 后端测试（Java 21）
cd onlineIM-server && ./mvnw test

# Web 单元测试与生产构建
cd OnlineIM-Vue && npm test && npm run build

# 桌面安装包（当前系统能打出的目标）
cd OnlineIM-Vue && npm run tauri:build

# Android APK
cd OnlineIM-Flutter && flutter pub get && flutter test && flutter build apk --release
```

桌面 CI（`.github/workflows/tauri-desktop.yml`）在 Windows 上打 `msi,nsis`，在 Linux 上打 `deb,appimage`。Android CI（`.github/workflows/flutter-android.yml`）执行 `flutter analyze`、`flutter test` 和 `flutter build apk --release`。

## 文档

- [v1 部署与构建](docs/v1-deployment-guide.md)
- [v1 更新日志](docs/v1-changelog.md)
- [WebSocket 协议清单](docs/ws-protocol-registry.md)
- [会话模型](docs/conversation-model.md)
- [Flutter 客户端说明](OnlineIM-Flutter/README.md)

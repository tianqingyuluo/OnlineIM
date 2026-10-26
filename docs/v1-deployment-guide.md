# OnlineIM v1 部署与构建

本文覆盖 v1 的后端、中间件、网页、Tauri 桌面端和 Flutter Android 包。协议行为见 [ws-protocol-registry.md](./ws-protocol-registry.md)，功能范围见 [v1-changelog.md](./v1-changelog.md)。

## 1. 端口与进程

| 进程 | 默认端口 | 配置 |
|------|----------|------|
| Spring Boot HTTP | 8080 | `server.port` |
| Vert.x WebSocket | 8081 | `websocket.port`，路径 `websocket.path=/api/v1/chat` |
| MySQL | 3306 | 库名 `online_im` |
| Redis | 容器内 6379，本机映射 **7000** | `spring.data.redis.port=7000` |
| MongoDB | 27017 | 库名 `online_im` |
| RustFS S3 API | 9000 | `oss.endpoint` |
| RustFS 控制台 | 9001 | 仅管理用 |
| Vite（开发） | 3000 | `OnlineIM-Vue/vite.config.ts` |

WebSocket 地址形如 `ws://<主机>:8081/api/v1/chat?token=Bearer%20<jwt>`。设备类型在登录时写入 JWT。Flutter 还会在查询串附带 `device_id` 和 `device_type=phone`。

多实例时每个节点都要能访问同一套 MySQL、MongoDB、Redis 和对象存储，并且 `JWT_SECRET` 相同。跨节点消息走 Redis Stream `im:message:stream`。各节点用自己的实例身份建立消费者组，从流尾开始读，重启不会把历史帧再投一遍。在线状态连接键也在 Redis 里，不依赖某台机器的本地内存。

`node.ip`（默认 `localhost:8081`）是节点标识的一部分。多实例部署时改成该节点可达的 `主机:8081`，避免几个进程抢同一个身份。

## 2. 中间件

仓库根目录的 `local-dev.middleware.yml` 启动 MySQL 8.0、Redis 7、MongoDB 7 和 RustFS。MySQL root 密码是 `root123`，并创建空库 `online_im`。RustFS 访问密钥是 `rustfsadmin` / `rustfsadmin`。

```bash
docker compose -f local-dev.middleware.yml up -d
```

生产环境可以换成托管的 MySQL、MongoDB、Redis 和 S3。只保留与 `application.properties` 里一致的库名、Redis 逻辑库和对象桶即可。Redis 需要支持 Stream（Redis 5+，建议 7）。

### MySQL

账号不写进 `application.properties`，而写在被 git 忽略的 `onlineIM-server/src/main/resources/db.properties`：

```properties
db.username=root
db.password=root123
```

表结构在 `onlineIM-server/src/main/resources/onlineIM.sql`。文件前 142 行是注释掉的旧稿，可执行语句从第 143 行的 `CREATE TABLE users` 开始，包含用户、好友、群、群设置、群公告、`group_announcement_reads`、群成员和入群申请。字符集为 `utf8mb4`。

```bash
docker compose -f local-dev.middleware.yml exec -T mysql \
  mysql -uroot -proot123 online_im \
  < onlineIM-server/src/main/resources/onlineIM.sql
```

已有库如果缺公告已读表，服务启动时会执行 `CREATE TABLE IF NOT EXISTS group_announcement_reads`。消息、会话、表情和上传记录在 MongoDB，首次写入时由应用建集合和搜索索引，不需要单独的 Mongo 脚本。

### 对象存储

默认 `oss.type=rustfs`。上传图片、文件、语音和表情时，适配器会在桶不存在时创建 `oss.default-bucket`（默认 `onlineim`）。

`oss.public-base-url` 是返回给客户端的文件 URL 前缀。本地开发用 `http://localhost:8080`，文件经后端 `/{bucket}/**`（对外路径 `/onlineim/**`）代理。站点放在统一域名的反向代理后面时，把它设成空字符串，让响应使用 `/onlineim/...` 相对路径。聊天文件带 `X-Content-Type-Options: nosniff`，`chats/files/` 强制附件下载，语音支持单段 `Range`。下载时校验当前用户是会话参与者。

换成其他厂商时改 `oss.type`，并参考 `onlineIM-server/src/main/resources/application-oss-example.properties`：

| `oss.type` | 还需要 |
|------------|--------|
| `rustfs` | `endpoint`、`access-key`、`secret-key`、`default-bucket` |
| `minio` | 同上 |
| `s3` | 再加 `oss.region` |
| `aliyun` | endpoint、密钥、桶；可选 `oss.cdn-domain` |
| `tencent` | 同上 |

密钥不要提交到仓库。

## 3. 后端

要求 Java 21。仓库自带 Maven Wrapper。

```bash
cd onlineIM-server
export JWT_SECRET='至少32字节的随机串-生产环境各节点必须相同'
./mvnw spring-boot:run
```

未设置 `JWT_SECRET` 时使用 `application.properties` 里的开发占位符。占位符短于 32 字节会拒绝启动；占位符本身够长，但生产或多节点必须用环境变量覆盖，并且各节点一致。修改密码、挤下线和远程登出都依赖 Redis 里的 token 黑名单，密钥不一致会导致旧 token 在别的节点仍然有效。

常用业务配置（均可在 `application.properties` 覆盖）：

| 键 | 默认 | 作用 |
|----|------|------|
| `im.upload.image-max-size` | 10MB | 图片上限，超出 HTTP 413 |
| `im.upload.file-max-size` | 20MB | 文件上限 |
| `im.upload.thumbnail-max-edge` | 320 | 缩略图最长边 |
| `im.upload.voice-max-duration` | 60s | 语音时长上限 |
| `im.message.recall-window-seconds` | 120 | 撤回时间窗，`<=0` 表示不限制 |
| `im.message.edit-window-seconds` | 同撤回窗 | 文本编辑时间窗 |
| `websocket.heartbeat.timeout` | 90s | 无入站帧则断开，在线状态用同一阈值 |
| `im.webrtc.*` | 见第 6 节 | STUN / TURN 与响铃超时 |

`spring.servlet.multipart.max-file-size` 是 100MB，保证超限请求能走到业务层并返回明确的 413，而不是被容器提前掐断。反向代理的 `client_max_body_size` 也要至少 100m。

打包：

```bash
cd onlineIM-server
./mvnw -DskipTests package
java -jar target/onlineim-0.0.1-SNAPSHOT.jar
```

测试：`./mvnw test`。

## 4. Web 前端

要求 Node.js 22（与 CI 一致）。

```bash
cd OnlineIM-Vue
npm ci
npm run dev
```

开发服务器监听 `0.0.0.0:3000`。默认 API 是 `http://localhost:8080/api/v1`，默认 WebSocket 是 `ws://localhost:8081/api/v1/chat`（`OnlineIM-Vue/shared/endpoints.js`）。浏览器里的覆盖值通过 `localStorage` 键 `onlineim.server_config` 读取。

```bash
npm test          # Vitest
npm run build     # vue-tsc 与 Vite 生产包，输出 OnlineIM-Vue/dist
npm run preview   # 预览 dist
```

`npm run proxy` 会在 `:8000` 起一个开发用 HTTP 代理，目标取 `API_BASE_URL`。日常开发直接访问 Vite 即可，不必开这个代理。

生产静态文件可以由任意 Web 服务器托管。页面、`/api/` 和 `/onlineim/` 最好在同一站点下，这样相对路径的媒体 URL 和 Cookie 无关的 Bearer token 都能工作。HTTPS 站点必须把接口配成 `https://` 和 `wss://`，否则浏览器会拦截麦克风、摄像头（语音消息和 WebRTC）。

## 5. Tauri 桌面端

桌面端包装现有 Vue 应用，不重写界面。配置在 `OnlineIM-Vue/src-tauri/tauri.conf.json`：产品名 `OnlineIM`，版本 `1.0.0`，标识 `icu.tianqingyuluo.onlineim`。打包目标是 `msi`、`nsis`、`deb`、`appimage`。v1 不打 macOS。

在 `OnlineIM-Vue` 目录：

```bash
npm ci
npm run tauri:build
```

`npm run tauri:build` 会先执行 `npm run build`，再调用 Tauri。只打部分格式：

```bash
# Windows
npm run tauri:build -- --bundles msi,nsis

# Linux
npm run tauri:build -- --bundles deb,appimage
```

产物目录：`OnlineIM-Vue/src-tauri/target/release/bundle/`。

- Windows：`msi/` 与 `nsis/`（安装器 exe）。WebView2 使用在线引导安装（`downloadBootstrapper`），NSIS 为当前用户安装。
- Linux：`deb/` 与 `appimage/`。deb 依赖 `libwebkit2gtk-4.1-0`、`libayatana-appindicator3-1`、`librsvg2-2`。

Linux 构建机需要：

```bash
sudo apt-get update
sudo apt-get install -y libwebkit2gtk-4.1-dev libayatana-appindicator3-dev \
  librsvg2-dev patchelf libssl-dev
```

另外需要稳定版 Rust（`rustup`）和 Node.js 22。CI 工作流 `.github/workflows/tauri-desktop.yml` 用同样的依赖和 `--bundles` 参数，并上传安装包。

开发调试：`npm run tauri:dev`。它会先起 Vite。桌面端的 API / WebSocket 地址可在应用里修改，不要写死在包里。

v1 的原生能力是系统通知、托盘（关闭窗口缩到托盘、未读红点、恢复和退出）和单实例锁。开机自启和应用内更新不做，安装包手动分发。

## 6. Flutter Android

工程在 `OnlineIM-Flutter`，Dart SDK `>=3.4.0 <4.0.0`，`minSdk` 24，`targetSdk` 34。模拟器访问宿主机时，默认 HTTP 是 `http://10.0.2.2:8080/api/v1`，WebSocket 是 `ws://10.0.2.2:8081/api/v1/chat`。真机请在登录页改成电脑的局域网地址；地址写入 Android Keystore。登录固定带 `device_type: phone`。

```bash
cd OnlineIM-Flutter
flutter pub get
flutter analyze
flutter test
flutter build apk --release
```

APK 在 `OnlineIM-Flutter/build/app/outputs/flutter-apk/app-release.apk`。

存在 `OnlineIM-Flutter/android/key.properties` 时使用其中的 release 签名；没有该文件时 release 构建回退到 debug 签名，因此上面的命令可以在没有密钥库时完成。密钥库不要提交。CI 见 `.github/workflows/flutter-android.yml`（Temurin 17、Flutter stable、Android platform-tools）。

## 7. HTTPS、WSS 与 coturn

语音录制和 WebRTC 的 `getUserMedia` 要求安全上下文：`https://` 或 `localhost`。页面一旦走 HTTPS，API 必须是 HTTPS，信令必须是 WSS，否则浏览器会混合内容拦截。

下面的示例把网页、HTTP API、文件代理放在同一个域名上，并把 WebSocket 单独反代到 8081。`client_max_body_size` 与后端 multipart 上限对齐。

```nginx
server {
    listen 443 ssl;
    server_name im.example.com;

    ssl_certificate     /etc/ssl/im.example.com.crt;
    ssl_certificate_key /etc/ssl/im.example.com.key;

    client_max_body_size 100m;

    root /var/www/onlineim;
    index index.html;

    location /api/v1/chat {
        proxy_pass http://127.0.0.1:8081;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_read_timeout 3600s;
        proxy_send_timeout 3600s;
    }

    location /api/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-Proto https;
    }

    location /onlineim/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-Proto https;
    }
}
```

此时把客户端 API 设为 `https://im.example.com/api/v1`，WebSocket 设为 `wss://im.example.com/api/v1/chat`，并把 `oss.public-base-url` 留空（或设成 `https://im.example.com`）。

### STUN / TURN

通话媒体不经过业务服务器。`GET /api/v1/calls/ice-servers` 把 ICE 配置发给已登录用户。默认只有 `stun:stun.l.google.com:19302`。对称 NAT 或严格防火墙后的客户端需要 TURN。

coturn 示例（`/etc/turnserver.conf`）：

```
listening-port=3478
fingerprint
use-auth-secret
static-auth-secret=请换成足够长的随机串
realm=im.example.com
```

后端对应：

```properties
im.webrtc.stun-urls=stun:im.example.com:3478
im.webrtc.turn-urls=turn:im.example.com:3478
im.webrtc.turn-secret=请换成足够长的随机串
im.webrtc.turn-ttl-seconds=86400
im.webrtc.call-timeout-seconds=45
```

`turn-secret` 非空时，用户名是 `{过期 unix 秒}:{用户ID}`，凭证是 HMAC-SHA1。不要同时依赖静态 `turn-username` / `turn-credential`，密钥模式优先。3478 需要对客户端的 UDP（以及你启用的 TCP/TLS 端口）放行。未配置 TURN 时接口只返回 STUN，通话在复杂网络下可能建立失败，信令本身仍然可用。

## 8. 上线前检查

- `JWT_SECRET` 已替换，且每个后端节点相同。
- MySQL 已导入 `onlineIM.sql`，MongoDB 和 Redis 可从每个节点访问。
- `oss.endpoint` 从后端机器可达，`oss.public-base-url` 从浏览器可达，或为空并由反向代理提供 `/onlineim/`。
- 反向代理允许 WebSocket Upgrade，上传 body 至少 100MB。
- 需要语音和视频的用户通过 HTTPS / WSS 访问；跨网通话已配置 TURN。
- 桌面端用 `npm run tauri:build` 按目标系统出包；Android 用 `flutter build apk --release`。

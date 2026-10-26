# v1 - 文件与图片缩略图

## 背景

图片消息若在会话流里直接加载原图，列表会变慢、滚动会卡。v1 在上传时由服务端生成缩略图，消息体带上缩略图地址和尺寸：列表用缩略图，点击再看原图。普通文件走同一条上传管线，以文件卡片展示，不生成缩略图。

## 用户旅程

> 2026-10-21 按已合入的 `619388f`（服务端上传与缩略图）和 `2de6792`（Web 发送与展示）补全。视频首帧、多档缩略图、波形不在本次范围。

1. 用户在私聊或群聊（桌面 / 手机布局）从输入区选择一张图片或一个文件。
2. 客户端先调用 `POST /api/v1/messages/upload`（`type=image` 或 `type=file`），上传期间可看到进度。
3. 图片：服务端按文件内容识别格式（默认 jpeg/png/gif/bmp），同步生成长边不超过 320px 的缩略图（含透明通道时 PNG，否则 JPEG；GIF 只取第一帧做静态缩略图，原图保持动画），返回 `file_id`、`url`、`thumbnail_url` 以及原图和缩略图的宽高。
4. 文件：按扩展名白名单校验，上限默认 20MB，不生成缩略图，返回 `file_id`、`url`、文件名、大小和 MIME。
5. 上传成功后，客户端用 WebSocket（或 `POST /api/v1/messages`）发送 `message_type` 为 `image` / `file` 的消息。客户端可以附带上传响应，服务端只认 `file_id`，并改写成权威媒体 JSON。
6. 对方实时收到同一份内容。图片气泡用缩略图（加载失败时有占位），点击打开原图；文件气泡显示文件名、大小，点击下载。会话预览为 `[图片]` / `[文件]`，引用预览为 `[图片]` / `[文件] 文件名`。
7. 刷新、翻历史、上线补齐看到的是同一份服务端内容。聊天文件经 `/onlineim/**` 代理，且仅会话参与者可下载。

不在本次范围：独立视频消息、服务端 ffmpeg 抽视频首帧、多档（小/中/大）缩略图、客户端懒加载占位动画之外的渐显策略。mp4 等可作为普通文件发送。

## 需求

- 上传：`POST /api/v1/messages/upload`，字段 `type`=`image`|`file`，`file`=文件内容。错误：参数/损坏 `400`，超限 `413`，格式不允许 `415`。
- 图片限制：`im.upload.image-max-size`（默认 10MB）、`im.upload.image-max-pixels`（默认 4000 万）、`im.upload.image-formats`、`im.upload.thumbnail-max-edge`（默认 320）。
- 文件限制：`im.upload.file-extensions`、`im.upload.file-max-size`（默认 20MB）。
- 对象键：`chats/images/yyyyMM/<随机>.<ext>`，缩略图追加 `_thumb`；`chats/files/yyyyMM/<随机>.<ext>`。存储走 `OSSAdapterFactory`（默认 RustFS）。
- 发送：`content` 里的 `file_id` 必须属于发送者；`image` 必须引用图片上传。服务端忽略客户端自报的 `url`。字段契约登记在 `docs/ws-protocol-registry.md`「图片 / 文件消息」。
- 客户端：列表用 `thumbnail_url`，点击用 `url`；文件显示卡片。Web / Tauri 共用 Vue 实现，Flutter 使用同一上传与发送契约。

## e2e 测试场景

与上方用户旅程对应：

- 旅程 3：上传 jpeg/png/gif/bmp 成功并带缩略图；GIF 缩略图为第一帧静态图、原图仍是 GIF；损坏图片 `400`，超像素或无法识别 `400`/`415`，超过 10MB `413`，未登录 `401`。
- 旅程 4：白名单扩展名上传成功且无缩略图字段；不允许的扩展名 `415`，超过 20MB `413`。
- 旅程 5/6：私聊与群聊发送图片和文件；伪造 `url` 被忽略；引用他人上传或类型不匹配时 WS `ERROR code=400` / HTTP `400`，消息不落库；对方看到缩略图和文件卡片，点击可看原图或下载。
- 旅程 7：历史、上下文、上线补齐返回同一 `content`；非会话参与者不能下载 `/onlineim/**` 下的聊天文件。

## 测试范围

- 后端：`ChatMediaService` 图片解码、缩略图缩放与 GIF 首帧、文件扩展名与大小限制、`file_id` 归属校验；上传接口的 400/413/415。
- 前端：`parseMediaContent` 拒绝不安全 URL；图片气泡使用 `thumbnail_url`，文件卡片展示文件名与大小。
- 三端：Tauri 复用 Web；Flutter 按同一 `file_id` 契约选择并发送图片/文件（见 [[v1-flutter-android]]）。

## 验收标准

- [x] 图片消息上传时同步生成缩略图，列表用缩略图，点击加载原图
- [x] 文件消息走同一 OSS 上传管线，以文件卡片展示（不生成缩略图）
- [x] GIF 缩略图取第一帧静态图，原图保持不变
- [x] `thumbnail_url` / `thumbnail_width` / `thumbnail_height` 已登记到 v1 协议清单
- [x] 单元与 UI 测试覆盖上传元数据解析和缩略图/文件卡片渲染

## 依赖

- OSS 适配器已就绪。语音上传（[[v1-voice-message]]）与自定义表情（[[v1-emoji-system]]）复用同一存储适配器，但是独立的 `type` / 收藏接口。
- 发送确认复用 [[v1-message-ack-read]]。

# v1 - WebRTC 视频聊天

## 背景

IM 需要 1v1 音视频通话。信令复用现有 WebSocket（`/api/v1/chat`，snake_case JSON），媒体由浏览器 `RTCPeerConnection` 点对点传输（SRTP）。NAT 穿透使用可配置的 STUN，以及可选的 TURN。群视频不在 v1。

## 用户旅程

1. 用户在私聊（桌面或手机布局）点「语音通话」或「视频通话」。
2. 客户端拉取 `GET /api/v1/calls/ice-servers`，打开麦克风（视频通话同时打开摄像头）。权限被拒绝、没有设备或设备被占用时提示原因，不发出邀请。
3. 客户端发送 `CALL_INVITE`（`call_type` 为 `audio` 或 `video`，带 SDP）。界面显示对方头像、名字和「正在呼叫…」，可取消。
4. 服务端确认双方是好友且会话 ID 匹配。不是好友则回 WS `ERROR`（403），不振铃、不落库。
5. 被叫离线：不振铃，给主叫 `CALL_CANCEL`（`reason=offline`），并写入一条 `message_type=call`、`call_status=missed` 的私聊消息。
6. 被叫已在通话中：给主叫 `CALL_BUSY`，写入 `call_status=busy`，不向被叫振铃。
7. 被叫在线且空闲：其全部在线设备收到 `CALL_INVITE` 并显示来电（接听 / 拒绝）。被叫回 `CALL_RINGING`。
8. 一台设备接听后发送 `CALL_ANSWER`。主叫收到 SDP 并进入通话；被叫的其他设备收到 `CALL_HANDLED_ELSEWHERE` 并停止响铃。拒接则主叫收到 `CALL_REJECT`，其他设备同样停止响铃。
9. 双方用 `CALL_CANDIDATE` 交换 ICE。远端描述尚未设置时，候选先排队。
10. 通话中可静音、开关摄像头、最小化成底部浮条（仍可浏览会话），并显示 `mm:ss`。任一方 `CALL_HANGUP` / `CALL_END` 结束通话。
11. 主叫在接听前取消发送 `CALL_CANCEL`。超过 `call_timeout_seconds`（默认 45）仍未接听，服务端发送 `CALL_CANCEL`（`reason=timeout`）。
12. 终态都落成私聊消息，双方会话预览更新，并通过现有私聊推送出现在历史和离线同步里。点击通话气泡按原类型重拨。

## 需求

- `GET /api/v1/calls/ice-servers`（需登录）：`ice_servers`（`urls`，可选 `username` / `credential`）和 `call_timeout_seconds`。
- STUN 默认 `stun:stun.l.google.com:19302`（`im.webrtc.stun-urls`）。TURN 支持静态账号，或 coturn `use-auth-secret` 的限时 HMAC-SHA1 凭证。
- WS 帧：`CALL_INVITE` / `CALL_OFFER`、`CALL_RINGING`、`CALL_ANSWER`、`CALL_CANDIDATE`、`CALL_REJECT`、`CALL_CANCEL`、`CALL_BUSY`、`CALL_END` / `CALL_HANGUP`、`CALL_HANDLED_ELSEWHERE`。
- 好友校验、跨实例 Redis Stream 扇出、多端振铃与“已在其他设备处理”。
- 通话记录 `message_type=call`，预览为 `[视频通话] mm:ss`、`[语音通话] mm:ss`、`[未接视频通话]`、`[未接语音通话]`、`[已拒接]`、`[已取消]`、`[对方忙线]`。
- 前端：`call.store.ts`、WebRTC 管理、全局 `CallOverlay`、会话内入口、通话气泡可重拨。

## e2e 测试场景

1. 好友视频：邀请 → 响铃 → 接听 → 交换 candidate → 挂断。双方看到 `[视频通话] mm:ss`。
2. 好友语音：同上，预览为 `[语音通话] mm:ss`，界面不要求摄像头。
3. 被叫拒绝：主叫收到 `CALL_REJECT`，记录 `[已拒接]`，其他设备停止响铃。
4. 主叫取消：被叫停止响铃，记录 `[已取消]`。
5. 响铃超时：双方收到 `CALL_CANCEL`（`reason=timeout`），记录 `[未接视频通话]` 或 `[未接语音通话]`。
6. 被叫离线：不振铃，主叫收到 `reason=offline`，记录未接。
7. 被叫占线：主叫收到 `CALL_BUSY`，记录 `[对方忙线]`。
8. 非好友：`ERROR` 403，无通话记录。
9. 多设备：两台被叫设备同时响铃，一台接听后另一台收到 `CALL_HANDLED_ELSEWHERE`。
10. 无麦克风权限：不发送 `CALL_INVITE`，并提示权限被拒绝。
11. 点击历史中的通话气泡，按原 `call_type` 再次呼叫。

自动化覆盖在下方测试范围。真实双浏览器媒体连通依赖部署的 STUN/TURN，不放进默认 CI。

## 测试范围

- 后端单元 / 集成：ICE 配置（默认 STUN、静态 TURN、HMAC 凭证）、好友拒绝、离线未接、占线、多设备接听与 `CALL_HANDLED_ELSEWHERE`、拒接、取消、超时、挂断时长、candidate 转发、通话消息落库与预览文案、WebSocket `ERROR` 帧。
- 前端 Vitest：`call.store` 的邀请、接听、忙线、其他设备处理、响铃超时；ICE candidate 在远端描述前排队；设备权限错误；通话气泡渲染与点击重拨。
- `./mvnw test`、`npm run test:unit`、`npx vue-tsc --noEmit`、`npm run build`。

## 验收标准

- [x] 1v1 语音和视频通话信令闭环（邀请、响铃、接听、拒绝、取消、超时、占线、离线、挂断）
- [x] 信令经现有 WebSocket 与 Redis Stream 扇出到目标用户的在线设备
- [x] 一台设备接听或拒绝后，其他设备收到 `CALL_HANDLED_ELSEWHERE`
- [x] STUN/TURN 配置与限时 HMAC 凭证可下发
- [x] 非好友不能发起通话（WS `ERROR`）
- [x] 通话记录以 `message_type=call` 落库，并更新双方会话预览
- [x] 协议字段已登记，Web / Tauri / Flutter 对齐
- [x] 私聊桌面与手机布局都有语音、视频入口；全局浮层支持静音、摄像头、最小化、时长和重拨
- [x] 后端测试与前端单元测试、类型检查、构建通过

## 依赖

- 依赖 [[v1-ws-heartbeat-reconnect]]（信令通道稳定）。
- 与 [[v1-multi-device-login]]（多端响铃）协同。媒体默认走 WebRTC SRTP，不依赖 [[v1-e2ee]] 的消息加密。
- 群视频不在 v1 范围。

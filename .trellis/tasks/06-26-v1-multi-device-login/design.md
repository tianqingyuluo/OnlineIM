# 多端登录策略 — 设计

## 边界

- 设备类型只有 `web`、`desktop`、`phone`。别名（android/ios/tauri 等）在登录时归一，未知类型当作 `web`。
- 同一用户、同一类型同时一个会话。类型之间互不影响。
- 不提供“允许同类型多开”的用户开关。
- Web 端按 UA 区分网页和手机；`localStorage.onlineim.device_type` 可强制为 `desktop`，留给桌面壳。Tauri / Flutter 不在本变更里改。

## 会话数据

- `user:sessions:{userId}:{deviceId}` → `RedisConnectionMeta`（补 `userId`、`deviceId`、`deviceType`、`loginTime`）。
- `user:device-type:{userId}` Hash：`deviceType → deviceId`，用来找到要挤掉的那一台。
- WebSocket 断开只清空 `connectionID`。登录记录保留到主动登出、远程登出或被挤下线。
- JWT 增加 `devicetype`。刷新 token 沿用原来的设备和类型，并写回会话里的 token。

## 挤下线

1. 登录成功后，若该类型已有另一台设备：作废旧 token，删除旧会话，发布 `im:session:kick`。
2. 本节点先把 `SESSION_KICKED` 写给旧连接，写完以关闭码 4001 断开；其他节点收到 Redis 消息后做同样的事。
3. 同一 `device_id` 再次登录：作废旧 token，通知旧页面“重新登录”，新的 WebSocket 再标为在线。
4. 同一 `device_id` 的新连接替换旧连接时只发关闭码 4000，不注销账号。客户端不再自动重连，避免两个页面互抢。

## 消息同步

`LocalSessionRegistry` 按 `userId + deviceId` 保存多条连接。业务帧（私聊、群聊、撤回、编辑、通知、送达、已读）发给该用户的每一条连接。私聊和群聊包含发送者自己，发送端用 `client_message_id` 合并。已读事件的目标里加上阅读者，其他设备只推进自己的已读游标和未读数。

## 接口

- `GET /api/v1/devices`
- `POST /api/v1/devices/{deviceId}/logout`
- 登录响应增加 `device_id`、`device_type`

## 兼容

旧 token 没有 `devicetype` 时按 `web`。`getByUserId` 仍返回任意一条有效连接，避免只关心“是否在线”的调用方被改坏。

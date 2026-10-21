# 在线状态技术设计

## 边界

- 状态只有 `online` / `offline`。离线附带 `last_seen_at`（epoch millis），隐藏时对别人不返回该字段。
- 连接真相在 Redis，不改 MySQL 表。本机 `LocalSessionRegistry` 只负责把帧送到本节点的 socket，并在连接被移除时通知 presence。
- 不新增客户端订阅帧。观众 = 互为好友 ∪ 未解散群的在群成员，排除自己。

## Redis

| Key | 结构 | 含义 |
|-----|------|------|
| `presence:conn:{connectionId}` | string，PX = 心跳超时 | 这条连接还活着；值是 userId |
| `presence:conns:{userId}` | SET of connectionId | 该用户登记过的连接，读取时丢掉已过期的 |
| `presence:online` | SET of userId | 当前至少有一条连接的用户，给定时清理扫描 |
| `presence:last_seen:{userId}` | string millis | 最近一次变为离线的时间 |
| `presence:hidden:{userId}` | `1` 或无键 | 隐藏在线状态 |

上线、下线、过期清理用 Lua 保证“是不是从 0 变成 1 / 从 1 变成 0”只有一个实例会判定成功，避免多实例重复推送。

## 数据流

1. WebSocket `accept` 后 `PresenceService.onConnected`。`before == 0` 且未隐藏 → 推送 online。
2. 会话从 `LocalSessionRegistry` 移除（客户端关闭、异常、心跳超时清理）→ `onDisconnected`。只有 Lua 判定这次把在线集合删空才推送 offline，并写下 `last_seen`。
3. `HEARTBEAT` 给连接键续期。键已经没了（实例崩溃后客户端又连上同一条旧连接的极端情况，或超时后心跳恢复）则按新连接重新登记。
4. 每 30 秒抢 `presence:sweep:lock`，扫描 `presence:online`，把连接键已过期的用户收成 offline。
5. 推送走现有 `im:message:stream`，类型 `PRESENCE_UPDATE`，`receiverIDs` 为观众。本节点 `PresenceUpdateReceiver` 发给该用户的**全部**本地连接。

## REST

- `GET /api/v1/presence?user_ids=a,b`：最多 100 个。自己看真实状态；有权看见的人在对方隐藏时得到 `offline` + `last_seen_at=null`；其他人 `visible=false`。
- `GET /api/v1/presence/settings`、`PUT /api/v1/presence/settings`：`hide_online_status`。打开隐藏时向观众推一条不带上次在线时间的 offline；关闭时按真实状态再推一次。

## 客户端

Pinia `presence` store 保存 `userId → {status, last_seen_at, visible}`。登录后用好友 id 拉一次；打开私聊 / 群成员时补拉；`PRESENCE_UPDATE` 直接写入。好友列表、私聊标题、群成员列表、群资料网格和群标题在线人数都读这份数据。隐藏开关放在桌面和手机的编辑资料里。

## 兼容

- `LocalSessionRegistry` 改为一个用户多条连接：`getByUserId` 仍返回其中一条活跃连接，消息投递行为不变；新增 `getSessionsByUserId` 给 presence 扇出。关掉其中一条不会把该用户从映射里整行删掉。
- 不改已有消息帧。新增帧只在协议清单登记。

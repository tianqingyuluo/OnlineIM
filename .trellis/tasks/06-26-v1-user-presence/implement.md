# 在线状态实现清单

1. Redis 连接存储（Lua：上线 / 下线 / 续期 / 过期清理）与内存实现（单测用，语义与 Lua 对齐）。
2. `PresenceAudienceService`：互为好友、未解散群的在群成员。
3. `PresenceService`：聚合、隐私、查询、经 Redis Stream 推送。
4. 接入 WebSocket 连接、断开、心跳，以及注册表多连接。
5. `PresenceController` 与 `PresenceUpdateReceiver`。
6. 协议清单、PRD 旅程与测试范围。
7. Vue：store、文案、好友列表、私聊标题、群标题在线人数、群成员列表与网格、资料页隐藏开关。桌面与手机共用。
8. 后端单测与 vitest；`mvn test` 与 `vitest run` 通过。

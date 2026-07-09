# WebSocket 重连状态与离线队列修复

## 日期

2026-07-10

## 变更摘要

- 修改 `OnlineIM-Vue/src/services/websocket.service.ts`，阻止连接仍处于 `CONNECTING` 时重复创建 WebSocket，并通过连接实例校验忽略旧连接迟到的事件，避免旧连接关闭后覆盖新连接状态或重复启动重连。
- 修复离线消息未携带自动生成 `client_message_id` 的问题，确保重连发送后服务端响应可以关联并将队列状态更新为已发送。
- 重连刷新队列时增加连接开放状态校验，避免连接在刷新过程中再次断开后继续发送。
- 连接建立后立即发送首个心跳，使连续两个心跳周期未收到回执时能在约 60 秒判死，而不是延迟到第三个周期。
- 后台自动重试时保持离线状态可见，只有手动重试或连接成功才切换状态，避免“离线”和“重连中”反复闪烁。
- 修复历史消息接口响应被误当作数组的问题，正确读取 `messages` 与 `has_more_before`，并显式启动头像缓存任务；同时清理 IndexedDB 升级回调中的无用参数和变量，使本任务涉及文件通过 TypeScript 检查。
- 新增 `OnlineIM-Vue/src/services/__tests__/websocket.service.test.ts`，覆盖重复连接、旧连接事件、心跳判死、离线队列关联 ID、重连刷新及响应确认。
- 新增真实 Vert.x WebSocket 集成测试，覆盖握手后的心跳 ACK、静默连接超时关闭与错误路径拒绝；同时修复服务端未校验配置 WebSocket 路径的问题。

## 关联任务

- `v1-ws-heartbeat-reconnect`

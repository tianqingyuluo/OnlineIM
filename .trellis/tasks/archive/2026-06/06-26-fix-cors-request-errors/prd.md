# Fix frontend backend CORS and request errors

## Goal

修复前后端联调中好友请求处理链路的失败问题，避免真实业务错误被浏览器显示为 CORS 失败；补齐本地联调所需中间件；并将默认对象存储从 MinIO 切换为 RustFS。

## Confirmed Facts

- 前端开发服务器运行在 `http://localhost:3000`，后端 HTTP API 运行在 `http://localhost:8080/api/v1`。
- 后端已有 Spring Security CORS 配置，但仅显式放行 `/api/v1/auth/**` 的 `OPTIONS` 请求，且 `allowedHeaders` 只列出少量头。
- 浏览器报错路径为 `/api/v1/friends/[object%20Object]/group`，说明前端把对象插入了 URL path。
- `friendsService.handelFriendRequest` 实际返回后端响应体对象，组件却按字符串使用，导致好友关系 ID 变成 `[object Object]`。
- `FriendRequestsList.vue` 把 `listStore.FriendRequestsList` 当成 `{ requests }` 对象读取，但 store 类型是 `FriendRequest[]`，会出现 `undefined.length` 崩溃。
- 后端接受好友请求时返回的 `friend_id` 是 `user_friends.id`，当前排序逻辑可能返回非当前登录用户视角的关系 ID。
- `usersSelectResoult.vue` 和 `GroupSelectResult.vue` 模板存在多根节点，父组件传入 `class` 时触发 Vue extraneous attributes 警告。
- `/api/v1/conversations` 的浏览器 HAR 表现为 `status: 0`，但服务端直连会等待约 30 秒后返回 `500`，符合 MongoDB 连接超时特征。
- 当前本机只运行了 `MySQL` 和 `Redis`，缺少后端真实依赖的 `MongoDB`；默认对象存储仍指向 `MinIO`，与本次联调环境选择不一致。
- 用户要求将默认对象存储从 `MinIO` 调整为 `RustFS`，并允许新增适配器。

## Requirements

- 后端 CORS 必须稳定支持 Vite 开发源访问所有 API 路径，包括带 `Authorization` 的预检请求。
- 前端好友请求列表必须始终以数组形式消费，空列表、404 和成功响应都不能导致渲染崩溃。
- 处理好友请求时，前端必须使用明确的响应类型，从响应体中提取当前用户可用的 `friend_id`。
- 后端接受好友请求成功后必须返回当前登录用户视角的好友关系 ID，供后续设置好友分组使用。
- 拒绝好友请求的前后端状态值必须一致，不能因 `reject/refuse` 不一致导致 400/500。
- 修复多根组件的 class 继承警告，不改变现有页面结构和交互含义。
- 本地联调环境必须能启动后端真实依赖的中间件，至少覆盖 `MySQL`、`Redis`、`MongoDB`、`RustFS`。
- 后端默认 OSS 配置必须切换到 `RustFS`，并通过统一 `OSSAdapter` 抽象接入，不破坏现有上传接口调用方式。
- 不回滚或覆盖本工作区已有的无关未提交变更。

## Acceptance Criteria

- [ ] Vite 前端对后端认证 API 的跨源 GET/POST/PUT/DELETE/OPTIONS 请求有正确 CORS 响应。
- [ ] 好友请求页在无请求、有请求、接口 404 时均不抛出 `requests is undefined` 或 `undefined.length`。
- [ ] 点击“同意”好友请求不会再发出 `/friends/[object Object]/group` 请求。
- [ ] 点击“同意”后用于设置默认分组的是当前登录用户的 `user_friends.id`。
- [ ] 点击“拒绝”时前端发送的状态能被后端识别。
- [ ] `UsersSelectResoult` 和 `GroupSelectResult` 接收父组件 `class="h-full"` 时不再触发 extraneous attributes 警告。
- [ ] 本地启动所需中间件后，`GET /api/v1/conversations` 不再因 Mongo 不可用而超时 500。
- [ ] 默认 OSS 类型切换为 `rustfs` 后，后端可以成功初始化对象存储适配器。
- [ ] 前端类型检查/构建和后端编译尽可能通过；如环境阻止验证，需要记录阻塞原因。

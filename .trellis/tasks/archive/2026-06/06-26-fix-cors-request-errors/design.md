# 技术设计

## 范围

本次修复覆盖后端 HTTP CORS/Security 配置、好友请求处理接口合同、前端好友请求服务类型、好友请求列表组件、搜索结果组件的根节点结构，以及本地联调对象存储与中间件运行环境。

## 数据流

1. 前端 `FriendRequestsList.vue` 从 `useListStore().FriendRequestsList` 读取待处理好友请求数组。
2. 用户点击同意或拒绝后，前端调用 `friendsService.handleFriendRequest(requestId, action)`。
3. 后端 `FriendController.handleFriendRequest` 校验当前登录用户是否为请求接收者。
4. 同意时后端创建双向 `user_friends` 关系，并返回当前登录用户视角的 `friend_id`。
5. 前端拿到 `friend_id` 后调用 `PUT /friends/{friend_id}/group` 设置默认好友分组。
6. 会话列表接口通过 `ConversationRepository` 访问 MongoDB；本地环境必须提供可达的 MongoDB 服务，避免请求挂起后被前端误判为 CORS。
7. 文件上传相关接口继续依赖 `OSSAdapter`，但默认实例从 `MinIO` 切换为 `RustFS` 适配器。

## 后端设计

- CORS 配置改为：
  - 对所有路径放行 `OPTIONS`。
  - 允许 `Authorization`、`Content-Type` 等所有请求头，避免预检因新增头失败。
  - 使用 `allowedOriginPatterns` 支持本地开发源和配置项中的前端源。
  - 开启 credentials 后返回具体 Origin，兼容未来需要凭据的请求。
- `JwtAuthenticationFilter` 对 `OPTIONS` 预检请求直接放行，避免认证过滤器提前写错误响应。
- `FriendController.handleFriendRequest`：
  - 请求不存在返回 404。
  - 当前用户不是接收者返回 403。
  - 非待处理请求返回 409。
  - 接受 `accept`、`reject`、`refuse` 三种动作输入，其中 `reject` 作为前端兼容别名。
  - 同意时不再按用户 ID 字典序排序，而是先创建当前用户视角关系，再创建对方视角关系，返回当前关系 ID。
- 对象存储适配层改为：
  - 在 `OSSAdapterFactory.OSSType` 中新增 `RUSTFS`。
  - 新增 `RustFSAdapter`，复用现有 MinIO Java SDK 的 S3 兼容调用链。
  - 抽取 `MinIO`/`RustFS` 共用逻辑，避免复制整套上传、预签名、批量删除实现。
  - 默认 `oss.type` 和本地示例配置切换为 `rustfs`。
- 本地联调编排补充 `MongoDB` 和 `RustFS` 容器，保证后端四类依赖都可用。

## 前端设计

- `FriendRequestsResponse` 作为服务层响应合同，`getReceivedFriendRequests` 解包为 `FriendRequest[]`，404 时返回空数组，其它错误继续抛出。
- 新增明确的好友请求处理响应类型 `{ friend_id?: string }`，调用方只在同意且存在 `friend_id` 时设置默认分组。
- `FriendRequestsList.vue` 使用 store 数组作为数据源，并在本地操作后同步 store，避免数组和对象结构混用。
- 搜索结果组件使用单一根容器并把 `$attrs` 透传到根节点，解决父组件 class 继承警告。

## 兼容性

- 保留现有接口路径。
- 后端兼容前端可能发送的 `reject` 和原有 `refuse`。
- 前端继续使用 snake_case 字段，不引入额外转换层。
- 不迁移配置系统到 `.env`，只修复当前跨源联调问题。
- RustFS 走 S3 兼容接口，现有控制器和 `FileStorageService` 无需改方法签名。

## 回滚

- 后端 CORS 和好友请求处理修改集中在 `SecurityConfig`、`JwtAuthenticationFilter`、`FriendController`。
- 对象存储切换修改集中在 `OSSConfig`、`OSSAdapterFactory`、`storage/impl/`、`application*.properties` 和本地编排文件。
- 前端修改集中在 `friends.service.ts`、`FriendRequestsList.vue`、两个搜索结果组件。
- 如验证发现行为回归，可逐文件回滚本次改动，不影响已有无关工作区变更。

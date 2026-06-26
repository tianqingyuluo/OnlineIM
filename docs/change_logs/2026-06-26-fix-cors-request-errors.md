# 2026-06-26 修复跨域与好友请求处理异常

## 日期

2026-06-26

## 变更摘要

- 修改 `onlineIM-server/src/main/java/icu/tianqingyuluo/onlineim/config/SecurityConfig.java`：放行所有路径的 `OPTIONS` 预检请求，补充本地 Vite 开发源，允许全部请求头并开启凭据模式，避免带 `Authorization` 的跨源请求被预检拦截。
- 修改 `onlineIM-server/src/main/java/icu/tianqingyuluo/onlineim/filter/JwtAuthenticationFilter.java`：对 `OPTIONS` 请求直接透传，避免 JWT 过滤器提前写入无 CORS 头的错误响应。
- 修改 `onlineIM-server/src/main/java/icu/tianqingyuluo/onlineim/controller/FriendController.java`：处理好友请求前增加请求存在性、归属用户、待处理状态校验；兼容 `reject/refuse`；接受好友时返回当前登录用户视角的好友关系 ID。
- 修改 `OnlineIM-Vue/src/services/api.service.ts`：网络错误或 CORS 失败时直接返回规范化错误对象，避免继续访问 `error.response` 导致二次 TypeError。
- 修改 `OnlineIM-Vue/src/services/friends.service.ts`：明确好友请求响应类型，收到好友请求接口统一解包为数组，404 视为空列表；新增正确拼写的 `handleFriendRequest` 并保留旧拼写兼容。
- 修改 `OnlineIM-Vue/src/components/independent/founding/FriendRequestsList.vue`：好友请求列表直接消费 store 数组，避免 `undefined.length`；接受好友后从响应体读取 `friend_id`，不再把对象拼入 URL。
- 修改 `OnlineIM-Vue/src/components/independent/founding/usersSelectResoult.vue`、`OnlineIM-Vue/src/components/independent/founding/GroupSelectResult.vue`、`OnlineIM-Vue/src/components/AppSideBar/left/AppSidebarLeft.vue`：显式处理多根组件属性继承，消除父组件传入 `class` 时的 Vue 警告。
- 修改 `OnlineIM-Vue/src/views/choiceOne.vue`：使用 `markRaw` 和 `shallowRef` 存储动态布局组件，避免 Vue 将组件对象转为响应式对象。
- 修改 `OnlineIM-Vue/src/type/Friends.ts`：补充好友请求发送者的 `username` 字段，匹配模板和后端响应。
- 修改 `OnlineIM-Vue/src/stores/list.ts`：默认好友分组缺失时跳过迁移，避免继续使用可能为 `undefined` 的分组。
- 修改 `.trellis/spec/frontend/hook-guidelines.md`、`.trellis/spec/backend/quality-guidelines.md`：补充跨源/网络错误、服务层响应解包、认证预检放行的防复发规范。

## 关联任务

- Trellis 任务：`06-26-fix-cors-request-errors`

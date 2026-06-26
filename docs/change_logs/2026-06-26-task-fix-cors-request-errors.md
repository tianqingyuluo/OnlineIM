# 任务完成记录：06-26-fix-cors-request-errors

## 任务信息

- 任务 ID：`fix-cors-request-errors`
- 任务标题：Fix frontend backend CORS and request errors

## 变更范围

- 后端安全与接口：`onlineIM-server/src/main/java/icu/tianqingyuluo/onlineim/config/SecurityConfig.java`、`onlineIM-server/src/main/java/icu/tianqingyuluo/onlineim/filter/JwtAuthenticationFilter.java`、`onlineIM-server/src/main/java/icu/tianqingyuluo/onlineim/controller/FriendController.java`
- 后端对象存储与本地依赖编排：`onlineIM-server/src/main/java/icu/tianqingyuluo/onlineim/config/OSSConfig.java`、`onlineIM-server/src/main/java/icu/tianqingyuluo/onlineim/storage/OSSAdapterFactory.java`、`onlineIM-server/src/main/java/icu/tianqingyuluo/onlineim/storage/impl/MinIOAdapter.java`、`onlineIM-server/src/main/java/icu/tianqingyuluo/onlineim/storage/impl/RustFSAdapter.java`、`onlineIM-server/src/main/resources/application.properties`、`onlineIM-server/src/main/resources/application-oss-example.properties`、`local-dev.middleware.yml`
- 前端好友请求与页面稳定性：`OnlineIM-Vue/src/services/api.service.ts`、`OnlineIM-Vue/src/services/friends.service.ts`、`OnlineIM-Vue/src/components/independent/founding/FriendRequestsList.vue`、`OnlineIM-Vue/src/components/independent/founding/usersSelectResoult.vue`、`OnlineIM-Vue/src/components/independent/founding/GroupSelectResult.vue`、`OnlineIM-Vue/src/components/AppSideBar/left/AppSidebarLeft.vue`、`OnlineIM-Vue/src/views/choiceOne.vue`、`OnlineIM-Vue/src/stores/list.ts`、`OnlineIM-Vue/src/type/Friends.ts`
- 规范与记录：`.trellis/spec/backend/database-guidelines.md`、`.trellis/spec/backend/quality-guidelines.md`、`.trellis/spec/frontend/hook-guidelines.md`、`docs/change_logs/2026-06-26-fix-cors-request-errors.md`、`docs/change_logs/2026-06-26-switch-local-storage-to-rustfs.md`

## 验收结果

- `cd onlineIM-server && ./mvnw compile`：通过
- `curl -X OPTIONS http://127.0.0.1:8080/api/v1/conversations ...`：通过，预检返回 `200` 且包含预期 CORS 响应头
- `curl http://127.0.0.1:8080/api/v1/conversations ...`：通过，返回 `200 []`
- `docker compose -f local-dev.middleware.yml up -d rustfs`：通过
- `docker compose -f local-dev.middleware.yml up -d mongo`：通过
- `cd OnlineIM-Vue && npm run build`：未完全通过，失败点记录为仓库内既有前端类型问题，不在本任务修改范围内

## 遗留问题

- 前端构建仍存在既有类型错误，集中在公告组件、右侧栏、群聊主面板、群组请求列表、移动端组件与 `vite.config.ts publicPath` 等位置，需在后续独立任务中处理。

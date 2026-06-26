# 实施计划

## Checklist

- [x] 启动 Trellis 任务进入 `in_progress`。
- [x] 修复后端 CORS 和预检请求放行。
- [x] 修复后端好友请求处理接口校验与返回合同。
- [x] 修复前端好友请求服务类型和响应解包。
- [x] 修复好友请求列表组件空值崩溃、响应对象误用和拒绝动作值。
- [x] 修复搜索结果组件多根节点导致的 class 继承警告。
- [x] 增加中文 change log。
- [x] 运行前端构建/类型检查和后端编译；如失败，区分本次问题与既有问题。
- [x] 新增 RustFS adapter 并切换默认 OSS 配置。
- [x] 补充本地联调所需中间件编排，启动 MongoDB 与 RustFS。
- [x] 重启前后端并验证 `/api/v1/conversations` 与对象存储初始化链路。

## Validation Results

- `cd onlineIM-server && ./mvnw compile`：通过。
- `cd OnlineIM-Vue && npm run build`：失败，剩余错误不在本次修改文件内，集中在公告组件、右侧栏、群聊主面板、群组请求列表、移动端组件、`vite.config.ts publicPath` 等既有类型问题。
- `docker compose -f local-dev.middleware.yml up -d rustfs`：通过，`RustFS` 容器使用本地缓存镜像启动成功。
- `docker pull m.daocloud.io/docker.io/library/mongo:7`：通过，经国内镜像源补齐 `MongoDB` 镜像。
- `docker compose -f local-dev.middleware.yml up -d mongo`：通过，`MongoDB` 容器启动成功。
- `./mvnw spring-boot:run`：通过，日志确认连接 `127.0.0.1:27017` 成功，并初始化 `rustfs` 适配器。
- `curl -X OPTIONS http://127.0.0.1:8080/api/v1/conversations ...`：返回 `200`，包含 `PATCH`、`Allow-Credentials` 等预期 CORS 头。
- `curl http://127.0.0.1:8080/api/v1/conversations ...`：使用新登录 token 返回 `200 []`，不再出现 30 秒超时和 `500`。

## Validation Commands

```bash
cd OnlineIM-Vue && npm run build
cd onlineIM-server && ./mvnw compile
docker compose -f local-dev.middleware.yml up -d rustfs
docker pull m.daocloud.io/docker.io/library/mongo:7
docker compose -f local-dev.middleware.yml up -d mongo
cd onlineIM-server && ./mvnw spring-boot:run
cd OnlineIM-Vue && npm run dev -- --host 0.0.0.0 --port 3000
```

## Risky Files

- `onlineIM-server/src/main/java/icu/tianqingyuluo/onlineim/config/SecurityConfig.java`
- `onlineIM-server/src/main/java/icu/tianqingyuluo/onlineim/filter/JwtAuthenticationFilter.java`
- `onlineIM-server/src/main/java/icu/tianqingyuluo/onlineim/controller/FriendController.java`
- `onlineIM-server/src/main/java/icu/tianqingyuluo/onlineim/config/OSSConfig.java`
- `onlineIM-server/src/main/java/icu/tianqingyuluo/onlineim/storage/OSSAdapterFactory.java`
- `onlineIM-server/src/main/java/icu/tianqingyuluo/onlineim/storage/impl/MinIOAdapter.java`
- `onlineIM-server/src/main/java/icu/tianqingyuluo/onlineim/storage/impl/RustFSAdapter.java`
- `onlineIM-server/src/main/resources/application.properties`
- `onlineIM-server/src/main/resources/application-oss-example.properties`
- `local-dev.middleware.yml`
- `OnlineIM-Vue/src/services/friends.service.ts`
- `OnlineIM-Vue/src/components/independent/founding/FriendRequestsList.vue`
- `OnlineIM-Vue/src/components/independent/founding/usersSelectResoult.vue`
- `OnlineIM-Vue/src/components/independent/founding/GroupSelectResult.vue`

## Review Gate

用户已明确允许创建任务并要求继续。规划完成后按本清单直接实施。

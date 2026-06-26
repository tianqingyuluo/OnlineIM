# 2026-06-26 切换本地对象存储到 RustFS 并补齐联调中间件

## 日期

2026-06-26

## 变更摘要

- 新增 `onlineIM-server/src/main/java/icu/tianqingyuluo/onlineim/storage/impl/RustFSAdapter.java`：为 `RustFS` 增加专用对象存储适配器，复用现有 S3 兼容调用链，避免控制器和服务层感知底层存储切换。
- 修改 `onlineIM-server/src/main/java/icu/tianqingyuluo/onlineim/storage/impl/MinIOAdapter.java`：提取可复用的 provider 名称，供 `MinIO` 和 `RustFS` 共用上传、删除、预签名和存储桶初始化逻辑。
- 修改 `onlineIM-server/src/main/java/icu/tianqingyuluo/onlineim/storage/OSSAdapterFactory.java`：新增 `RUSTFS` 类型与工厂分支，让配置层可以直接声明 `rustfs`。
- 修改 `onlineIM-server/src/main/java/icu/tianqingyuluo/onlineim/config/OSSConfig.java`：将非法 OSS 类型的回退目标改为 `RustFS`，与新的本地默认环境一致。
- 修改 `onlineIM-server/src/main/resources/application.properties`、`onlineIM-server/src/main/resources/application-oss-example.properties`：默认 OSS 类型切换为 `rustfs`，默认访问密钥改为 `rustfsadmin`，并显式声明本地 `MongoDB` 连接 URI。
- 新增 `local-dev.middleware.yml`：补充本地联调所需的 `MySQL`、`Redis`、`MongoDB`、`RustFS` 编排，便于后续一键起依赖。
- 修改 `.trellis/spec/backend/database-guidelines.md`：补充 Mongo 仓库存在时必须显式配置 `spring.data.mongodb.uri` 的规范，避免默认值导致首个查询超时后被前端误判为跨域问题。

## 关联任务

- Trellis 任务：`06-26-fix-cors-request-errors`

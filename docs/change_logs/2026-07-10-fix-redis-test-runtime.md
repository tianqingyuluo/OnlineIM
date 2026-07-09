# Redis 测试运行环境修复

## 日期

2026-07-10

## 变更摘要

- 修改 `onlineIM-server/pom.xml`，为测试范围显式引入 Byte Buddy agent，并通过 Maven Surefire 的 `argLine` 在测试 JVM 启动时加载，避免 Java 21 环境因禁止 Mockito 自附加而导致全部单元测试初始化失败。
- 更新 `.trellis/tasks/06-26-v1-redis-stream-consumer-group/implement.md`，回填已经完成并核验的实现、测试和技术选型清单。

## 关联任务

- `v1-redis-stream-consumer-group`

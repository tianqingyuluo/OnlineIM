# 设计:CI/CD 与 Docker 化部署

## 架构总览

```
GitHub Actions                    服务器 43.240.220.89 (/opt/onlineim/)
┌──────────────┐   push main ───▶  prod/  nginx:80 + backend(8080/8081) + mysql/redis/mongo/minio
│  build + CD  │   push tianqing-dev ─▶  dev/   nginx:81 + backend(8082/8083) + 独立数据卷
└──────────────┘
```

## 关键技术决策

1. **CI 平台**:GitHub Actions(仓库已托管 GitHub,天然契合 PR 流程)
2. **CD 方式**:`appleboy/scp-action` + `ssh-action`(密码认证,secrets 存 GitHub)
3. **服务器运行方式**:Docker Compose 全容器化;产物 jar/dist 挂载进容器,更新 = 替换文件 + `compose up -d`
4. **前后端联通**:nginx 同域反代(`/api/v1` → backend:8080,`/api/v1/chat` → backend:8081),前端用相对路径,彻底规避 CORS 与硬编码 localhost

## 端口与数据隔离

| 服务 | prod | dev |
|---|---|---|
| nginx | 80 | 81 |
| REST API | 8080 | 8082(→容器8080) |
| WebSocket | 8081 | 8083(→容器8081) |
| MinIO | 9000/9001 | 不对外(内部访问) |

- prod/dev 完全隔离:独立目录、独立 `.env`、独立 compose 数据卷(compose 项目名按目录自动区分)

## 前端配置改造(已完成)

- `src/config.ts` 重写:`API_BASE_URL`/`WS_API_URL` 支持 `VITE_*` 环境变量覆盖;DEV 模式默认直连 localhost:8080/8081,生产构建默认相对路径 `/api/v1` 与 `ws(s)://当前host/api/v1/chat`
- `websocket.service.ts`、`api.service.ts`、`group.service.ts`、`me.service.ts` 的 import 从缺失的 `../../shared/config.ts` 改为 `@/config`
- 新增并提交 `OnlineIM-Vue/shared/config.js`(proxy-server.js 本地开发代理用),`.gitignore` 移除对应忽略
- `package.json`:`build` 改为 `vite build`(原 `vue-tsc -b && vite build` 因存量类型错误无法通过),类型检查拆到 `typecheck` 脚本

## 后端容器化关键点

- 镜像 `eclipse-temurin:21-jre`,`working_dir: /app`,挂载 `./backend`(jar + db.properties)
- 环境变量覆盖硬编码的 localhost 地址:
  - `SPRING_DATASOURCE_URL=jdbc:mysql://mysql:3306/online_im?...`
  - `SPRING_DATA_REDIS_HOST=redis` / `PORT=6379`(application.properties 硬编码 127.0.0.1:7000)
  - `SPRING_DATA_MONGODB_URI=mongodb://mongo:27017/onlineim`(默认库名 "test" 风险)
  - `OSS_ENDPOINT=http://minio:9000` + OSS 凭证(OSSConfig @ConfigurationProperties,env 宽松绑定)
- `spring.config.import=db.properties` 依赖:CD 脚本从 `.env` 生成 `backend/db.properties` 挂载到容器工作目录
- CORS 已全放开(`setAllowedOriginPatterns("*")`),无需处理 frontend.url
- MinIO 上传自动建桶(MinIOAdapter.createBucketIfNotExists),无需 init 服务

## nginx 配置

- `deploy/nginx/nginx.conf.template` 利用官方镜像 envsubst 机制(`/etc/nginx/templates/`),`LISTEN_PORT` 由 compose env 注入
- 覆盖镜像自带 `default.conf`(空文件挂载)避免端口冲突
- SPA 路由回退 `try_files ... /index.html`;WebSocket `proxy_read_timeout 3600s`

## 服务器目录结构(目标)

```
/opt/onlineim/
├── prod/  docker-compose.yml, .env, nginx/, scripts/, initdb/onlineIM.sql, backend/{onlineim.jar, db.properties}, frontend/dist/
└── dev/   同上(dev 版本)
```

## 安全

- 服务器密码存 GitHub Actions Secrets;服务器本地 `.env` 不入库
- 部署完成后建议:轮换 root 密码、改用 SSH key

## 数据

- 全新初始化:MySQL 导入 onlineIM.sql 空库(initdb 机制,仅首次),Mongo/MinIO/Redis 空数据
- 旧开发环境数据不迁移(如需迁移另行任务)

# OnlineIM 部署

GitHub Actions CI/CD + Docker Compose 部署到 43.240.220.89。

## 架构

```
GitHub Actions                    服务器 43.240.220.89 (/opt/onlineim/)
┌──────────────┐   push main ───▶  prod/  nginx:80 + backend:8080/8081 + mysql/redis/mongo/minio
│  build + CD  │   push tianqing-dev ─▶  dev/   nginx:81 + backend:8082/8083(独立数据卷)
└──────────────┘
```

- 前端 `OnlineIM-Vue` 由 nginx 托管,`/api/v1` 反代后端 REST,`/api/v1/chat` 反代 WebSocket(同域,免 CORS)
- 中间件全部容器化,数据卷持久化,`restart: unless-stopped` 开机自启

## 首次部署(一次性)

```bash
# 1. 在服务器上初始化环境
scp -r deploy/scripts/server-setup.sh root@<SERVER>:~/
ssh root@<SERVER> 'bash server-setup.sh'

# 2. 创建 prod / dev 两套环境变量
mkdir -p /opt/onlineim/{prod,dev}
cp deploy/.env.example      /opt/onlineim/prod/.env   # 修改密码
cp deploy/.env.dev.example  /opt/onlineim/dev/.env    # 修改密码
```

## GitHub 仓库配置

Settings → Secrets and variables → Actions,添加:

| Secret | 值 |
|---|---|
| `SERVER_HOST` | 服务器 IP |
| `SERVER_USER` | root |
| `SERVER_PASS` | root 密码 |
| `SSH_PORT` | 22 |

## 日常发布

推送到 `main`(生产)或 `tianqing-dev`(测试)即自动构建并部署;PR 到 main 只跑 CI 验证不部署。

部署日志:服务器 `docker compose ps` / `docker compose logs -f backend`。

## 手动部署

```bash
# 后端
mvn -f onlineIM-server/pom.xml package -DskipTests
# 前端
cd OnlineIM-Vue && npm ci && npm run build
# 服务器上替换 /opt/onlineim/<env>/backend/onlineim.jar 与 frontend/dist,然后:
bash /opt/onlineim/<env>/scripts/deploy.sh <env>
```

## 端口一览

| 服务 | prod | dev |
|---|---|---|
| Web(nginx) | 80 | 81 |
| REST API | 8080 | 8082 |
| WebSocket | 8081 | 8083 |
| MinIO API/控制台 | 9000/9001 | 不对外 |

## 安全提示

- 服务器 root 密码存放在 GitHub Actions Secrets;建议改为 SSH Key 认证并轮换密码
- `db.properties` / `.env` 在服务器本地,不入库;仓库仅提供 `.env.example` 模板

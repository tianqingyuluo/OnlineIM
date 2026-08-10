# 执行计划与进度跟踪

> **状态:WIP(已暂停,等待迁移设备接续)**
> 最后更新:2026-08-10(本机 tianqing-dev 分支,尚未推送远程)

## 执行清单

### 阶段 A:前端配置修复 ✅ 完成(本地已验证)

- [x] 重写 `OnlineIM-Vue/src/config.ts`:VITE_* 环境变量覆盖 + DEV 直连 / 生产相对路径
- [x] 4 个 service 文件 import 改为 `@/config`:`api.service.ts`、`group.service.ts`、`me.service.ts`、`websocket.service.ts`
- [x] 新建 `OnlineIM-Vue/shared/config.js`(proxy-server.js 用);`.gitignore` 移除 `shared/config.js`
- [x] `package.json` build 脚本改为 `vite build`(vue-tsc 存量类型错误,拆出 `typecheck` 脚本)
- [x] 本地验证:`npm run build` 产出 dist ✓(顺带补装本地缺失的 idb 依赖,lock 无 diff)
- [x] 本地验证:`./mvnw package -DskipTests` 产出 jar ✓

### 阶段 B:部署文件 ✅ 完成(未在服务器落地)

- [x] `deploy/docker-compose.yml`(prod)+ `deploy/docker-compose.dev.yml`(dev):mysql:8.4 / redis:7 / mongo:7 / minio / eclipse-temurin:21-jre backend / nginx:1.27-alpine
- [x] `deploy/nginx/nginx.conf.template`(envsubst 模板,LISTEN_PORT 注入)+ `default.conf` 空文件覆盖
- [x] `deploy/.env.example`、`deploy/.env.dev.example`、`deploy/README.md`
- [x] `deploy/scripts/server-setup.sh`(装 Docker + compose 插件 + 建目录)
- [x] `deploy/scripts/deploy.sh`(CD 脚本:同步文件 → 替换产物 → 生成 db.properties → compose up → 健康检查)
- [x] `bash -n` 语法校验通过

### 阶段 C:workflow ✅ 完成(未验证)

- [x] `.github/workflows/deploy.yml`:build(java21+maven缓存 / node20+npm缓存)+ deploy matrix(prod/dev)
- [ ] 注意:matrix 里 `branch` 字段仅作标识,实际分支过滤靠外层 if 条件;若后续需要"main 才部署 prod",应改为显式判断(当前 prod/dev 各由各自分支触发,与 matrix 绑定无碍)

### 阶段 D:服务器初始化 ⏸️ 进行中(暂停点)

- [x] 服务器现状确认:Debian 12 bookworm、无 Docker、3.8G 内存、58G 磁盘、仅 22 端口开放
- [x] 已执行 `curl -fsSL https://get.docker.com | sh`(nohup 后台跑,日志 /tmp/docker-install.log)
- [ ] **确认 Docker 安装完成**(断点:安装进行中时会话暂停)
  - 检查:`docker --version && docker compose version`
  - 若失败:查看 `/tmp/docker-install.log` 尾部,重跑 `sh /tmp/get-docker.sh`
  - 若成功:继续 server-setup.sh 的后续步骤(或直接跳过,手动执行等效命令)
- [ ] 创建 `/opt/onlineim/{prod,dev}` 并初始化 `.env`(密码从 `deploy/.env.example` / `.env.dev.example` 复制修改)

### 阶段 E:首次部署(未开始)

- [ ] 上传 `deploy/` 全套 + jar + dist + `onlineIM.sql` 到 `/opt/onlineim/prod/`
- [ ] 首次跑 `bash scripts/deploy.sh prod`(会放置 initdb/onlineIM.sql 并首次导入建表)
- [ ] 验证:nginx `http://43.240.220.89` 200、`docker compose ps` 全 healthy/Up、后端日志无异常、WebSocket 可连

### 阶段 F:GitHub Secrets + 流水线验证(未开始)

- [ ] Secrets:`SERVER_HOST=43.240.220.89`、`SERVER_USER=root`、`SERVER_PASS=<密码>`、`SSH_PORT=22`(本机无 gh CLI,需网页手动配置或新设备装 gh)
- [ ] 推送 main / tianqing-dev 触发 workflow,观察 build + deploy 全绿
- [ ] 浏览器端到端:登录、收发消息(验证 WS 反代)

### 阶段 G:收尾(未开始)

- [ ] 按 trellis 流程 `task.py start` → `finish` → `archive`
- [ ] 安全提醒:轮换服务器 root 密码、可选改 SSH key

## 验证命令速查

```bash
# 本地构建(已验证)
cd OnlineIM-Vue && npm run build && cd ../onlineIM-server && ./mvnw package -DskipTests
# 服务器 Docker 就绪检查
docker --version && docker compose version
# 部署(服务器上)
cd /opt/onlineim/prod && bash scripts/deploy.sh prod
# 健康检查
docker compose ps
curl -s -o /dev/null -w '%{http_code}' http://localhost:80/
docker compose logs -f backend
```

## 交接备注(迁移设备)

- 分支 `tianqing-dev`,本次改动**未推送远程**,新设备 clone 后需包含本次 WIP 提交
- 本机辅助工具:`.trellis/workspace/tianqingyuluo/ssh_exec.py`(paramiko 免交互 SSH 执行,密码硬编码在脚本内,用后删除;新设备需 `pip install paramiko`)
- 服务器已执行过 get-docker.sh 后台安装,新设备接手第一步就是确认 Docker 是否装好(见阶段 D 断点)

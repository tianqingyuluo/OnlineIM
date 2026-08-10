# 配置CI/CD与Docker化部署

## Goal

为 OnlineIM 配置 GitHub Actions CI/CD 与 Docker Compose 部署(prod/dev 双环境),目标服务器 43.240.220.89。

## Requirements

- 前端 `OnlineIM-Vue` + 后端 `onlineIM-server` 均支持 CI 构建,产出 jar 与 dist
- GitHub Actions 流水线:push/PR 到 `main` → 构建并部署生产(80/8080/8081);push 到 `tianqing-dev` → 构建并部署测试环境(81/8082/8083);PR 到 main 仅 CI 不部署
- 服务器全容器化:MySQL、Redis、MinIO、MongoDB、后端、Nginx 前端,数据卷持久化,开机自启
- nginx 统一入口:静态文件 + `/api/v1` 反代 REST + `/api/v1/chat` 反代 WebSocket(同域,免 CORS)
- 敏感配置(数据库密码、OSS 凭证)只存在于服务器 `.env`,仓库仅提供 `.env.example` 模板
- MySQL 首次启动自动导入 `onlineIM.sql` 建表

## Acceptance Criteria

- [x] 前端 `npm run build` 在 CI 上可产出 dist(修复 shared/config 缺失引用)
- [x] 后端 `mvn package` 可产出 jar
- [x] `deploy/` 目录含 compose(prod/dev)、nginx 模板、部署脚本、env 模板
- [x] `.github/workflows/deploy.yml` 已编写(build + matrix deploy)
- [ ] 服务器 43.240.220.89 安装 Docker 完成
- [ ] 首次部署 prod 成功:nginx 200、后端健康、WebSocket 可连
- [ ] GitHub Secrets(SERVER_HOST/USER/PASS/SSH_PORT)配置完成
- [ ] 推送 main/tianqing-dev 触发流水线全绿,浏览器端到端验证通过

## Notes

- 状态:**进行中(WIP)**,因迁移设备暂停;实施进度详见 `implement.md`
- 服务器 root 密码已出现在对话中,部署完成后建议轮换密码并改用 SSH key
- 旧开发环境(局域网 10.191.x.x)数据不迁移,新部署全空库初始化

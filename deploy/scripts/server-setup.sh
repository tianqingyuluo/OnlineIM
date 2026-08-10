#!/usr/bin/env bash
# ============================================
# OnlineIM 服务器初始化脚本(一次性)
# 功能: 安装 Docker + compose 插件、创建部署目录
# 用法: sudo bash server-setup.sh
# ============================================
set -euo pipefail

echo ">>> [1/4] 检查 Docker..."
if ! command -v docker >/dev/null 2>&1; then
  echo ">>> 安装 Docker..."
  curl -fsSL https://get.docker.com | sh
fi

echo ">>> [2/4] 启动 Docker 并设置开机自启..."
systemctl enable --now docker 2>/dev/null || service docker start 2>/dev/null || true

echo ">>> [3/4] 检查 docker compose 插件..."
if ! docker compose version >/dev/null 2>&1; then
  echo ">>> 安装 docker compose 插件..."
  apt-get update -y && apt-get install -y docker-compose-plugin
fi
docker compose version

echo ">>> [4/4] 创建部署目录..."
mkdir -p /opt/onlineim/prod /opt/onlineim/dev
echo ">>> 完成!"
echo ">>> 部署目录: /opt/onlineim/{prod,dev}"
echo ">>> 下一步:"
echo ">>>   1. 分别初始化 /opt/onlineim/{prod,dev}/.env(参考 deploy/.env.example / .env.dev.example)"
echo ">>>   2. GitHub 仓库配置 Secrets: SERVER_HOST / SERVER_USER / SERVER_PASS"
echo ">>>   3. 推送 main / tianqing-dev 分支触发 CI/CD 自动部署"

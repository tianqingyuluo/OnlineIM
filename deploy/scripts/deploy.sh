#!/usr/bin/env bash
# ============================================
# OnlineIM CD 部署脚本(在服务器上由 CI 触发执行)
# 功能: 用 staging 目录中的新产物更新服务并重启
# 用法: bash deploy.sh <prod|dev>
# 前置: /opt/onlineim/<env>/.env 已初始化
# ============================================
set -euo pipefail

ENV="${1:?usage: bash deploy.sh <prod|dev>}"
BASE="/opt/onlineim/$ENV"
cd "$BASE"

# 0. 校验环境变量文件已初始化
if [ ! -f .env ]; then
  echo "ERROR: $BASE/.env 不存在,请先参考 deploy/.env.example 初始化" >&2
  exit 1
fi

STAGING="staging/$ENV"
if [ ! -d "$STAGING" ]; then
  echo "ERROR: 未找到暂存产物 $BASE/$STAGING" >&2
  exit 1
fi

echo ">>> [$ENV] [1/5] 同步基础设施文件(compose / nginx / scripts)..."
[ -f "$STAGING/docker-compose.yml" ] && cp -f "$STAGING/docker-compose.yml" ./docker-compose.yml
if [ -d "$STAGING/nginx" ]; then
  mkdir -p nginx
  cp -rf "$STAGING/nginx/." nginx/
fi
if [ -d "$STAGING/scripts" ]; then
  mkdir -p scripts
  cp -rf "$STAGING/scripts/." scripts/
fi
if [ -f "$STAGING/onlineIM.sql" ] && [ ! -f initdb/onlineIM.sql ]; then
  # 仅首次部署导入建表 SQL;后续数据库结构变更需手动处理
  mkdir -p initdb
  cp -f "$STAGING/onlineIM.sql" initdb/onlineIM.sql
  echo ">>> 首次部署: 已放置建表脚本 initdb/onlineIM.sql"
fi

echo ">>> [$ENV] [2/5] 替换后端 jar 与前端静态文件..."
mkdir -p backend frontend/dist
if [ -f "$STAGING/backend/onlineim.jar" ]; then
  mv -f backend/onlineim.jar backend/onlineim.jar.old 2>/dev/null || true
  mv -f "$STAGING/backend/onlineim.jar" backend/onlineim.jar
  rm -f backend/onlineim.jar.old
fi
if [ -d "$STAGING/frontend" ] && [ -n "$(ls -A "$STAGING/frontend" 2>/dev/null)" ]; then
  rm -rf frontend/dist
  mv "$STAGING/frontend" frontend/dist
fi

echo ">>> [$ENV] [3/5] 生成 db.properties..."
set -a
# shellcheck disable=SC1091
source .env
set +a
cat > backend/db.properties <<EOF
db.username=root
db.password=${MYSQL_ROOT_PASSWORD}
db.redis.host=redis
db.redis.port=6379
db.redis.database=0
db.redis.password=
EOF

echo ">>> [$ENV] [4/5] 启动/更新容器..."
docker compose up -d --remove-orphans

echo ">>> [$ENV] [5/5] 健康检查..."
sleep 8
for i in $(seq 1 12); do
  CODE=$(curl -s -o /dev/null -w '%{http_code}' "http://localhost:${LISTEN_PORT:-80}/" || true)
  if [ "$CODE" = "200" ]; then
    echo ">>> [$ENV] 部署成功: nginx 返回 $CODE"
    rm -rf "$STAGING"
    docker compose ps
    exit 0
  fi
  echo ">>> 等待 nginx 就绪... ($CODE)"
  sleep 5
done

echo ">>> [$ENV] ERROR: nginx 未就绪,部署失败" >&2
docker compose ps >&2 || true
docker compose logs --tail 50 backend >&2 || true
exit 1

#!/usr/bin/env bash
set -euo pipefail

# 本地 SSO 联调一键脚本（issue #16）。
# 起 PG+Redis，然后打印三个进程的启动命令（各自开一个终端跑）。

echo "==> 启动 PG + Redis（docker compose）..."
docker compose up -d
echo "==> 基建就绪。分别在三个终端执行："
echo
echo "  [identity]  cd $(pwd) && mvn spring-boot:run -Dspring-boot.run.profiles=local -Dspring-boot.run.jvmArguments=--enable-preview"
echo "  [bff]       cd $(pwd)/demo/demo-backend && mvn spring-boot:run -Dspring-boot.run.profiles=local -Dspring-boot.run.jvmArguments=--enable-preview"
echo "  [web]       cd $(pwd)/demo/demo-web && pnpm install && pnpm dev"
echo
echo "==> 访问 http://demo.localhost:3000 （点「登录」走完整 SSO 闭环）"
echo "==> 详见 docs/guide/local-sso-debugging.md"

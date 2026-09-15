#!/usr/bin/env bash
# build.sh — 用项目内 JDK/Gradle 构建 Debug APK。
# 依赖首次会全部下载到仓库内 .gradle-home/（约 10-20 分钟），之后走缓存。
# 用法：bash scripts/build.sh [gradle任务...]  （缺省为 assembleDebug；如 bash scripts/build.sh help）
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=env.sh
source "$SCRIPT_DIR/env.sh"

cd "$REPO_ROOT"

TASKS=("$@")
if [ ${#TASKS[@]} -eq 0 ]; then
  TASKS=(assembleDebug)
fi

echo "[build] gradle ${TASKS[*]} --no-daemon"
"$GRADLE_CMD" --no-daemon "${TASKS[@]}"

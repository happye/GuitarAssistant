#!/usr/bin/env bash
# sync-agent-skills.sh — 把 skills/<name>/SKILL.md 主副本同步到各 Agent 工具的自动发现目录。
# 背景：各工具的 skill 目录互不兼容，只写在某一个目录 = 其他工具永远看不到（见 AGENTS.md 工程纪律/门禁 7）。
# 兜底原则：入口文件（AGENTS.md / CLAUDE.md）里"开工前先读"的引用覆盖面 > 目录自动发现。
# 用法：项目里新增/修改 skills/<name>/SKILL.md 后运行 bash scripts/sync-agent-skills.sh
set -euo pipefail
cd "$(dirname "$0")/.."

SRC="skills"   # 主副本目录（工具中立，任何工具都能直接读）
DESTS=(".claude/skills" ".github/skills" ".codex/skills" ".cursor/rules")

if [ ! -d "$SRC" ]; then
  echo "no skills/ directory yet — nothing to sync (create skills/<name>/SKILL.md first)"
  exit 0
fi

for skill in "$SRC"/*/; do
  name=$(basename "$skill")
  for dest in "${DESTS[@]}"; do
    mkdir -p "$dest/$name"
    cp -r "$skill." "$dest/$name/"
  done
done
echo "synced: $(ls "$SRC")"

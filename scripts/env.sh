#!/usr/bin/env bash
# env.sh — 项目内虚拟环境：JDK 17、Gradle、依赖缓存全部留在仓库目录内，不污染本机全局。
# 用法：source scripts/env.sh （可从任意工作目录 source；路径均按仓库根起算）
# 仅在本 shell 进程内生效：JAVA_HOME / GRADLE_USER_HOME 不写注册表、不写全局配置。

# 仓库根 = 本脚本所在 scripts/ 目录的上一级
if [ -z "${BASH_SOURCE[0]:-}" ]; then
  echo "[env.sh] 请以 source 方式使用：source scripts/env.sh" >&2
  return 1 2>/dev/null || exit 1
fi
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
export REPO_ROOT

# --- 项目内 JDK 17（自动适配解压出的 jdk-17.x.y+z 目录名）---
JDK_DIR="$(ls -d "$REPO_ROOT"/toolchain/jdk-17* 2>/dev/null | head -n1)"
if [ -n "$JDK_DIR" ] && [ ! -e "$JDK_DIR/bin/java.exe" ]; then JDK_DIR=""; fi
if [ -z "$JDK_DIR" ]; then
  echo "[env.sh] 未找到项目内 JDK 17：$REPO_ROOT/toolchain/jdk-17*/bin/java.exe" >&2
  echo "[env.sh] 请先解压 Temurin JDK 17 zip 到 toolchain/ 下" >&2
  return 1 2>/dev/null || exit 1
fi
export JAVA_HOME="$JDK_DIR"
export PATH="$JDK_DIR/bin:$PATH"

# --- 项目内 Gradle 8.13 ---
GRADLE_DIR="$REPO_ROOT/toolchain/gradle/gradle-8.13"
if [ ! -e "$GRADLE_DIR/bin/gradle.bat" ]; then
  echo "[env.sh] 未找到项目内 Gradle：$GRADLE_DIR/bin/gradle.bat" >&2
  return 1 2>/dev/null || exit 1
fi
export GRADLE_HOME="$GRADLE_DIR"
export PATH="$GRADLE_DIR/bin:$PATH"

# Gradle 启动器：优先用 POSIX 启动脚本（Git Bash 下更稳），缺失时退回 gradle.bat
if [ -e "$GRADLE_DIR/bin/gradle" ]; then
  GRADLE_CMD="$GRADLE_DIR/bin/gradle"
else
  GRADLE_CMD="$GRADLE_DIR/bin/gradle.bat"
fi
export GRADLE_CMD

# --- 依赖缓存收进仓库（.gradle-home/ 已被 .gitignore 排除）---
export GRADLE_USER_HOME="$REPO_ROOT/.gradle-home"

echo "[env.sh] REPO_ROOT=$REPO_ROOT"
echo "[env.sh] JAVA_HOME=$JAVA_HOME"
echo "[env.sh] GRADLE_CMD=$GRADLE_CMD"
echo "[env.sh] GRADLE_USER_HOME=$GRADLE_USER_HOME"

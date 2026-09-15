#!/usr/bin/env bash
# run-tests-local.sh — L012 本地单测绕法的产品化：编译产物拷到 ASCII 临时目录跑 JUnitCore。
# 用法：bash scripts/run-tests-local.sh [测试类全名...]（缺省自动发现全部 *Test）
# 说明：main/test 产物每次成对全新拷贝（L013：只刷一半会跑旧产物，浪费排查时间）。
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=env.sh
source "$SCRIPT_DIR/env.sh"
cd "$REPO_ROOT"

# 1) 编译主代码与单测
"$GRADLE_CMD" --no-daemon compileDebugUnitTestKotlin

# 2) 成对拷贝到全新 ASCII 临时目录（中文路径 + GBK @argfile 是跑不了的根因，见 .learnings L012）
#    mktemp 给的是 POSIX 路径，java.exe 读不了 → cygpath -m 转成 Windows 混合斜杠形式
TMPD="$(cygpath -m "$(mktemp -d)")"
trap 'rm -rf "$(cygpath -u "$TMPD")"' EXIT
cp -r app/build/tmp/kotlin-classes/debug "$TMPD/main"
cp -r app/build/tmp/kotlin-classes/debugUnitTest "$TMPD/test"

# 3) 从 .gradle-home 缓存收集运行时 jar（取版本号最大的；相对路径，java.exe 以仓库为 CWD 可读）
CACHE=".gradle-home/caches/modules-2/files-2.1"
pick() { find "$CACHE/$1" -name "$2" 2>/dev/null | sort | tail -n1; }
JARS=(
  "$(pick junit/junit 'junit-4*.jar')"
  "$(pick org.hamcrest/hamcrest-core 'hamcrest-core-*.jar')"
  "$(pick org.jetbrains.kotlin/kotlin-stdlib 'kotlin-stdlib-2*.jar')"
  "$(pick org.jetbrains.kotlinx/kotlinx-coroutines-core-jvm 'kotlinx-coroutines-core-jvm-*.jar')"
  "$(pick org.jetbrains.kotlinx/kotlinx-serialization-core-jvm 'kotlinx-serialization-core-jvm-*.jar')"
  "$(pick org.jetbrains.kotlinx/kotlinx-serialization-json-jvm 'kotlinx-serialization-json-jvm-*.jar')"
)
for j in "${JARS[@]}"; do
  [ -n "$j" ] || { echo "[run-tests] 缺少运行时 jar：$1" >&2; exit 1; }
done

# 4) 测试类：参数优先，否则从源码树自动发现
if [ $# -gt 0 ]; then
  TESTS=("$@")
else
  TESTS=()
  while IFS= read -r f; do
    rel="${f#app/src/test/java/}"; rel="${rel%.kt}"; TESTS+=("${rel//\//.}")
  done < <(find app/src/test/java -name '*Test.kt' | sort)
fi
[ ${#TESTS[@]} -gt 0 ] || { echo "[run-tests] 没有找到测试类" >&2; exit 1; }

CP="$TMPD/main;$TMPD/test;$(IFS=';'; echo "${JARS[*]}")"
echo "[run-tests] JUnitCore: ${TESTS[*]}"
exec "$JAVA_HOME/bin/java.exe" -cp "$CP" org.junit.runner.JUnitCore "${TESTS[@]}"

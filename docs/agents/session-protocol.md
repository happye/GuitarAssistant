# 会话协议 — 上下文重置 > 压缩

> 完全清除上下文 + 新 Agent + 结构化交接，优于在同一 Agent 里压缩。压缩会让模型陷入"怕遗漏"的上下文焦虑，表现下降。
> 三个交接 artifact：`progress.md` + `feature_list.json` + `git log`（描述性提交信息本身就是交接文档）。

## 会话启动协议（每次 Agent 会话开始执行）

1. `pwd` → 确认在 G:\Tools\ClaudeCodeRepo\吉他演奏学习助手
2. `git log --oneline -10` → 了解最近 10 次提交
3. 读 `progress.md` → 当前特性状态与 Blockers
4. 读 `feature_list.json` → 选定本会话唯一的 pending 特性
5. 环境检查（Android 语境，替代 init.sh/dev server——原生 App 无常驻服务与 /health 端点）：
   - `./scripts/build.sh assembleDebug` 基线构建绿
   - `adb devices` 设备在线；不在线则本会话限 JVM 单测可验证的工作并如实说明
6. 冒烟：装机后跑一遍所改路径的最短用户流程，确认基线正常再动手

## 会话结束协议（每次会话收尾执行）

1. `git add -A`（先 `git check-ignore local.properties` 确认密钥文件未被跟踪）
2. `git commit -m "feat([特性ID]): 中文描述"`
3. 更新 `progress.md`：本会话完成了什么 / 当前特性状态（in_progress 或 completed）/ 下一步建议
4. 特性完成且验证通过：`feature_list.json` 中标 `status=done`、`passes=true`（附完成日期）
5. 记忆沉淀（AGENTS.md「记忆纪律」）：新教训 → `.learnings/LEARNINGS.md`（带防复发措施）；失败/复现条件 → `.learnings/ERRORS.md`

## 交接质量标准

- progress.md 一条记录能让新 Agent 不问任何问题就开工：做了什么、怎么验证的、卡在哪、下一步做什么
- 提交信息遵循 `feat([特性ID]): 描述` / `wip([特性ID]): 描述 + 未完成点`，让 git log 可读
- 相对日期一律转绝对日期（"今天" → "2026-09-15"）

## 一次一个特性

每次会话只做一个特性：避免上下文耗尽留下半成品、确保每个特性得到充分关注、符合增量交付。发现的额外问题记 `docs/exec-plans/tech-debt.md`，不顺手扩工。

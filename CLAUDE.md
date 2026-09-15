# CLAUDE.md — 吉他学习助手（GuitarCoach）

> 本文件只包含 Claude Code 特有配置。通用规范（架构红线 / 协作约定 / 工程纪律）见 ./AGENTS.md，不在此重复。

## 开工检查

- **新对话/交接：先读根目录 [HANDOFF.md](HANDOFF.md)**（状态快照 + 多 Agent 机制 + 信息地图），再走下面三步
- 先读 ./AGENTS.md，尤其「架构红线」「工程纪律」两节；会话协议见 docs/agents/session-protocol.md
- 会话开始三件事：`git log --oneline -10` → 读 progress.md → 读 feature_list.json 选一个 pending 特性（一次只做一个）
- 构建/测试统一走 ./scripts/build.sh（用法见 AGENTS.md「开发环境」）；该脚本缺位时先用 `./gradlew` 并在报告里说明

## 工作流

- 新功能开发前先进 plan mode（Shift+Tab）确认方案再动手；破坏性 / 不可逆操作必须先问用户
- 大任务拆子任务；每个 sprint 开始前按 docs/agents/README.md 的合同机制与 Evaluator 协商完成标准
- 重大变更前先 git commit 当前干净状态（人工 checkpoint）

## 上下文管理

- 上下文接近限制：结束当前特性 → 更新 progress.md → 开新会话交接，不做压缩式硬撑
- 独立子任务（调研、扫描同类点、lint 清查）交给 subagent，主上下文只留结论
- 会话收尾按 AGENTS.md「记忆纪律」落 `.learnings/`，这是流程不是自觉

## Android 反馈工具（本项目无浏览器可自动化，Playwright 不适用）

- UI 验证：真机/模拟器 `adb exec-out screencap -p` 截图回读；`adb shell uiautomator dump` 查控件树
- 运行日志：`adb logcat -s GuitarCoach`（结构化 tag 约定见 docs/references/coding-standards.md）
- 测试：`./scripts/build.sh test`（JVM 单测，CI 为准）；本地跑单测用 `bash scripts/run-tests-local.sh`（L012 绕法已产品化，自动发现全部 *Test；相机/音频冒烟走 `connectedDebugAndroidTest`）

## 渐进式扩展触发规则

- 两次搞错同一规范 → 加入 AGENTS.md 或 docs/references/ 对应文档
- 反复输入同一 prompt → 存为项目 skill（skills/<name>/SKILL.md），并跑 scripts/sync-agent-skills.sh
- 数据在应用外不可见 → 评估接入对应 MCP server（Android 场景常用 adb / 文件系统类）
- 每次自动发生 → 写 Hook（settings.json）

## 禁止事项

- 不要跳过测试直接提交；不要在未跑验证时声称"完成"
- 不要修改 local.properties、gradle 配置、docs/开发方案.md、docs/模型API接入手册.md（历史信源，改动需用户确认）
- 不要把 API Key / 密钥写进任何被 git 跟踪的文件

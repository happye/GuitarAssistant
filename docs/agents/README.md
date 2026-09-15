# Agent 架构 — Planner / Generator / Evaluator

> 基于 GAN 思想的三 Agent 体系：Generator 与 Evaluator 对抗提升，Planner 负责把需求扩成规格。
> Agent 之间**只通过文件通信**（docs/exec-plans/、feature_list.json、progress.md），不直接对话，避免上下文污染。

## 三角色总览

| 角色 | 职责 | 输入 → 输出 | 提示词 |
|---|---|---|---|
| Planner | 把简单需求扩展为完整产品规格 | 1-4 句需求 → docs/product-specs/spec-[主题].md + feature_list.json 更新 | docs/agents/planner.md |
| Generator | 每个 sprint 实现一个特性 | feature_list + progress → 代码 + commit + progress 更新 | docs/agents/generator.md |
| Evaluator | 用真机/测试验证产出并评分 | sprint 产出 → 评分报告 + 反馈（docs/exec-plans/feedback-[sprint-id].md） | docs/agents/evaluator.md |

硬阈值：评估四维（产品深度/功能性/视觉设计/代码质量）每维 ≥7 才通过；低于 7 即 sprint 失败，Generator 读反馈下个 sprint 修复。

## Sprint 合同机制

1. Generator 写 `docs/exec-plans/sprint-[id]-proposal.md`（建什么 + 怎么验证）
2. Evaluator 写 `docs/exec-plans/sprint-[id]-review.md`（是否建正确的东西 + 补充验证标准）
3. 迭代到一致后锁定，Generator 开工；一次一个特性

## Android 语境适配（相对 Web 项目的差异）

- **浏览器自动化 → 设备验证**：无 Playwright/浏览器；端到端验证 = `./scripts/build.sh` 构建产物 + `adb install` + `adb exec-out screencap` 截图回读 + `adb logcat -s GuitarCoach` + Compose/instrumented 测试
- **init.sh 常驻开发服务器 → 无服务器**：原生 App 没有 dev server 与 /health 端点；"环境就绪"的定义 = 构建绿 + 设备在线（`adb devices`）+ 冒烟通过。构建统一走 `./scripts/build.sh`
- **日志/指标暴露 → adb logcat**：结构化 tag `GuitarCoach/<模块>`（见 docs/references/coding-standards.md）；模型链路无 /metrics，成本观测看厂商控制台与月度 token 统计

## 通用红线（所有角色适用）

- 先读根目录 AGENTS.md；架构红线与工程纪律对三个角色同等生效
- 不修改 `docs/开发方案.md`、`docs/模型API接入手册.md`、gradle 配置、local.properties
- 会话收尾按 AGENTS.md「记忆纪律」落 `.learnings/`

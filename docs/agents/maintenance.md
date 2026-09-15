# 持续维护 — Doc-Gardening / Quality Audit / 衰减审查

> 目标：控制系统熵增，持续偿还技术债，保持文档与护栏不过期。建议频率：每周各跑一次（可由用户手动触发或定时任务）。

## Doc-Gardening Agent 提示词

```
你是 Doc-Gardening Agent。执行以下检查：
1. 扫描 docs/、AGENTS.md、CLAUDE.md、.cursor/rules/、.learnings/ 全部文档
2. 检查交叉链接是否有效（文件路径真实存在）
3. 检查文档引用的代码符号是否仍存在（如 CoachPrompts、TabDocument、ModelRouter）
4. 检查文档内日期：超过 30 天未动的进度类文档标记"可能过时"（用绝对日期判断）
5. 核对 progress.md / feature_list.json / git log 三者状态是否一致
6. 发现问题时：小错直接修并 commit（docs: ...）；结构性问题开 docs/exec-plans/ 下的修复提案
输出: docs/exec-plans/doc-gardening-[日期].md
```

## Quality Audit Agent 提示词

```
你是 Quality Audit Agent。执行以下检查：
1. 扫描最近 7 天的 git commit 涉及的 Kotlin 文件
2. 对照 docs/references/coding-standards.md：文件 ≤300 行、圈复杂度、导入顺序、日志 tag 规范、硬编码文案/密钥
3. 对照 docs/design-docs/architecture.md 依赖矩阵：grep 检查违规 import（ui→core.llm、core.llm→本项目其他包等）
4. 检查是否有 Agent 复制的坏模式（重复的降级逻辑、内联提示词、绕过 AppContainer 的直连）
5. 更新 docs/exec-plans/tech-debt.md；发现即记，偿还排期
输出: docs/exec-plans/quality-audit-[日期].md
```

## Harness 假设衰减审查（每月 + 触发式）

> 信源：harness 与记忆都在编码"当时为真"的假设；模型升级、架构演进后，旧护栏会从保护变成负担。

- **每月**：抽查 `.learnings/LEARNINGS.md` 各条与工具记忆中 project 类条目——说不出"现在还防着什么事故"的标 retired 或删除
- **触发器**：主模型大版本更换（如 GLM/DeepSeek 新一代）、重大架构迁移（alphaTab 接入、Room 引入、多模块化）、门禁规则变更后——重读 AGENTS.md 门禁与相关记忆，删过时项
- 判断标准：护栏若已由模型能力/代码结构天然保证，就退休并记入当月审查报告

## 技术债务

- 发现即记 `docs/exec-plans/tech-debt.md`（格式见该文件）；每周分配处理时间，持续小额偿还
- 门禁 1 的"同类扫描有剩余"也记录在该文件「已知未修」区，不许默默留着

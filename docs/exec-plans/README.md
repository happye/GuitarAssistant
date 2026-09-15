# 执行计划目录（docs/exec-plans/）

> Sprint 合同、评估反馈、维护扫描产物的落盘地。全部可追溯，Agent 之间只通过这里的文件通信，不直接对话。

## 文件命名约定

| 文件 | 产出者 | 时机 |
|---|---|---|
| `sprint-[id]-proposal.md` | Generator | 每个 sprint 开工前：建什么、怎么验证 |
| `sprint-[id]-review.md` | Evaluator | 审查提案：是否建正确的东西，补充验证标准 |
| `feedback-[sprint-id].md` | Evaluator | 评估不通过时：逐维度评分 + 具体问题 + 修复建议（模板见 docs/agents/evaluator.md） |
| `doc-gardening-[日期].md` | Doc-Gardening | 每周：过时文档/断链扫描报告（docs/agents/maintenance.md） |
| `quality-audit-[日期].md` | Quality Audit | 每周：编码规范偏差、超 300 行文件扫描报告 |
| `tech-debt.md` | 任何 Agent | 随时：技术债务台账（持续小额偿还） |

## Sprint 合同机制（Generator ↔ Evaluator）

1. Generator 写 `sprint-[id]-proposal.md`：本 sprint 建造什么 + 如何验证
2. Evaluator 读后写 `sprint-[id]-review.md`：审查是否建造正确的东西 + 补充验证标准
3. 双方迭代文件直到一致；合同锁定后 Generator 开工
4. sprint 结束 Evaluator 按 docs/agents/evaluator.md 评分，硬阈值每维 ≥7

## 已知未修（门禁 1 的"同类扫描剩余"记录处）

- 暂无（截至 2026-09-15 harness 初始化）。

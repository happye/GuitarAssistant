# 产品规格目录（docs/product-specs/）

## 信源约定

- **主产品规格 = `docs/开发方案.md`**（痛点调研、竞品、可行性、架构、里程碑——历史信源，不在此重复，也不修改）
- 增量/新主题的产品规格由 Planner Agent 写到本目录：`spec-[主题].md`
- 特性状态唯一信源是根目录 `feature_list.json`；spec 与 feature_list 冲突时，以更新时间新者为准并回写另一份

## Planner 工作流

见 `docs/agents/planner.md`。每次新增/扩展规格后：

1. 同步更新 feature_list.json（保持 JSON 可解析，新特性带 test_criteria）
2. 更新 progress.md 的「下一步」
3. 不改 `docs/开发方案.md`（如需修订总方案，先向用户提出）

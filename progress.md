# Progress Log — 吉他学习助手

> Agent 交接 artifact。每次会话收尾必更新：做了什么 / 当前特性状态 / 下一步建议。
> 特性状态唯一信源是 `feature_list.json`；里程碑范围与验收标准见 `docs/开发方案.md` §9。

## 2026-09-15 - Session: M0 骨架完成（历史，来自 README / docs）

- Completed: F001-F005（M0 全部）——工程骨架与 5 Tab UI、LLM 网关（流式+视觉+路由）、教练编排器三链路、谱面引擎（TabDocument + TextTabParser + LlmTabExtractor）、MPM 调音器
- 验收：App 可安装启动；填 Key 后「测试连通」出 AI 回答；调音器能用（见 docs/开发方案.md §9 M0 行）
- Notes: 模型接入定稿（2026-09-15 真实 key 实测）——DeepSeek 全家桶主力（快答 deepseek-chat / 深思 deepseek-flash / 视觉 deepseek-v4-flash-vision-exp），方舟 GLM 文本备份（id `glm-5-3-flash-260828`，需控制台开通）；链式自动降级；细节见 docs/模型API接入手册.md

## 2026-09-15 - Session: Harness 工程化初始化

- Completed: git 仓库初始化；AGENTS.md / CLAUDE.md / .cursor/rules/、feature_list.json、progress.md、.learnings/、docs/agents/（Planner/Generator/Evaluator/会话协议/维护）、docs/design-docs|references|exec-plans|product-specs、scripts/sync-agent-skills.sh
- Status: 本 session 只做脚手架，未动 app/ 源码
- Notes: feature_list.json 从 README 当前状态表 + 开发方案 §9 生成，M0=done、M1-M5=pending；浏览器自动化适配为 adb 截图/uiautomator（原生 App 无浏览器）

## 2026-09-15 - 当前状态与下一步

- Current feature: F101（乐理聊天页：流式 + 多轮历史）—— M1 首个特性，尚未开始
- Status: pending
- Next: 按 docs/agents/generator.md 流程开工：先读 AGENTS.md 工程纪律 → 环境检查 → 实现前写 sprint 提案（docs/exec-plans/sprint-[id]-proposal.md）
- Blockers:
  1. 本机缺 JDK 17（只有 Java 8）→ 装好后 `gradle wrapper --gradle-version 8.13` 生成 wrapper（gradle/wrapper 目前只有 properties，缺 jar 与 gradlew 脚本，见 docs/exec-plans/tech-debt.md DEBT-002）
  2. `scripts/build.sh`、`scripts/env.sh`、`scripts/test-api.sh` 由并行任务创建中，本 session 未创建（引用但不实现）
  3. M3 前需下载 hand_landmarker.task 放入 app/src/main/assets/（不阻塞 M1）

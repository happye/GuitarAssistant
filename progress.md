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

## 2026-09-15 - Session: F104 + 构建环境攻坚

- Completed: F104 代码（TabStudioScreen 粘贴解析展示）+ TextTabParserTest 4 用例（首次单测，DEBT-001 开账）；wrapper 全套生成（DEBT-002 偿还）；GitHub Actions CI（test + assembleDebug + APK artifact）
- 排障: 本地 test 失败根因定位（实验证实）= L012 中文路径 + JDK 原生层 GBK 读 @argfile；JDK17/21、junction 均不可解 → 单测以 CI 为准
- Status: F104 代码完成，**待 CI 绿后置 done/passes**；F101 代码完成待真机验收（流式+多轮）
- Blockers: 方舟 GLM 待用户控制台开通（文本备份链不可用）；小米 14 真机未连接（装机验收待做）
- Next: F102 乐理概念卡片 / F105 节拍器；M2 前置调研（图片逐句讲解、音频扒谱）由并行调研 agent 进行中

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
- Status: F104 done/passes（CI 绿 + 本地 5/5）；F101 代码完成待真机验收（流式+多轮）；CI 全绿（单测+APK artifact）
- Blockers: 方舟 GLM 待用户控制台开通（文本备份链不可用）；小米 14 真机未连接（装机验收待做）
- Next: F102 乐理概念卡片 / F105 节拍器；M2 前置调研（图片逐句讲解、音频扒谱）由并行调研 agent 进行中

## 2026-09-16 - Session: 留痕体系 + M1 全特性代码完成

- Completed: CHANGELOG + docs/roadmap/（ROADMAP + M0-M6 七文档）+ 版本规则（Mk→v0.(k+1).0）；F107 对话管理（Room v1→v2，监督员 P1 流式删会话容错已修，done）；F102/F103/F105/F106 代码完成（待真机验收）；Room schema 1.json/2.json 入库
- 验证: compileDebugKotlin/assembleDebug 全绿；CI 绿（faf8bd1 单测通过）；APK 33MB
- Status: M1 代码面收口，**里程碑关闭条件 = 真机验收 F101/F102/F103/F105/F106**
- Next: 真机验收（用户连接小米 14）→ M1 收口 v0.2.0 → M2 开工（F201 拍谱识谱优先）

## 2026-09-16 - Session: F201 拍谱识谱（M2 开工）+ 用户反馈 bug 修复

- Completed: F201 代码（系统相机拍照 FileProvider + Photo Picker 选图 → 视觉识谱 → 展示 + AI 讲解流式对话框）；降级原语重构 core/llm/LlmFallback（编排器+识谱管线共用，L010/L011 契约单点维护）；用户反馈 bug 修复：识谱页输入区可滚动 + 粘贴框限高
- 验证: compile/assembleDebug 全绿；本地单测 5/5；CI 绿（650f7ec）；监督员复审通过（P1 类型错配在提交前由 main 修掉、布局修复结构确认）
- Status: F201 代码完成待真机验收；M2 剩余：F202-F206（alphaTab 渲染/GP 导入/移调计算器/逐句讲解 F207-F209）
- Next: F202 谱面渲染（alphaTab 决策点）或 F206 移调计算器（纯 JVM 快交付）

## 2026-09-16 - Session: M2 代码面收口（F202-F209 全部落地）+ F206 done + M3 前置

- Completed: F206 移调计算器（CI 绿 **done**）；F209 指法 DP（CI 绿 **done**，横按物理口径：同品异弦同指=合法横按，集成进 F207）；F204 结构化讲解（识别与讲解分离，explainTabDocument 替代看图讲解）；F203 编辑修正（TabBarEditDialog + withBar 回写，讲解基随修正数据）；F202 谱面渲染/点按试听（自绘 Canvas + TonePlayer；alphaTab 待 gradle 确认）；F208 卡片点读（TTS + PhraseCache 离线缓存 + 原图对照）；M3 前置 hand_landmarker.task 入库 assets（unzip 校验通过）
- 验证: 每特性先本地 JUnitCore（**产品化为 scripts/run-tests-local.sh**，L013：临时目录必须成对刷新）后 CI 绿；单测从 5 → 73 个
- 监督员: 常驻 subagent 本轮抓 6 个实错全部修复——P1×3（FingeringSolver 全空弦槽崩溃、TabEditPanel rows[i] 赋值与 Icons 导入编译错）+ P2×3（TonePlayer audio→tab 依赖红线、tech-debt 编号重号、TTS isReady 非 Compose State）；另促成两处超 300 行文件拆分（PhraseCoach→PhraseExplainModel、DEBT-005 记账）
- Status: M2 代码面 8/9 完成（F205 阻塞）；新特性均待真机验收；feature_list: done 9 项（F001-F005、F104、F107、F206、F209）
- Next: 用户连小米 14 → M1+M2 一起真机验收 → M1 收口 v0.2.0；用户确认 gradle 依赖后做 F205（alphaTab）；然后 M3

# AGENTS.md — 吉他学习助手（GuitarCoach）

> 本文件是 Agent 的单一信源与导航目录（兼容 agents.md 标准）。深层文档在 docs/ 下，本文件只指路不复述。
> 兼容工具：Claude Code / Cursor / Codex / Copilot 等。开工前先读完本文件，再按 `docs/agents/session-protocol.md` 启动会话。

## 项目概述

Android 电吉他自学 App（Kotlin + Jetpack Compose，包名 `com.guitarcoach.app`）：识谱（拍照/GP/文本谱）· 视觉纠手型 · 乐理问答 · 调音。用户 = 项目所有者本人 + 多 AI agents 长期迭代；模型接入方式与排障见 `docs/模型API接入手册.md`（端点与密钥不写入代码/配置/文档）。

## 开发环境

- JDK 21（兼容 17）与 Gradle 8.13 已装在项目内虚拟环境 `toolchain/`（不入库，不影响本机全局）；Android SDK 36 用本机已有安装（`local.properties` 的 `sdk.dir`）
- 构建与测试统一入口：`./scripts/build.sh <gradle 参数>`（如 `./scripts/build.sh assembleDebug`、`./scripts/build.sh test`）；环境变量由 `scripts/env.sh` 提供，勿手工设置全局 JAVA_HOME
- **单元测试本地跑不了 Gradle 的 test 任务（L012：中文路径 + JDK 原生层 GBK 读 @argfile，JDK17/21 均如此）——测试以 GitHub Actions 为准**（push 即触发，见 .github/workflows/android-ci.yml）。本地跑单测：`bash scripts/run-tests-local.sh`（L012 绕法已产品化：成对拷贝产物到 ASCII 目录 + JUnitCore，勿再手工拼 classpath，见 L013）；本地常规验证用 `compileDebugKotlin` / `assembleDebug`
- 目标设备基线：小米 14（arm64-v8a）
- 真机/模拟器反馈：`adb devices` → `adb install -r app/build/outputs/apk/debug/app-debug.apk`；`adb exec-out screencap -p > shot.png` 截图回读；`adb logcat -s GuitarCoach` 看日志
- 当前状态：`progress.md`；特性清单：`feature_list.json`（一次只做一个 pending 特性）

## 目录结构速览

```
app/src/main/java/com/guitarcoach/app/
├── MainActivity.kt / ui/    # Compose 五 Tab + 主题（只依赖 data 与 core 的输出模型）
├── core/llm/                # LLM 网关：ChatModels · OpenAiCompatClient · ModelRouter
├── core/coach/              # 教练编排：CoachPrompts（提示词唯一归属）· CoachFeedback · CoachOrchestrator
├── core/tab/                # 谱面：TabDocument（唯一谱面数据模型）· TextTabParser · LlmTabExtractor
├── core/vision/             # MediaPipe 手部 21 点 · FrameCodec（纯端侧，无 LLM 依赖）
├── core/audio/              # PitchDetector(MPM) · TunerEngine（纯端侧）
└── data/                    # AppContainer（手工 DI）· SettingsStore（DataStore）
```

## 架构红线（不变量，详规见 docs/design-docs/architecture.md）

1. **TabDocument 是谱面唯一数据模型**：文本解析 / GP 导入 / 拍照识谱全部先转 TabDocument；渲染、播放、讲解、记录一律基于它
2. **提示词只放 `core/coach/CoachPrompts`**：任何文件不得内联 system prompt
3. **依赖方向**：`ui → data → core/*`；`core/coach`、`core/tab` 可依赖 `core/llm`；`core/llm`、`core/vision`、`core/audio` 不得反向依赖 coach/tab/ui/data；UI 不直接调 LlmClient/OpenAiCompatClient
4. **模型路由**：多逻辑客户端共享账号、链式降级（当前：DeepSeek 快答/深思/视觉 + 方舟 GLM 文本备份，全部 baseUrl 与模型 id 可在设置页修改）；新增提供方在 `data/AppContainer` 注册 LlmClient 并插入 `ModelRouter` 链，默认优先更便宜的 DeepSeek，链路配置以 `docs/模型API接入手册.md` 最新实测为准（思考模式靠选 id 不靠参数）
5. **许可证**：只引入 Apache/MIT/BSD（MPL-2.0 为文件级 copyleft，引入前单独评估）；GPL/LGPL/AGPL 项目只参考思路，不复制代码
6. **密钥与隐私**：密钥只存 `local.properties`（已 gitignore）与 App 内 DataStore；绝不入库、绝不写进任何被 git 跟踪的文件、绝不外传；谱面图片等用户数据不出设备

## 编码与测试

- 编码规范：`docs/references/coding-standards.md`（单文件 ≤300 行、圈复杂度 ≤10、结构化日志、命名）
- 测试要求：`docs/references/testing-guide.md`（core/* 业务逻辑必须有 JVM 单测；提交前全绿）
- 黄金原则：`docs/references/golden-principles.md`

## 协作约定

- **语言**：会话与 commit message 用中文；代码注释随周围密度
- **交付**：完成 = 验证过。没跑的检查明说；不做"看不见的改动"；局限如实标注。**每笔功能改动收尾必须本地跑 `bash scripts/build.sh assembleDebug` 产出 APK 并向用户报告路径**（APK 是用户的验收物；CI artifact 只是备份，不能替代本地出包）
- **报告**：改了什么 / 为什么 / 怎么验证的，三项齐全；发现的问题直说，不粉饰
- **推送**：已授权任务验证后直接 push，不询问
- **修复**：先立病根假设 → 最小改动验证 → 扫同类点 → 落 `.learnings/`
- **防过度**：动手前先想会不会误伤已跑通逻辑；测试先行而非事后补
- **重大动作**（架构决策、引入新依赖、删数据）先计划确认再动手

## 工程纪律（门禁，违反即返工）

1. **修 bug 四步缺一不提交**：回归测试（先红灯）→ 修复（转绿）→ 全局扫同类点（剩余的记入 `docs/exec-plans/tech-debt.md`「已知未修」）→ 教训落 `.learnings/`
2. **文档动作 ≠ 修复**：diff 里没有改变运行时的代码行 = 没修，不许标完成
3. **验证靠跑不靠读**：每笔改动给一条可复现验证命令 + 期望输出；全局状态类改动必须跑全量测试
4. **提交前自检**：非空代码改动？同类扫描？验证命令？用户可感知？文档同步？教训落库？
5. **第三轮失败原则**：同一方向调 2 次无显著改善即停手换方向；改善须过噪声门槛并设对照
6. **harness 衰减审查**：主模型大版本升级或重大架构迁移后，重读本文件门禁，说不出"还防着什么事故"的护栏退休

## 记忆纪律

- 会话收尾必做：新教训 → `.learnings/LEARNINGS.md`（必须带防复发措施，"以后注意"= 没写）；失败/复现条件 → `.learnings/ERRORS.md`
- 技术债务随时记 `docs/exec-plans/tech-debt.md`；sprint 反馈写 `docs/exec-plans/feedback-[sprint-id].md`
- 项目 skill 主副本放 `skills/<name>/SKILL.md`，改完跑 `scripts/sync-agent-skills.sh` 同步到各工具目录

## 文档索引

- 产品与总方案：`docs/开发方案.md`（痛点 §2、可行性 §5、架构 §7、模块方案 §8、里程碑 §9）
- 模型接入与排障：`docs/模型API接入手册.md`（curl 冒烟测试、两家坑点）
- Agent 工作流：`docs/agents/`（README 三角色总览、planner/generator/evaluator 提示词、session-protocol、maintenance）
- 设计决策：`docs/design-docs/` · 规范：`docs/references/` · 执行计划与反馈：`docs/exec-plans/` · 增量产品规格：`docs/product-specs/`

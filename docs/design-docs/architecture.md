# 架构设计 — 分层规则与依赖不变量

> 2026-09-15（Harness 初始化）起生效。本文档是不变量的信源：定义"什么不能变"，而不是微管理"必须怎么做"。
> 总体架构图与两条核心数据流见 `docs/开发方案.md` §7（历史信源，勿改）；本文负责工程约束的精确化。

## 1. 分层结构（与实际包一一对应）

```
UI      → ui/ + MainActivity         （Jetpack Compose 五 Tab）
Data    → data/                      （AppContainer 手工 DI、SettingsStore/DataStore、Room v2：对话+练习记录+讲解缓存文件）
Coach   → core/coach/                （编排：感知 → 模型 → 输出；提示词唯一归属）
LLM     → core/llm/                  （网关：ChatSpec → ModelRouter → OpenAiCompatClient）
Music   → core/music/                （中立纯乐理：移调计算 / midiToFreq；零依赖，供 UI/data/core 各层共用）
Percep  → core/tab/ core/vision/ core/audio/（谱面 / 视觉 / 音频，端侧为主）
```

依赖只允许自上而下；`Percep` 内部互不依赖（tab 依赖 llm 是唯一例外，见下）；`music` 不依赖任何本项目包。

## 2. 依赖矩阵（A 行可以依赖 B 列 = ✅）

| from \ to | ui | data | coach | llm | tab | vision | audio | music |
|---|---|---|---|---|---|---|---|---|
| ui | — | ✅ | ✅(输出模型) | ❌ | ✅(TabDocument) | ❌ | ❌(引擎经 data) | ✅ |
| data | ❌ | — | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| coach | ❌ | ❌ | — | ✅ | ✅ | ❌ | ❌ | ✅ |
| llm | ❌ | ❌ | ❌ | — | ❌ | ❌ | ❌ | ❌ |
| tab | ❌ | ❌ | ❌ | ✅ | — | ❌ | ❌ | ✅ |
| vision | ❌ | ❌ | ❌ | ❌ | ❌ | — | ❌ | ✅ |
| audio | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | — | ✅ |
| music | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | — |

补充规则：

- UI 对 coach 只依赖其**输出模型**（`CoachFeedback`、`Flow<String>`、`PhraseCoach.Report`）；对 tab 只依赖 `TabDocument` 及纯函数；引擎类（TunerEngine、HandLandmarkerHelper、LlmClient）一律经 `AppContainer` 注入/获取
- `core/tab → core/llm` 是既有事实（`LlmTabExtractor` 用 LlmClient 做识谱）：允许，但 llm 侧永远不得反向感知 tab
- `vision`、`audio` 保持纯端侧（MediaPipe / DSP / AudioTrack / TTS），不得引入任何 LLM 调用——视听互验（M4）在 coach 或新的编排点组合两者，而不是让它们互相依赖
- **共享纯乐理函数进 `core/music`**（如 `midiToFreq`、移调换算），禁止把 `core/tab` 当共享函数库——audio→tab 的依赖违规（2026-09-16 监督员抓到并修复）就是这么来的
- 所有跨层异步输出用 `Flow` / `suspend`；回调接口不跨层

## 3. 数据模型不变量

- **TabDocument 是谱面唯一数据模型**：文本解析（TextTabParser）、GP 导入（alphaTab，M2）、拍照识谱（LlmTabExtractor）全部先转 TabDocument；渲染、播放、讲解、练习记录一律基于它。新增谱源 = 新增一个"源 → TabDocument"的转换器，不得另起数据结构
- 识谱产物入模前必须过合法性过滤：弦 1-6、品 0-24，非法事件丢弃（LlmTabExtractor 既有行为）
- 提示词唯一归属 `core/coach/CoachPrompts`；结构化输出解析与数据模型同模块（CoachFeedback / LlmTabExtractor）

## 4. 模型接入不变量

- 网关只认 OpenAI 兼容协议（`ChatSpec → OpenAiCompatClient`），厂商差异吸收在配置层；baseUrl/modelId 必须可配置（DataStore），禁止写死在调用点
- 多逻辑客户端共享账号、`ModelRouter` 链式降级（2026-09-15 实测定稿：DeepSeek 快答/深思/视觉 + 方舟 GLM 文本备份）；新增提供方 = AppContainer 注册 + 插链，编排层只面对客户端抽象。链路配置以 `docs/模型API接入手册.md` 最新实测为准
- 成本约束：默认优先更便宜的模型；图片 base64 内联、批量限 1-3 帧、思考模式按任务选 id 而非传参数（见 `.learnings/` L002/L007/L008）
- 端点/密钥的任何细节只在 `docs/模型API接入手册.md` 与 `local.properties`，不入其他被跟踪文件

## 5. 强制执行方式

- **现状（N/A 说明）**：Kotlin 生态的依赖方向工具是 detekt/自写 Gradle 校验，但本任务禁止改 gradle 文件，detekt 接入排入 M1 首个 sprint（见 `docs/exec-plans/tech-debt.md` DEBT-003）
- 过渡期执行方式：AGENTS.md「架构红线」+ `.cursor/rules/android-core.mdc`（编辑 core 文件时自动激活）+ Generator/Evaluator 的 review 检查表（docs/agents/）
- detekt 落地时优先自定义规则：禁止 `ui.*` import `core.llm.*`；禁止 `core.llm` import 本项目其他包；错误信息写明修复指引（"提示词请放 CoachPrompts"式）

## 6. 扩展指引（不改不变量的前提下）

- 新 AI 功能：CoachPrompts 加提示词 → CoachOrchestrator/PhraseCoach 加链路（沿用 streamWithFallback/completeWithFallback）→ ui 接 Flow
- 新谱源：实现"源 → TabDocument"转换器 + 合法性过滤；讲解/渲染零改动
- 新感知能力（M4 技巧检测等）：进 `core/audio` 或 `core/vision`，保持纯端侧；需要模型参与时经 coach 编排
- 共享纯乐理/换算函数：进 `core/music`（中立包，零依赖），不进 tab 也不进 audio
- 数据持久化（M1 Room）：新表进 `data/`，UI 与 core 不直接持 DAO，经容器暴露的 repository 访问

# 更新日志（CHANGELOG）

> 版本规则：**大版本 = 里程碑完成**。映射：M0→v0.1.0、M1→v0.2.0、M2→v0.3.0…… Mk→v0.(k+1).0（对应 docs/roadmap/ 的里程碑，详细描述）；**小版本 = 里程碑内的特性/修复批次**（v0.(k+1).x，一行概括）。
> 口径对齐：特性状态唯一信源是 `feature_list.json`（CHANGELOG 中的特性若未在 feature_list 置 done，注明"代码完成/待验收"）；里程碑详情见 `docs/roadmap/`；本文件按时间倒序记录。

## [未发布] v0.2.0 —— M1 乐理与识谱基础（进行中）

### 小版本
- v0.2.2（2026-09-16）：F102 乐理概念卡片（10 张高频问题卡）+ F103 指板可视化 + F105 节拍器（AudioTrack 硬件时基）+ F106 练习记录（Room v1→v2 正式迁移 + 计时入口 + 「我的」列表）——代码完成，待真机验收
- v0.2.1（2026-09-16）：F107 对话管理——多会话/重命名/删除（带确认）+ Room 持久化（会话表+消息表级联、单事务落库、流式删会话容错）；已验收 done（CI 绿 + 监督员复审）

## [v0.1.5] - 2026-09-15 —— F104 文本谱解析展示 + 新功能立项

- F104：识谱工作台支持粘贴 UG 风格文本谱，逐小节展示弦/品/音名，错误可读化
- 修复 TextTabParser 多位数品数重复扫描 bug（"10 品"读出两个音符；单测先红后绿）
- 用户需求立项：F207-F209（M2 逐句大白话讲解）、F601-F604（M6 扒谱），调研结论见开发方案 §8.7/§8.8

## [v0.1.4] - 2026-09-15 —— 测试基建与 CI

- 首批 JVM 单测（TextTabParserTest）；JUnit 4.13.2 引入（DEBT-001 开账）
- Gradle wrapper 全套生成（DEBT-002 偿还）；GitHub Actions：单测 + assembleDebug + APK artifact
- L012 定案：中文路径 + JDK 原生层 GBK 读 @argfile，本地单测不可行 → 测试以 CI 为准（实验记录见 .learnings；后续 589ac0e 补本地 JUnitCore 绕法，见 L012 防复发栏）
- 修复：gradlew 丢失可执行位；CoachOrchestrator 取消穿透；流式降级"零输出前才降级"契约（L010）

## [v0.1.3] - 2026-09-15 —— F101 乐理聊天页（代码完成，真机验收待做，feature_list 仍为 pending）

- 流式气泡对话 + 多轮历史 + 快捷提问；气泡稳定 key；中断保半截内容

## [v0.1.2] - 2026-09-15 —— Harness 工程化

- AGENTS.md（单一信源）/ CLAUDE.md / Cursor 规则 / feature_list.json（28 特性）/ .learnings 记忆库 / Agent 三角色协议
- git 仓库初始化，推送 GitHub（happye/GuitarAssistant）

## [v0.1.1] - 2026-09-15 —— 模型接入实测修正

- 真实 key 实测定稿：DeepSeek 全家桶主力（deepseek-chat 快答 / deepseek-flash 深思 / v4-flash-vision-exp 视觉），方舟 GLM 文本备份（id `glm-5-3-flash-260828`，待控制台开通）
- 四逻辑客户端 + 链式自动降级（ModelRouter/CoachOrchestrator）；思考控制靠选 id 不靠参数（L002）

## [v0.1.0] - 2026-09-15 —— M0 工程骨架（大版本）

对应里程碑：docs/roadmap/M0-工程骨架.md（F001-F005 全部 done）

- **工程**：Kotlin 2.1 + Compose + AGP 8.11 / Gradle 8.13 项目内虚拟环境（toolchain/，本机零污染）；5 Tab 导航（首页/练习室/识谱/乐理/我的）
- **LLM 网关**：OpenAI 兼容流式客户端（SSE + 图片 base64 + json_object 降级）+ 任务路由；密钥仅存本机（DataStore + local.properties，均已 gitignore）
- **教练编排器**：拍谱讲解 / 手势点评（结构化 JSON）/ 乐理问答 三链路 + 四套中文教学提示词
- **谱面引擎**：TabDocument 统一数据模型（string/fret/beat/technique，MIDI 换算）+ TextTabParser + LlmTabExtractor 识谱管线
- **调音器**：MPM 音高检测（4096 点 @44.1kHz，抗八度错误 + 抛物线插值）
- **视觉底座**：MediaPipe 手部 21 关键点封装 + 帧压缩编码（M3 启用）

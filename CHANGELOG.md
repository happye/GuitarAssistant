# 更新日志（CHANGELOG）

> 版本规则：**大版本 = 里程碑完成**。映射：M0→v0.1.0、M1→v0.2.0、M2→v0.3.0…… Mk→v0.(k+1).0（对应 docs/roadmap/ 的里程碑，详细描述）；**小版本 = 里程碑内的特性/修复批次**（v0.(k+1).x，一行概括）。
> 口径对齐：特性状态唯一信源是 `feature_list.json`（CHANGELOG 中的特性若未在 feature_list 置 done，注明"代码完成/待验收"）；里程碑详情见 `docs/roadmap/`；本文件按时间倒序记录。

## [未发布] v0.2.0 —— M1 乐理与识谱基础（进行中）

### 小版本
- v0.2.8（2026-09-17）：**用户反馈五连修**——①签名统一（仓库内 debug.keystore，本地/CI 任意来源 APK 可覆盖安装，此前 CI runner 每次自生成签名互斥）+ 版本注入（versionCode=提交数/versionName=tag 号）②全屏状态持久化（rememberSaveable+JSON Saver：识谱粘贴/解析/讲解、聊天、移调、节拍器、周复盘、曲库搜索、音色向导）③长音频流式抽取（Whole Lotta Love 5:30 报错根修：StreamingResampler 跨块插值直写文件，上限放宽 30 分钟）④识谱准确性 v2（提示词三步法+四类错型自检+CoVe 式原图复核二次 pass）与谱面渲染观感精修（参考 Songsterr/alphaTab 惯例：品数落线/拍位比例/技巧记号/段落条）⑤监督员 P1：imageBase64 出 saveable 防 TransactionTooLargeException
- v0.2.7（2026-09-16）：**M3-M6 代码面全部落地**——M3 视觉教练（F301 实时跟踪叠加/F302 姿势警报/F303 关键帧点评+TTS）+ M4 音频反馈（F401 跟练判定/F402 音高曲线/F403 视听互验/F404 诚实降级）+ M5 打磨（F501 AI 周复盘/F502 曲库管理 Room v3/F503 音色向导）+ M6 扒谱基建（F601 抽 PCM/F603 弦品 DP 量化）——以上代码完成待真机验收；F504/F604 评估决策记录落档（暂不做）；F602 阻塞（basic-pitch 无官方 ONNX，需 Python 转换验证）
- v0.2.6（2026-09-16）：F205 Guitar Pro 导入 done（alphaTab 1.8.4 引入经用户目标确认，MPL-2.0；Score→TabDocument 映射 + alphaTex 同路径 3 单测；GP 导入按钮入识谱台）；TabStudioScreen 拆分偿清 DEBT-005（382→237 行）；gradle.properties 降并发修 dex 合并原生崩溃；CI 发布工作流（tag→Release 挂 APK）
- v0.2.5（2026-09-16）：**M2 代码面收口**——F209 自动指法 DP（横按物理规则校验，CI 绿 done）+ F204 结构化讲解（识别与讲解分离落地）+ F203 识别结果编辑修正（逐小节改弦/品/拍回写）+ F202 谱面渲染与点按试听（自绘 Canvas + AudioTrack 单音合成；alphaTab 引入待用户确认 gradle 改动）+ F208 乐句卡片点读（原图对照/整段与单音 TTS 朗读/本地缓存离线回看）——以上代码完成待真机验收；F205 GP 导入阻塞待确认；M3 前置 hand_landmarker.task 入库；本地单测绕法产品化 scripts/run-tests-local.sh（L013）
- v0.2.4（2026-09-16）：F206 移调/变调夹计算器（core/music 纯 JVM 换算 + 乐理页对话框；10 组单测，C 调用 G 指法夹 5 品口径验证，CI 绿 done）
- v0.2.3（2026-09-16）：F201 拍谱识谱（拍照/相册→视觉识别→展示+AI 讲解）+ 降级原语重构（LlmFallback 共享）；修复用户反馈 bug：识谱页文本框撑爆布局、解析按钮被顶出屏幕
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

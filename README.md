# 吉他学习助手（GuitarCoach）

面向非专业爱好者的 Android 电吉他自学工具：**识谱（拍照识谱 / Guitar Pro / 文本谱）· 视觉纠手型 · 乐理问答（降key升key/移调/变调夹）· 调音**。

AI 底座（2026-09-15 真实 key 实测）：**DeepSeek 全家桶主力**（`deepseek-chat` 快答 / `deepseek-flash` 深思 / `deepseek-v4-flash-vision-exp` 看谱看手）+ **火山方舟 GLM 文本备份**（`glm-5-3-flash-260828`）。两家互为备份、失败自动降级；OpenAI 兼容协议接入，模型与地址均可在 App 内配置。详见 [docs/模型API接入手册.md](docs/模型API接入手册.md)。

📖 完整开发方案见 [docs/开发方案.md](docs/开发方案.md)（痛点调研、竞品分析、可行性论证、架构设计、里程碑路线图）。

## 当前状态（2026-09-16）

- **M0 ✅**（v0.1.0）｜**M1-M6 代码面全部落地**｜**M7 集百家之长扩展持续迭代**（全网调研驱动，最新 v0.2.18）——里程碑关闭条件为真机验收；发版走 GitHub Releases（tag 即自动挂 APK，v0.2.13 起签名统一可直接覆盖安装）
- 已实现：识谱三通道（拍谱+CoVe 自校验 / 文本谱解析 v2 / GP 导入）· 谱面渲染 v2（拍位比例/技巧记号）+ 点按试听 + **整段播放 + 播放光标跟随** · 逐句大白话讲解（CoVe 审计）· 自动指法 DP · 移调计算器 · 乐理聊天/概念卡片/指板可视化/**和弦库（28 形状琶音试听）**/**音色向导**/**AI 周复盘/今日练习单** · 调音器（MPM+音分表盘+**7 种调弦预设**）· 节拍器（**细分/预备拍/重音 pattern**）· 练习记录 + 曲库（Room）· 多会话管理 · **视觉教练**（实时手部跟踪/姿势警报/关键帧点评 TTS）· **跟练判定**（对齐+音高曲线+星级连击+提速提示）· **扒谱**（抽 PCM→**端侧转写引擎**→弦品 DP→自动 BPM→六线谱）
- 未实现/后续：变速不变调跟练（WSOLA）、alphaTab 五线谱精渲染、转写模型进阶——见 docs/roadmap/M7 与 tech-debt

## 环境要求

- **目标设备：小米 14**（骁龙 8 Gen 3，arm64-v8a；构建按此优化，其他机型适配后置）
- **JDK 17+**（项目内虚拟环境 `toolchain/` 已含，无需装全局）
- **Android SDK Platform 36**（本机已有：`%LOCALAPPDATA%\Android\Sdk`，`local.properties` 已配 `sdk.dir`）
- Android Studio 非必需

## 构建运行

```bash
bash scripts/build.sh              # assembleDebug（自动用项目内 JDK/Gradle 与本地缓存目录）
bash scripts/run-tests-local.sh    # 本地 JVM 单测（73 个；L012 绕法已产品化，详见脚本头注释）
bash scripts/test-api.sh           # 两个模型接口的连通冒烟测试
```

产物在 `app/build/outputs/apk/debug/app-debug.apk`。

> ⚠️ Gradle 的 `test` 任务本地跑不了（中文路径 + JDK 原生层编码限制，见 `.learnings/LEARNINGS.md` L012/L013）——本地单测走上面的 `run-tests-local.sh`；正式验证与 APK artifact 以 GitHub Actions 为准（push 自动触发）。

## 首次启动配置

1. 打开 App → 首页「模型设置」→ 填入火山方舟与 DeepSeek 的 API Key（只存本机 DataStore）
2. 点「测试连通」验证两条链路
3. 手部关键点模型（`app/src/main/assets/hand_landmarker.task`）**已内置**，无需下载

## 目录结构

```
app/src/main/java/com/guitarcoach/app/
├── MainActivity.kt            # 入口 + 底部导航（首页/练习室/识谱/乐理/我的）
├── ui/                        # Compose 界面与主题（聊天/识谱工作台/乐理工具/练习室）
├── core/
│   ├── llm/                   # LLM 网关：ChatModels / OpenAiCompatClient / ModelRouter / LlmFallback
│   ├── coach/                 # 教练编排：CoachPrompts / CoachOrchestrator / PhraseCoach（逐句讲解）
│   ├── tab/                   # 谱面：TabDocument / TextTabParser / LlmTabExtractor / 分段与指法与版面
│   ├── music/                 # 中立纯乐理：移调换算（F206）/ midiToFreq
│   ├── vision/                # 视觉：HandLandmarkerHelper / FrameCodec
│   └── audio/                 # 音频：PitchDetector(MPM) / MetronomeEngine / TonePlayer / TtsController
└── data/                      # AppContainer（手工依赖）+ SettingsStore + Room（对话/练习）+ PhraseCache
```

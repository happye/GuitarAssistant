# 吉他学习助手（GuitarCoach）

面向非专业爱好者的 Android 电吉他自学工具：**识谱（拍照识谱 / Guitar Pro / 文本谱）· 视觉纠手型 · 乐理问答（降key升key/移调/变调夹）· 调音**。

AI 底座（2026-09-15 真实 key 实测）：**DeepSeek 全家桶主力**（`deepseek-chat` 快答 / `deepseek-flash` 深思 / `deepseek-v4-flash-vision-exp` 看谱看手）+ **火山方舟 GLM 文本备份**（`glm-5-3-flash-260828`）。两家互为备份、失败自动降级；OpenAI 兼容协议接入，模型与地址均可在 App 内配置。详见 [docs/模型API接入手册.md](docs/模型API接入手册.md)。

📖 完整开发方案见 [docs/开发方案.md](docs/开发方案.md)（痛点调研、竞品分析、可行性论证、架构设计、里程碑路线图）。

## 当前状态：M0 骨架就绪

| 已实现 | 说明 |
|---|---|
| LLM 网关 | OpenAI 兼容流式客户端 + 视觉/文本任务路由，设置页改 Key 即生效 |
| 教练编排器 | 拍谱讲解 / 手势点评（结构化 JSON）/ 乐理问答 三条链路 |
| 谱面引擎 | 统一数据模型 TabDocument + 文本六线谱解析器 + LLM 识谱管线 |
| 调音器 | 麦克风采样 → MPM 音高检测 → 音分偏差提示 |
| 视觉底座 | MediaPipe 手部 21 关键点封装（等 hand_landmarker.task 模型文件） |

未实现（按里程碑推进）：谱面渲染与播放、相机实时界面、语音、练习记录 —— 见方案文档 §9。

## 环境要求

- **目标设备：小米 14**（骁龙 8 Gen 3，arm64-v8a；构建按此优化，其他机型适配后置）
- **JDK 17+**（项目内虚拟环境 `toolchain/` 已含，无需装全局）
- **Android SDK Platform 36**（本机已有：`%LOCALAPPDATA%\Android\Sdk`，`local.properties` 已配 `sdk.dir`）
- Android Studio 非必需

## 构建运行

```bash
bash scripts/build.sh        # assembleDebug（自动用项目内 JDK/Gradle 与本地缓存目录）
bash scripts/test-api.sh     # 两个模型接口的连通冒烟测试
```

产物在 `app/build/outputs/apk/debug/app-debug.apk`。

> ⚠️ 单元测试本地跑不了（中文路径 + JDK 原生层编码限制，见 `.learnings/LEARNINGS.md` L012）——**测试与 APK 构建以 GitHub Actions 为准**（push 自动触发，Actions 页可下载 APK）；本地用 `bash scripts/build.sh assembleDebug` / `compileDebugKotlin` 验证。

## 首次启动配置

1. 打开 App → 首页「模型设置」→ 填入火山方舟与 DeepSeek 的 API Key（只存本机 DataStore）
2. 点「测试连通」验证两条链路
3. （M3 之前需要）下载手部关键点模型放入 `app/src/main/assets/`：
   ```
   https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/1/hand_landmarker.task
   ```

## 目录结构

```
app/src/main/java/com/guitarcoach/app/
├── MainActivity.kt            # 入口 + 底部导航（首页/练习室/识谱/乐理/我的）
├── ui/                        # Compose 界面与主题
├── core/
│   ├── llm/                   # LLM 网关：ChatModels / OpenAiCompatClient / ModelRouter
│   ├── coach/                 # 教练编排：提示词 / 反馈模型 / CoachOrchestrator
│   ├── tab/                   # 谱面：TabDocument / TextTabParser / LlmTabExtractor
│   ├── vision/                # 视觉：HandLandmarkerHelper / FrameCodec
│   └── audio/                 # 音频：PitchDetector(MPM) / TunerEngine
└── data/                      # AppContainer（手工依赖）+ SettingsStore（DataStore）
```

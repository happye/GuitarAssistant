# 吉他学习助手（GuitarCoach）

面向非专业爱好者的 Android 电吉他自学工具：**识谱（拍照识谱 / Guitar Pro / 文本谱）· 视觉纠手型 · 乐理问答（降key升key/移调/变调夹）· 调音**。

AI 底座：**GLM-5.3-Flash**（原生多模态，负责"看谱、看手"）+ **DeepSeek-V4-Flash**（负责"乐理讲解、练习规划"），OpenAI 兼容协议接入，模型与地址均可在 App 内配置。

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

- **JDK 17+**（本机当前只有 Java 8，需先安装，如 Temurin 17）
- **Android SDK Platform 36**（本机已有：`%LOCALAPPDATA%\Android\Sdk`）
- **Gradle 8.13**（通过 wrapper 自动下载，或本地安装）
- Android Studio 非必需，装了更方便

## 构建运行

```bash
# 首次：生成 Gradle Wrapper（本机已装 Gradle 的话）
gradle wrapper --gradle-version 8.13

# 构建 Debug APK（需要先在 local.properties 里写 sdk.dir，或设置 ANDROID_HOME）
./gradlew assembleDebug
```

产物在 `app/build/outputs/apk/debug/app-debug.apk`。

## 首次启动配置

1. 打开 App → 首页「模型设置」→ 填入智谱 BigModel 与 DeepSeek 的 API Key（只存本机 DataStore）
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

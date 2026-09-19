# 扒谱重构前置调研（2026-09-19）——双线全网检索结论存档

> 信源：两个并行 general-purpose 调研 agent（A=端侧扒谱现实路径 15+ 轮检索 142 次工具调用；B=渲染播放方案，Maven/源码级证据）。全部关键数字经 2-3 独立信源交叉验证。架构决策见 docs/exec-plans/rework-plan-2026-09-19.md。

## A 线：混音歌曲→六线谱，手机端可达上限（核心事实）

1. **无商业产品端侧做混音→逐音符 tab**。Klangio Guitar2Tabs：云端处理（App Store 描述原文），自认"无法分离多乐器，混录会得到糟糕的转谱"，移动端 3.43★/28 评分；API $0.10-0.20/次（免费 50 次）。Moises/Chordify：云端，只做分离+和弦级（4.7★ 级口碑）。Ultimate Guitar：无 AMT，纯曲库。
2. **学界 SOTA 也到不了**：YourMT3+（2024，GPL-3.0 禁用）商业流行录音非主奏乐器 F1<10%（arXiv 2407.04822）。失真削顶+多乐器频谱叠印=信息论边界，不是工程问题。
3. **可达上限分层**：干净单音/分解和弦 F1 70-90%（GuitarSet 域内）；失真 riff 段落级（强力和弦根音+和弦型可靠）；混音吉他线经 guitar stem 分离后预期 50-70%；混音全曲**和弦级**是大众市场验证过的可靠形态（Chordify 4.73★）。
4. **升级件（许可证全过线）**：htdemucs_6s（唯一 guitar stem 开源模型，MIT，权重 80MB，浏览器优化版 172MB；demucs.cpp Android 先例 RTF 2.6 桌面；demucs.onnx STFT 外移导出法与本项目 Kotlin STFT 契合）；UVR-MDX-NET-Inst（sherpa-onnx 现成 ONNX 28-64MB，剥人声前置 RTF 0.6）；TabCNN/FretNet（MIT，音频→弦品，GuitarSet 谱 F1 0.748 / 出域 0.447-0.585，GOAT 2025 数据集 29.5h 失真增强可加训）；MT3（Apache-2.0，T5-small 60M，无官方移动移植）；YourMT3+ GPL 禁用。
5. **LLM（DeepSeek）角色限定**：闭集任务安全（指法候选排序/变调夹建议/校对提示/练习建议）；节奏量化与音符纠错必须确定性算法——LLM 不生成不删除音符（ChatMusician 证明 LLM 能理解符号音乐，但无数值纠错先例，幻觉音符比缺音更毁体验）。
6. **混合云成本**：Klangio 免费 50 次起步；Replicate 自托管 htdemucs_6s ≈ $0.03-0.05/首；对标 Music Demixer（浏览器 WASM 端侧 Demucs 商用）$9.99/月。

## B 线：渲染+播放

1. **alphaTab 全量启用为最优**（调研时最新版=1.8.4，恰为项目已用版本，含 #2714 Android AudioTrack 欠载修复）：APK 增量 ≈15MB（alphaSkia-android arm64 .so 11.8MB + Bravura 0.5MB + 内置 sonivox.sf2 1.35MB）；排版直接解决"同拍挤格"（1.8 系列：多声部错位符头 #2430/连杠重构 #2475/平滑光标滚动 #2447）；交互 API：playedBeatChanged/noteMouseDown/boundsLookup/playBeat/playbackRange/loadSoundFont；Android 端 AlphaTabView=RelativeLayout+ScrollView，Compose 官方路径=AndroidView 包裹（issue #1475）。
2. **许可证**：alphaTab MPL-2.0（仅链接不改源码=无传染义务）+ alphaSkia BSD-3 + Bravura OFL-1.1。TuxGuitar 实为 LGPL（修正此前记忆）。
3. **播放**：AlphaSynth+内置 sf2 零成本起步；GeneralUser GS 30.8MB 可切片 5-10MB（吉他+鼓）；GM 失真吉他有波表天花板→正解=GP8 外部媒体 API 挂预烘焙真实电吉他音频（Songsterr 同形态）；Karplus-Strong 保留为低延迟单音反馈（调音/点弦），不值得再投入失真链。
4. **性能**：Android 端 lazy loading 不可用（DOM 专属），全量排版 200 小节 ≈19MB 位图可承受；官方示例 github.com/CoderLine/alphaTabSamplesAndroid（MIT）。

## 对产品的口径修正（用户已拍板接受）

把"扒出六线谱"改成"扒出能练的东西"：单音给谱、riff 给根音+和弦形、混音给和弦级+置信度热图+可选 riff 尝试。UI 文案需明示业界边界（Klangio 自认混录差）。

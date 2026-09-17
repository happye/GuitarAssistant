# 技术债务台账

> 发现 debt 立即记录；每周安排处理。格式：DEBT-编号 | 发现日期 | 严重程度 | 影响 | 计划偿还。
> 技术债务像高息贷款：持续小额偿还优于累积后爆发式处理。

## DEBT-001: 零自动化测试

- 发现日期: 2026-09-15
- 严重程度: high
- 影响: 工程纪律门禁 1（回归测试先行）与门禁 3（验证靠跑）目前无法机器执行，只能人工验证；core/* 纯逻辑（TextTabParser、MPM、JSON 过滤）是单测性价比最高的区域
- 计划偿还: M1 首个 sprint 建立 `app/src/test/`，优先覆盖 TextTabParser、CoachFeedback.parse、LlmTabExtractor 合法性过滤、PitchDetector（合成正弦波）

## DEBT-002: Gradle wrapper 不完整 【已偿还 2026-09-15】

- 发现日期: 2026-09-15
- 严重程度: high
- 影响: `gradle/wrapper/` 只有 properties，缺 gradle-wrapper.jar 与 gradlew/gradlew.bat 脚本，任何机器都无法一键构建；叠加本机缺 JDK 17（见 .learnings/ERRORS.md E003）
- 计划偿还: 安装 JDK 17 后 `gradle wrapper --gradle-version 8.13`，wrapper 产物入库（.gitignore 已放行）
- 偿还记录: toolchain 就绪后已生成全套 wrapper 产物并入库；gradlew 可用（CI 采用 ./gradlew）。注：生成时因国际带宽用腾讯云镜像校验、properties 最终写回官方 URL

## DEBT-003: 架构依赖方向无机器强制

- 发现日期: 2026-09-15
- 严重程度: medium
- 影响: docs/design-docs/architecture.md 的依赖矩阵目前只靠 Agent 自觉 + review；一旦 Agent 复制出坏模式（如 ui 直接 import core.llm）无护栏
- 计划偿还: M1 引入 detekt 并配置自定义规则（错误信息写明修复指引）；接入 gradle 时同步配置（本任务期间禁止改 gradle 文件，故记债）

## DEBT-004: ui/screens 存在占位页

- 发现日期: 2026-09-15
- 严重程度: low
- 影响: PlaceholderScreen.kt 承载练习室/识谱/乐理/我的等未实现 Tab——这是 M0 的计划内状态而非缺陷；列出仅为防止"占位页被遗忘到 M4 之后"
- 计划偿还: 随 M1（乐理/练习室）/ M2（识谱）里程碑逐个替换；M3 结束时应无残留占位

## DEBT-005: TabStudioScreen 单文件超 300 行规范上限

- 发现日期: 2026-09-16（监督员 P2，F207 提交后达 316 行）
- 严重程度: low
- 影响: docs/references/coding-standards.md 单文件 ≤300 行；识谱工作台把拍谱路径、文本谱路径、结果列表都装在一个文件里，继续膨胀会失控
- 计划偿还: F208 乐句卡片 UI 改造时顺手拆分——拍谱输入区与 ParsedTabList 各自成文件（PhraseCoachPanel.kt 已是现成拆分模式）；此前新增识谱相关 UI 一律放独立文件，不再往 TabStudioScreen 塞

## DEBT-006：debug keystore 已随仓库公开（2026-09-17）

- 现状：keystore/debug.keystore 入库（debug 专用弱口令，仅用于统一本地/CI 签名让 APK 可覆盖安装），签名为 CN=GuitarCoach Debug
- 风险窗口：任何人可造同签名 debug APK 冒充更新；当前个人项目场景可接受
- 偿还条件：上 Google Play/对外发布前，换正式签名（不入库、密钥进本机 keychain），且 debug/正式签名明确区分

## DEBT-008：F602 转写模型来源标注（2026-09-17 记录）

- `app/src/main/assets/nmp.tflite`（204KB）= spotify/basic-pitch 0.4.0（Apache-2.0）PyPI wheel 内 `saved_models/icassp_2022/nmp.tflite`，ICASSP 2022 论文模型（"A Lightweight Instrument-Agnostic Model for Polyphonic Note Transcription"，arXiv:2203.09893）
- 输入 [1,43844,1] float @22050Hz；输出 contours[172,264]/notes[172,88]/onsets[172,88]；2026-09-17 onnxruntime 本机实测 440Hz→midi69 精确命中 + 用户素材（Blur-Song 2）端到端 166 音符

## DEBT-007：谱面渲染升级 alphaTab 引擎（评估项，2026-09-17）

- 现状：F202 v2 为自绘精修（参考 Songsterr/alphaTab 记谱惯例：品数落线/拍位比例展开/技巧记号/段落条），观感已可用
- 升级路径：alphaTab 1.8.4 完整渲染需要 AlphaSkia 原生库（额外 .so，APK +10~20MB）+ Bravura.otf 字体入 assets + AlphaSkiaAndroid 初始化链；已实探 classes.jar/sources 确认可行性，未引入
- 偿还条件：用户对自绘观感仍不满意，或需要五线谱双谱/复杂拍号/回放光标时立项

## DEBT-008：对抗性审查 P2 余项（2026-09-17，来源 .learnings/adversarial-review-2026-09-17b.md）

本轮已修：P1×5 全部 + P2 高优×4（Room 单例/positionMs 复位/history 上限/曲库删除确认）。余项按发现顺序：

1. TranscriptionEngine：rank<3 输出张量 AIOOBE 防呆；88 通道头缺失时静默空谱（防呆不对称）；frames=-1 动态 shape 防御；note/onset 头逐窗自校准在 onset 密集段可能翻转产生碎假音符（考虑跨窗投票）；输出数组每窗重复分配 GC churn
2. NoteDecoder FRAME_RATE 硬编码 hop（与 constants 对齐检查）
3. TonePlayer：整段播放不可取消（转写/长播场景需 cancel 通道）；旋转后双引擎并发防呆
4. LLM：`obj["error"]?.let` 未防显式 `"error":null` chunk；阻塞读上取消穿透最长滞后 120s（L011 软违例）
5. rememberSaveable：TabDocument 整体进 Bundle 的 binder 1MB 风险（长谱 JSON 几十 KB，叠加逼近阈值）——考虑迁移 DataStore
6. jsonSaver restore 失败 null 塞非空类型洞（当前全部可空 UI 态，暂无实害）
7. 练习计时放弃无确认

## DEBT-009：扒谱精度天花板与升级路径（2026-09-17 实证收口）

- **模型事实修正**：basic-pitch 官方唯一模型就是 0.2MB nmp.tflite（PyPI 包内置，ICASSP 2022 卖点即端侧轻量）——此前"官方 16MB 完整模型"的说法有误（调研 agent 评估偏差，已纠正）。模型侧无"换大版"捷径
- 当前链路（v0.2.25）：MP3→MediaCodec PCM→线性重采样 22050（无抗混叠，已知限制）→TFLite 2s 窗→NoteDecoder v2（官方 output_to_notes_polyphonic 移植）→弦品 DP
- 精度损失归因排序：①素材（全频段混音超出模型能力，官方论文用单乐器评估）②melodia_trick 未移植（官方默认开，对 melodic line 有增益）③重采样质量
- 升级路径（按收益/成本）：
  1. 素材引导（零成本）：清音/dry 单音吉他直录素材，避开混音
  2. melodia_trick 移植（中）：官方默认开启，对旋律线连续性有增益
  3. 抗混叠重采样（小）：soxr 级质量需自研多相滤波器
  4. GuitarSet 微调蒸馏（大，中期最优）：需 Python 训练链
  5. MT3 系大模型（不可行）：百 MB 级

## DEBT-010：吉他分离立项评估结论（2026-09-17，调研报告 .learnings/research-f602-tone-2026-09-17.md 补充轮）

- **端侧 Demucs TFLite 化：不立项**（三重否决：无现成转换链/手机外推 25-80 分钟每分钟音频/6s guitar stem 官方自评仅 okay）。demucs.cpp+GGML 走 JNI 理论可行（代码现成）但"导入后慢慢转"的离线模式 UX 差
- **立项推荐（v1）：Spleeter 2stems ONNX 26MB int8 经 sherpa-onnx Android（Kotlin 一等公民支持）先消人声+鼓再转谱**——手机 ARM 实测 RTF 0.127-0.258（1 分钟音频 8-16 秒出结果）；局限：吉他仍与 bass/keys 混叠（诚实预期）
- 纯 DSP 中央消除+带通（已交付）：辅助定位——摇滚双轨 hard-pan 混音歪打正着，中央 funk/solo 与 mono 混音失效；Audacity 官方已弃 DSP 路线改神经方案（佐证）
- 后续路线图：v1 Spleeter 消人声+鼓 → v2 demucs.cpp JNI 离线深度模式（可选）→ v3 自训 <30MB guitar-stem 模型（MoisesDB/MUSDB 数据，数周研究项目）

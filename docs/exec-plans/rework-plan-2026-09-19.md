# 扒谱+渲染播放 重构蓝图（Phase 0 / A / 1 / 2）——架构师方案 v1.0

> 2026-09-19 立项。信源：双线全网调研（.learnings/research-rework-2026-09-19.md）+ 代码逐文件核实 + 用户拍板三决策（混音默认和弦级 ✓ / 大模型按需下载 ✓ / Phase 0+Phase A 并行先行 ✓）。
> 本文件是跨会话执行契约；每批完成后回写状态标记。

## 0. 事实核实结论（方案前提）

| # | 结论 | 来源 |
|---|---|---|
| V1 | TabDocument 无拍号/和弦轨/置信度字段；toTabDocument 硬编码 4/4 | core/tab/TabDocument.kt:12-32 |
| V2 | 固定 secPerBeat 一次换算 + 0.25 拍网格量化 + 无音符清洗 | MidiTabConverter.kt（v0.2.31 版） |
| V3 | TempoDetector 只出全局 BPM 无拍相位——漂移根因之一 | core/audio/TempoDetector.kt |
| V4 | TranscribePanel 在 UI 层直接构造 SpleeterSeparator/TranscriptionEngine/TempoDetector（ui→audio 违规）——**P0-1 修，未开工** | TranscribePanel.kt:70,96,99 |
| V5 | TranscriptionEngine import core.tab（audio→tab 违规）——**✅ v0.2.32 已修** | TimedNote/Tunings 上收 core/music |
| V6 | Spleeter 注释 23.2s 实为 ~11.9s——**✅ v0.2.32 已修** | SpleeterSeparator.kt |
| V7 | GpImporter 无拍号提取，3/4、6/8 被按 4/4 切——A4 修 | GpImporter.kt:85-108 |
| V8 | alphaTab 交互 API 主张缺文档级证据（.tmp_at_docs.html 是 403 页）——A0 spike 前置门禁 | 调研 B |
| V9 | F202 原始定义就是 alphaTab 渲染+SoundFont 试听——Phase A 是补完规格不是新方向 | feature_list.json |
| V10 | SongRepository ignoreUnknownKeys——TabDocument 加带默认值新字段零迁移 | SongRepository.kt:27,66 |
| V11 | DEBT-007 偿还条件已触发；DEBT-010 否决的是 TFLite 化，与 htdemucs ONNX 路线不冲突 | tech-debt.md |
| V12 | APK 120MB 口径；随包资产：spleeter×2 + nmp.tflite + hand_landmarker | assets |

## 1. 目标架构

扒谱管线：`PCM → InputClassifier 路由 →（按需）分离层 → 转写层（+激活矩阵旁路）→ BeatGrid 逐拍对齐 → NoteCleaner → Quantizer → 弦品 DP → 分级 TabDocument（音符级/和弦级+置信度）`。
UI 只经 data 层 TranscriptionController 编排（P0-1）；DeepSeek 仅闭集任务（指法候选排序/校对提示/练习建议），不生成不删除音符。

渲染播放：`TabDocument → TabDocumentScoreAdapter 单向投影（含 Beat→文档位置注册表）→ AlphaTabPanel(AndroidView 包 AlphaTabView) → AlphaSynth 播放（内置 sf2 起步）`；TabRenderPanel 自绘保留为降级模式；KS 保留做调音/点弦低延迟单音。

## 2. 时序层规格（§细节见批次代码）

- BeatTracker：Ellis 2007 DP，TIGHTNESS=100，onset 归一化 95 分位到 [0,2]，t=0 锚点，回溯后按周期回填至 [0,P)；拍号推断 lag∈{2,3,4,6} 均值口径。**✅ v0.2.32**
- NoteCleaner：R4 低置信（amp<0.25×中位）→R1 同音合并（<max(60ms,0.25拍)，v1 无激活谷值校验）→R2 鬼影（q=p+{12,7,19} 且 amp<0.35·amp(p)、Δ<50ms、dur 不更长→删 q）→R3 近同时成组（<35ms 链式聚簇，同 midi 留最响，整组标 groupId）。**✅ v0.232**
- Quantizer v2：locate 插值 → IOI 中位数自适应步长（<0.3 拍→1/16；<0.6→1/8；否则 1/4）→ 0.12 拍容差吸附，超差保留分数拍+snapped=false。**✅ v0.2.32**
- 弦品 DP v2（待做）：R3 组整组枚举 ChordLibrary 强力和弦形候选；同弦连续折扣 −0.2。

## 3. 里程碑

### Phase 0 扒谱止血（≈15-20 人日）
| Issue | 内容 | 天数 | 状态 |
|---|---|---|---|
| P0-1 | data/TranscriptionController 编排迁出 UI（修 V4），进度 Flow | 1 | ⬜ |
| P0-2 | core/music BeatGrid/TimedNote；TranscriptionEngine 改产 TimedNote（修 V5） | 1 | ✅ v0.2.32 |
| P0-3 | TempoDetector 相位+强拍扩展 | 1.5 | 并入 BeatTracker ✅ |
| P0-4 | BeatTracker（Ellis DP） | 3 | ✅ v0.2.32 |
| P0-5 | NoteCleaner（R1-R4） | 3 | ✅ v0.2.32 |
| P0-6 | Quantizer v2 + MidiTabConverter v2 | 3 | ✅ v0.2.32（组枚举待 Batch 2） |
| P0-7 | InputClassifier + ChordTracker + 和弦级输出（TabDocument 新字段） | 4 | ⬜ |
| P0-8 | 置信度热图 UI | 1.5 | ⬜ |
| P0-9 | 转写三小修：50% 窗重叠+跨窗去重 / melodia_trick（DEBT-009） / 抗混叠重采样 | 3 | ⬜ |

验收：混音测试曲和弦正确率 ≥70%；干净素材音符数偏差 ±5% 不劣化；小节线对强拍（真机看谱）。

### Phase A 渲染播放（≈14 人日）
| Issue | 内容 | 天数 | 状态 |
|---|---|---|---|
| A0 | alphaTab 1.8.4 sources.jar 源码级 API spike（V8 门禁） | 0.5 | ⬜ |
| A1 | TabDocumentScoreAdapter 单向投影+注册表 | 3 | ⬜ |
| A2 | AlphaTabPanel（AndroidView+事件桥+双渲染切换 chip） | 4 | ⬜ |
| A3 | AlphaSynth 播放+光标+点选+循环段 | 3 | ⬜ |
| A4 | TabDocument 拍号/和弦轨字段 + GpImporter 拍号映射（修 V7） | 2 | ⬜ |
| A5 | 音色：内置 sf2 起步（GeneralUser GS 切片可选） | 2 | ⬜ |

### Phase 1 分离升级（PoC 门禁后）
- 1a PoC：PC 侧 demucs.onnx 导出（STFT 外移）+ int8/fp16 → androidTest benchmark 真机扫参（线程 2/4/6 × XNNPACK）。门禁：RTF≤1.5、峰值内存≤1.5GB、2min≤3min。
- 1b Htdemucs6sSeparator（7.8s 分段+0.75s overlap 流式）+ StftV2 参数化。
- 1c 模型按需下载基建（Range 断点续传+SHA-256+版本清单 JSON+镜像列表；GitHub Releases 当 CDN）。
- 1d UVR-MDX-Inst 替换 Spleeter（A/B 后），APK −24MB。
- 失败降级：维持 Phase 0 和弦级 → UVR 替换 → 云端（Phase 2）。

### Phase 2 云端精转（D1 用户拍板后）
端侧初稿+云端覆盖 diff 视图；Klangio 免费额度 50 次验证或 Replicate 自托管 $0.03-0.05/首。

## 4. 风险台账
R1 htdemucs 不过门禁（中）→ 三级降级已定义；R2 alphaTab API 与调研不符（低-中）→ A0 spike 止损；R3 alphaSkia 初始化失败（低）→ 自绘降级；R4 新时序劣化干净素材（中）→ 回归测试集门禁+旧 convert 保留；R5 中国网络下载失败（中）→ 断点续传+镜像+降级提示；R6 和弦级被视为敷衍（中）→ UI 明示业界边界；R7 GPL 污染（低）→ 只参考不引码；R8 双引擎内存叠加（低）→ 分时+分块流式纪律。

## 5. 需用户拍板剩余项
D1 云端按次付费（Phase 2 前置，建议先吃 Klangio 免费额度）｜D5 双渲染观察期时长｜D6 音色档位｜D7 弦品模型特化（Phase 1 后评估）——均低不可逆。

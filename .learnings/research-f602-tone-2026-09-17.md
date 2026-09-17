# 调研报告 F602：转写精度工程提升 + 拨弦音色合成最佳实践

- 日期：2026-09-17
- 任务：为扒谱（basic-pitch TFLite 0.2MB 小模型 + 自写阈值解码，碎音/错音多）与谱面试听（正弦+谐波+指数衰减，音色生硬）寻找不换大模型的工程改进路径
- 检索通道备注：bocha_web_search 前 5 次调用成功后配额耗尽（403 无余额），bocha_ai_search 同样 403；内置 WebSearch（火山引擎 403）与 WebFetch（域名验证失败）亦不可用；github.com / wikipedia 直连超时。后续改用本机可达通道直查**一手信源**：jsdelivr CDN（GitHub 文件镜像）、api.github.com、ccrma.stanford.edu、arxiv.org、hf-mirror、cn.bing.com（结果稀薄，仅作补充）。下文结论以源码/官方文档/论文原文为准，少量经验性内容已明确标注"推断/未证实"。

---

## 主题一：提升 basic-pitch 类模型转写精度的工程手段（不换大模型）

### 1.1 官方后处理参数（spotify/basic-pitch@main 源码实测，非转述）

| 参数 | 默认值 | 实际语义（源码 docstring + 算法逻辑） |
|---|---|---|
| onset_thresh | **0.5** | onset 激活矩阵超过该值才算一个新音起点 |
| frame_thresh | **0.3** | frame（延音）激活低于该值视为音符不在发声 |
| min_note_len | **11 帧**；公开 API 层换算为 **127.7 ms** | 短于此的音符直接丢弃（碎音的主要闸门） |
| min_freq / max_freq | **None（不限）** | 输出频率域硬限制，超出区间的音符不生成 |
| melodia_trick | **True** | 见下述算法说明 |
| energy_tol | **11（帧）** | 注意：**不是能量阈值**，是"连续 11 帧低于 frame_thresh 才终止音符"的空隙容忍度；官方 docstring "Drop notes below this energy" 有误导性 |

- 帧率换算：ANNOTATIONS_FPS = 22050 // 256 = **86 fps**（每帧 11.63 ms）；11 帧 ≈ 127.9 ms，与 DEFAULT_MINIMUM_NOTE_LENGTH_MS = 127.7 吻合。
  - 信源：https://github.com/spotify/basic-pitch/blob/main/basic_pitch/constants.py （FFT_HOP=256, AUDIO_SAMPLE_RATE=22050, ANNOTATIONS_FPS=22050//256）；https://github.com/spotify/basic-pitch/blob/main/basic_pitch/inference.py （L185-188：DEFAULT_ONSET_THRESHOLD=0.5 / DEFAULT_FRAME_THRESHOLD=0.3 / DEFAULT_MINIMUM_NOTE_LENGTH_MS=127.7）
- 解码器核心机制（note_creation.py `model_output_to_note_events`，L363-473）：
  1. 只在 onset 激活超过 onset_thresh 的 (时间, 频率bin) 处起始音符；
  2. 从起点向后扫描，累计连续低于 frame_thresh 的帧数，达到 energy_tol(11) 帧则截断音符；音符占用期间把 frame 矩阵该 bin 及**相邻 ±1 半音 bin** 清零（防重复检出）；
  3. `if i - note_start_idx <= min_note_len: continue` —— 比 min_note_len 短直接跳过；
  4. amplitude（0~1）= 该音符区间 frame 激活的**均值**，随音符事件输出，可做后过滤；
  5. **melodia_trick**：onset 贪心解码后，只要剩余能量矩阵最大值仍 > frame_thresh，就从全局峰值点向前后双向扩展（同样用 energy_tol 做空隙容忍）补建"没有明确 onset"的音符——本质是 Melodia 式旋律轮廓跟踪，用来挽回延音/弱起音。
  - 信源：https://github.com/spotify/basic-pitch/blob/main/basic_pitch/note_creation.py
- 公开 API 与内层解码参数的对应：inference.py 的 minimum_note_length(ms) → note_creation.py 的 min_note_len(帧)；minimum_frequency/maximum_frequency 直接透传。
  - 信源：https://github.com/spotify/basic-pitch/blob/main/basic_pitch/inference.py （L434-459）

### 1.2 音频预处理对精度的影响

- 官方输入管线：`librosa.load(path, sr=22050, mono=True)` —— 官方即**单声道混缩**，重采样到 22050 Hz；librosa 0.10+ 默认 `res_type="soxr_hq"`（soxr 高质量带限 sinc 插值）。**含义：输入域是"soxr_hq 质量的 22050Hz 单声道"。Android 端自写重采样若用线性插值，等于主动制造与训练域不同的频谱失真（混叠/高频缺失），对模型精度是隐性伤害。** 端侧应换 soxr（有 C 移植）或 speexdsp 重采样。
  - 信源：https://github.com/spotify/basic-pitch/blob/main/basic_pitch/inference.py （L239）；https://github.com/librosa/librosa/blob/0.10.2.post1/librosa/core/audio.py （L66: `res_type: str = "soxr_hq"`，L116 "uses soxr's high-quality mode"）
- 窗口/切片：2 秒窗口（AUDIO_N_SAMPLES = 22050×2−256 = 43844 样本），滑窗推理后拼接（unwrap_output 按 hop 对齐）。长音频精度与拼接对齐质量相关；端侧扒谱若自己分窗，需复刻同样的重叠/去重叠逻辑。
  - 信源：https://github.com/spotify/basic-pitch/blob/main/basic_pitch/constants.py （L38-47）；https://github.com/spotify/basic-pitch/blob/main/basic_pitch/inference.py （window_audio_file / unwrap_output）
- 归一化：**官方推理代码不含显式峰值归一化**（get_audio_input 仅 load+pad+分窗）。模型激活幅度直接受输入电平影响 → 录音偏小会整体压低 onset/frame 激活，等效于阈值失配、碎音与漏音增多。**建议端侧在送入模型前做峰值归一化到 -3~0 dBFS**（此项为工程推断：官方代码无归一化步骤是源码事实，"归一化能改善精度"是从激活幅度线性依赖推出的合理结论，未取得直接 A/B 数据）。
  - 信源：https://github.com/spotify/basic-pitch/blob/main/basic_pitch/inference.py （L222-244，无归一化步骤）

### 1.3 电吉他调参经验（失真 vs 清音、单音 riff）

- 失真问题：过载/失真改变谐波结构（附加奇偶次谐波、能量向中高频集中），多音高估计的经典失败模式就是八度/五度误判；吉他 AMT 研究把"训练域与真实（失真）录音的失配"作为核心挑战，用域适配（domain adaptation，商业谱-音频对训练）解决，HRGT 模型在 GuitarSet 上 zero-shot 达到已发表方法 SOTA。
  - 信源：https://arxiv.org/abs/2402.15258 （High Resolution Guitar Transcription via Domain Adaptation, Riley/Edwards/Dixon 2024, 摘要原文）
- "八度/五度误检是失真音高检测主要错误类型"为多音高估计领域工程共识，本次未取得可直接引用的开放信源（标注：推断，需以本项目 A/B 实测为准）。
- 清音 vs 失真的实操含义（推断，基于上两条）：若用户走箱头/失真音色扒谱，优先建议用户用清音/插 USB 直采（DI）扒谱——谐波列干净，模型域更接近；失真riff可在解码后用"相邻音符强合并 + 频域合理性过滤（八度折叠检查）"兜底。
- 单音 riff：melodia_trick 本身就是旋律轮廓跟踪，单音 riff 场景应保持开启；更稳的做法是解码后再做"单音化"后处理（同一时刻多音符只保留激活最高者，滞回保持防抖）。
- 频域硬限制是官方一等公民参数：电吉他有效音域 E2(82.4Hz)~高频泛音，设 `min_freq≈80, max_freq≈1400` 可从根上杜绝低音误检——**本项目已发生的"贝斯检出 midi37 毁掉转写"事故正是无 min_freq 的后果**（内层解码按 onset 生成音符后才由上层过滤，白耗算力且污染 remaining_energy 清零逻辑）。
  - 信源：https://github.com/spotify/basic-pitch/blob/main/basic_pitch/note_creation.py （L78-79）；电吉他音域 E2=82.4Hz 为通用乐理常识。
- 参数方向建议（推断，需 A/B）：碎音多 → onset_thresh 0.5→0.6~0.7、min_note_len 127.7→180~250ms（按曲目最快音符时长的 70~80% 取值，勿盲目加大，否则吞掉真实短音）；延音被截断 → energy_tol 11→15~20 或 frame_thresh 微降。**官方默认值是为"全风格多乐器"折中的，针对单一乐器收窄参数几乎总有收益。**

### 1.4 Android 端 TFLite 音频转写的业界实践

- basic-pitch 官方同时发布 TensorFlow / TFLite / CoreML / ONNX 四种运行时与 TypeScript 版本（basic-pitch-ts），TFLite 是官方一等公民——"轻量 AMT 模型 + TFLite 端侧推理"路线本身是 Spotify 官方产品化选择（ICASSP 2022 论文明确以"存储/网络/内存约束下的真实部署"为动机）。
  - 信源：https://github.com/spotify/basic-pitch （README "Model Runtime" 节）；https://github.com/spotify/basic-pitch-ts ；https://arxiv.org/abs/2203.09893 （A Lightweight Instrument-Agnostic Model for Polyphonic Note Transcription and Multipitch Estimation, ICASSP 2022）
- C++/ONNX 端侧先例：sevagh/basicpitch.cpp 用 ONNXRuntime 精简构建（只保留所需 op）+ 模型权重编译进二进制（.c/.h），含 WASM demo —— 证明 <20MB 级 AMT 模型在无 Python 运行时环境（含移动端同类约束）推理成熟可行，且推理逻辑可完全脱离 TF 生态复刻（对 TFLite 自写解码器有直接参考价值：输入特征/CQT、输出三矩阵语义一致）。
  - 信源：https://github.com/sevagh/basicpitch.cpp （README）
- 业界现状：完整多音 AMT 的端侧产品化罕见，商用吉他转谱（Klangio Guitar2Tabs、Chordify、Moises 等）走云端；端侧音频 MIR 在移动 App 中以单音 pitch 跟踪（调音器、跟弹、人声）为主，Java 生态用 TarsosDSP（YIN / McLeod MPM / 动态小波，纯 Java 可直接跑 Android），深度模型用 CREPE 系小容量档。（Klangio/Chordify/Moises 云端结论：业界常识，本次未取得直接信源，弱信源标注；TarsosDSP 为一手。）
  - 信源：https://github.com/JorenSix/TarsosDSP （README：YIN/MPM/Dynamic Wavelet pitch trackers）

### 1.5 精度更高的端侧可行替代模型（<30MB）评估

| 方案 | 体积 | 单/多音 | 评估 | 信源 |
|---|---|---|---|---|
| **basic-pitch 官方完整模型（nmp.tflite）** | ~16MB（社区常见数值，本次未取得直接信源，以实际下载为准） | 多音 | **最直接升级路径**：与现用 0.2MB 小模型同架构同解码器，官方 TFLite 可直接换；ICASSP 论文即为此模型的精度背书 | https://github.com/spotify/basic-pitch ；https://arxiv.org/abs/2203.09893 |
| CREPE（marl/crepe） | tiny~full 五档（全量 82MB；tiny 约 2MB 级——未证实） | **仅单音** | 波形域直接输入、2018 年精度优于 pYIN/SWIPE 的单音跟踪；适合"单音 riff 模式"与主模型交叉验证，不适合多音 | https://github.com/marl/crepe （README：monophonic CNN pitch tracker, capacities tiny\|small\|medium\|large\|full） |
| pYIN / TarsosDSP | 0（纯 DSP） | 单音 | Java 直接上 Android，零模型体积；单音场景可与解码器输出做一致性校验 | https://github.com/JorenSix/TarsosDSP |
| Onsets and Frames（复现仓库若干） | 检查点 40MB+ | 多音 | 钢琴偏置、体积超预算，不推荐 | https://github.com/jongwook/onsets-and-frames |
| MT3 / EMO / HRGT / TART | 100MB 级 / 未开源端侧权重 | 多音 | SOTA 但均超 <30MB 预算；TART 四阶段管线（piano 模型域适配→技巧分类→弦品指派→tab 生成）代表方向 | https://arxiv.org/abs/2510.02597 ；https://arxiv.org/abs/2402.15258 |
| **微调路线（中期最优）** | 模型体积可控 | 多音 | GuitarSet（marl/GuitarSet，360 段带六弦真值的吉他录音）+ basic-pitch-torch（gudgud96 的 PyTorch 版便于微调）→ 吉他域专用模型 → 量化/蒸馏回端侧 | https://github.com/marl/GuitarSet ；https://github.com/gudgud96/basic-pitch-torch |

---

## 主题二：拨弦音色合成最佳实践（Android 端、无大音色库前提）

### 2.1 Karplus-Strong（KS）算法细节

- 起源与核心：Karplus & Strong 1983 与 Jaffe & Smith 的扩展算法（EKS）同年发表于 Computer Music Journal。Strong 的原始发现：波表循环读出时**每过一轮做一次二点平均 H(z)=0.5+0.5·z⁻¹**（无需乘法），"无论初始波表是什么，听起来都出奇地像一根弦"；标准激发随后定为**随机数（白噪声 burst）**。JOS（斯坦福 CCRMA Julius O. Smith）明确记载该算法被归类为滤波延迟环（filtered delay loop）弦模型。
  - 信源：https://ccrma.stanford.edu/~jos/pasp/Karplus_Strong_Algorithms.html （原文已抓取核对）
- 算法四要素（多信源交叉）：
  1. **延迟线长度 N = fs / f0**（实例：fs=44100、f0=200Hz → N=220.5→取 220）；N 只能取整 → 高音区音高量化失谐，EKS 用分数延迟/全通插值解决调音问题。
  2. **初始填充**：长度 N 的白噪声 burst；激发谱决定音头——快速消散的频率成分形成拨弦瞬态，存活下来的谐波列给出音高（物理建模视角：拨弦=给弦一个宽谱冲激，不符合弦共振的成分迅速衰减）。
  3. **反馈环**：输出经二点平均低通后乘反馈增益再回灌延迟线；二点平均使高频每轮衰减更快 → 自然弦"高频先死"的衰减包络；反馈增益/低通极点共同决定整体衰减时长（eechina 实测文："滤波器系数决定音调的粘性"）。
  4. **衰减控制**：EKS（Jaffe-Smith 1983）为调音、亮度、动态电平、拨弦位置等提供了一整套扩展；JOS 记载其动机是 Jaffe 在作品《Silicon Valley Breakdown》(1982) 中对音乐可控性的需求。
  - 信源：https://ccrma.stanford.edu/~jos/pasp/Karplus_Strong_Algorithms.html ；https://www.eechina.com/thread-129494-1-1.html （中文实测：延迟线取整、低通极点、频响/谐波分析）；https://blog.csdn.net/weixin_39622643/article/details/110798834 （物理建模视角中文讲解）；中文专利 https://www.xjishu.com/zhuanli/21/202410286558.html （KS + 拨弦阻尼/琴弦阻尼/琴弦松紧模拟组件 + 扰动白噪声，直接证明 KS 可控化在中文移动端专利层的活跃度）
- 衰减公式（由 T60 定义推得，工程推导非检索信源）：每周期幅度乘子 ρ 与目标 T60 关系为 `ρ = L / |H(f0)|`，其中 `L = 10^(−3 / (f0·T60))` 为每周期目标幅度比（-60dB），|H(f0)| 是平均滤波器在基频处的损耗——即反馈增益需补偿平均滤波器在低音区的额外损耗，否则低音弦衰减过快。
- 音色评价：实现成本极低（一行递归）、真实感强——JOS："sounded curiously like a string"；中文专利背景："算法简单但是合成的声音比较真实"；移动端先例：iOS 付费合成器 Strng（midifan 报道）以 KS 为引擎，暴露的参数集（noise 长度/noise 滤波/琴弦张力/ADSR/失谐）即"KS 参数化"的产品化清单。
  - 信源：同上 JOS/CSDN/专利；https://www.midifan.com/modulenews-detailview-20547.htm

### 2.2 让它更像电吉他（加什么）

- **拾音器/拨弦位置滤波**：真实电吉他音色 = 弦振动 × 拾取位置。EKS 的拨弦位置扩展即 comb 预滤波激发噪声（y[n] − y[n−βN]，β≈0.1~0.15 桥侧）：靠近桥 = 亮、comb 峰谷深；拾音器位置等效再叠一个低通（琴颈拾音器暗、琴桥亮）。（机制来自 EKS/物理建模共识，JOS 页记载 EKS 含 pick position 控制；具体 β 数值为常用经验值，标注推断）
  - 信源：https://ccrma.stanford.edu/~jos/pasp/Karplus_Strong_Algorithms.html
- **轻微过载**：软削波 waveshaping。工程常用 `y = tanh(k·x)/tanh(k)`；硬件原型是二极管削波电路（对称/非对称削波决定音色软硬），效果链结构 = drive（削波强度）→ tone（高频低通）→ 电平（Apple Logic Distortion 控件文档即此结构：Drive 饱和 + Tone high-cut "谐波丰富的失真信号滤波后更柔和"）。电吉他化要点是**轻度过载（k≈2~4）而非满失真**，叠加后仍要保留 KS 的衰减动态。
  - 信源：https://www.deeptronic.com/electronic-circuit-design/symmetric-distortion-guitar-effect-using-back-to-back-clipper-diodes/ ；https://support.apple.com/ko-kr/guide/final-cut-pro-logic-effects/lgex92c5e58e （Drive/Tone 结构官方描述；tanh 公式为通用工程做法，标注）
- **音箱/cabinet 模拟**：真实吉他箱频响上限 ≈ 4.5~5.5kHz 陡降，等效对过载输出加二阶低通即可"入箱"。（推断，通用音箱模拟常识，未取直接信源）
- **拨片瞬态**：KS 的噪声 burst 本身给音头；更真实的做法是音头前 5~10ms 叠加一层高通噪声/短脉冲，幅度随力度——Strng 的独立 noise 参数与专利的"扰动白噪声"均为此思路的产品化佐证。
  - 信源：https://www.midifan.com/modulenews-detailview-20547.htm ；https://www.xjishu.com/zhuanli/21/202410286558.html
- **力度→音色联动**（教学 App 的刚需）：力度不只改增益，改激发噪声的低通截止（轻拨=暗、重拨=亮）——EKS dynamic level 控制的直接应用。
  - 信源：https://ccrma.stanford.edu/~jos/pasp/Karplus_Strong_Algorithms.html （EKS: dynamic level 控制）

### 2.3 加性合成 vs Karplus-Strong 真实感对比

- 加性（谐波叠加+ADSR）的根本短板：**每个谐波的衰减率天然不同**（高次谐波衰减快得多），且真实弦存在失谐非整倍频与相位关系；单一包络套所有谐波 + 静态谐波幅度比 → 听感"风琴/电子琴化"，这正是本项目现状（正弦+谐波+指数衰减）。要逼近真实需逐谐波独立衰减/拍频/相位，参数量与运算量随谐波数线性增长，且仍缺拨弦瞬态。
  - 信源：JOS 对"精确周期声音单调"的论述 https://ccrma.stanford.edu/~jos/pasp/Karplus_Strong_Algorithms.html （"The sounds were rather boring, as any precisely periodic sound tends to be"——正弦谐波固定比即此类）；物理建模叙事 https://blog.csdn.net/weixin_39622643/article/details/110798834
- KS 的天然优势：频变衰减（高频先死）、相位关系、拨弦瞬态**由结构免费提供**；每音符开销 = 一条延迟线 + 一次加法/平均（JOS：二点平均无需乘法），比 N 谐波振荡器更省且更真。
- KS 的短板与对策：整数延迟线高音失谐（→分数延迟全通插值）；衰减固定难做长延音（→反馈增益补偿，见 2.1 公式）；激发随机性导致每次音色微差（→固定随机种子按音符 hash，试听一致性更好，标注推断）。
- 结论：**无大音色库前提下，KS 在真实感/体积/算力三角上全面优于加性合成**；加性仅在被需要"完全可控的均匀音色"（如铃声）时才占优。

### 2.4 对照：SoundFont / FluidSynth 方案的体积与复杂度

- FluidSynth 官方原生支持 Android：仓库含 `doc/android/`（JNI `Android.mk`、Java 资产加载器 fluid_androidasset、android CI 流水线）；CMakeLists 提供 `enable-opensles` 与 `enable-oboe`（Oboe 需 OpenSLES 和/或 AAudio）官方低延迟音频驱动选项。
  - 信源：https://github.com/FluidSynth/fluidsynth/tree/master/doc/android （经 jsdelivr 目录树核实存在 fluid_androidasset.c/.h、jni/Android.mk、Makefile.android）；https://github.com/FluidSynth/fluidsynth/blob/2.4.6/CMakeLists.txt （L89-90）
- 社区 Android 集成先例：atsushieno/android-fluidsynth（旧移植，已弃用并指向官方）、degill/fluidsynth-android-opensles（OpenSL ES 驱动示例）、ChinaWallace/Android_FluidSynth_MidiDriver（sf2 或 SONiVOX 双路线）——说明 NDK 集成路径成熟但需自管构建与驱动接线，复杂度中高。
  - 信源：https://github.com/search?q=fluidsynth+android&type=repositories （以上仓库名与描述来自 GitHub API 实查）
- **TinySoundFont（schellingb/TinySoundFont）**：**单 C 头文件**的 SF2 合成器（基于 SFZero），零依赖（仅 fopen/math/malloc），`tsf_load_filename("x.sf2") → tsf_note_on(preset, key, vel) → tsf_render_short(buf, 22050, 0)` 三步出 PCM，接 Oboe/AudioTrack 即可。**复杂度远低于 FluidSynth NDK 全家桶，是"SF2 对照方案"的首选实现载体。**
  - 信源：https://github.com/schellingb/TinySoundFont （README 已抓取核对）
- 体积结构：合成器代码本身可忽略（tsf 单文件；fluidsynth 编译后数 MB）；**体积大头是 SF2 音色资产**——通用 GM 大库百 MB 级（FluidR3_GM ≈ 148MB，常识值未证实），小型 GM 库数 MB~30MB，吉他专用 SF2 可压到 1~5MB 量级（未直接证实，实施前实测）。风险：SF2 音色质量高度依赖库本身，免费吉他库音色参差。
- Sonivox（AOSP EAS）路线：Android 系统同源波形表合成器（Google 授权 Sonic Network EAS，Apache 2.0），**内嵌样本、默认零外置资产**，也支持外挂 DLS/SF2 提升质量；资源占用极小但**不含 MIDI 输入/音频输出**，需自接 Oboe；独立仓库已 CMake 化（EmbeddedSynth/sonivox，被 ScummVM 等采用）。
  - 信源：https://github.com/pedrolcl/sonivox （README 已抓取核对）
- 对照结论：SF2 方案真实感上限高（采样音色）但**体积不可控、音色不可参数化**（换力度/换琴需换库）；KS 零资产、算力毫秒级、参数可玩性高（力度→音色、拨弦位置、过载都能实时映射），与教学 App 的"谱面试听 + 视觉反馈"定位更匹配。SF2/TinySoundFont 适合作为 KS 打磨期的**保底 fallback 音色开关**，而非主方案。

---

## 实施建议 A：转写解码器改进清单（按收益排序）

> 全部条目不换模型、不动 TFLite，只动解码器与输入链路；每条可独立 A/B。

1. **min_freq/max_freq 限吉他域（≈80~1400Hz）**【官方参数直支持，收益最大】：从根上消除 midi37 类低音误检（本项目已实际发生的崩溃级事故），且减少解码器 remaining_energy 清零浪费。信源：note_creation.py L58-59。
2. **min_note_len 提到 180~250ms（按曲目最快音符时长的 70~80% 自适应）**【直击"碎音"】：默认 127.7ms 是全风格折中值；单音 riff 场景短音符本就该少。信源：inference.py L187 / note_creation.py L57,429。
3. **输入峰值归一化（-3~0dBFS）+ soxr 级高质量重采样**【对齐训练域】：官方管线=soxr_hq@22050 mono；端侧线性插值重采样与小电平输入都会整体压低激活。信源：inference.py L239 + librosa audio.py L66。
4. **onset_thresh 0.5→0.6~0.65 A/B**【减错音】：代价是弱起音漏检，配合第 5 条可部分挽回。信源：note_creation.py L75。
5. **同 pitch 音符合并后处理**：间隔 <40~60ms 且中间无新 onset 的两段同音合并为延音；可再按 amplitude 均值丢弃 <0.15~0.25 的低置信音符（解码器已输出 amplitude）。信源：note_creation.py L439。
6. **单音 riff 模式开关**：解码后 mono 化（每帧取激活最高 pitch，滞回保持），或与 pYIN/TarsosDSP（零体积）做交叉验证；melodia_trick 保持开启。信源：note_creation.py L82,449-473 + TarsosDSP README。
7. **中期：直接换官方 ~16MB 完整模型量化（或 GuitarSet 微调）**：同架构同解码器、官方 TFLite 运行时，是 <30MB 约束下精度上限最高的一步；微调用 basic-pitch-torch + marl/GuitarSet。信源：README Model Runtime；marl/GuitarSet；gudgud96/basic-pitch-torch。
8. **失真 riff 兜底**：解码后八度折叠合理性检查（相邻强合并）；产品层建议用户清音/DI 扒谱。信源：arxiv 2402.15258（域适配动机）。

## 实施建议 B：音色合成改造推荐方案（首选 Karplus-Strong）

> 目标：替换"正弦+谐波+指数衰减"；零音频资产；Android Oboe/AudioTrack 直出。

**推荐架构：KS 核心（清音基色）→ 拨弦位置 comb → 力度激发滤波 → 轻过载 → cabinet 低通。**

KS 核心参数建议表：

| 模块 | 参数建议 | 依据 |
|---|---|---|
| 延迟线 | N = fs/f0，**全通分数延迟插值**（高把位 f0 高、N 小，整数取值失谐最严重）；fs 用 44100/48000 | eechina（N=fs/f0）；JOS（EKS 调音扩展） |
| 激发 | 白噪声 burst 长度 = N；预低通截止随力度 2→8kHz 映射（轻拨暗、重拨亮） | JOS（随机激发标准）；Strng noise 参数产品化 |
| 反馈环 | 二点平均 H(z)=0.5(1+z⁻¹) × 增益 ρ；ρ = 10^(−3/(f0·T60)) / \|H(f0)\|，低音弦 T60≈2.5~4s、高音弦≈0.8~1.5s（高音弦需 \|H(f0)\| 补偿防衰减过快） | JOS；T60 定义推导 |
| 拨弦位置 | comb 预滤波 y[n]−y[n−βN]，β≈0.13（桥侧拾音器，亮而有力）；β 可做成音色旋钮 0.1~0.25 | JOS（EKS pick position） |
| 过载 | tanh(k·x)/tanh(k)，清音 k≈1.5~2、过载 k≈3~6；过载后必须接低通 | deeptronic（二极管削波原型）；Apple Logic（Drive→Tone 结构） |
| cabinet | 二阶低通 fc≈4.5~5kHz，输出轻限幅 | 音箱模拟常识（推断） |
| 拨片瞬态 | 音头 5~10ms 叠加高通噪声，幅度随力度，随机种子按 (pitch,力度,时间片) hash 保证可复现 | Strng/专利佐证；可复现为工程建议 |
| 立体声/厚度（可选 M4） | 双实例失谐 ±3 cents 硬左右 | Strng 失谐控制佐证 |

**分阶段落地**：
- M1：纯 KS 清音（延迟线+噪声+平均反馈），先替换合成器输出链路，一天级工作量，真实感即可超越现状；
- M2：+ 全通调音 + 力度激发滤波 + 拨弦位置 comb；
- M3：+ 轻过载/cabinet 链（"电吉他"档位），清音/过载做成音色开关；
- M4（打磨）：双弦失谐、 sympathetic 微混响、按需 fallback 到 TinySoundFont+吉他 SF2（1~5MB 资产）做 A/B 音色对照。

**对照方案结论**：TinySoundFont（单头文件，三步出 PCM）+ Oboe 是 SF2 路线最省力载体；FluidSynth 官方支持 Android 但构建/接线复杂度高一档；Sonivox 零资产但音色固定且需自接音频输出。**首选仍是 KS**：零资产、参数与教学场景（力度/音色联动、视觉反馈同步）天然契合。

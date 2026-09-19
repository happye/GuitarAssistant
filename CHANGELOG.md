# 更新日志（CHANGELOG）

> 版本规则：**大版本 = 里程碑完成**。映射：M0→v0.1.0、M1→v0.2.0、M2→v0.3.0…… Mk→v0.(k+1).0（对应 docs/roadmap/ 的里程碑，详细描述）；**小版本 = 里程碑内的特性/修复批次**（v0.(k+1).x，一行概括）。
> 口径对齐：特性状态唯一信源是 `feature_list.json`（CHANGELOG 中的特性若未在 feature_list 置 done，注明"代码完成/待验收"）；里程碑详情见 `docs/roadmap/`；本文件按时间倒序记录。

## [未发布] v0.2.0 —— M1 乐理与识谱基础（进行中）

### 小版本
- v0.2.33（2026-09-19）：**重构第二批：专业谱面 + 和弦级输出**（架构方案 Phase A + Phase 0 剩余）。①**专业谱面上线**（A2/A3）：识谱台三态切换（列表/自绘谱面/专业谱面），专业视图=alphaTab 引擎正规排版（节奏符干/连杠/拍号/段落名/播放光标）+ AlphaSynth 波表音源整段播放 + 点音符试听单音（F202 原始规格补完，置 done）；TabDocument→alphaTex→Score 投影器 round-trip 单测守恒 ②**和弦级输出**（P0-7，D3 口径）：ChordTracker 拍同步 chroma+模板匹配+短段吸收，混音素材自动附和弦行（列表/谱面上方显示）；InputClassifier 素材分级路由 ③管线编排下沉 data 层（P0-1，修 ui→audio 违规 V4）④置信度热图（P0-8）：量化超差音红块/⚠ 标记 ⑤转写窗 50% 重叠+跨窗同音合并（P0-9①，边界音不再丢）⑥GP 导入拍号提取（A4，修 3/4、6/8 被按 4/4 切的 V7）。**这批你会看到**：识谱台多出「专业谱面」切换（正规乐谱观感+可听音色+光标）；扒混音歌多出和弦行；列表里 ⚠ 低置信标记。**这批看不到**：逐音符准确率的大幅提升——那需要 Phase 1 分离换血（htdemucs PoC 待做）与素材质量；本地包版本号已可区分（0.2.33-dev.N）。单测 166 全绿；melodia_trick 与抗混叠重采样维持待办（DEBT-009，附风险分析）
- v0.2.32（2026-09-19）：**扒谱时序层重构第一批**（架构师重构方案 Phase 0 核心，全网调研 + 架构设计落档后启动）——①**逐拍跟踪 BeatTracker**（Ellis 2007 DP 纯 Kotlin 实现，替代"固定 BPM 一次换算"：BPM 检出误差不再随曲长线性累积，合成漂移素材全曲拍位残差 ≤0.1 拍）②**音符清洗 NoteCleaner**（R4 低置信过滤/R1 同音合并/R2 泛音鬼影删除/R3 近同时成组标记——"同拍音符挤一格"的转写侧根因）③量化 v2：拍位实测插值 + IOI 自适应网格（1/16/1/8/1/4）+ 0.12 拍吸附容差（超差保留分数拍并标记低置信）④修 V5 依赖违规：audio→tab 反向依赖清零（TimedNote/调弦常量上收 core/music）⑤时序诊断日志（bpm/beatsPerBar/清洗计数）。真机 Song 2 验证：129 BPM/4 拍小节/56 小节/22 组和弦簇标记；单测 136→154。**用户可见变化有限：本版只改善拍位对齐与部分假音，音高检出质量根修在后续批次（L027）**
- v0.2.31（2026-09-19）：真机走查轮三修——①**系统返回键不再丢识谱工作台状态**（BACK 弹栈销毁 rememberSaveable 状态→CoachApp BackHandler 统一到 saveState 切回首页语义，home 返回仍正常退出）②**扒谱 BPM 自动检测真生效**（原默认预填 120 架空「留空=自动检测」；检测值/手输值分标志 bpmManuallyEdited，防跨曲 BPM 污染；检测失败不再预填 120 假装检出）③对抗审查 P1（跨曲污染）同批修；真机实测 Song 2 全链路：分离 23s（vocals/accompaniment RMS 101.94/199.11）→ 转写 → BPM 自动检出 129（真实 ~132）→ 198 音符进谱面；发现设备无 TTS 引擎（tts_default_synth=null，语音点评静音待用户装 TTS 引擎，非 App bug）；本地 136/136 单测绿
- v0.2.30（2026-09-18）：分离诊断日志（vocals/accompaniment RMS 对比）+ 扒谱重复使用防御日志 + KS 双发失谐音色（±0.15% 两弦叠加+时长人手微变，消机械感）
- v0.2.29（2026-09-18）：**分离闪退根修**——整曲一次性分离内存累计 700MB+ 超 Java 堆（OOM 为 Error 不可捕获）→ 分块流式重构（每 23.2s 一块，峰值 50MB）+ 分离进度回调接 UI
- v0.2.28（2026-09-17）：**音轨分离初版（F602 v2）**——Spleeter 2stems int8 ONNX（26MB×2 随包）端侧分离人声/鼓，伴奏轨进转写；纯 Kotlin STFT/iSTFT（radix-2 FFT round-trip 测试）；算法忠实移植 sherpa-onnx spleeter impl
- v0.2.27（2026-09-17）：Biquad 高通系数修正（此前写错成直通形状致 8 倍增益——提交时测试未绿的纪律违规，本轮修正）
- v0.2.26（2026-09-17）：**吉他聚焦预处理**——中央消除（mid/side）+ 带通聚焦（HPF80+LPF1600）立体声域预处理，混音素材转写增强；扒谱 UI 加开关
- v0.2.28（2026-09-17）：**音轨分离初版（F602 v2）**——Spleeter 2stems int8 ONNX（26MB×2 随包）端侧分离人声/鼓，伴奏轨进转写（basic-pitch）——混音素材专攻；纯 Kotlin STFT/iSTFT（radix-2 FFT，round-trip 2 测试）忠实移植 sherpa-onnx spleeter impl（soft mask 平方比归一）；扒谱页新增「分离人声/鼓」开关（默认开）；测试素材统一 testdata/（music+guitartablature）
- v0.2.25（2026-09-17）：**扒谱精度与音色双升级**（全网调研落档 .learnings/research-f602-tone-2026-09-17.md，源码级核实）——①NoteDecoder v2：官方 output_to_notes_polyphonic 算法移植（差分推断 onset/局部峰值取平台左沿/能量消耗防重复/官方 min_note_len）+ 吉他调优（min_note_len 186ms、解码层音域收口 bin19~67 根治 midi37 类低音误检）+ amplitude 输出 ②试听音色 Karplus-Strong 物理拨弦合成替换正弦谐波（延迟线+反馈低通+软过载+拨片瞬态，确定性可测）③超域音三层修复（跳过+计数上报，Song 2 实测崩溃根修）④转写超域过滤/amplitude 透传链
- v0.2.23（2026-09-17）：监督员抓崩点转移——playSequence 裸线程包 render try-catch（防爆炸 require 的 IAE 不再进程崩溃）+ 600s 上限单测；抽取错误文案引用实际 maxSeconds。深度复盘落 .learnings L019-L021/E007-E008（JsonNull 双向错误、崩点转移模式、对抗审查门禁）；对抗审查升级为发版前固定门禁（AGENTS.md 工程纪律 7）
- v0.2.22（2026-09-17）：**对抗性审查轮**（独立攻击代理：P0×0/P1×5/P2×16，报告 .learnings/adversarial-review-2026-09-17b.md）——P1×5 全修：①SSE 字符串过滤误杀 jsonMode 合法 null token（JSON 损坏源）②空流零发射不换链（UI 永远"…"）③转写内存路径 10 分钟上限下沉解码循环内（旧版先驻留 240MB 再拒→OOM）④病态长行谱 beat/时值爆炸防爆炸上限（解析段列 cap + ToneRenderer 600s require）⑤调音器麦克风占用防崩+防死循环。高优 P2×4：Room 连接进程级单例（Activity 重建堆积连接）/播放光标自然播完复位/聊天 history 40 条上限/曲库删除加确认。P2 余 12 条落 DEBT-008
- v0.2.19（2026-09-17）：播放光标跟随+自动滚动（Songsterr 式，单音试听不触发）+ 曲库曲目直通跟练（学练闭环）+ 试听时长真实化（按谱面 duration）+ 首页快捷导航
- v0.2.18（2026-09-17）：调音器音分表盘（±50 指针/±5 绿区；监督员 P1 修正角度错位 90° 的功能性错误）+ 播放光标完善（单音试听不触发滚动）+ 首页快捷导航
- v0.2.17（2026-09-17）：F704 调音器调弦预设（7 种）+ F705 节拍器高级化（细分/count-in/重音 pattern）+ L019/L020 教训落库
- v0.2.16（2026-09-17）：F707 AI 今日练习单（统计+曲库→深思链流式安排，首页卡片）
- v0.2.15（2026-09-17）：F706 BPM 自动检测（onset 自相关+八度折叠，扒谱留空即自动）+ F705 节拍器高级化（细分×1-4/预备拍/重音 pattern）+ P0 修复（.gitignore 锚定——core/music 三源文件曾被静默挡在仓库外）
- v0.2.14（2026-09-17）：M7 集百家之长首批——F701 和弦库（28 形状指板图+琶音试听+指法信息，数据 4 单测）/ F702 跟练星级连击 / F703 速度训练渐进提速提示；全网功能调研落档 .learnings/feature-research-2026-09-17.md
- v0.2.13（2026-09-17）：**F602 扒谱端侧转写引擎落地（阻塞解除）**——basic-pitch 0.4.0 PyPI wheel 自带 nmp.tflite（0.2MB 随 APK，Apache-2.0）；onnxruntime 实测 440Hz→midi69 精确命中 + music/ 素材端到端 166 音符；TranscriptionEngine（22050Hz 2s 窗推理）+ NoteDecoder 纯函数 5 单测；扒谱入口全链路（选音频→转写→弦品 DP→TabDocument 进工作台）
- v0.2.12（2026-09-17）：用户反馈三 bug——文本谱解析 v2.1（和弦行/说明行误读、拍位跨小节漂移、x 重复标记三根因修复，真实 UG 谱回归 7 用例）/ 试听重写 STREAM 模式+「▶ 播放整段」（ToneRenderer 序列渲染，stop 泄漏与 UI 线程合成修复）/ 布局多模态视觉自检基建（PNG 生成+程序化断言，亲验无偏移）
- v0.2.11（2026-09-17）：开发文档全量对齐（用户指令授权）——docs/开发方案.md §7.3 代码结构/§9 里程碑表（M1-M6 状态标注）/§11 行动清单三节刷新；App 首页「开发路线图」卡片更新为真实功能进度（原 M0 时代占位文案）；versionName 缺省跟齐
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

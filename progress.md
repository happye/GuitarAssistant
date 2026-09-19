# Progress Log — 吉他学习助手

> Agent 交接 artifact。每次会话收尾必更新：做了什么 / 当前特性状态 / 下一步建议。
> 特性状态唯一信源是 `feature_list.json`；里程碑范围与验收标准见 `docs/开发方案.md` §9。

## 2026-09-19 - Session 3: 用户暴怒反馈轮（版本号/预期管理）+ 洁癖清理 + Phase A 提前

- 用户实测 v0.2.32 后暴怒：①本地包版本号永远显示 0.2.10 无法分辨新旧 ②谱面"没变化还是烂" ③裸数字谱"不像吉他谱" ④叫停了监督员与审查代理
- **检讨落账**：L026（本地 versionName 必须自区分——build.gradle.kts 缺省改「最后tag+dev.提交数」，真机验证 0.2.32-dev.92）/ L027（每批交付必须写明用户可见变化）/ L028（排期按用户痛点强度，渲染可读性=产品存亡，Phase A 提到最优先）/ L029（卡住直说复发，阈值降为 2 次必求援）；E012/E013
- **洁癖清理**：.tmp_* 临时工件（19MB 二进制 aar/html）误入库→已 git rm+gitignore；CHANGELOG 补 v0.2.32 可见变化警示；AGENTS.md 交付纪律追加版本号+可见变化两句；项目记忆刷新（v0.2.32 状态/监督员叫停/TTS 待装）
- **结论修正（对用户的诚实口径）**：v0.2.32 时序层不改变音高检出质量（只修对齐+部分假音），音质根修=和弦级输出+htdemucs guitar stem（后续批）；"不像吉他谱"根修=alphaTab 节奏符号排版（Phase A，提前做）
- Next: Phase A（A0 spike→适配器→面板→播放）；监督员/对抗审查在用户叫停期间不再执行

## 2026-09-19 - Session 2: 扒谱重构启动（调研→架构方案→Phase 0 第一批落地）

- 用户判定扒谱质量不合格，要求启动架构师全网调研、重构不设限；另报渲染不专业（单音挤一格）+ 试听要重写
- **双线调研完成**（两个并行 general-purpose agent，15+ 轮检索交叉验证）：A=端侧扒谱现实路径（结论：混音逐音符全行业不可达——Klangio 云端 $0.1-0.2/次且自认混录差、YourMT3+ 商业混音非主奏 F1<10%；可达=干净单音 70-90%/失真 riff 段落级/混音和弦级；升级件=htdemucs_6s guitar stem MIT+TabCNN/FretNet 系+UVR 剥人声；LLM 只做闭集任务）B=渲染播放（结论：alphaTab 全量启用=补完 F202 原始规格，+15MB，AlphaSynth 替换 KS 主路径，MPL 仅链接无约束）
- **架构师方案 v1.0**（code-architect，逐文件核实）：时序层根修=逐拍跟踪+清洗+自适应量化（非换模型）；三决策用户已拍板：混音默认和弦级✓/模型按需下载✓/Phase 0+Phase A 并行先行✓；审查新发现 V4（ui→audio 违规）V5（audio→tab 违规）V6（注释失真）V7（GP 无拍号）全部入方案
- **Phase 0 第一批已落地（本批 v0.2.32）**：BeatTracker（Ellis DP 逐拍）/NoteCleaner（R1-R4）/MidiTabConverter v2（插值量化+IOI 自适应+吸附容差）/TimedNote+BeatGrid 中立类型/TranscriptionEngine 改产 TimedNote（修 V5）/时序诊断日志；真机 Song 2 验证 129BPM/4拍/56 小节/22 和弦簇；单测 136→154 全绿
- 待办（Phase 0 剩余）：P0-1 TranscriptionController（修 V4）/P0-7 InputClassifier+ChordTracker 和弦级输出/P0-8 置信度热图/P0-9 三小修（窗重叠+melodia_trick+抗混叠）；Phase A：alphaTab 渲染播放（A0 spike 前置）
- 验证: 本地 154/154；APK 真机安装；对抗审查门禁进行中

## 2026-09-19 - Session: adb 真机全走查 + 返回键丢状态/BPM 默认值双修（v0.2.31）

- **真机走查（小米 14，agent 全自动 adb）**：5 Tab 全过——调音器聆听态/节拍器运行/文本谱解析渲染点按试听/扒谱全链路/概念卡片展开/指板可视化（C 音六弦位置全对）/移调计算器（C→G=+7 半音）/我的空态，全绿无崩溃
- **扒谱全链路真机实测数据**：Song 2 全曲——分离 23s（诊断 vocals RMS=101.94 / accompaniment RMS=199.11，比值 0.51 分离有效）→ BPM 自动检出 129（真实 ~132，-2%）→ 198 音符 54 小节进谱面，无超域/截断警告；谱面 DP 弦品分布合理；试听播放/停止/光标正常
- **两 bug 修复（对抗审查 P1 清零后发）**：①系统 BACK 弹栈丢工作台状态（E011/L024）→ CoachApp BackHandler 统一 saveState 语义，真机验证往返状态保留 + home 返回仍退出 ②BPM 默认预填 120 架空自动检测 + 机器预填/手输不分（L025，审查抓的跨曲污染 P1）→ 默认空 + bpmManuallyEdited 标志
- **环境发现（非 App bug）**：小米 14 无任何标准 TTS 引擎（tts_default_synth=null）→ 语音点评/卡片点读静音，待用户装 TTS 引擎（讯飞语记/Google TTS）
- 验证: 本地 136/136 单测绿；assembleDebug APK（120MB）真机安装验证；CI push 后为准
- Next: 用户实测出谱质量（素材端侧天花板内调参）→ M1-M6 收口验收 → F202 alphaTab 评估

## 2026-09-15 - Session: M0 骨架完成（历史，来自 README / docs）

- Completed: F001-F005（M0 全部）——工程骨架与 5 Tab UI、LLM 网关（流式+视觉+路由）、教练编排器三链路、谱面引擎（TabDocument + TextTabParser + LlmTabExtractor）、MPM 调音器
- 验收：App 可安装启动；填 Key 后「测试连通」出 AI 回答；调音器能用（见 docs/开发方案.md §9 M0 行）
- Notes: 模型接入定稿（2026-09-15 真实 key 实测）——DeepSeek 全家桶主力（快答 deepseek-chat / 深思 deepseek-flash / 视觉 deepseek-v4-flash-vision-exp），方舟 GLM 文本备份（id `glm-5-3-flash-260828`，需控制台开通）；链式自动降级；细节见 docs/模型API接入手册.md

## 2026-09-15 - Session: Harness 工程化初始化

- Completed: git 仓库初始化；AGENTS.md / CLAUDE.md / .cursor/rules/、feature_list.json、progress.md、.learnings/、docs/agents/（Planner/Generator/Evaluator/会话协议/维护）、docs/design-docs|references|exec-plans|product-specs、scripts/sync-agent-skills.sh
- Status: 本 session 只做脚手架，未动 app/ 源码
- Notes: feature_list.json 从 README 当前状态表 + 开发方案 §9 生成，M0=done、M1-M5=pending；浏览器自动化适配为 adb 截图/uiautomator（原生 App 无浏览器）

## 2026-09-15 - Session: F104 + 构建环境攻坚

- Completed: F104 代码（TabStudioScreen 粘贴解析展示）+ TextTabParserTest 4 用例（首次单测，DEBT-001 开账）；wrapper 全套生成（DEBT-002 偿还）；GitHub Actions CI（test + assembleDebug + APK artifact）
- 排障: 本地 test 失败根因定位（实验证实）= L012 中文路径 + JDK 原生层 GBK 读 @argfile；JDK17/21、junction 均不可解 → 单测以 CI 为准
- Status: F104 done/passes（CI 绿 + 本地 5/5）；F101 代码完成待真机验收（流式+多轮）；CI 全绿（单测+APK artifact）
- Blockers: 方舟 GLM 待用户控制台开通（文本备份链不可用）；小米 14 真机未连接（装机验收待做）
- Next: F102 乐理概念卡片 / F105 节拍器；M2 前置调研（图片逐句讲解、音频扒谱）由并行调研 agent 进行中

## 2026-09-16 - Session: 留痕体系 + M1 全特性代码完成

- Completed: CHANGELOG + docs/roadmap/（ROADMAP + M0-M6 七文档）+ 版本规则（Mk→v0.(k+1).0）；F107 对话管理（Room v1→v2，监督员 P1 流式删会话容错已修，done）；F102/F103/F105/F106 代码完成（待真机验收）；Room schema 1.json/2.json 入库
- 验证: compileDebugKotlin/assembleDebug 全绿；CI 绿（faf8bd1 单测通过）；APK 33MB
- Status: M1 代码面收口，**里程碑关闭条件 = 真机验收 F101/F102/F103/F105/F106**
- Next: 真机验收（用户连接小米 14）→ M1 收口 v0.2.0 → M2 开工（F201 拍谱识谱优先）

## 2026-09-16 - Session: F201 拍谱识谱（M2 开工）+ 用户反馈 bug 修复

- Completed: F201 代码（系统相机拍照 FileProvider + Photo Picker 选图 → 视觉识谱 → 展示 + AI 讲解流式对话框）；降级原语重构 core/llm/LlmFallback（编排器+识谱管线共用，L010/L011 契约单点维护）；用户反馈 bug 修复：识谱页输入区可滚动 + 粘贴框限高
- 验证: compile/assembleDebug 全绿；本地单测 5/5；CI 绿（650f7ec）；监督员复审通过（P1 类型错配在提交前由 main 修掉、布局修复结构确认）
- Status: F201 代码完成待真机验收；M2 剩余：F202-F206（alphaTab 渲染/GP 导入/移调计算器/逐句讲解 F207-F209）
- Next: F202 谱面渲染（alphaTab 决策点）或 F206 移调计算器（纯 JVM 快交付）

## 2026-09-17 - Session: 8 小时迭代专项（M7 集百家之长）+ 三 bug 根修 + F602 转写引擎落地

- 用户目标：8 小时不间断开发、自行找活、全网调研集百家之长；点名三 bug（谱面试听听不了/文本谱解析错/音符位置偏移）与"转写引擎都没有"
- **F602 端侧转写引擎落地（最大项，阻塞解除）**：实证 basic-pitch 0.4.0 PyPI wheel 自带 nmp.tflite（0.2MB，Apache-2.0）→ onnxruntime 本机实测 440Hz→midi69 精确命中 + music/ 素材端到端 166 音符 → TranscriptionEngine（22050Hz 2s 窗）+ NoteDecoder 纯函数 → 扒谱全链路（选音频→转写→弦品 DP→自动 BPM→TabDocument 进工作台）
- **三 bug 根修**：文本谱解析 v2.1（弦行判定收紧/拍位按段归零/x 标记跳过——监督员抓出首版拍位"声称已修未修"后真修，7 真实 UG 回归用例）；试听重写 STREAM+「▶ 播放整段」（ToneRenderer 序列渲染，stop 泄漏/UI 线程合成修复）；布局偏移以多模态视觉自检确认已修（PNG 生成+程序化断言基建）
- **M7 新特性（F701-F709）**：和弦库 28 形状（数据 4 单测）/跟练星级连击/速度训练提示/调音器 7 调弦预设+音分表盘/节拍器细分+count-in/BPM 自动检测（5 单测）/AI 今日练习单/播放光标跟随+自动滚动/首页快捷导航/曲库曲目直通跟练
- 调研落档：.learnings/feature-research-2026-09-17.md（8 组产品 40 功能点，Top8 排序+负清单）
- 监督员全程抓实错：TunerMeter 角度错位 90°（调音功能性错误）、TonePlayer stop 泄漏、gitignore music/ 无锚定吞 core/music 三源文件（P0，CI 必炸）等全部修复
- **流程教训（L019/L020）**：gitignore 锚定语义、`&&` 硬链（连续两次编译错误误推后立规矩）
- 验证: 124/124 单测全绿；每批 APK；发版 v0.2.12-v0.2.19（GitHub Releases 全部挂包）
## 2026-09-18 - Session: 真机调试闭环建立 + Spleeter 分离落地 + 对抗审查轮（v0.2.21-v0.2.30）

- **adb 真机调试闭环建立（L022）**：用户 USB 连接小米 14 + 开 USB 调试/USB 安装——screencap 截图多模态亲看 UI、input tap 操作、logcat 抓堆栈、push 推素材。协作模式（L023）：用户操作 App，agent 监控 logcat+读截图
- Completed 本轮：三 bug 修复（满屏 null JsonNull 根修/首页跳转删除/转写张量 shape 动态分配）→ 对抗审查轮（P1×5 全修+高优 P2×4）→ 扒谱超域崩溃根修（Song 2 贝斯检出 midi37）→ KS 双发失谐音色 → **Spleeter 音轨分离初版落地**（2stems int8 ONNX 26MB×2 随包 + 纯 Kotlin STFT/iSTFT）→ 分离闪退根修（分块流式 50MB 峰值）→ 真机实测分离走通
- 用户实测确认: 分离不再闪退（分块修复生效）；出谱质量与音色仍待迭代（0.2MB 转写模型天花板 + Spleeter 2stems 无吉他 stem 的诚实边界）
- 版本: v0.2.21→v0.2.30 连发（Releases 挂包；v0.2.29 上传超时→release.yml 分步+重试修复）
- 教训: L022（adb 闭环）/L023（控制权协作）/E009（分离 OOM）/E010（大文件上传）；AGENTS.md 纪律 7 对抗审查门禁落地并两轮验证有效
- Next: 用户实测分离效果（分离诊断日志读数）→ 出谱质量调参 → Spleeter v1 深化（v2 demucs.cpp JNI 离线模式已评估不立项，见 DEBT-010）→ M1-M6 真机验收收口

## 2026-09-17 - Session: 用户实测反馈轮（三 bug）+ 对抗性审查门禁确立

- 用户实测 v0.2.20 反馈: 首页顶部跳转冗余+无法返回 / 转写推理 TFLite 张量形状崩溃 / 空记录练习单满屏 null / "每个 UI 界面应多模态预览测试"
- Completed: ①SSE JsonNull 根修（as? JsonPrimitive 放行 JsonNull 子类致深思链满屏 null；首版补丁字符串过滤又误杀 jsonMode 合法 token——同点双向连错，L019）②首页跳转 Row+onNavigate 参数全清 ③TranscriptionEngine 输出张量按实际 shape 动态分配+激活总量自校准（TFLite 图序≠onnx，E007）④对抗性审查代理（P1×5 全修：SSE 误杀/空流换链/转写上限下沉/防爆炸 cap/麦克风占用防御）+高优 P2×4（Room 单例/光标复位/history 上限/删除确认）⑤监督员再抓崩点转移（P1）修复⑥多模态视觉自检基建（TabLayoutPreviewTest 纯 Kotlin PNG，布局正确性已亲验）
- 教训: L019（JsonNull 双向错误）/L020（崩点转移必答"新失败路径被谁捕获"）/L021（对抗审查=发版前门禁，AGENTS.md 纪律 7）
- 验证: 124/124 单测全绿；v0.2.21→v0.2.23 三连发（Releases 在线）
- Next: 用户真机验证三 bug 修复 + music/ 素材全链路转写；F602 转写质量真机评估（0.5/0.06 参数下失真素材碎音属素材本质）

## 2026-09-17 - Session: 用户反馈五连修（签名/持久化/长音频/识谱观感与准确性）

- 用户实测反馈（v0.2.7 APK）：①切界面丢状态 ②debug APK 签名不一致无法覆盖安装、版本不变 ③文档滞后 ④识谱不准+太丑（要求参考网上资源）⑤重要点要记忆；music/ 下两首测试 riff（Blur-Song 2、Led Zeppelin-Whole Lotta Love，后者 5:30 抽取报错）
- Completed: 签名统一（keystore/debug.keystore 入库+signingConfigs，CI/本地同签名）+ 版本注入（-P pkgVersionName/Code，release.yml 按 tag 注入，versionCode=提交数）；9 个 Screen 的用户内容态 rememberSaveable+JSON Saver（ui/Saveable.kt）；AudioPcmExtractor 流式化（StreamingResampler 跨块插值=尾块对齐数学，30 分钟上限，106 用例含逐点一致性）；TAB_TO_JSON v2+TAB_VERIFY（CoVe 自校验，回退阈值防修正版缩水）；TabRenderPanel v2 观感（Songsterr 惯例：品数落线/4/4 拍位比例/技巧记号/段落条/弦名/终止双线）+ TabLayout 固定 4/4 口径；监督员 P1 imageBase64 出 saveable
- 决策: alphaTab 完整渲染列 DEBT-007（需 AlphaSkia 原生链+Bravura 字体，APK +10~20MB，未引入）；music/ 素材 gitignore（版权歌曲不入库）
- 验证: 106/106 单测全绿；APK 50.4MB 出炉（versionCode 动态=提交数，见下条 gradle 修正）
- 阻塞: GitHub 网络间歇不通（用户代理 7890 时关）→ 每小时自动重试 push（cron），恢复后补推 main+tags 并验证 v0.2.8 Release

## 2026-09-16 - Session: M3-M6 代码面全部落地 + Release 机制上线（目标驱动长会话·第二段）

- Completed: 用户目标"把 M1-M6 所有能做的任务都做完"——**M3 视觉教练**（F301 LiveHandLandmarker LIVE_STREAM+GPU/骨架叠加、F302 PostureRules 折腕近似/塌指/拇指/出框 7 单测、F303 关键帧点评+TTS）；**M4 音频反馈**（F401 RiffMatcher 对齐判定、F402 音高曲线+闷音灰段、F403 以音频为准提示、F404 未标定不显示的诚实口径）；**M5**（F501 WeeklyReview+周复盘卡、F502 曲库 Room v3 迁移+保存/搜索/进度/加载、F503 音色向导）；**M6 基建**（F601 AudioPcmExtractor 抽 16k 单声道 PCM+扒谱入口、F603 MidiTabConverter 弦品 DP+量化；F504/F604 评估决策=暂不做/不上线 v1 落档）
- 阻塞如实标注: F602（basic-pitch 无官方 ONNX，需 Python 转换+推理验证）；F504 实测阻塞于方舟开通
- **Release 机制**: .github/workflows/release.yml（tag v* → 测试 → APK → GitHub Releases 挂安装包），v0.2.5 已发布成功（GuitarCoach-v0.2.5-debug.apk 47.6MB 远程可装）；softprops action 解析失败两次后改用 runner 自带 gh
- 验证: 103/103 单测全绿（run-tests-local.sh）；每特性本地 assembleDebug 出 APK（交付纪律）；CI 逐笔绿
- 监督员: 本段抓 4 个实错修复——dex 合并原生崩溃（gradle.properties 降并发）、midiFromFrequency 截断半音级偏差（P1）、AudioPcmExtractor 装箱 OOM（P1）、相机不 unbind+executor 竞态（P1）等
- Next: 真机验收 M1-M6 全部待验收特性 → M1 收口 v0.2.0 → F602 待 ONNX 转换工具链 → 后续打磨


- Completed: F206 移调计算器（CI 绿 **done**）；F209 指法 DP（CI 绿 **done**，横按物理口径：同品异弦同指=合法横按，集成进 F207）；F204 结构化讲解（识别与讲解分离，explainTabDocument 替代看图讲解）；F203 编辑修正（TabBarEditDialog + withBar 回写，讲解基随修正数据）；F202 谱面渲染/点按试听（自绘 Canvas + TonePlayer；alphaTab 待 gradle 确认）；F208 卡片点读（TTS + PhraseCache 离线缓存 + 原图对照）；M3 前置 hand_landmarker.task 入库 assets（unzip 校验通过）
- 验证: 每特性先本地 JUnitCore（**产品化为 scripts/run-tests-local.sh**，L013：临时目录必须成对刷新）后 CI 绿；单测从 5 → 73 个
- 监督员: 常驻 subagent 本轮抓 6 个实错全部修复——P1×3（FingeringSolver 全空弦槽崩溃、TabEditPanel rows[i] 赋值与 Icons 导入编译错）+ P2×3（TonePlayer audio→tab 依赖红线、tech-debt 编号重号、TTS isReady 非 Compose State）；另促成两处超 300 行文件拆分（PhraseCoach→PhraseExplainModel、DEBT-005 记账）
- Status: M2 代码面 8/9 完成（F205 阻塞）；新特性均待真机验收；feature_list: done 9 项（F001-F005、F104、F107、F206、F209）
- Next: 用户连小米 14 → M1+M2 一起真机验收 → M1 收口 v0.2.0；用户确认 gradle 依赖后做 F205（alphaTab）；然后 M3

## 2026-09-16 - Session: M2 代码面收口（F202-F209 全部落地）+ F206 done + M3 前置

- Completed: F206 移调计算器（CI 绿 **done**）——本标题行原被误置，2026-09-17 洁癖整理时修正锚位（重复段清理）

# 交接文档（HANDOFF）——新对话从这里开始

> 更新：2026-09-19 ｜ 用途：用户开启新对话时的唯一入口文件。读完本文 → 按「开工三步」走，即可无缝续作。
> 本文是快照；**实时状态以 progress.md / feature_list.json / CI 为准**（三者永远比本文新）。

## 一、开工三步（每次新会话必做，与 CLAUDE.md 一致）

1. `git log --oneline -10` + 读 `progress.md`（会话交接日志，最新块在上/下皆可，按日期找）
2. 读 `feature_list.json` 选一个 pending 特性（**一次只做一个**）
3. 读根目录 `AGENTS.md`（架构红线 6 条 + 工程纪律 6 门禁）——尤其红线 3（依赖方向）、纪律 3（验证靠跑）

## 二、项目一句话 + 硬约束

- **是什么**：Android 电吉他自学 App（Kotlin+Compose，包名 com.guitarcoach.app）：识谱 · 视觉纠手型 · 乐理问答 · 调音。目标设备**小米 14（arm64-v8a）**，其他设备后置
- **模型**：DeepSeek 官方主力（deepseek-chat 快答/flash 深思/v4-flash-vision-exp 视觉）+ 方舟 GLM 文本备份（`glm-5-3-flash-260828`，**用户尚未在控制台开通**）。四客户端链式降级。思考控制靠选 id 不靠参数。Key 在 local.properties（已 gitignore）
- **硬红线**：只引 Apache/MIT 依赖；密钥不入库；TabDocument 是谱面唯一模型；提示词只放 core/coach/CoachPrompts；流式降级契约（L010：零输出前才降级）与取消穿透（L011）在 core/llm/LlmFallback
- **GitHub**：happye/GuitarAssistant（用户已授权验证后直接 push，不询问）

## 三、构建与测试（本机特殊，别踩坑）

```bash
bash scripts/build.sh assembleDebug        # 构建（项目内 toolchain：JDK21+Gradle8.13，勿装全局）
bash scripts/run-tests-local.sh            # 本地单测（L012 绕法已产品化；166 用例）
bash scripts/build.sh compileDebugKotlin   # 本地快速验证
```
- **L012/L013（.learnings）**：中文项目路径 + JDK 原生层 GBK 读 @argfile → Gradle 的 test 任务本地不可行；本地单测走 run-tests-local.sh（ASCII 临时目录 JUnitCore，main/test 成对刷新）。正式口径以 GitHub Actions 为准
- **每笔功能改动收尾必须本地 assembleDebug 出 APK 并报告路径**（AGENTS.md 交付纪律，用户硬性要求）
- 本机 Android SDK 36 已装（local.properties 已配 sdk.dir）；WSL 无关；Windows GBK 控制台乱码属正常

## 四、当前状态快照（2026-09-19 · 扒谱+渲染重构第一二批已落地）

- **里程碑**：M0 ✅（v0.1.0）｜M1-M6 代码面全部落地（**F602 端侧转写引擎已落地**，basic-pitch TFLite 0.2MB 随 APK）｜M7 集百家之长扩展进行中（F701-F709 done：和弦库/游戏化/提速/调弦预设/音分表盘/节拍器高级化/BPM 自动检测/今日练习单/光标跟随/快捷导航/曲库直通跟练）——**关闭条件=真机验收**
- **特性计数**：以 feature_list.json 为准（F202 已 done——alphaTab 专业渲染落地；F602 口径已转向和弦级+分离换血路线）
- **Release**：tag v* → release.yml 自动测试+出 APK+挂 GitHub Releases；**v0.2.33 在线**（签名统一版：仓库内 keystore/debug.keystore，任意来源 APK 可覆盖安装；versionCode=提交数/versionName：CI=tag 号、本地=最后tag+dev.N 可区分，L026）。用户网络：代理 127.0.0.1:7890 间歇关闭，push 失败先试直连 `git -c "http.https://github.com.proxy=" push`，都挂就定时重试
- **CI**：全绿；**本地单测** `bash scripts/run-tests-local.sh` 166 用例全绿；每特性收尾本地 assembleDebug 出 APK + docs/App 内文案双同步（AGENTS.md 交付纪律 + L014）
- **扒谱+渲染重构进行中（2026-09-19 立项，用户已拍板）**：方案 docs/exec-plans/rework-plan-2026-09-19.md；三拍板=混音默认和弦级（D3）/模型按需下载（D2）/Phase 0+Phase A 并行先行；**v0.2.33：Phase A done（专业谱面/AlphaSynth 播放/点按试听，F202 done）+ Phase 0 剩余 done（和弦级/Controller/热图/窗重叠/GP 拍号）**；v0.2.32 用户可见变化有限已在 CHANGELOG 声明（L027）；melodia_trick 与抗混叠重采样维持 DEBT-009 待办
- 本段特性明细：M3 视觉教练（跟踪/警报/点评+TTS）、M4 跟练判定+曲线+互验、M5 曲库 Room v3+周复盘+音色向导、M6 扒谱基建（抽 PCM+弦品 DP）
- **真机走查轮（2026-09-19）**：5 Tab 全过 + 扒谱全链路实测（Song 2：分离 23s/RMS 诊断、BPM 自动检出 129、198 音符进谱）+ 两 bug 修复（BACK 丢状态 E011、BPM 默认值架空自动检测 L025）；小米 14 无 TTS 引擎（语音点评静音，待装 TTS）；F205 GP 导入（alphaTab 1.8.4 引入经目标确认）

## 五、下一步（按优先级）

1. **真机验证 v0.2.33**（设备中途离线未走查）：专业谱面渲染/播放/点按试听/和弦行/热图——恢复接线后第一件事
2. **Phase 1 分离换血**：htdemucs_6s PoC（PC 侧模型转换 + 真机门禁 RTF≤1.5/内存≤1.5GB）→ guitar stem 进转写；失败降级 UVR-MDX-Inst（28MB 按需下载）
3. **Phase 2 云端**（待 D1 拍板）：Klangio 免费额度 50 次先行验证
4. 出谱质量数据继续收（分离/时序/分级诊断日志已埋：`adb logcat -s GuitarCoach`）

## 六、待用户操作（阻塞项，别干等）

- **小米 14 安装 TTS 引擎**（讯飞语记 / Google TTS 任一并在系统设置设为默认）——语音点评（F303）与卡片点读（F208）的前置；当前 `tts_default_synth=null`，TTS init failed status=-1
- 火山方舟控制台开通 `glm-5-3-flash-260828`（开通后按 docs/模型API接入手册.md §2.4 回补 3 项实测）
- 可选：手机支架（M3 视觉教练外设）

## 七、多 Agent 运行机制（本项目的增效约定）

- **常驻代码监督员：用户已于 2026-09-19 叫停，不要重建**（新会话注意）。AGENTS.md 纪律 7 的发版前对抗审查门禁在叫停期间同样不执行，除非用户重新要求
- **调研/扫描类子任务**随时并行派发（用户明确要求善用 subagent）；WebSearch 工具可能 403 → 用 mcp__bocha__bocha_web_search，WebFetch 部分域名被拦（调研类 agent 实测：Bocha 也可能 403，兜底 curl 直抓 + GitHub API + Maven 直查）
- **/loop 重试观察员**（用户要求）：LLM 发送失败（429/额度类）每小时重试，额度 4h 刷新；非额度错误不重试。新会话若还需此机制则重建

## 八、信息地图（谁管什么，防臃肿）

| 文件 | 职责 |
|---|---|
| docs/开发方案.md | 战略/痛点/可行性/架构/技术方案（§5 可行性、§8.7 逐句讲解、§8.8 扒谱） |
| docs/roadmap/ROADMAP.md + M0-M6.md | 里程碑详情/验收/状态 |
| feature_list.json | 38 特性唯一状态信源（done 必须 passes=true） |
| CHANGELOG.md | 版本留痕（大版本详细、小版本一行，规则见文件头） |
| progress.md | 会话交接日志（每会话必更） |
| docs/模型API接入手册.md | 实测接入参数/curl/坑点 |
| .learnings/LEARNINGS.md | L001-L029 教训（L010-L013/L017/L020-L021/L024-L025 是代码级/流程级契约） |
| docs/agents/ | Planner/Generator/Evaluator 协议 |
| docs/exec-plans/f504-realtime-eval.md / f604-cloud-eval.md | F504/F604 评估决策记录 |
| scripts/run-tests-local.sh | 本地单测一键入口（L012/L013 绕法产品化） |

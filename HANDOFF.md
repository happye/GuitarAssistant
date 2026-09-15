# 交接文档（HANDOFF）——新对话从这里开始

> 更新：2026-09-16 ｜ 用途：用户开启新对话时的唯一入口文件。读完本文 → 按「开工三步」走，即可无缝续作。
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
bash scripts/build.sh test                 # 本地会失败！见下
bash scripts/build.sh compileDebugKotlin   # 本地快速验证
```
- **L012（.learnings）**：中文项目路径 + JDK 原生层 GBK 读 @argfile → 本地 Gradle 单测不可行（JDK17/21 都如此）。**单测以 GitHub Actions 为准**（push 即触发，CI 绿 = 验证完成；Actions 页可下载 APK artifact）
- 本地快速跑单测的绕法：拷编译产物+依赖 jar 到 ASCII 临时目录手工跑 JUnitCore（详见 L012 防复发栏，未产品化）
- 本机 Android SDK 36 已装（local.properties 已配 sdk.dir）；WSL 无关；Windows GBK 控制台乱码属正常

## 四、当前状态快照（2026-09-16 交付点）

- **里程碑**：M0 ✅（v0.1.0）｜M1 代码面 7/7 完成（v0.2.x 小版本已记），**关闭条件=真机验收**｜M2 已开工（F201 done）
- **特性计数**：38 项 —— 9 done（F001-F005、F104、F107、F201…）、7 代码完成待真机验收（F101/F102/F103/F105/F106 + M1 相关）、其余 pending
- **CI**：全绿（main @ de053c5 之后均为绿）；APK 33MB（arm64）可在 Actions artifact 下载
- **用户反馈 bug 已修**：识谱页文本框撑爆布局（09a8507）
- 进行中无未提交代码，工作区干净

## 五、下一步（按优先级）

1. **真机验收**（等用户连小米 14）：`adb devices` → `adb install -r app/build/outputs/apk/debug/app-debug.apk` → 按 docs/roadmap/M1 文档逐特性验收 → 全过后 M1 收口（CHANGELOG v0.2.0 大版本 + versionName 0.2.0 + ROADMAP 状态表）
2. **M2 继续**：F202 谱面渲染（决策点：alphaTab 1.8.4 MPL-2.0 引入 vs 自绘 Canvas）或 F206 移调计算器（纯 JVM 快交付）；然后 F207 逐句大白话讲解管线（方案 §8.7：结构化优先 + CoVe 覆盖审计）
3. **M3 前置**：下载 hand_landmarker.task 入 assets（地址在 HandLandmarkerHelper 注释与 README）

## 六、待用户操作（阻塞项，别干等）

- 火山方舟控制台开通 `glm-5-3-flash-260828`（开通后按 docs/模型API接入手册.md §2.4 回补 3 项实测）
- 小米 14 USB 连接（授权 adb 调试）
- 可选：手机支架（M3 视觉教练外设）

## 七、多 Agent 运行机制（本项目的增效约定）

- **常驻代码监督员**（用户要求，随开发停止而停止）：只读、300s 巡检 git 变更集 + 按审查清单报告（P0 密钥/P1 编译崩溃/P2 建议），SendMessage 给 main。**新会话重建方式**：spawn 一个 general-purpose 后台 agent，职责 = ①开工先读 AGENTS.md + .learnings/LEARNINGS.md + docs/模型API接入手册.md ②每轮只审 git 变更集 ③findings 按协议汇报 ④收到「停止监督」即退场。开发间歇期可 TaskStop 停掉，SendMessage 同一 agent 可恢复上下文
- **调研/扫描类子任务**随时并行派发（用户明确要求善用 subagent）；WebSearch 工具可能 403 → 用 mcp__bocha__bocha_web_search，WebFetch 部分域名被拦
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
| .learnings/LEARNINGS.md | L001-L012 教训（L010/L011/L012 是代码级契约） |
| docs/agents/ | Planner/Generator/Evaluator 协议 |

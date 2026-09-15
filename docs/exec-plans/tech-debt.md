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

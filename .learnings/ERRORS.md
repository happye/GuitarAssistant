# ERRORS — 失败与坑记录

> 出现失败/异常必记：现象 | 复现条件 | 根因 | 修复 | 验证命令。
> 可泛化的教训同时进 `LEARNINGS.md`（带防复发措施）。

## E001 GLM 思考 token 成本翻倍

- 现象: 调用 glm-5.3-flash 不传（或传错）reasoning_effort 时，回复前有大量不可见思考 token，成本约为预期 2 倍以上
- 复现条件: 不带 reasoning_effort 字段调用任意 GLM 生成任务
- 根因: 官方缺省回退 max（2026-09 查证，docs/模型API接入手册.md §1.4）
- 修复: 编排层所有 ChatSpec 显式 `reasoningEffort="low"`（CoachOrchestrator 三条链路均已设置）
- 验证命令: 手册 §1.3 curl 冒烟测试（Key 从 local.properties 读，勿入库勿外传）；App 内「测试连通」对照 token 账单

## E002 DeepSeek 回复空白 / 先吐思考内容

- 现象: json_object 模式下可能输出空白直到截断；思考模式下流式先吐 delta.reasoning_content 再吐 content
- 复现条件: response_format=json_object 但提示词未声明"只输出 JSON"；思考模式默认值漂移
- 根因: DeepSeek 思考默认值不稳定 + JSON 模式需提示词配合（手册 §2.3）
- 修复: 显式 `enableThinking=false`；jsonMode 时提示词同声明只输出 JSON；网关只取 delta.content 并判空
- 验证命令: 手册 §2.2 curl 冒烟测试 + App 内「测试连通」

## E003 本机构建链缺位（截至 harness 初始化时）

- 现象: 无法本地出 APK——机器只有 Java 8；gradle/wrapper 只有 properties，缺 gradle-wrapper.jar 与 gradlew/gradlew.bat 脚本
- 复现条件: 直接运行 `./gradlew` 或 `gradle` 命令
- 根因: 未装 JDK 17；wrapper 未生成入库
- 修复: 安装 JDK 17（如 Temurin 17）→ `gradle wrapper --gradle-version 8.13` → wrapper 产物入库（README「环境要求」同款步骤）
- 验证命令: `./scripts/build.sh assembleDebug`（产物 app/build/outputs/apk/debug/app-debug.apk）；已记 docs/exec-plans/tech-debt.md DEBT-002

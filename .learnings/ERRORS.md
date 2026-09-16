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

## E004 方舟 GLM 调不通：点号 id 404 + ModelNotOpen 伪装成 404

- 现象: `glm-5.3-flash`（点号版）请求 404；改用正确 id `glm-5-3-flash-260828` 后仍报 404，实际是账号未开通该模型（`ModelNotOpen`）
- 复现条件: 用点号 id 调方舟；或用正确 id 但方舟控制台未在「开通管理」开通该模型
- 根因: 方舟模型 id 命名带日期后缀不用点号；ModelNotOpen 错误伪装成 404（2026-09-15 真实 Key 实测，来源：并行接入任务）
- 修复: id 改为 `glm-5-3-flash-260828`；到方舟控制台开通模型后重试（当前 GLM 作为文本备份链，开通前链式降级跳过它）
- 验证命令: docs/模型API接入手册.md 的方舟 curl 冒烟（Key 从 local.properties 读，勿入库）

## E005 DeepSeek 思考开关参数静默无效

- 现象: 传 `enable_thinking` 无任何效果（静默忽略）；对 `deepseek-chat` 传 `reasoning_effort` 反而强制打开思考，快答链路变慢变贵
- 复现条件: 对 DeepSeek 官方端点传思考相关参数（2026-09-15 实测）
- 根因: DeepSeek 用不同 id 区分思考形态（`deepseek-chat`=关 / `deepseek-flash`=开），参数通道已废弃
- 修复: 代码不传思考参数；按任务选 id（快答→deepseek-chat，深度→deepseek-flash）；见 LEARNINGS L002/L008
- 验证命令: docs/模型API接入手册.md 的 DeepSeek curl 冒烟，对比两个 id 的响应延迟与 token 账单

## E006 git push 网络间歇失败（代理与直连都不稳）

- 现象: push 时而报 "Failed to connect ... via 127.0.0.1:7890"（用户代理关闭），时而直连 github.com 443 超时；深夜时段两路同时不通
- 复现条件: 用户 clash 类代理（~/.gitconfig 的 http.https://github.com.proxy=127.0.0.1:7890）关闭或直连被墙
- 根因: 网络环境依赖本地代理开关；URL 级代理配置覆盖需用 `git -c "http.https://github.com.proxy=" push`（普通 -c http.proxy= 无效）
- 修复: 双路重试（默认 push → 直连覆盖 push）；两路都挂时定时（每小时）重试并推送 tags，成功后撤任务（本会话已实践：深夜不通、清晨恢复，v0.2.8-v0.2.11 全部补推成功）
- 验证命令: push 后 `git log origin/main..HEAD` 应为空

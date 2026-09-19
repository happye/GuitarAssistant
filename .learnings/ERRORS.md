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

## E007 转写推理 TFLite 输出张量 IndexOutOfBounds 类崩溃（用户真机）

- 现象: Cannot copy from a TensorFlowLite tensor (StatefulPartitionedCall:2) with shape [1,172,88] to a Java object with shape [1,172,264]
- 复现条件: 真机跑转写（onnx 实测的输出图序 ≠ TFLite 转换后图序，按 index 硬编码 0=264 通道必崩）
- 根因: 跨运行时的多输出模型输出顺序不可跨平台假设（StatefulPartitionedCall 打包会重排）
- 修复: 运行时按实际 shape 动态分配（264=contours 丢弃；两个 88 通道用激活总量自校准区分 note/onset——持续激活远大于稀疏触发）；帧数读实际值；形状异常时报全部输出 shape
- 验证命令: 真机 music/ 素材走"扒谱→抽取→转写"全程（v0.2.21+）

## E008 深思链流式输出满屏 nullnull（用户真机）

- 现象: 今日练习单（deepseek-flash 深思链）生成结果显示 "nullnullnull…"；周复盘/音色向导同病
- 复现条件: 任何走 deepText 链的流式 UI（思考阶段每 chunk 的 delta.content 为 JSON null）
- 根因: SSE 解析 `as? JsonPrimitive` 放行了 JsonNull（子类），其 content 属性即字面 "null"；修复补丁的字符串过滤又误杀 jsonMode 合法 null token（E 系与 L019 同源，见 L019 双向错误记录）
- 修复: `is JsonNull` 类型过滤（唯一精确解）+ streamWithFallback 空流视为失败换链（LlmFallback P1）
- 验证命令: 空记录场景点「生成今日练习单」应输出可读练习建议而非 null

## E009 扒谱分离闪退（用户真机，Song 2 + Spleeter 分离开）

- 现象: 开「分离人声/鼓」开关选音频直接闪退，无任何 UI 错误提示
- 复现条件: 5 分半歌曲 + Spleeter 分离开 → 整曲一次性 STFT+分离，内存分配累计 700MB+（x 张量 120MB×2 + spec×2 + mask×2 + STFT 结果 116MB）远超 App Java 堆
- 根因: OOM 是 Error 不是 Exception，UI 的 catch(Exception) 接不住；整曲一次性处理架构性缺陷
- 修复: separate() 分块流式重构——每 23.2s（512 帧）独立 STFT→双模型→mask→iSTFT，峰值内存 ~50MB；分离进度回调接 UI
- 验证命令: 真机 Song 2 开分离开关全链路（v0.2.29+ 用户实测分离走通）

## E010 CI Release 大文件上传超时（v0.2.29）

- 现象: gh release create 附 120MB APK 上传失败，Release 无包（构建本身成功）
- 复现条件: release.yml 单步直传大文件，runner 网络抖动
- 根因: gh 一次直传大文件无重试
- 修复: 分步——先 gh release create（不带资产）→ gh release upload 循环重试 5 次（--clobber）
- 验证命令: 重打 tag 后 Releases 页资产出现（v0.2.29 重跑后验证）

## E011 系统返回键丢识谱工作台全部状态（真机走查发现）

- 现象: 识谱工作台扒出 198 音符谱面后按系统返回键，再进识谱 Tab——文档、粘贴内容、BPM 输入全部清空
- 复现条件: 识谱 Tab 载入文档 → keyevent 4（系统 BACK）→ 底部导航重进识谱。底部 Tab 切换（saveState 路径）不受影响，仅 BACK 路径丢
- 根因: Navigation Compose 弹栈直接销毁 back stack entry，无 save-on-pop；TabStudioScreen 的 rememberSaveable 状态只在底部导航 popUpTo{saveState=true} 路径存活
- 修复: CoachApp 加 BackHandler——非 home 路由返回时 navigate 到 home 并 saveState（与 Tab 切换同语义）；home 返回仍正常退出（dumpsys 焦点验证）
- 验证命令: 识谱→填示例→解析→keyevent 4→底部点识谱→粘贴内容+解析结果应在（v0.2.31 真机验证通过）

## E012 本地包版本号不可区分（用户实测暴怒）

- 现象: 用户手机上 App 永远显示 v0.2.10，而交付报告声称装了新代码——用户无法验证，判定"没更新/说了谎"
- 复现条件: 本地 assembleDebug 构建（不传 -PpkgVersionName）→ versionName 走 build.gradle.kts 写死的缺省值
- 根因: 版本注入只在 CI tag 构建路径生效；本地缺省值写死后从未随开发推进，且我早看到显示却未告知用户
- 修复: 本地缺省 versionName = git 最后 tag + dev.提交数（git describe --tags --abbrev=0 + rev-list --count），如 0.2.32-dev.92；tag 构建仍由 -PpkgVersionName 覆盖
- 验证命令: bash scripts/build.sh assembleDebug && adb install -r ... && adb shell dumpsys package com.guitarcoach.app | grep versionName → 应显示 x.y.z-dev.N（2026-09-19 15:39 真机验证 0.2.32-dev.92）

## E013 选择器内连续盲试违反协作约定（L029 复发）

- 现象: MIUI 文件选择器里为找 mp3 连续 5+ 次滑动试错，用户被迫看反复无效的屏幕滚动
- 复现条件: 系统选择器列表顺序动态变化，按记忆坐标盲滑
- 根因: 把"再滑一次"当便宜动作，未执行"卡住直接开口要人帮忙"的记忆规则
- 修复: 行为规则升级为"同一目标连续 2 次未果即停手求援"（L029）；本例最终靠截屏逐次定位解决
- 验证命令: 无自动化——协作类，靠 L029 阈值执行

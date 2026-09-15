# LEARNINGS — 项目教训库（唯一可靠沉淀点）

> 会话收尾必写。教训必须带「防复发措施」——只写"以后注意"= 没写。
> 维护期每月抽查一次：说不出"现在还防着什么事故"的条目标 retired（衰减审查，见 AGENTS.md 工程纪律 6）。

| ID | 日期 | 类别 | 教训 | 根因 | 防复发措施 | Status |
|---|---|---|---|---|---|---|
| L001 | 2026-09-15 | API | GLM 的 reasoning_effort 缺省或填错会回退 max，思考 token 翻倍烧钱 | 官方缺省值不是 low（初版接入手册 §1.4 记录） | 当前主力链路是 DeepSeek（见 L002），代码不传思考参数；方舟 GLM 账号开通后实测复核本条再决定是否保留 | pending |
| L002 | 2026-09-15 | API | ~~DeepSeek 思考默认值不稳定~~ 实测修正：**DeepSeek 思考开关靠选模型 id，不靠参数**——`deepseek-chat`=思考关（快答）、`deepseek-flash`=思考开；`enable_thinking` 参数被静默忽略；`reasoning_effort` 反而会强制 deepseek-chat 开思考 | 文档口径与真实行为不一致（2026-09-15 真实 Key 实测，同步自并行接入任务） | 代码不传思考参数，按任务选 id：快答/问答用 deepseek-chat，规划/复盘用 deepseek-flash；新增链路先跑手册 curl 冒烟 | active |
| L003 | 2026-09-15 | API | 图片走 URL 直传可能被 CDN 防盗链挡掉，识谱/看手静默失败 | 模型服务端下载图片受 referer/签名限制 | 图片一律 base64 data URI 内联（FrameCodec → EncodedImage）；禁 URL 直传 | active |
| L004 | 2026-09-15 | 调研 | ASCII 六线谱没有维护良好的开源解析器；JVM 生态也没有可用的第三方 GP 解析库（alphaTab 除外） | 小众格式，检索到的开源项目多为玩具级或无许可证（开发方案 §4.0.1） | 坚持自研 TextTabParser 路线；GP 导入选 alphaTab 1.8.4（MPL-2.0）前再核一次 LICENSE | active |
| L005 | 2026-09-15 | 产品 | 单目摄像头可靠判"第几品"物理上做不到（透视/遮挡/分辨率/动态标定），硬做必然误报伤信任 | 物理边界不是工程问题（开发方案 §5.4） | 不承诺判品：视觉只做姿势类判定，正确性真相源是音频（MPM）；参考品位只在高置信度显示并标注"参考" | active |
| L006 | 2026-09-15 | 产品 | 手型纠错误报会直接摧毁用户信任（"改手型最贵"）；单次轰炸多条点评没人看得完 | 模型幻觉 + 无置信度门槛 | 提示词强制"看不清就说，不硬猜"；规则层只报高置信度问题；单次点评 ≤2 条（开发方案 §10 风险 2） | active |
| L007 | 2026-09-15 | API | 方舟模型 id 不用点号：`glm-5.3-flash` 返回 404，正确 id 带日期后缀 `glm-5-3-flash-260828`；且账号未开通模型时报 `ModelNotOpen` 伪装成 404 | 方舟 id 命名规范 + 错误码语义混淆（2026-09-15 真实 Key 实测） | 方舟 id 一律用"短横线+日期后缀"形式；调不通先查方舟控制台「开通管理」的模型开通状态，再排查 id 拼写 | active |
| L008 | 2026-09-15 | 方法 | 接入事实必须实测：官方文档快照与真实行为有出入（enable_thinking 文档说可传、实测静默无效） | 文档滞后/口径漂移 | 每次接新模型/新 id，先跑 docs/模型API接入手册.md 的 curl 冒烟再写代码；手册与实测冲突时以实测为准并回写手册（改动手册需用户确认） | active |
| L009 | 2026-09-15 | 代码 | ModelRouter 链式 API 重构时把 suspend 调用（router.vision()）放进了非 suspend 上下文（Flow 工厂的急切实参位置），编译期才暴露 | 重构悄悄改变了求值时机：原实现里该调用在 flow{}（suspend 上下文）内，改成参数后变成立即求值 | 链式/回调式 API 重构后立即跑 `bash scripts/build.sh compileDebugKotlin` 验证，不等完整构建；自检口诀："每个 suspend 调用点是否还在 suspend 上下文" | active |
| L010 | 2026-09-15 | 设计 | 流式 + 备份降级若不约定契约：主链吐了半截话后失败、备份链从头重生成，追加式渲染的 UI 会显示"半截+全文"拼接乱文（监督员第一轮审查抓到） | 降级逻辑与渲染逻辑分开设计，谁都没管"已发射内容" | 降级只允许发生在"未发射任何内容"前（streamWithFallback 用 emitted 标志守卫）；已发射后失败直接抛错，UI 保留半截并追加错误提示（用户可重发）。新增流式链路先写这条契约再写代码 | active |
| L011 | 2026-09-15 | 代码 | 降级/重试循环里 `catch (Exception)` 会吞 CancellationException：用户切页取消协程后，循环继续走完整条链，后台残留至多 readTimeout（120s）（监督员复审抓到） | Kotlin 里 CancellationException 是 Exception 子类，宽 catch 天然吞掉取消 | 协程取消协作规范：凡 catch (Exception) 前置 `catch (e: CancellationException) { throw e }`；UI 层收集处取消时静默停止不落错误气泡。代码评审清单加这一条 | active |
| L012 | 2026-09-15 | 环境 | **中文项目路径下 Gradle 单元测试任务无法运行**：Gradle 测试 worker 的 @argfile 由 Gradle 按 UTF-8 写入，但 JDK 原生启动器按系统码页（中文 Windows=GBK）解码，中文 classpath 条目（app 类、workerMain jar）被读坏 → `GradleWorkerMain`/测试类 ClassNotFoundException。实验证实：UTF-8 argfile + 中文 -cp 在 JDK17 与 JDK21 上都复现；JEP 400 不覆盖原生启动器；junction/别名方案被 JVM canonicalize 击穿（worker Working directory 仍是物理中文路径） | JDK 原生层 argfile 解码不服从 -D/JAVA_TOOL_OPTIONS，也无 Gradle 开关可绕过 | **正式口径：单测以 GitHub Actions 为准**（.github/workflows/android-ci.yml）。本地临时绕法（未产品化，勿直接照抄成常规流程）：把编译产物与依赖 jar 拷到 ASCII 临时目录，手工 `java -cp <ASCII> org.junit.runner.JUnitCore <测试类>` 可跑（已验证 5/5），适合 push 前快速自检；若要根治需仓库迁 ASCII 路径（需用户确认）。新环境搭好后第一个动作跑 `bash scripts/build.sh test` 验证 | active |
| L013 | 2026-09-16 | 环境 | 本地 JUnitCore 绕法的临时目录若只刷新 test 不刷新 main，会拿旧产物跑测试——代价模型改了 3 轮"修复无效"，排查被假象误导 | 手工拷贝步骤不一致（有的命令只 rm/cp 了 test 目录） | 绕法产品化为 `scripts/run-tests-local.sh`：成对全新拷贝（rm 整个临时目录）、自动收 jar、自动发现测试类、cygpath 处理 POSIX→Windows 路径；禁止再手工拼 classpath | active |

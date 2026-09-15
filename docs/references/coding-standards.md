# 编码规范（Kotlin + Jetpack Compose）

> 品味不变量：Agent 在约束内自由发挥。违反者 Evaluator/Review 直接打回。
> 通用工程纪律（门禁）见根目录 AGENTS.md；本文件只管代码本身。

## 命名（Android/Kotlin 官方风格，覆盖 Web 习惯）

- 文件名与顶层声明同名：PascalCase（`CoachOrchestrator.kt`）；只有一个类的文件与类同名
- 类/Composable 组件：PascalCase；函数/变量：camelCase；常量：SCREAMING_SNAKE_CASE（如 `DEFAULT_GLM_BASE`）
- 资源文件：小写下划线（`ic_launcher_foreground.xml`）；字符串一律进 `res/values/strings.xml`，不硬编码 UI 文案
- 测试：`XxxTest.kt` 对应 `Xxx.kt`

## 文件与函数尺寸

- 单文件 ≤ 300 行；超限先按职责拆分再继续加功能
- 函数圈复杂度 ≤ 10；单个函数只做一件事
- Composable 函数：无副作用；重活（网络/DSP/IO）移出组合，状态提升到容器或 ViewModel

## 导入顺序

1. `kotlin.*` / `kotlinx.*`
2. `android.*` / `androidx.*` / `com.google.*`（含 MediaPipe）
3. 其他第三方（okhttp 等）
4. 项目内 `com.guitarcoach.app.*`
（Android Studio 默认排序即可，禁止通配符 import）

## 错误处理

- 网关层抛具体异常（`LlmException`），禁止 catch-all 吞掉后假装成功；所有外部调用（模型 API、文件、数据库）必须有超时与失败路径
- 面向用户的错误文案要可执行（"请到首页检查 API Key 配置"），不暴露堆栈与技术细节
- 模型输出永远不可信：JSON 解析判空容错，识谱结果过合法性过滤（弦 1-6 / 品 0-24）才入 TabDocument

## 日志

- 统一 tag 前缀 `GuitarCoach`，格式：`Log.d("GuitarCoach/<模块>", "...")`；模块 = llm/coach/tab/vision/audio/data/ui
- 只记录必要上下文（任务类型、耗时、状态码），**绝不打 Key、不打完整用户数据**；发布构建前清理调试日志
- 结构化：模型链路日志带 chain（GLM/DeepSeek）与 fallback 发生标记，便于 adb logcat 排障

## 注释与文档

- 公共 API（对外类/函数）必须有 KDoc 一句话说明；复杂算法（MPM、角度阈值判定）写明依据来源
- 注释随周围密度；已有中文注释的文件保持中文
- 不确定的事在注释里直说（"v0 启发式，M1 校准"），不许假装是定论

## 禁止事项

- 禁止在 UI 层直接调 LlmClient/OpenAiCompatClient/DAO（一律经 data/AppContainer）
- 禁止内联 system prompt（只放 CoachPrompts）
- 禁止硬编码密钥/端点（baseUrl/modelId 走 DataStore 配置）
- 禁止复制 GPL/LGPL/AGPL 代码；引入任何新依赖前核实许可证

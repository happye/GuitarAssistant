# 对抗性审查报告 2026-09-17b（adversarial review）

> 审查对象：commit 708e95c（三修复：SSE JsonNull 过滤 / 转写输出张量动态分配 / HomeScreen 跳转删除）+ 相关边界与并发面。
> 方式：只读攻击性审查，逐条给出具体触发路径；不报风格意见。
> 结论速览：**P0 = 0，P1 = 5，P2 = 16**。

---

## P1（功能错误 / 有具体触发路径的崩溃风险）

### P1-1 SSE 过滤 `content != "null"` 会误杀合法的 JSON null 值 chunk，破坏 jsonMode 输出
- **文件**：`app/src/main/java/com/guitarcoach/app/core/llm/OpenAiCompatClient.kt:98`
- **代码**：`if (delta.content.isNotEmpty() && delta.content != "null") emit(delta.content)`
- **攻击**：`delta is JsonNull` 分支已正确拦截 `{"content":null}`（思考阶段）。但这里额外的**字符串**比较 `!= "null"` 针对的是「内容恰好为字面 4 字符 null 的 JsonPrimitive」——即模型真的在输出文本 `null`。jsonMode（`reviewHandFrames`/`LlmTabExtractor`，均 `jsonMode = true`）下模型输出紧凑 JSON，null 值是独立 token，SSE 常按 token 分块，`{"issues":null}` 的 `null` chunk 无前导空格 → 恰好等于 `"null"` → 被丢弃。
- **触发路径**：视觉链返回 `{"overall":"...","issues":null,...}` 且 `null` 为独立 SSE chunk → 流出文本变成 `{"issues":}`。
- **后果**：`CoachFeedback.parse` 解析失败返回 null（手型点评降级显示原文）；`LlmTabExtractor.extract` 的 `decodeFromString` 抛异常 → 整次识谱报错。非 jsonMode 下模型用英文说 "null" 也会丢词。
- **最小修法**：删除字符串比较，仅保留 `if (delta is JsonNull) continue`（该类型检查已覆盖全部 JSON null 情形）；若担心历史网关把字符串 "null" 当占位符，只在 `!spec.jsonMode` 时放宽。

### P1-2 空流 = 成功：全被过滤的流绕过 LlmFallback 降级，UI 永远停在占位符
- **文件**：`OpenAiCompatClient.kt:81-99`（过滤后可能零发射）+ `core/llm/LlmFallback.kt:22-28`
- **攻击**：`streamWithFallback` 只在客户端**抛错**时换链；流"正常完成但一个字没吐"视为成功直接 `return@flow`。深思链（deepText = deepseek-flash 优先）思考阶段所有 delta 要么带 `reasoning_content`（无 `content` 键 → `?: continue`），要么 `content` 为 JsonNull（被过滤）；若回答阶段因 maxTokens/截断/内容策略等原因零 content delta（`practicePlan` 的 maxTokens 只有 600），整个流零发射且不报错。
- **触发路径**：首页「生成今日练习单」/「我的」周复盘/音色向导 → deepseek-flash 只吐思考不吐正文 → collect 正常结束。
- **后果**：`HomeScreen` planText 停在 `""` → 界面永远显示 "…"，按钮恢复可点但无错误提示、无降级到方舟 GLM；TheoryScreen 落库 "（空回复）"。
- **最小修法**：`streamWithFallback` 记录发射计数，流正常结束但 `emitted==false` 时按失败处理（换链下一家）；或在 OpenAiCompatClient 流末检查 finish_reason 与空内容并抛 `LlmException`。

### P1-3 音频转写内存路径：10 分钟上限在**整个解码完成后**才检查，10–30 分钟素材先 OOM 后报错
- **文件**：`app/src/main/java/com/guitarcoach/app/core/audio/AudioPcmExtractor.kt:31-37`
- **攻击**：`extractToMono(fd, targetRate)` 先把全部重采样 PCM 写进 `ByteArrayOutputStream`（30 分钟 @22050 mono ≈ 79MB，BAOS 扩容峰值翻倍），再 `toByteArray()` 复制一份，再转 `ShortArray` 一份 —— 全部完成后才执行 `if (r.durationSeconds > MAX_SECONDS_IN_MEMORY) throw`。Manifest 未开 `largeHeap`。
- **触发路径**：扒谱入口选 20~30 分钟音/视频 → 流式循环里 30 分钟硬顶（line 123）之前已驻留约 240–300MB → 低端机/256MB 堆设备 `OutOfMemoryError` 崩溃，而不是预期的"文件太长（超过 10 分钟）"提示。
- **最小修法**：把 10 分钟上限下沉到流式循环内（像 MAX_SECONDS 那样按 `totalOutBytes / 2.0 / targetRate` 边写边判），或给 BAOS 包一个超限即抛的限量 OutputStream。

### P1-4 ToneRenderer 渲染缓冲无上限：极端长行文本谱 → 拍位爆炸 → 点播放 OOM/整型溢出崩溃
- **文件**：`core/audio/ToneRenderer.kt:19-21`，源头 `core/tab/TextTabParser.kt:38-56`
- **攻击链**：TextTabParser 对行的**列数无上限**，且段边界取"最长行的 `|` 列"——若最长行 body 里一个 `|` 都没有，`boundaries = [baseBody.length]`，整行成为**单一小节**，`beat = (col - segStart) / 4.0` 可达数千上万。`TabRenderPanel.playEvents` 按 `(globalBar*4 + beat) * secPerBeat` 换算 → `timeSec` 达数千秒 → `render()` 里 `samples = (total * sampleRate).toInt()`：数千秒 → 数亿 `Double`（GB 级分配）；更大时 Int 溢出可产生负数被 `coerceAtLeast(1)` 吞掉变成无声错乱。
- **触发路径**：识谱页粘贴 ≥4 行、每行数万字符且无 `|` 的文本 → 解析成功（无数值违规）→ 切"谱面（点按试听）"→ 点"▶ 播放整段" → `TonePlayer.playSequence` 的裸 `Thread { ToneRenderer.render(...) }` 内 OOM，**未捕获异常 → 进程崩溃**。
- **最小修法**：双端设限——TextTabParser 给 `value in 0..24` 之外再给段列数/beat 上限（如单段 ≤ 512 列）；ToneRenderer 给 `samples` 设硬上限（如 5 分钟，超限抛 `IllegalArgumentException` 而非分配）。

### P1-5 TunerEngine 麦克风初始化失败两条歧路：无异常处理器崩溃 / 静默自旋 100% CPU
- **文件**：`core/audio/TunerEngine.kt:43-67`
- **攻击**：`start()` 在 `scope.launch(Dispatchers.IO)` 内：① `AudioRecord.Builder()...build()`（line 49）与 `record.startRecording()`（line 63）**不在 try 内**（try 从 line 62 的 `record.startRecording()` 才开始……实际 build 在 try 外，startRecording 在 try 内但 try/finally **无 catch**）——build 抛 `UnsupportedOperationException` / startRecording 抛 `IllegalStateException` 时异常从协程逃逸，而 `PracticeScreen` 传入的是裸 `CoroutineScope(Dispatchers.Default)`（无 CoroutineExceptionHandler）→ 进程崩溃。② `startRecording()` 失败但**返回错误状态而非抛错**（返回值被忽略），后续 `record.read` 恒返回负数 → `if (n < frameSize) continue` **无退避死循环** → 单核打满、界面永远"正在聆听…"。
- **触发路径**：来电/其他 App 独占麦克风时点"开始调音"；或蓝牙 SCO 切换瞬间启动。
- **最小修法**：build+startRecording 包 try-catch（失败记日志并置状态为"启动失败"）；检查 `recordingState == RECORDSTATE_RECORDING`；read 负数/短读时 `delay(50)` 再重试并计连续失败上限。

---

## P2（健壮性 / 边界条件）

### P2-1 输出张量 shape 读取假设 rank ≥ 3
- `TranscriptionEngine.kt:68-71`：`t.shape()[1]`/`[2]` 对任意输出张量成立的前提是全部 rank≥3。换模型出现 rank<3 的输出（标量/state 张量等）→ `ArrayIndexOutOfBoundsException` 崩溃——恰是本修复想防的"模型差异"场景。
- 修法：`if (t.shape().size < 3) continue` 跳过非 3 维张量并记日志。

### P2-2 88 通道头缺失时静默输出空谱（防呆不对称）
- `TranscriptionEngine.kt:72-85`：只对 264 通道缺失做了显式报错；`spec88` 为空时 headA/headB 保持全零、不进 outputs map，`total 相等取 headA` → 解码全零 → `TranscribePanel` 报"没有转写出音符——试试更干净的单音素材"（误导性提示，真实原因是模型形状不匹配）。
- 修法：`spec88.size < 2` 时抛出与 spec264 对称的诊断错误（附 outSpecs 全量 shape）。

### P2-3 动态 shape 模型下 frames 可为 -1
- `TranscriptionEngine.kt:79-82`：`frames = spec264.second` 未校验 ≥ 1；TFLite 动态 shape 张量推理前 shape 元素可为 -1 → `Array(-1)` → `NegativeArraySizeException`。
- 修法：`require(frames > 0)` 并并入形状异常诊断。

### P2-4 note/onset 头按"每窗"激活总量自校准，onset 密集段可能整窗用错头
- `TranscriptionEngine.kt:90-91`：判定在 while 循环内逐窗进行。快速交替拨弦/强击勾击段某窗 onset 激活总量反超 note 头 → 该窗拿 onset 矩阵当 note 解码 → 一批 ~35ms 碎假音符混入谱面；两个激活总量恰好相等（全静音窗）→ 恒取 headA（静音下无实际危害，但语义脆弱、跨窗不一致）。
- 修法：首窗定头后锁定（或跨窗累计激活量投票），窗间不翻转。

### P2-5 输出数组与 outSpecs 在每窗循环内重复分配
- `TranscriptionEngine.kt:68-82`：旧版三块输出缓冲在循环外分配一次；现版每窗重新 `getOutputTensor` + 分配 172×264 + 2×172×88（≈300KB/窗）。10 分钟素材 ≈ 160 窗 → ~48MB GC churn，长曲掉帧/卡顿。
- 修法：模型静态 shape 下把 outSpecs 与三块缓冲提到循环外（每窗仅 rewind 复用）。

### P2-6 FRAME_RATE 硬编码 hop=256，防了 shape 防不了 hop
- `NoteDecoder.kt:14`：帧数已动态读取，但 `22050/256` 仍写死。换模型 hop=512 时全部音符时间/时长错一倍。
- 修法：TranscriptionEngine 按 WINDOW_SAMPLES/frames 反推 hop 传入，或至少在注释标注为模型契约。

### P2-7 TonePlayer 自然播完不复位 positionMs
- `TonePlayer.kt:89,96-100`：`stop()` 才置 -1；写线程自然结束时 finally 只 stop/release track。`TabRenderPanel.kt:161-171,225-236` 以 `positionMs >= 0` 判定"播放中"→ 播完后绿色光标永久停留最后一小节，滚动跟随逻辑也持续活跃。
- 修法：写线程 finally 中 `if (playbackId == myId) _positionMs.value = -1L`。

### P2-8 流式 error 字段未做 JsonNull 防御（与本次修复口径不一致）
- `OpenAiCompatClient.kt:88-90`：`obj["error"]?.let { throw }` —— 部分网关会发 `{"error":null,...}` 心跳 chunk，JsonNull 是非 null 引用 → 误抛"流式返回错误: null"，主链被永久误杀（静默靠 fallback 掩盖）。
- 修法：`if (obj["error"] != null && obj["error"] !is JsonNull) throw`。

### P2-9 取消穿透在阻塞 IO 上有最长 120s 滞后
- `OpenAiCompatClient.kt:81-82` + `LlmFallback.kt:24`：`source.readUtf8Line()` 是阻塞读，协程取消后要等下一行到达/读超时才在 `emit` 处真正抛 CancellationException。deepseek-flash 思考长静默段期间取消 → IO 线程与连接滞留最多 120s（L011 契约的软违例）。
- 修法：保存 `Call` 引用，取消时 `call.cancel()`（OkHttp 阻塞读对 cancel 立即醒），或在 collect 外用 `invokeOnCancellation` 关 response。

### P2-10 AppContainer 每次 onCreate 重建，Room 实例泄漏
- `MainActivity.kt:16`：无 ViewModel/retain 机制，旋转即新容器；`AppContainer.database by lazy` 新建 Room 实例且旧实例从不 `close()` → 反复旋转累积连接池；进行中的转写/聊天协程随旧 scope 死亡（结果写进已失效的组合）。
- 修法：AppContainer 挂到 retainCustomComponentOnConfigurationChange / 单例 applicationScope，或至少在 shutdown 里 `database.close()`。

### P2-11 TabDocument/ExtractResult 整体 JSON 进 rememberSaveable，binder 缓冲风险
- `TabStudioScreen.kt:60,66`：整曲 GP 导入的 TabDocument JSON 可达数百 KB，`onSaveInstanceState` 1MB 共享 binder 缓冲逼近时抛 `TransactionTooLargeException`（jsonSaver 的 `runCatching` 拦不住 binder 层异常，崩溃点在系统侧）。
- 修法：saveable 里只存 songId/轻量索引，大文档落盘文件（复用 SongRepository 或 PhraseCache 模式）。

### P2-12 转写不可取消 + 旋转后可并发双引擎
- `TranscribePanel.kt:54-97` + `TranscriptionEngine.transcribe`：循环无 `isActive` 检查点，导航离开只是协程取消而阻塞推理继续跑完；旋转后 `working` 状态丢失 → 可再次选文件 → 两个 engine、2× 线程与内存并发跑。
- 修法：transcribe 每窗回调后检查协程 isActive（把 onProgress 签名改为返回 Boolean 或注入 cancel 标志）；旋转用 `rememberSaveable` 保护 working 状态或把管线提升到 VM。

### P2-13 聊天 history 无上限
- `TheoryScreen.kt:81-84`：每轮把该会话**全部**落库消息作为 history 重发；长会话请集体积与成本线性膨胀，可触发 4xx（超上下文）。
- 修法：截尾保留最近 N 轮（如 20 条）。

### P2-14 jsonSaver 恢复失败把 null 塞进非空类型
- `ui/Saveable.kt:16-17`：`jsonSaver` 的 restore `getOrNull()` 返回类型是 T（非空）——save 失败存 "" → restore 解析失败 → 返回 null 被塞进非空泛型，调用方按非空使用时 NPE（类型系统洞，当前调用点恰好可空才未爆）。
- 修法：`jsonSaver` restore 失败时返回 `error("saveable restore failed")` 或改为可空 Saver + 调用方默认值。

### P2-15 破坏性操作缺确认：曲库删除 / 练习计时放弃
- `SongLibrary.kt:147,204-206`：垃圾桶图标单击即永久删曲（无确认、无撤销）；`PracticeExtras.kt:141-144` 保存对话框"放弃"直接丢掉本次计时（长练 1 小时手滑即丢）。
- 修法：删除加 AlertDialog 确认（会话删除已有此模式，对齐即可）；"放弃"按钮改名"不保存并清零"或加确认。

### P2-16 SettingsDialog 表单用 remember 而非 rememberSaveable
- `HomeScreen.kt:41-44,242-254`：`editing/testing/settings` 与 `form` 全是 remember（同屏 planText/testResult 已 saveable）→ 填了一半 API Key 旋转屏幕全部丢失。
- 修法：form 改 rememberSaveable（SettingsForm 是纯 String 字段，可直接用 mapSaver/listSaver）。

---

## 攻击后确认无问题的点（免复查）

- **HomeScreen onNavigate 删除**：全库 grep 无残留引用（仅定义 + CoachApp 调用点），无未用参数导致的编译问题；底部导航五 Tab 的 `popUpTo(saveState)+restoreState+launchSingleTop` 是标准写法。
- **对话框期间切 Tab**：Compose AlertDialog 遮罩挡住底部导航（对话框窗口全屏），"对话框开着切走导致对话框残留"不可达。
- **空 PCM/全零 PCM/超短 PCM**：`transcribe` 对空数组直接返回空表（while 不进），TranscribePanel 以"没有转写出音符"友好报错；`PcmResampler.toMonoRate` 对空输入/等采样率/多声道缩混均安全（含 `mono[i0]` 越界推演，最大 i0 ≤ N-1）。
- **TextTabParser 常规畸形输入**：单弦行/全空行/竖线开头/`(x2)` 重复标记/`r\n` 回车（`lines()` 已消化 CR）均有兜底；fret>24、x 前缀、非法数字被过滤；<4 弦块报错文案明确。**例外见 P1-4（极端长行）**。
- **Room 迁移**：v1→v2→v3 建表 SQL 与实体列定义逐列核对一致（含 NOT NULL / AUTOINCREMENT），未配 destructive fallback（升级缺迁移会显式崩而非静默清库）。
- **DataStore 空值**：所有键均有默认值，未配置时 isConfigured=false → 明确的"请到首页填 Key"错误。
- **TonePlayer 双线程竞态**：`playbackId`+`current === track` 双卫兵推演下，渲染-启动-写入-打断各交错窗口的最终态均自洽（新播放覆盖旧播放、旧写线程静默退出、current 不会被陈旧线程清掉）。**例外见 P2-7（positionMs 复位）**。
- **ChatRepository 流式期间删会话**：`appendMessage` 捕获 `SQLiteConstraintException` 静默丢弃（注释明确），TheoryScreen 的 catch 内二次 append 不会崩。
- **LlmFallback 取消穿透（语义层）**：`CancellationException` 显式 rethrow，NonCancellable 落库路径正确；** IO 层滞后见 P2-9**。
- **TempoDetector/MidiTabConverter**：BPM 全链路收敛在 40..240（检测端 coerceIn、UI 端 coerceIn(40,300)、GP 导入端 coerceIn(1,300)），`60.0/bpm` 无除零；DP 弦品分配候选空集显式抛错且被 UI 捕获。
- **GP 导入**：alphaTab 解析失败包装为友好错误；空轨道/空 masterBars 有兜底；`barsByIndex.getOrNull` 防错位越界。

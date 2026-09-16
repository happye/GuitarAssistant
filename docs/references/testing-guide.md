# 测试指南

> 门禁要求（AGENTS.md 工程纪律 1/3）：修 bug 先立回归测试；验证靠"跑"不靠"读"。
> 现状（2026-09-16）：JVM 单测 73 个全绿（TextTabParser / TransposeCalculator / PhraseSegmenter / CoverageAuditor / PhraseExplainCodec / PhraseCoach 管线 mock / FingeringSolver / TabLayout / TabDocumentEdit / TabCompact / PhraseCache / PhraseSpeech），DEBT-001 已偿还。

## 测试分层

| 层 | 位置 | 范围 | 运行 |
|---|---|---|---|
| JVM 单元测试 | `app/src/test/` | 纯逻辑：谱面解析与校验、移调/指法/版面计算、讲解管线（mock LlmClient）、缓存往返 | `bash scripts/run-tests-local.sh [测试类...]`（缺省全量）；CI push 自动跑 |
| Compose UI 测试 | `app/src/androidTest/` | 关键交互：Tab 导航、识谱结果编辑（F203）、聊天页历史渲染 | `./scripts/build.sh connectedDebugAndroidTest` |
| 真机冒烟 | adb | 相机/音频/模型链路等无法单测的端到端路径 | 见下方"真机验证" |

> ⚠️ Gradle 的 `test` 任务本地跑不了（L012：中文路径 + JDK 原生层 GBK 读 @argfile）——本地单测用 **run-tests-local.sh**（产物拷 ASCII 目录跑 JUnitCore，L013：main/test 必须成对刷新，脚本已内置）；正式口径以 GitHub Actions 为准。

## 编写规则

- 每个特性在 feature_list.json 的 test_criteria 写清"怎么算过"；实现完成后照它验收
- 修 bug 四步：先写回归测试（红灯）→ 修（绿灯）→ 全局扫同类点 → 落 `.learnings/`
- 模型相关逻辑单测**不发真实请求**：mock LlmClient（接口已抽象，可注入）；流式降级、JSON 降级重试都可用假客户端驱动
- 数学类（音高、音分、移调）用已知输入算已知输出，禁止"看着像对"
- 测试命名中文描述场景：`fun 空弦E返回82.4Hz附近音高()`

## 真机验证（替代浏览器自动化，本项目无 Web 界面）

```bash
adb devices                                    # 确认设备/模拟器在线
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb exec-out screencap -p > shot.png           # 截图回读，验证 UI 状态
adb shell uiautomator dump && adb pull /sdcard/window_dump.xml   # 控件树
adb logcat -s GuitarCoach                      # 运行日志（结构化 tag）
```

- UI 改动必须附至少一张截图证据（Evaluator 检查此项）
- 音频类用"合成音源/已知琴弦"做输入对照；相机类用固定姿势样本
- 模型链路冒烟优先用手册 curl（免装机），App 内「测试连通」做端到端确认

## 提交门槛

- 本地 `bash scripts/run-tests-local.sh` 全绿才允许 commit（每笔功能改动收尾还要本地 `assembleDebug` 出 APK 并报告路径，见 AGENTS.md「交付」）；正式验证以 CI 为准
- 新功能无测试 = 未完成；跑不了的验证明说"没跑什么、为什么"

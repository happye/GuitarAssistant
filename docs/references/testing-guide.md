# 测试指南

> 门禁要求（AGENTS.md 工程纪律 1/3）：修 bug 先立回归测试；验证靠"跑"不靠"读"。
> 现状：M0 骨架尚无 test/androidTest 源集（tech-debt DEBT-001），M1 首个 sprint 补齐。

## 测试分层

| 层 | 位置 | 范围 | 运行 |
|---|---|---|---|
| JVM 单元测试 | `app/src/test/` | 纯逻辑：TextTabParser、TabDocument 校验与 midi()、LlmTabExtractor 的 JSON 过滤、CoachFeedback.parse、移调计算（F206）、MPM 对合成正弦波的音高检测 | `./scripts/build.sh test` |
| Compose UI 测试 | `app/src/androidTest/` | 关键交互：Tab 导航、识谱结果编辑（F203）、聊天页历史渲染 | `./scripts/build.sh connectedDebugAndroidTest` |
| 真机冒烟 | adb | 相机/音频/模型链路等无法单测的端到端路径 | 见下方"真机验证" |

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

- `./scripts/build.sh test` 全绿才允许 commit（含被改文件的编译）
- 新功能无测试 = 未完成；跑不了的验证明说"没跑什么、为什么"

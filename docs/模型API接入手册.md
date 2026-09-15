# 模型 API 接入手册（实测版）

> **2026-09-15 用真实 key 实测定稿**（非文档转述）。App 网关已按本手册实现。
> 修改接入配置前先跑 `scripts/test-api.sh`（由环境脚本提供）复核。

## 0. 当前可用状态速览

| 客户端 | 账号 | Base URL | 模型 id | 状态 |
|---|---|---|---|---|
| DeepSeek快答 | DeepSeek 官方 | `https://api.deepseek.com` | `deepseek-chat` | ✅ 实测可用（思考关，快且省） |
| DeepSeek深思 | DeepSeek 官方 | 同上 | `deepseek-flash` | ✅ 实测可用（思考开，用户给的 id 正确） |
| DeepSeek视觉 | DeepSeek 官方 | 同上 | `deepseek-v4-flash-vision-exp` | ✅ 实测可用（成功识别图片） |
| 方舟GLM | 火山方舟 | `https://ark.cn-beijing.volces.com/api/v3` | `glm-5-3-flash-260828` | ⚠️ **key 有效但账号未开通模型**（见 §2） |

## 1. DeepSeek（当前主力，key 实测全通）

### 1.1 关键实测结论

- **模型 id 与思考开关**：`deepseek-flash` 与 `deepseek-chat` 是**同一个模型的两种形态**——前者思考开（8/8 次输出 `reasoning_content`），后者思考关（10/10 次无）。`deepseek-v4-flash` 只是路由到 deepseek-flash 的别名。
- **思考开关靠选 id，不靠参数**：`enable_thinking:false` 被静默忽略（不报 400 也不生效）；`reasoning_effort:"low"` 会让 deepseek-chat **强制开思考**（反效果）。→ 本项目代码**不传任何思考控制参数**，要快用 `deepseek-chat`，要深用 `deepseek-flash`。
- **视觉**：`deepseek-v4-flash-vision-exp` 实测识别 1x1 测试图成功；裸 `deepseek-flash` 也接受 image_url 块（兜底可用）。视觉请求格式与 OpenAI 完全一致（`content[]` + `image_url`）。
- **流式**：正常。先 `choices[0].delta.reasoning_content`（思考流，网关忽略），后 `choices[0].delta.content`（正文），终止 `data: [DONE]`。
- **坑**：响应的 `model` 字段一律回显 `deepseek-flash`，不可用于路由审计。
- **延迟**：思考型首正文 token ≈2.5s，吞吐 ~75 tok/s；冷启动可达 ~5s。网关读超时已设 120s。

### 1.2 curl 模板

```bash
export DEEPSEEK_API_KEY="<见 local.properties>"

# 非流式（思考开）
curl -sS https://api.deepseek.com/chat/completions \
  -H "Content-Type: application/json" -H "Authorization: Bearer $DEEPSEEK_API_KEY" \
  -d '{"model":"deepseek-flash","messages":[{"role":"user","content":"你好"}],"max_tokens":512}'

# 思考关：model 换成 deepseek-chat 即可
# 视觉：model 用 deepseek-v4-flash-vision-exp，content 用块数组（text + image_url/data URI）

# 流式
curl -sS -N https://api.deepseek.com/chat/completions \
  -H "Content-Type: application/json" -H "Authorization: Bearer $DEEPSEEK_API_KEY" \
  -d '{"model":"deepseek-flash","messages":[{"role":"user","content":"你好"}],"stream":true}'
```

### 1.3 JSON 模式

`response_format: {"type":"json_object"}` 官方文档声明支持（本轮未逐项实测）。网关已实现 400 自动降级（去掉 response_format 重试），两条路都安全。

---

## 2. 火山方舟 · GLM（文本备份，当前被「未开通」阻塞）

### 2.1 ⚠️ 待办：先开通模型

实测结论：**key 有效**（`GET /api/v3/models` 返回 200 列出 132 个模型），但对该账号下任何模型的调用都返回：

```
HTTP 404  {"error":{"code":"ModelNotOpen","message":"Your account 2130886108 has not activated the model glm-5-3-flash-260828. Please activate the model service in the Ark Console."}}
```

**注意两个坑**：① 错误伪装成 404，别误判成 id 写错；② 开通操作有 1-2 分钟生效延迟。

**修复步骤**（官方文档：www.volcengine.com/docs/82379/1159200 开通管理）：
火山引擎控制台 → 火山方舟 → **开通管理/模型广场** → 搜索 `glm` → 对 `glm-5-3-flash` 点「开通」→ 勾选服务协议 → 等 1-2 分钟。

### 2.2 模型 id（实测确认，精确到字符）

```
glm-5-3-flash-260828
```

- Ark 命名**不用点号**：`glm-5.3-flash`、`glm-5-3-flash`（裸名）等各种变体全部 404，必须用带日期后缀的全 id。
- 元数据（来自 `/api/v3/models`）：上下文 1,048,576 / 最大输出 131,072 / function_calling ✓ / structured_outputs ✓ / prefix cache ✓ / **input_modalities: ["text"]——仅文本，无视觉**。

### 2.3 curl 模板

```bash
export ARK_API_KEY="<见 local.properties>"

curl -sS https://ark.cn-beijing.volces.com/api/v3/chat/completions \
  -H "Content-Type: application/json" -H "Authorization: Bearer $ARK_API_KEY" \
  -d '{"model":"glm-5-3-flash-260828","messages":[{"role":"user","content":"你好"}],"max_tokens":512}'

# 查模型列表 / 验 key
curl -sS https://ark.cn-beijing.volces.com/api/v3/models -H "Authorization: Bearer $ARK_API_KEY"
```

### 2.4 开通后的回补实测清单

1. 流式 SSE 是否正常、delta 字段路径
2. `reasoning_effort:"low"` 是否被接受（元数据显示 max_reasoning 131072，说明有思考；参数名未验证，**开通前不要传**）
3. **视觉复核**：发一张真实图片看是否被接受——元数据说仅文本，若实测支持图片，把方舟 GLM 补进 `ModelRouter.vision()` 备份链
4. 实际延迟体感

---

## 3. 通用坑点（实测踩到）

1. **Git Bash + curl 发中文 JSON 会 400**（Windows shell 按 GBK 编码）：`invalid unicode code point`。用 UTF-8 文件或 Python 发请求测试。
2. Ark 的 `ModelNotOpen` 是 404 —— 排障时先查开通状态再查 id。
3. DeepSeek 回显 model 字段不可信。
4. 想换聚合平台/中转：只需在 App「模型设置」改 baseUrl + 模型 id，网关代码零改动。

## 4. 网关行为对照（OpenAiCompatClient.kt）

| 手册要求 | 代码落实 |
|---|---|
| Bearer 鉴权（两家一致） | `header("Authorization", "Bearer ${cfg.apiKey}")` |
| 图片 base64 data URI | `data:${mime};base64,${base64}` |
| 思考开关靠 id 不靠参数 | 不传 enable_thinking / reasoning_effort；快答链用 deepseek-chat，深思链用 deepseek-flash |
| 流式 reasoning_content 忽略、只取 content | 已实现 |
| json_object + 失败降级 | 400 时去掉 response_format 自动重试 |
| 思考型模型冷启动 | 读超时 120s / callTimeout 300s |
| 四客户端（方舟GLM/快答/深思/视觉） | SettingsStore + AppContainer + ModelRouter 链式降级 |

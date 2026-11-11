# vFlow MCP 服务器说明

## 1. 这是什么

vFlow MCP 服务器把 vFlow 中已经注册的本地模块（点击、找控件、OCR、Shell、变量、网络请求、文件操作等）直接暴露为 MCP 工具，供 AI 客户端（Claude Desktop、Cursor、Cherry Studio、自研 Agent 等）调用。

它与旧的“远程 API”最大的区别：

- 旧远程 API 通过 REST 接口管理工作流、异步执行工作流。
- 新 MCP 服务器直接调用单个本地模块，不创建、不排队、不等待工作流调度，工具调用返回即执行结果。
- 设置页中的“启用远程API”已改为“启用mcp服务器”，“API管理”已改为“mcp服务器管理”。

MCP 端点为：

```text
http://<手机局域网 IP>:<端口>/mcp
```

默认端口 `8080`，可在“mcp服务器管理”中修改（1024–65535）。服务器固定绑定 `0.0.0.0`，即监听全部网络接口，局域网设备与 `adb forward` 转发均可接入。

传输方式为 **MCP Streamable HTTP**：客户端通过 `POST /mcp` 发送 JSON-RPC 2.0 请求，服务器同步执行并返回 JSON 响应。服务器为无状态模式，不依赖 SSE 长连接，也不需要 `Mcp-Session-Id`。

---

## 2. 快速开始

### 2.1 在手机上启动

1. 打开 vFlow → 设置。
2. 打开“启用mcp服务器”开关。
3. 进入“mcp服务器管理”，记下页面显示的服务器地址，例如：

   ```text
   http://192.168.1.23:8080/mcp
   ```

4. 确保手机与 AI 客户端所在电脑处于同一局域网。
5. 如需鉴权，在“访问令牌”中生成令牌并妥善保存。

### 2.2 鉴权规则

- **没有创建任何令牌时**：局域网内可直接访问，适合本地调试。
- **创建了至少一个令牌后**：所有 `/mcp` 请求必须携带有效令牌。
- 令牌可通过以下任一方式传递：

```http
Authorization: Bearer <token>
```

```http
X-MCP-Token: <token>
```

```text
http://192.168.1.23:8080/mcp?token=<token>
```

未通过鉴权时返回 HTTP `401`。

### 2.3 健康检查

```bash
curl http://192.168.1.23:8080/health
```

返回示例：

```json
{
  "status": "ok",
  "server": "vFlow MCP",
  "version": "1.5.3-pr15",
  "protocolVersion": "2025-06-18",
  "bindAddress": "0.0.0.0",
  "tools": 142,
  "authRequired": false
}
```

### 2.4 后台保活

开启 MCP 服务器后，vFlow 会同时启动 `McpForegroundService`（前台服务，低优先级常驻通知）。这样即使切到桌面、QQ 或其他应用，MCP 端口也不会被 MIUI 等系统冻结，AI 客户端仍能正常调用工具。

- 通知渠道：`mcp_server_channel`，通知标题为“MCP 服务器运行中”。
- 前台服务类型：`specialUse`，通知 ID `3003`。
- 如果系统仍然限制后台运行，请把 vFlow 加入电池优化白名单，并允许自启动。
- 关闭“启用mcp服务器”开关会同时停止 MCP 服务器和前台服务。

---

## 3. 客户端配置

### 3.1 通用 Streamable HTTP 配置

不同客户端的字段名略有差异，核心参数如下：

```json
{
  "mcpServers": {
    "vflow": {
      "type": "http",
      "url": "http://192.168.1.23:8080/mcp",
      "headers": {
        "Authorization": "Bearer <token>"
      }
    }
  }
}
```

如果没有创建令牌，可以省略 `headers`。

### 3.2 Claude Code

```bash
claude mcp add --transport http vflow \
  http://192.168.1.23:8080/mcp \
  --header "Authorization: Bearer <token>"
```

### 3.3 支持 HTTP MCP 的桌面客户端

在客户端的 MCP 配置中添加：

- 名称：`vflow`
- 类型：`HTTP` / `Streamable HTTP`
- URL：`http://192.168.1.23:8080/mcp`
- Header：`Authorization: Bearer <token>`（可选）

> 只支持 stdio 的客户端需要借助 `mcp-remote` 等桥接工具，把 HTTP 端点转换为 stdio；vFlow 本身不提供 stdio 模式。

---

## 4. 协议与接口

### 4.1 支持的方法

| 方法 | 说明 |
| --- | --- |
| `initialize` | 协商协议版本，返回服务器信息与 `tools` 能力 |
| `notifications/initialized` | 客户端初始化完成通知，返回 HTTP 202 |
| `ping` | 连通性探测 |
| `tools/list` | 列出全部可执行本地模块工具 |
| `tools/call` | 直接调用一个本地模块 |
| `resources/list` | 返回空资源列表 |
| `resources/templates/list` | 返回空模板列表 |
| `prompts/list` | 返回空提示列表 |
| `logging/setLevel` | 接受日志级别设置 |
| `completion/complete` | 返回空补全结果 |

JSON-RPC 错误码：

- `-32700` 解析错误
- `-32600` 非法请求
- `-32601` 方法不存在
- `-32602` 参数错误 / 未知工具
- `-32603` 服务器内部错误

### 4.2 初始化

```bash
curl -s http://192.168.1.23:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "initialize",
    "params": {
      "protocolVersion": "2025-06-18",
      "capabilities": {},
      "clientInfo": { "name": "curl", "version": "1.0" }
    }
  }'
```

响应：

```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": {
    "protocolVersion": "2025-06-18",
    "capabilities": {
      "tools": { "listChanged": false }
    },
    "serverInfo": {
      "name": "vFlow MCP",
      "version": "1.5.3-pr15"
    },
    "instructions": "vFlow 本地自动化工具服务器……"
  }
}
```

### 4.3 列出工具

```bash
curl -s http://192.168.1.23:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}'
```

响应中的每个工具包含：

```json
{
  "name": "vflow_device_toast",
  "title": "显示Toast",
  "description": "在屏幕底部弹出一个简短的提示消息……\nvFlow module: vflow.device.toast",
  "inputSchema": {
    "type": "object",
    "additionalProperties": false,
    "properties": {
      "message": {
        "type": "string",
        "description": "消息内容"
      }
    },
    "required": ["message"]
  },
  "annotations": {
    "title": "显示Toast",
    "readOnlyHint": false,
    "destructiveHint": false,
    "idempotentHint": false,
    "openWorldHint": true
  }
}
```

### 4.4 调用工具

```bash
curl -s http://192.168.1.23:8080/mcp \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d '{
    "jsonrpc": "2.0",
    "id": 3,
    "method": "tools/call",
    "params": {
      "name": "vflow_device_toast",
      "arguments": { "message": "Hello from MCP" }
    }
  }'
```

成功响应：

```json
{
  "jsonrpc": "2.0",
  "id": 3,
  "result": {
    "content": [
      { "type": "text", "text": "Toast 显示成功" }
    ],
    "isError": false,
    "_meta": {
      "status": "SUCCESS",
      "summary": "显示Toast",
      "artifacts": []
    }
  }
}
```

失败时 `isError` 为 `true`，`content[0].text` 中给出原因。权限不足时不会执行模块，而是返回缺少的权限名称。

### 4.5 获取手机状态（权限感知）

工具名：`vflow_system_get_phone_status`

模块 ID：`vflow.system.get_phone_status`

这是 MCP 的**上下文入口工具，必须在调用任何其他 vFlow MCP 工具之前成功调用一次**。服务器会记录该状态；未完成时，其他工具会直接返回 `STATUS_REQUIRED` 错误，不会执行模块。

它默认返回**广泛但有上限的状态摘要**，避免每次会话都把整棵无障碍 UI 树、142 个工具的完整权限数组和大截图传给 AI；同时不再过度过滤，尽量把 AI 操作可能需要的控件都带上：

- **权限摘要**：已授予数/总数，以及缺失权限的 `id`/`name`；完整权限描述需要 `include_permission_details=true`。
- **工具摘要**：工具总数、完全可用/部分可用/不可用数量，以及 `partial`/`unavailable` 工具的精简条目（名称、标题、缺失权限）；完整 142 条工具明细需要 `include_tool_details=true`。
- **设备状态**：厂商/型号/Android 版本/安全补丁/ABI/运行时长/语言/时区/电池/内存/存储/网络。
- **屏幕状态**：亮屏/锁屏/分辨率/DPI/方向/旋转/刷新率/亮度/当前应用与窗口。
- **UI 摘要**：只读取**当前活动窗口**，过滤不可见、屏幕外、宽高为 0 的节点；保留有文本/描述/hint、可点击、可长按、可滚动、可输入、可勾选、已聚焦或带有效 viewId 的控件，**包括无文字但可点击的卡片容器和列表容器**。不再按父子包含关系删除节点，只在完全相同 `bounds` 时合并属性，因此卡片和内部按钮、列表和列表项会同时保留；**默认返回当前窗口全部有效控件，不设上限**；只有传 `max_ui_nodes>0` 时才主动限制数量（最低 20）。不会遍历状态栏、导航栏等其他窗口，也不会返回整棵树。
- **截图**：默认**不截图**；需要视觉信息时显式传 `include_screenshot=true`。优先使用无障碍截图（Android 11+），失败时回退 Shizuku/Root 的 `screencap`；两条路径都失败/超时则跳过截图，状态工具仍然成功，`content` 中不附加 `image`。

该模块本身不声明必需权限，因此在权限不完整时仍然可以调用，用于告诉 AI「现在能做什么、缺什么」。截图内部设有超时保护（无障碍约 2 秒、Shell 回退约 4 秒），任何截图异常都不会导致状态工具失败。

参数：

| 参数 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `include_ui` | boolean | `true` | 是否返回当前活动窗口的 UI 摘要 |
| `max_ui_nodes` | number | `0` | UI 节点数量；`0` 表示不设上限，返回全部有效控件；大于 0 时最低按 20 处理 |
| `include_screenshot` | boolean | `false` | 是否截图；需要视觉信息时再开启 |
| `max_screenshot_width` | number | `1080` | 截图最大宽度，范围 320-2160 |
| `include_permission_details` | boolean | `false` | 是否返回完整权限描述；默认只返回缺失权限 |
| `include_tool_details` | boolean | `false` | 是否返回完整 142 条工具明细；默认只返回部分/不可用工具 |

调用示例（默认精简模式）：

```bash
curl -s http://192.168.1.23:8080/mcp \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d '{
    "jsonrpc": "2.0",
    "id": 4,
    "method": "tools/call",
    "params": {
      "name": "vflow_system_get_phone_status",
      "arguments": {}
    }
  }'
```

需要截图时：

```json
{
  "name": "vflow_system_get_phone_status",
  "arguments": {
    "include_screenshot": true,
    "max_screenshot_width": 720
  }
}
```

默认返回的 `content` 只有文本：

- `content[0]`：`type = "text"`，其中 `status_json:` 后面是 JSON；默认体积约 6-10 KB。

显式开启截图且截图成功时，才会追加图片：

- `content[1]`：`type = "image"`，`mimeType = "image/jpeg"`，`data` 是 Base64；图片超过 6 MB 时不附加。
- `_meta.artifacts` 中仍保留 `artifact://` 句柄，可继续传给图片处理或 OCR 工具。

`status_json` 顶层字段：

- `success`、`timestamp`、`server`、`call_requirement`
- `permissions`：默认 `granted`、`total`、`missing[]`（id/name）；开启完整权限后为 `items[]`（id/name/description/type/granted）
- `tool_summary`：`total`、`available`、`partial`、`unavailable`
- `tools`：默认是 `{ partial: [...], unavailable: [...] }`，每项只含 name/title/missing_permissions；开启完整工具后为 142 条完整数组
- `ui`：`requested`、`available`、`package`、`window_class`、`count`、`visited`、`truncated`、`nodes[]`
- `ui.nodes[]`：`text`（最多 120 字符）、`desc`（最多 80 字符）、`hint`（最多 60 字符）、`id`、`class`、`bounds`、`center`，以及需要时出现的 `clickable`/`long_clickable`/`editable`/`scrollable`/`focusable`/`focused`/`checkable`/`checked`/`selected`/`password`/`enabled:false`
- `device`：build/battery/memory/storage/network/uptime/locale/timezone
- `screen`：screen_on/locked/width/height/density/orientation/rotation/brightness/current_package/current_activity 等
- `screenshot`：requested/captured/skipped/source/width/height/format/file/error

`ui` 示例（当前活动窗口为系统设置）：

```json
{
  "requested": true,
  "available": true,
  "package": "com.android.settings",
  "window_class": "android.widget.FrameLayout",
  "count": 29,
  "visited": 104,
  "truncated": false,
  "nodes": [
    {
      "text": "WLAN",
      "id": "title",
      "class": "TextView",
      "bounds": "199,1030,330,1093",
      "center": "264,1061"
    },
    {
      "text": "已开启",
      "desc": "",
      "id": "text_right",
      "class": "TextView",
      "bounds": "836,1187,953,1245",
      "center": "894,1216"
    }
  ]
}
```

未先调用该工具时，其他工具的返回示例：

```json
{
  "isError": true,
  "_meta": { "status": "STATUS_REQUIRED", "summary": "Phone status required" },
  "content": [
    {
      "type": "text",
      "text": "请先调用工具 `vflow_system_get_phone_status` 获取当前权限、设备状态和屏幕状态，然后再调用 `vflow_device_toast`。"
    }
  ]
}
```

真机实测（Redmi 23049RAD8C，Android 15，1080×2400）：

- 默认广泛模式（release pr13）：QQ 主界面 `status_json` 约 **15.5 KB**，当前活动窗口返回 **66 个**节点（含 10 个会话卡片容器、头像、列表容器、底部 tab），访问 263 个节点；HTTP 往返约 **98–221 ms**。vFlow 设置页约 **8.0 KB**、21 个节点、58 个访问节点。
- 会话列表中的「666」群聊同时给出可点击容器（desc 含完整会话摘要）和内部标题/预览节点，AI 可以直接选择容器点击，也可以点击具体文字，不会因为父节点可点击而丢掉子按钮。
- 显式 `include_screenshot=true`：HTTP 往返约 **161 ms**，JPEG 约 **83.5 KB**（Base64 后响应约 119 KB）。
- 显式 `include_permission_details=true` + `include_tool_details=true`：响应约 48 KB，用于确实需要完整清单时。
- 默认不再返回截图和 142 条完整工具明细；相比旧版约 40 KB 文本 + 约 200 KB 图片，默认负载仍下降约 **90% 以上**，但保留全部可能用到的控件。
- 远程设备（OnePlus PKR110，Android 16，1080×2376，MCP Bearer Token）在 pr13 后实测：QQ 主界面默认状态工具返回 **67 个节点 / 19.4 KB / 352 ms**，群 666 的可点击卡片容器和内部文本同时存在；从启动 QQ 到进入群 666、输入「你好」并点击发送，全流程 **10.958 秒**完成（含截图验证，目标 <60 秒）。截图确认聊天窗口出现新的「你好」消息，发送后输入框清空。

### 4.6 工作流导出工具（AI 操作过程 → vFlow JSON）

工具名：`vflow_workflow_export`

模块 ID：`vflow.mcp.workflow_export`（MCP 原生工具，不属于 142 个模块工具列表）

AI 在使用 MCP 工具完成一段操作后，可以调用该工具把**本次 MCP 会话中的成功动作**导出为真正的 vFlow 工作流，而不是脚本。导出前会自动做一次确定性检查：

- 只记录成功执行、状态为 `SUCCESS` 的模块调用；
- 不记录 `vflow_system_get_phone_status`、`vflow_workflow_export` 自身、失败或权限不足的调用；
- 默认移除纯观察类调用（状态读取、屏幕状态、当前 Activity、`find_element`/`find_text`/`find_until`/`find_image`/OCR/UI selector、剪贴板、电池、使用统计、系统信息、IP、通知查找、Core `*_state` 等）；
- 移除完全重复的调用（工具名 + 参数完全相同，只保留第一次）；
- `keep_observations=true` 可保留观察类步骤，便于生成“检查后操作”的完整流程。

参数：

| 参数 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `name` | string | 自动生成 | 工作流名称 |
| `description` | string | 自动生成 | 工作流描述 |
| `save` | boolean | `true` | 是否保存到 vFlow 工作流列表和 `/sdcard/vFlow/exports/` |
| `keep_observations` | boolean | `false` | 是否保留观察类步骤 |
| `dry_run` | boolean | `false` | 只预览 JSON 和清理结果，不保存 |

调用示例：

```json
{
  "name": "vflow_workflow_export",
  "arguments": {
    "name": "QQ 群 666 发送你好",
    "description": "打开 QQ，进入群 666 并发送一条消息",
    "save": true
  }
}
```

返回内容包含：

- 保留的步骤数和被清理步骤的列表（工具名、摘要、原因）；
- `workflow_id`、保存路径；
- 完整 vFlow 工作流 JSON（`triggers` 为手动触发器，`steps` 为真实动作步骤，可直接导入/编辑/执行）；
- `_meta` 中的 `workflow_id`、`step_count`、`removed_count`、`saved`、`dry_run`、`json_path`。

生成的工作流使用 vFlow 标准 JSON 结构，`ActionStep` 的 `moduleId` 与 MCP 工具对应的模块一致，参数已经按模块输入默认值补全。保存时会经过 `WorkflowManager` 的规范化逻辑，并写入 SharedPreferences 的工作流列表。

注意：导出的是“本次 MCP `initialize` 之后”的动作历史；新的 MCP 会话会清空历史。需要跨会话保存时，应先在同一会话内完成操作再导出。

---

### 4.7 UI 录制工具（录制屏幕操作 → vFlow JSON）

> v1.5.3-pr18 新增。MCP 总工具数为 **144**：142 个本地模块工具 + `vflow_workflow_export` + `vflow_workflow_from_recording`。

**入口**

- 首页 →「录制UI结果」进入 `UiRecordingsActivity` 录制管理页；
- 管理页右下角「录制UI」→ 阅读使用说明 →「开始录制」；
- 悬浮窗提供「开始/暂停/继续/结束」；结束后输入自定义名称，留空使用默认名称；
- 录制列表可查看、删除录制。

**录制方式**

- 服务 `UiRecordingService` 在前台运行，每 **300ms** 采样一次当前屏幕：
  - 当前活动窗口的无障碍节点树；
  - 屏幕截图（JPEG，质量 60，最大宽度 540，异步保存）。
- 每次采样计算 UI 快照签名：
  - 签名变化 → 新开一个时间段，保存该段 UI 快照与截图；
  - 签名未变化 → 只延长当前时间段，不重复保存节点/截图；
  - 因此静止画面不会无限增长数据。
- 录制结束后保存为：

```text
/sdcard/vFlow/recordings/<recordingId>.json
/sdcard/vFlow/recordings/<recordingId>/seg_0.jpg
/sdcard/vFlow/recordings/<recordingId>/seg_1.jpg
...
```

**JSON 结构**

- 顶层：`id`、`name`、`createdAt`、`durationMs`、`screenWidth`、`screenHeight`、`segments`、`events`。
- `segments[]`：时间段、节点快照、`screenshot` 文件名。
- `events[]`：`timestampMs`、`type`（`CLICK`/`TEXT_CHANGED`/`SCROLL`/`WINDOW_CHANGED`）、`text`、`packageName`、`className`、`viewId`、`gkd`、`left/top/right/bottom` 等。
- 录制服务自身的悬浮窗控制按钮（开始/暂停/继续/结束/UI 录制）会被过滤，不进入 `events`。

**MCP 工具：`vflow_workflow_from_recording`**

参数：

| 参数 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `recording` | string | 必填 | 录制 ID、名称或 JSON 绝对路径；找不到时会返回可用录制列表 |
| `name` | string | 录制名 + 工作流 | 生成的工作流名称 |
| `description` | string | 自动生成 | 工作流描述，自动包含录制来源与截图目录 |
| `save` | boolean | `true` | 保存到 WorkflowManager 与 `/sdcard/vFlow/exports/` |
| `dry_run` | boolean | `false` | 只预览，不保存 |

还原规则：

- `CLICK` → `vflow.interaction.ui_selector` 定位控件，再用 `vflow.device.click` 点击 `{{<selectorStepId>.element}}`；
- `TEXT_CHANGED` → 合并连续输入，密码/空文本跳过，必要时补定位+点击，再生成 `vflow.interaction.input_text`；
- `SCROLL` → 生成 `vflow.interaction.screen_operation` 的 swipe，方向未知时按上滑处理；
- 无事件或事件不足时，用相邻时间段的差异推断输入/点击；跨应用或不可靠信息只记录 warning，不硬编码；
- 选择器优先使用录制时的 GKD 表达式，其次使用 `viewId`、`className`、`text`、`contentDescription`、`clickable`/`editable`、bounds、depth；
- 每个定位步骤额外携带 `recording_node`（完整控件 JSON：包名、类名、viewId、GKD、bounds、actions、state 等）和 `fallback_target`（中心坐标），方便控件变化时人工修正；
- 生成的工作流为手动触发器，`maxExecutionTime=300`，`reentryBehavior=stop_current_and_run_new`，可立即在 vFlow 中执行或编辑。

实测（d29e87ee，Android 15）：

- 录制：5,195ms、2 个时间段、3 个事件、2 张截图；
- `vflow_workflow_from_recording`：**124ms**，生成 2 步：
  1. `ui_selector`：selector `[vid='action_bar_container']`，带完整 `recording_node` 与 `fallback_target=540,216`；
  2. `click`：target `{{<selectorStepId>.element}}`，`fallback_target=540,216`；
- 未出现 vFlow 悬浮窗“暂停/结束”等录制控制步骤。

> 录制 UI 功能需要无障碍服务与悬浮窗权限；首次点击录制会跳转系统设置授权，授权后再次点击即可显示使用说明。

---

## 5. 工具来源

### 5.1 暴露范围

MCP 工具来自 `ModuleRegistry` 中已注册的本地模块，包含内建模块和通过外部 APK 加载的扩展模块。

以下模块不会暴露：

- 触发器模块（`vflow.trigger.*`）：触发器只能由系统事件启动，无法作为独立工具调用。
- 块控制模块（`If`、`Loop`、`ForEach`、`Menu`、`OnUiEvent` 等的开始/中间/结束块）：它们依赖完整工作流的程序计数器和步骤上下文，单独调用没有意义。
- 名称规范化后发生冲突的模块：保留先注册的工具，避免同名覆盖。

其余可独立执行的模块全部作为 MCP 工具暴露。

### 5.2 命名规则

工具名由模块 ID 转换而来：

1. 全部转小写。
2. 非 `[a-z0-9_]` 字符替换为 `_`。
3. 去掉首尾 `_`。
4. 如果结果不以 `vflow_` 开头，则添加 `vflow_` 前缀。

示例：

| 模块 ID | MCP 工具名 |
| --- | --- |
| `vflow.device.toast` | `vflow_device_toast` |
| `vflow.system.get_clipboard` | `vflow_system_get_clipboard` |
| `vflow.network.http_request` | `vflow_network_http_request` |
| `vflow.core.shell_command` | `vflow_core_shell_command` |

### 5.3 参数

工具的 `inputSchema` 由模块的 `InputDefinition` 自动生成：

- `STRING` → `string`
- `NUMBER` → `number`
- `BOOLEAN` → `boolean`
- `ENUM` → `string` + `enum`
- `ANY`（仅接受魔法变量的输入）→ `string`

调用参数按输入 ID 匹配，未识别的字段会被忽略。必填项来自模块的 `aiMetadata.requiredInputIds`。

---

## 6. 为什么没有工作流调度延迟

MCP 调用链：

```text
AI 客户端
  → POST /mcp (JSON-RPC tools/call)
  → McpServer.callTool()
  → ChatAgentModuleExecutor.prepareBatch()
  → ChatAgentModuleExecutor.executeBatch()
  → ActionModule.execute(ExecutionContext)
  → 返回 ChatToolResult
  → MCP content 响应
```

关键点：

- 不创建 `Workflow`，不经过 `WorkflowExecutor` 的程序计数器循环。
- 不写入工作流列表，不触发 `TriggerService`。
- 直接构造只包含当前步骤的 `ExecutionContext`，同步等待模块 `execute` 返回。
- 采用 `runBlocking(Dispatchers.Default)` 在 HTTP 请求线程内完成执行，响应即结果。
- 多个 MCP 请求通过 `Mutex` 串行执行，避免并发点击、滑动、UI 操作互相干扰；单次调用本身没有额外排队。

---

## 7. 权限与安全

### 7.1 权限检查

执行前会调用 `PermissionManager.isGranted()` 检查模块声明的权限。缺少权限时：

- 不执行模块。
- 返回 `isError: true`。
- 文本中列出缺少的权限，例如“无障碍服务”“存储权限”“通知权限”。

请在 vFlow 的权限设置中授予对应权限后重试。

### 7.2 安全建议

- 不要把 MCP 端口暴露到公网。
- 在公共 Wi-Fi 下务必创建访问令牌。
- 令牌等同于设备控制权限，泄露后请立即在“mcp服务器管理”中撤销。
- MCP 工具可以执行点击、输入、Shell、文件读写等高风险操作，请只连接可信的 AI 客户端。
- 首次接入建议先在测试设备上验证，并只授予必要的无障碍/存储权限。

### 7.3 服务器绑定

`McpServer` 通过 `NanoHTTPD(MCP_BIND_ADDRESS, port)` 显式绑定 `0.0.0.0`，即所有网络接口（Wi-Fi、USB 网络共享、`adb forward` 等）。`/health` 与 `/` 会返回 `bindAddress: "0.0.0.0"` 便于确认。若只想本机访问，请使用手机防火墙限制端口，或修改 `McpServer.kt` 中的 `MCP_BIND_ADDRESS`。

---

## 8. 与旧 REST API 的关系

- 设置入口和用户可见文案已切换为 MCP。
- `ApiService` 现在启动 `McpServer`，不再启动旧的 `ApiServer`。
- 旧 REST Handler（工作流、执行记录、文件夹、导入导出等）代码仍保留在仓库中，但默认不再对外提供服务。
- 访问令牌存储、端口配置、启停逻辑继续复用原有 `vflow_api` / `vflow_api_tokens` SharedPreferences。

---

## 9. 源码位置

| 文件 | 作用 |
| --- | --- |
| `app/src/main/java/com/chaomixian/vflow/api/mcp/McpServer.kt` | MCP HTTP 服务器、JSON-RPC 路由、鉴权、工具列表与调用 |
| `app/src/main/java/com/chaomixian/vflow/api/ApiService.kt` | 单例生命周期、端口配置、Token 管理、启动 `McpServer` |
| `app/src/main/java/com/chaomixian/vflow/ui/chat/ChatAgentToolRegistry.kt` | 模块 → 工具定义/schema；新增按需注册全部模块的方法 |
| `app/src/main/java/com/chaomixian/vflow/ui/chat/ChatAgentModuleExecutor.kt` | 参数转换、权限检查、直接执行模块 |
| `app/src/main/java/com/chaomixian/vflow/core/module/ModuleRegistry.kt` | 本地模块注册表 |
| `app/src/main/java/com/chaomixian/vflow/core/workflow/module/system/GetPhoneStatusModule.kt` | 权限感知手机状态模块（权限/工具可用性/设备/屏幕/精简 UI/可选截图） |
| `app/src/main/res/values/strings.xml` | 中文设置文案 |
| `app/src/main/res/values-en/strings.xml` | 英文设置文案 |
| `app/src/main/res/values-ja/strings.xml` | 日文设置文案 |

---

## 10. 故障排查

### 客户端连接失败

1. 确认手机和电脑在同一网段，且能互相 ping 通。
2. 确认“启用mcp服务器”已打开。
3. 浏览器访问 `http://<手机IP>:<端口>/health` 是否有 JSON 返回。
4. 检查电脑防火墙是否拦截了出站/入站连接。
5. 确认客户端使用 `POST /mcp`，而不是 `/sse` 或 `/api/v1`。

### 返回 401

- 已创建令牌时必须携带 `Authorization: Bearer <token>`。
- 令牌可能已过期，重新生成一个。
- 使用 URL 参数时注意 URL 编码。

### 返回“缺少权限”

在 vFlow 权限页授予对应权限。部分权限（无障碍、悬浮窗、通知监听、MANAGE_EXTERNAL_STORAGE、Shizuku/Root）需要跳转系统设置手动开启。

### 工具列表为空

- 确认模块注册成功（重启 vFlow 后重试）。
- 查看 logcat 中 `McpServer` / `ChatAgentToolRegistry` 相关日志。
- 外部模块需要其 APK 已安装且 `MODULE_PROVIDER` Service 可被发现。

### 工具执行超时或客户端断开

- 部分模块（HTTP 请求、OCR、AI 调用）本身耗时较长，请调大客户端超时。
- 服务器同一时间只执行一个工具调用，多个请求会依次排队。
- 如果模块卡死，停止并重新启用 MCP 服务器。

### 调用一直卡住、`/health` 无响应

- 旧版本在应用退到后台后可能被 MIUI 冻结，表现为端口能连接但一直不返回。pr10 起由 `McpForegroundService` 保活，正常不会出现。
- 确认通知栏有“MCP 服务器运行中”；没有则重新打开“启用mcp服务器”。
- 确认客户端连接的是已升级到 pr15 的设备。旧版 pr2 只有 141 个工具，不包含 `vflow_system_get_phone_status`，调用会返回 `Unknown tool`；pr10 默认返回完整权限/工具清单和截图，负载较大；pr11/pr12 的过滤过严，会丢掉无文字可点击容器、可滚动列表等操作必需节点；pr13 改为广泛采集（默认 120 个节点），pr14 移除 `max_ui_nodes` 的 300 上限但默认仍 120；pr15 默认返回当前窗口全部有效控件，不再设上限，只有传 `max_ui_nodes>0` 时才限制（最低 20）。

---

## 11. 验证命令

```bash
# 健康检查
curl http://192.168.1.23:8080/health

# 初始化
curl -s http://192.168.1.23:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"curl","version":"1.0"}}}'

# 工具数量
curl -s http://192.168.1.23:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}' \
  | python -c "import sys,json; print(len(json.load(sys.stdin)['result']['tools']))"
```

构建验证：

```powershell
$env:JAVA_HOME="C:\Users\Administrator\.jdks\jdk-17.0.2"
$env:ANDROID_HOME="E:\Android\sdk"
gradle :app:compileDebugKotlin --no-configuration-cache
```

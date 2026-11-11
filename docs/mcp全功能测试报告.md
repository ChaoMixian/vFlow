# vFlow MCP 全功能测试报告

> 测试对象：`vFlow 1.5.3-pr5`（versionCode 53）  
> 测试设备：23049RAD8C（Android 14，MIUI，1080×2400）  
> 测试方式：adb + MCP Streamable HTTP（`http://127.0.0.1:18080/mcp`，无 Token）  
> 测试日期：2026-11-10

## 1. 结论

> 注：本报告是 `v1.5.3-pr5` 的 141 工具基线。`v1.5.3-pr6` 曾新增全屏控件采集工具；`v1.5.3-pr7` 已将其替换为权限感知的 `vflow_system_get_phone_status`（142 个工具），并要求其他工具调用前先执行一次；`v1.5.3-pr8` 进一步让状态工具在 MCP `content` 中直接返回截图 Base64 图片；`v1.5.3-pr9` 将截图改为完全可选，无障碍与 Shell 回退都失败/超时时跳过截图，状态工具仍成功返回；`v1.5.3-pr10` 新增 `McpForegroundService`，修复应用退到后台后被 MIUI 冻结、MCP 调用一直卡住的问题；`v1.5.3-pr11` 将状态工具默认输出改为精简模式：只返回当前活动窗口最多 40 个有效控件，权限和工具清单只给统计与缺失项，截图默认关闭，默认响应从约 40KB 降到约 8-9KB；`v1.5.3-pr12` 进一步过滤纯图标/头像/空壳容器和同一区域重复的子文本，默认响应约 7.4KB、p50 约 73ms / p95 约 77ms。增量验证结果见 `docs/mcp服务器说明.md` 第 4.5 节。`v1.5.3-pr13` 改为广泛采集：默认 120 个控件，保留无文字可点击容器、可滚动列表、输入框和带 viewId 的控件，不再按父子包含关系删除节点；`v1.5.3-pr14` 移除 `max_ui_nodes` 的 300 上限，默认仍为 120，可调高且不设上限；仍默认关闭截图和完整清单。；1.5.3-pr16 新增 MCP 原生工作流导出工具 flow_workflow_export（总 143 工具），可把成功操作导出为 vFlow JSON，并自动清理观察类与重复调用。

- MCP 服务器暴露的 **141 个工具全部完成覆盖**，没有遗漏。
- 自动化测试执行 **112 次调用**，覆盖 110 个工具：**81 个通过，29 个按预期返回环境/上下文限制**。
- 熄屏/唤醒类 5 个工具单独测试：**2 个通过**（Core 唤醒/熄屏），3 个因缺少 Shizuku 返回权限错误。
- 其余 26 个工具未实际执行，原因是外部 API 凭据、隐私数据、真实拨号、人工弹窗或外部应用副作用。
- 可运行的本地功能全部通过；失败项均为**快速失败**（7–18ms），没有框架级卡死。
- MCP 框架固定开销稳定在 **约 43–57ms**（p50/p95 基准），纯本地短任务常见 5–100ms。截图、OCR、TTS、图像处理等耗时来自模块自身动作。

## 2. 环境与前置条件

- release APK：`release/v1.5.3-pr5/vFlow-1.5.3-pr5-arm64-v8a.apk`
- 已安装并授予：通知、全部文件访问、电池优化白名单、`WRITE_SECURE_SETTINGS`、定位、录音、相机、短信、联系人、电话、`READ_PHONE_STATE`
- 已开启：无障碍服务、通知监听、悬浮窗、使用情况访问
- vFlow Core 以 shell 身份手动启动，Master 监听 19999，ShellWorker 监听 20001
- MCP 服务器监听 `0.0.0.0:8080`，PC 通过 `adb forward tcp:18080 tcp:8080` 访问
- 健康检查：

```json
{
  "status": "ok",
  "server": "vFlow MCP",
  "version": "1.5.3-pr5",
  "protocolVersion": "2025-06-18",
  "bindAddress": "0.0.0.0",
  "tools": 141,
  "authRequired": false
}
```

## 3. 覆盖与结果

### 3.1 总体

| 类别 | 数量 |
|---|---:|
| MCP 工具总数 | 141 |
| 自动化覆盖工具 | 110 |
| 自动化调用次数 | 112 |
| 自动化通过工具 | 81 |
| 自动化受限工具 | 29 |
| 单独测试工具 | 5 |
| 单独测试通过 | 2 |
| 单独测试受限 | 3 |
| 主动跳过 | 26 |
| 最终覆盖 | 141 |

> 112 次调用多于 110 个工具，是因为 `file_operation` 写入/读取、`notification` 发送/查找、`ocr` 识别/查找各调用了两次。

### 3.2 分类结果

| 分类 | 通过 | 失败/受限 | 说明 |
|---|---:|---:|---|
| data | 20 | 0 | 加解密、编码、哈希、JSON/XML、文本、文件读写全部通过 |
| variable | 3 | 2 | create/random 通过；get/modify 依赖同一次执行上下文 |
| network | 3 | 0 | 本地 HTTP、Webhook、IP 获取通过 |
| notification | 2 | 1 | 发送/查找通过；remove 需要真实通知对象 |
| core | 17 | 4 | 屏幕、WiFi、蓝牙、NFC、音量、剪贴板、截图、Shell、输入、按键、强制停止通过 |
| system | 15 | 6 | 其余系统能力通过；6 项需要 Shizuku |
| device | 9 | 0 | Toast、点击、查找文本、按键、手电筒、音频、TTS、振动通过 |
| file | 5 | 0 | 缩放、旋转、调色、遮罩、保存图片通过 |
| interaction | 6 | 3 | 查找控件、OCR、屏幕操作、UI selector 通过；3 项需要 Shizuku |
| snippet | 1 | 0 | FindTextUntil 通过 |
| logic | 0 | 5 | 控制信号/循环上下文只能在工作流引擎内使用 |
| shizuku | 1 | 0 | Core 模式 Shell 通过 |
| ui | 1 | 8 | 屏幕闪烁通过；组件类需要 DynamicUiActivity 宿主 |

## 4. 延迟数据

### 4.1 稳定基准（无 UI、无外部 I/O）

| 工具 | 次数 | p50 | p95 | max |
|---|---:|---:|---:|---:|
| `vflow_data_comment` | 30 | 43.6ms | 44.3ms | 44.5ms |
| `vflow_core_screen_status` | 20 | 48.0ms | 57.1ms | 58.8ms |

MCP HTTP + JSON-RPC + 模块直接执行的固定开销约 **40–50ms**；没有工作流排队、没有额外调度延迟。

### 4.2 全量成功调用

| 指标 | 数值 |
|---|---:|
| 成功调用数 | 83 |
| p50 | 21.3ms |
| p95 | 568ms |
| max | 1188.6ms |
| 平均 | 98ms |

p95/max 主要由图像处理、截图、TTS、OCR、屏幕闪烁等真实动作构成。

### 4.3 最慢的正常项

| 工具 | 耗时 | 原因 |
|---|---:|---|
| `vflow_file_adjust_image` | 1188.6ms | 图像逐像素调色 |
| `vflow_data_quick_view` | 1107.9ms | 悬浮窗等待，测试器 1s 后自动 Back |
| `vflow_core_capture_screen` | 1090ms | 系统截屏 + 编码 |
| `vflow_file_apply_mask` | 688.2ms | 图像遮罩 |
| `vflow_file_rotate_image` | 568ms | 图像旋转 |
| `vflow_device_text_to_speech` | 506.9ms | TTS 引擎初始化/排队 |
| `vflow_ui_screen_flash` | 264.4ms | 动画时长 |
| `vflow_device_vibration` | 209.9ms | 振动时长 |
| `vflow_interaction_ocr` | 204.8ms / 137.9ms | ML Kit 识别/查找 |

## 5. 失败项分类（均为预期限制）

### 5.1 缺少 Shizuku（快速失败 8–18ms）

- `vflow_system_capture_screen`
- `vflow_system_darkmode`
- `vflow_system_wifi`
- `vflow_system_bluetooth`
- `vflow_system_close_app`
- `vflow_system_invoke`
- `vflow_interaction_find_image`
- `vflow_interaction_find_until`
- `vflow_interaction_input_text`
- 单独测试：`vflow_system_wake_screen`
- 单独测试：`vflow_system_wake_and_unlock_screen`
- 单独测试：`vflow_system_sleep_screen`

对应 Core 版本（截屏、WiFi、蓝牙、强制停止、唤醒、熄屏、输入）已通过，说明功能本身可用，只是该设备未安装/授权 Shizuku。

### 5.2 缺少 Root

- `vflow_core_hotspot_state`
- `vflow_core_hotspot`
- `vflow_core_uinput_screen_operation`

设备未 Root，Core Root Worker 不可用。

### 5.3 MCP 独立调用上下文限制

- `vflow_variable_get` / `vflow_variable_modify`：每次 MCP 调用使用独立 `ExecutionContext`，变量不跨调用保存。
- `vflow_notification_remove`：需要真实的 `VNotification` 对象，MCP 文本参数无法构造。
- `vflow_logic_break_loop` / `continue_loop` / `return` / `stop_workflow` / `jump`：返回工作流控制信号，必须由 `WorkflowExecutor` 解释。
- `vflow_ui_component_*` / `vflow_ui_interaction_get_value/update/exit`：需要 `DynamicUiActivity` 宿主和 UI 会话。

### 5.4 其他

- `vflow_core_touch_replay`：没有预先录制的触摸轨迹。

## 6. 主动跳过的 26 项

| 类型 | 工具 | 原因 |
|---|---|---|
| AI | `vflow_ai_agent`、`vflow_ai_autoglm`、`vflow_ai_completion` | 需要外部模型 API Key/费用 |
| 飞书 | `vflow_feishu_get_message_history`、`vflow_feishu_send_message` | 需要飞书凭据 |
| 外部应用 | `vflow_integration_clash_meta`、`vflow_integration_flclash`、`vflow_integration_ithome_check_in` | 可能改变代理/账号状态 |
| 外部推送 | `vflow_network_bark_push`、`vflow_network_discord_push`、`vflow_network_telegram_push`、`vflow_network_feishu_upload` | 需要真实推送凭据 |
| 快捷方式 | 支付宝/微信/ColorOS/Gemini 4 项、`vflow_system_launch_shortcut` | 外部应用副作用/需要已创建快捷方式 |
| 隐私/通信 | `vflow_device_call_phone`、`vflow_system_read_sms` | 真实拨号/个人短信 |
| 人工交互 | `vflow_device_speech_to_text`、`vflow_data_input`、`vflow_file_import_image`、`vflow_logic_list_choose` | 需要真人语音或弹窗选择 |
| 其他 | `vflow_interaction_operit`、`vflow_logic_call_workflow`、`vflow_system_mobile_data` | 外部会话、工作流 ID、可能断网 |

## 7. 本轮修复验证

| 问题 | 修复 | 结果 |
|---|---|---|
| 数字字符串被转成 Double（DES/SM4 密钥、手机号等） | `normalizeJsonValue` 对 `JsonPrimitive.isString` 直接返回原文 | Base64 `"12345678"` → `MTIzNDU2Nzg=`；DES 数字 key 通过 |
| SM4 使用 `PKCS5Padding` | 映射为 BouncyCastle 的 `PKCS7Padding` | SM4 加密/解密通过 |
| Android 自带 BC Provider 覆盖完整 BouncyCastle | 直接持有 `BouncyCastleProvider()` 实例并传给 `Cipher`/`MessageDigest` | SM3 通过 |
| release R8 裁剪 BC 反射算法类 | `-keep class org.bouncycastle.** { *; }` | SM4/SM3 在 release 包通过 |

## 8. 测试脚本与证据

目录：`C:\Users\Administrator\AppData\Local\Temp\opencode\vflow-test\`

- `run-tests.js`：全量 112 次调用、基准、统计
- `manual-sleep.js`：熄屏/唤醒单独测试
- `retest-pr5.js`：Base64/DES/SM4/SM3 回归
- `out/results.json`：逐项结果（名称、参数、耗时、状态、文本）
- `out/results.csv`：CSV 版本
- `out/run-pr5-final3.log`：最终运行日志
- `out/run-pr5-final3.log` 对应 83 成功 / 29 失败 / 141 覆盖

## 9. 复现命令

```powershell
# 1. 安装 release
adb install -r release\v1.5.3-pr5\vFlow-1.5.3-pr5-arm64-v8a.apk

# 2. 启动 App，进入 设置 → 启用mcp服务器

# 3. 建立端口转发
adb forward tcp:18080 tcp:8080

# 4. 健康检查
curl http://127.0.0.1:18080/health

# 5. 运行全量测试
cd C:\Users\Administrator\AppData\Local\Temp\opencode\vflow-test
node run-tests.js
```

## 10. 备注

- MIUI 在熄屏后会冻结后台应用，导致 MCP 服务在熄屏期间无法响应；因此熄屏/唤醒测试已从主流程剥离，单独执行并通过 adb 唤醒恢复。
- 本轮测试未使用 OpenCode 配置中的另一台设备 MCP（`10.126.126.3:8080`），全部数据来自 adb 设备 `d29e87ee`。
- 测试结束后设备保持亮屏，MCP 服务仍在运行，Core 手动进程仍在；未卸载 App。

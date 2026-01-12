# 触发器API参考

触发器模块定义了工作流的启动条件。

> **注意**：触发器通常不直接在用户模块的Lua脚本中调用。它们作为工作流的入口点存在。本章节主要供了解和参考。

## 触发器列表

### 手动触发

**模块ID**: `vflow.trigger.manual`

通过用户点击按钮手动启动工作流。

#### 特点

- 无需参数
- 始终成功返回
- 适合调试和测试

#### 在工作流中使用

在工作流编辑器中，将此模块设为第一步。用户点击"运行"按钮时触发。

---

### 接收分享

**模块ID**: `vflow.trigger.share`

当用户通过Android系统的分享菜单分享内容到vFlow时触发。

#### manifest.json配置示例

```json
{
  "inputs": [
    {
      "id": "acceptedType",
      "name": "接收内容类型",
      "type": "enum",
      "options": ["任意", "文本", "链接", "图片", "文件"],
      "defaultValue": "任意"
    }
  ],
  "outputs": [
    {
      "id": "shared_content",
      "name": "分享的内容",
      "type": "text"
    }
  ]
}
```

#### 输出说明

根据`acceptedType`设置，`shared_content`的类型不同：
- `文本` 或 `链接` → 文本类型
- `图片` → 图像类型
- `文件` 或 `任意` → 文本类型（URI）

---

### 应用启动

**模块ID**: `vflow.trigger.app_start`

当指定的应用程序打开或关闭时触发。

#### 参数

```json
{
  "inputs": [
    {
      "id": "event",
      "name": "事件",
      "type": "enum",
      "options": ["打开时", "关闭时"],
      "defaultValue": "打开时"
    },
    {
      "id": "packageName",
      "name": "应用包名",
      "type": "string",
      "defaultValue": ""
    },
    {
      "id": "activityName",
      "name": "Activity名称",
      "type": "string",
      "defaultValue": "LAUNCH"
    }
  ]
}
```

#### 参数说明

- `event`: 触发时机
  - `打开时`: 当应用启动时触发
  - `关闭时`: 当应用关闭时触发
- `packageName`: 目标应用的包名（例如：`com.tencent.mm`）
- `activityName`: 可选，指定Activity名称。默认为 `LAUNCH`，表示监听整个应用
```

---

### 键盘事件

**模块ID**: `vflow.trigger.key_event`

监听物理按键事件。需要Root或Shizuku权限。

#### 参数

```json
{
  "inputs": [
    {
      "id": "device_preset",
      "name": "设备预设",
      "type": "enum",
      "options": ["手动/自定义", "一加 13T (侧键)", "一加 13 (三段式)"],
      "defaultValue": "手动/自定义"
    },
    {
      "id": "device",
      "name": "输入设备",
      "type": "string",
      "defaultValue": "/dev/input/event0"
    },
    {
      "id": "key_code",
      "name": "按键码",
      "type": "string",
      "defaultValue": "KEY_POWER"
    },
    {
      "id": "action_type",
      "name": "操作类型",
      "type": "enum",
      "options": ["单击", "双击", "长按", "短按 (立即触发)"],
      "defaultValue": "单击"
    }
  ]
}
```

---

### 时间触发

**模块ID**: `vflow.trigger.time`

在指定时间和日期触发工作流。

#### 参数

```json
{
  "inputs": [
    {
      "id": "time",
      "name": "触发时间",
      "type": "string",
      "defaultValue": "09:00"
    },
    {
      "id": "days",
      "name": "重复日期",
      "type": "any",
      "defaultValue": [1, 2, 3, 4, 5, 6, 7]
    }
  ]
}
```

#### days参数说明

周日至周六的数字表示：`1`(周日) 到 `7`(周六)。

---

### 电池状态

**模块ID**: `vflow.trigger.battery`

当电池电量满足特定条件时触发。

#### 参数

```json
{
  "inputs": [
    {
      "id": "level",
      "name": "电量阈值",
      "type": "number",
      "defaultValue": 50
    },
    {
      "id": "above_or_below",
      "name": "触发条件",
      "type": "string",
      "defaultValue": "below"
    }
  ]
}
```

#### 参数说明

- `level`: 电量阈值 (0-100)
- `above_or_below`: 触发条件
  - `above`: 当电量**高于**阈值时触发
  - `below`: 当电量**低于**阈值时触发
```

---

### WiFi状态

**模块ID**: `vflow.trigger.wifi`

当Wi-Fi状态或网络连接变化时触发。

#### 参数

```json
{
  "inputs": [
    {
      "id": "trigger_type",
      "name": "触发类型",
      "type": "enum",
      "options": ["网络连接", "Wi-Fi状态"],
      "defaultValue": "网络连接"
    },
    {
      "id": "connection_event",
      "name": "事件",
      "type": "enum",
      "options": ["连接到", "断开连接"],
      "defaultValue": "连接到"
    },
    {
      "id": "network_target",
      "name": "网络",
      "type": "string",
      "defaultValue": "ANY_WIFI"
    },
    {
      "id": "state_event",
      "name": "事件",
      "type": "enum",
      "options": ["开启时", "关闭时"],
      "defaultValue": "开启时"
    }
  ],
  "outputs": [
    {
      "id": "ssid",
      "name": "网络SSID",
      "type": "text"
    },
    {
      "id": "bssid",
      "name": "网络BSSID",
      "type": "text"
    }
  ]
}
```

#### 参数说明

根据 `trigger_type` 的不同，使用不同的参数组合：

**当 `trigger_type` = "网络连接" 时：**
- `connection_event`: 连接事件
  - `连接到`: 当连接到指定WiFi时触发
  - `断开连接`: 当断开WiFi时触发
- `network_target`: WiFi网络SSID（设为 "ANY_WIFI" 表示任意WiFi）

**当 `trigger_type` = "Wi-Fi状态" 时：**
- `state_event`: 状态事件
  - `开启时`: 当Wi-Fi开启时触发
  - `关闭时`: 当Wi-Fi关闭时触发
```

---

### 蓝牙状态

**模块ID**: `vflow.trigger.bluetooth`

当蓝牙状态或设备连接变化时触发。

#### 参数

```json
{
  "inputs": [
    {
      "id": "trigger_type",
      "name": "触发类型",
      "type": "enum",
      "options": ["蓝牙状态", "设备连接"],
      "defaultValue": "蓝牙状态"
    },
    {
      "id": "state_event",
      "name": "事件",
      "type": "enum",
      "options": ["开启时", "关闭时"],
      "defaultValue": "开启时"
    },
    {
      "id": "device_event",
      "name": "事件",
      "type": "enum",
      "options": ["连接时", "断开时"],
      "defaultValue": "连接时"
    },
    {
      "id": "device_address",
      "name": "设备地址",
      "type": "string"
    },
    {
      "id": "device_name",
      "name": "设备名称",
      "type": "string",
      "defaultValue": "任何设备"
    }
  ],
  "outputs": [
    {
      "id": "device_name",
      "name": "设备名称",
      "type": "text"
    },
    {
      "id": "device_address",
      "name": "设备地址",
      "type": "text"
    }
  ]
}
```

#### 参数说明

根据 `trigger_type` 的不同，使用不同的参数组合：

**当 `trigger_type` = "蓝牙状态" 时：**
- `state_event`: 蓝牙状态事件
  - `开启时`: 当蓝牙开启时触发
  - `关闭时`: 当蓝牙关闭时触发

**当 `trigger_type` = "设备连接" 时：**
- `device_event`: 设备连接事件
  - `连接时`: 当设备连接时触发
  - `断开时`: 当设备断开时触发
- `device_name`: 蓝牙设备名称（默认 "任何设备"）
- `device_address`: 可选，蓝牙设备MAC地址
```

---

### 短信事件

**模块ID**: `vflow.trigger.sms`

当收到满足特定条件的短信时触发。

#### 参数

```json
{
  "inputs": [
    {
      "id": "sender_filter_type",
      "name": "发件人条件",
      "type": "enum",
      "options": ["任意号码", "号码包含", "号码不包含", "正则匹配"],
      "defaultValue": "任意号码"
    },
    {
      "id": "sender_filter_value",
      "name": "发件人值",
      "type": "string",
      "defaultValue": ""
    },
    {
      "id": "content_filter_type",
      "name": "内容条件",
      "type": "enum",
      "options": ["任意内容", "识别验证码", "内容包含", "内容不包含", "正则匹配"],
      "defaultValue": "任意内容"
    },
    {
      "id": "content_filter_value",
      "name": "内容值",
      "type": "string",
      "defaultValue": ""
    }
  ],
  "outputs": [
    {
      "id": "sender_number",
      "name": "发件人号码",
      "type": "text"
    },
    {
      "id": "message_content",
      "name": "短信内容",
      "type": "text"
    },
    {
      "id": "verification_code",
      "name": "验证码",
      "type": "text"
    }
  ]
}
```

#### 参数说明

**发件人过滤 (`sender_filter_*`)：**
- `任意号码`: 匹配所有发件人
- `号码包含`: 发件人号码包含指定文本
- `号码不包含`: 发件人号码不包含指定文本
- `正则匹配`: 使用正则表达式匹配号码

**内容过滤 (`content_filter_*`)：**
- `任意内容`: 匹配所有短信内容
- `识别验证码`: 自动识别并提取验证码
- `内容包含`: 短信内容包含指定文本
- `内容不包含`: 短信内容不包含指定文本
- `正则匹配`: 使用正则表达式匹配内容

> **注意**: 当选择 `识别验证码` 时，输出会额外包含 `verification_code` 字段。
```

---

### 通知事件

**模块ID**: `vflow.trigger.notification`

监听系统通知。

#### 参数

```json
{
  "inputs": [
    {
      "id": "app_filter",
      "name": "应用包名",
      "type": "string"
    },
    {
      "id": "title_filter",
      "name": "标题包含",
      "type": "string"
    },
    {
      "id": "content_filter",
      "name": "内容包含",
      "type": "string"
    }
  ],
  "outputs": [
    {
      "id": "package_name",
      "name": "应用包名",
      "type": "text"
    },
    {
      "id": "title",
      "name": "通知标题",
      "type": "text"
    },
    {
      "id": "content",
      "name": "通知内容",
      "type": "text"
    }
  ]
}
```

---

## 在用户模块中使用触发器输出

当你的模块需要接收触发器的输出时，在manifest.json中定义相应的输入：

```json
{
  "id": "user.process_sms",
  "name": "处理短信",
  "inputs": [
    {
      "id": "sender",
      "name": "发送者",
      "type": "string",
      "magic_variable": true
    },
    {
      "id": "content",
      "name": "短信内容",
      "type": "string",
      "magic_variable": true
    }
  ],
  "outputs": [
    {
      "id": "reply",
      "name": "自动回复",
      "type": "text"
    }
  ]
}
```

在script.lua中：

```lua
-- 获取短信触发器的输出
local sender = inputs.sender
local content = inputs.content

-- 处理短信
local reply_text = "收到来自 " .. sender .. " 的短信"

-- 显示通知
vflow.device.toast({
    message = reply_text
})

return {
    reply = reply_text
}
```

---

## 注意事项

1. **触发器是工作流入口**：触发器模块总是工作流的第一步，不能在用户模块的Lua脚本中主动调用
2. **输出传递**：触发器的输出会通过魔法变量传递给后续步骤
3. **权限要求**：某些触发器需要特殊权限（如通知监听、短信读取等）
4. **电池优化**：定时触发可能需要关闭电池优化才能正常工作

---

## 相关文档

- [系统API](./system.md) - 系统级功能
- [逻辑控制API](./logic.md) - 控制工作流执行
- [快速开始](./README.md) - 创建你的第一个模块

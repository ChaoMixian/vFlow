# 系统API参考

系统API用于在Lua脚本中执行系统级操作，如延迟、输入输出、剪贴板、设备控制等。

## 概述

系统API提供以下功能：
- **延迟**：暂停工作流执行
- **交互**：请求用户输入、快速查看、Toast提示
- **Lua脚本**：执行自定义Lua代码
- **应用管理**：启动/关闭应用
- **剪贴板**：读取和写入剪贴板
- **分享**：调用系统分享
- **系统控制**：Wi-Fi、蓝牙、亮度、屏幕等
- **通知**：读取、查找、删除通知
- **其他**：短信、应用使用统计、通用调用

## API列表

### vflow.device.delay

暂停工作流一段时间。

#### 参数

| 参数ID | 名称 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| `duration` | 延迟时间 | number | 是 | 毫秒 |

#### 返回值
`{ success = boolean }`

---

### vflow.data.input

弹出一个窗口，请求用户输入文本、数字、时间或日期。

#### 参数

| 参数ID | 名称 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| `inputType` | 输入类型 | enum | 是 | `文本`, `数字`, `时间`, `日期` |
| `prompt` | 提示信息 | string | 是 | 弹窗标题 |

#### 返回值
`{ userInput = any, success = boolean }`

---

### vflow.data.quick_view

在悬浮窗中显示文本、数字、图片等各种类型的内容。

#### 参数

| 参数ID | 名称 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| `content` | 内容 | any | 是 | 支持文本或 ImageVariable |

#### 返回值
`{ success = boolean }`

---

### vflow.device.toast

在屏幕底部弹出一个简短的提示消息。

#### 参数

| 参数ID | 名称 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| `message` | 消息内容 | string | 是 | |

#### 返回值
`{ success = boolean }`

---

### vflow.system.lua

执行一段Lua脚本，可调用其他模块功能。

#### 参数

| 参数ID | 名称 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| `script` | Lua 脚本 | string | 是 | 代码内容 |
| `inputs` | 脚本输入 | dictionary | 否 | 传递给脚本的变量 |

#### 返回值
`{ outputs = dictionary }`

---

### vflow.system.launch_app

启动一个指定的应用程序或其内部的某个页面(Activity)。

#### 参数

| 参数ID | 名称 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| `packageName` | 应用包名 | string | 是 | |
| `activityName` | Activity 名称 | string | 否 | 默认 "LAUNCH" |

#### 返回值
`{ success = boolean }`

---

### vflow.system.close_app

强制停止指定的应用程序 (需要 Shizuku 或 Root)。

#### 参数

| 参数ID | 名称 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| `packageName` | 应用包名 | string | 是 | |

#### 返回值
`{ success = boolean }`

---

### vflow.system.get_clipboard

获取系统剪贴板的当前内容。

#### 参数
无

#### 返回值
```lua
{
    text_content = string,
    image_content = ImageVariable,
    success = boolean
}

```

---

### vflow.system.set_clipboard

将指定的文本或图片内容写入系统剪贴板。

#### 参数

| 参数ID | 名称 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `content` | 内容 | any | 是 | 文本或 ImageVariable |

#### 返回值

`{ success = boolean }`

---

### vflow.system.share

调用系统分享功能来分享文本或图片。

#### 参数

| 参数ID | 名称 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `content` | 分享内容 | any | 是 | 文本或 ImageVariable |

#### 返回值

`{ success = boolean }`

---

### vflow.notification.send_notification

发送系统通知。

#### 参数

| 参数ID | 名称 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `title` | 标题 | string | 是 |  |
| `message` | 内容 | string | 是 |  |

#### 返回值

`{ success = boolean }`

---

### vflow.system.wifi

开启、关闭或切换Wi-Fi状态。

#### 参数

| 参数ID | 名称 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `state` | 状态 | enum | 是 | `开启`, `关闭`, `切换` |

#### 返回值

`{ success = boolean }`

---

### vflow.system.bluetooth

开启、关闭或切换蓝牙状态。

#### 参数

| 参数ID | 名称 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `state` | 状态 | enum | 是 | `开启`, `关闭`, `切换` |

#### 返回值

`{ success = boolean }`

---

### vflow.system.brightness

设置屏幕的亮度值。

#### 参数

| 参数ID | 名称 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `brightness_level` | 亮度值 | number | 是 | 0-255 |

#### 返回值

`{ success = boolean }`

---

### vflow.system.wake_screen

唤醒屏幕（无密码）。

#### 参数

无

#### 返回值

`{ success = boolean, screen_on = boolean }`

---

### vflow.system.sleep_screen

关闭屏幕 (息屏)。

#### 参数

无

#### 返回值

`{ success = boolean, screen_off = boolean }`

---

### vflow.system.read_sms

读取短信。

#### 参数

| 参数ID | 名称 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `filter_by` | 筛选方式 | enum | 是 | `最新一条`, `来自发件人`, `包含内容`, `发件人与内容` |
| `sender` | 发件人号码 | string | 否 |  |
| `content` | 内容包含 | string | 否 |  |
| `max_scan` | 扫描数量 | number | 否 | 默认 20 |
| `extract_code` | 提取验证码 | boolean | 否 | 默认 false |

#### 返回值

```lua
{
    found = boolean,
    sender = string,
    content = string,
    timestamp = number,
    verification_code = string
}

```

---

### vflow.notification.find

查找通知。

#### 参数

| 参数ID | 名称 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `app_filter` | 应用包名 | string | 否 |  |
| `title_filter` | 标题包含 | string | 否 |  |
| `content_filter` | 内容包含 | string | 否 |  |

#### 返回值

```lua
{
    notifications = list  -- NotificationObject 列表
}

```

---

### vflow.notification.remove

移除通知。

#### 参数

| 参数ID | 名称 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `target` | 目标通知 | any | 是 | 单个 NotificationObject 或 列表 |

#### 返回值

`{ success = boolean }`

---

### vflow.system.get_usage_stats

获取应用使用统计。

#### 参数

| 参数ID | 名称 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `interval` | 时间范围 | enum | 是 | `今天`, `过去24小时`, `本周`, `本月`, `本年` |
| `max_results` | 最大结果数 | number | 否 | 默认 10 |

#### 返回值

```lua
{
    stats_list = list,     -- 统计数据列表
    most_used_app = string,-- 最常应用包名
    success = boolean
}

```

---

### vflow.system.invoke

通用调用 (Intent)。

#### 参数

| 参数ID | 名称 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `mode` | 调用方式 | enum | 是 | `链接/Uri`, `Activity`, `Broadcast`, `Service` |
| `uri` | 链接/Data | string | 否 |  |
| `action` | Action | string | 否 |  |
| `package` | Package | string | 否 |  |
| `class` | Class | string | 否 |  |
| `type` | MIME Type | string | 否 |  |
| `flags` | Flags | string | 否 | 整数值 |
| `extras` | 扩展参数 | dictionary | 否 |  |

#### 返回值

`{ success = boolean }`

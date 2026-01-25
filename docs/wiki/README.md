# vFlow 用户模块开发指南

欢迎使用vFlow用户模块开发文档！本指南将教你如何创建自己的自定义模块。

## 目录

- [快速开始](#快速开始)
- [模块结构](#模块结构)
- [manifest.json 参考](#manifest-json-参考)
- [script.lua 编写指南](#script-lua-编写指南)
- [Lua运行时环境](#lua运行时环境)
- [权限列表](#权限列表)
- [内置API参考](#内置api参考)
- [最佳实践](#最佳实践)
- [常见问题](#常见问题)

## 快速开始

### 创建你的第一个模块

#### 步骤1：创建manifest.json

```json
{
  "id": "user.my_first_module",
  "name": "我的第一个模块",
  "description": "显示一个问候消息",
  "category": "用户脚本",
  "author": "YourName",
  "version": "1.0.0",
  "inputs": [
    {
      "id": "name",
      "name": "姓名",
      "type": "string",
      "defaultValue": "World"
    }
  ],
  "outputs": [
    {
      "id": "message",
      "name": "问候消息",
      "type": "text"
    }
  ]
}
```

#### 步骤2：创建script.lua

```lua
-- 获取输入参数
local name = inputs.name

-- 调用vFlow内置API
vflow.device.toast({
    message = "Hello, " .. name .. "!"
})

-- 返回输出
return {
    message = "Hello, " .. name .. "!"
}
```

#### 步骤3：打包和安装

1. 将 `manifest.json` 和 `script.lua` 打包成ZIP文件
2. 在vFlow中打开"模块管理"页面
3. 点击"导入模块"并选择ZIP文件
4. 在工作流中使用你的新模块

## 模块结构

一个vFlow用户模块是一个ZIP包，包含以下文件：

```
my_module.zip
├── manifest.json    # 模块元数据定义（必需）
└── script.lua       # Lua脚本实现（必需）
```

## manifest.json 参考

### 基本结构

```json
{
  "id": "user.module_id",
  "name": "模块显示名称",
  "description": "模块功能描述",
  "category": "用户脚本",
  "author": "作者名称",
  "version": "1.0.0",
  "inputs": [...],
  "outputs": [...],
  "permissions": [...]
}
```

### 字段说明

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `id` | string | 是 | 模块唯一ID，建议以 `user.` 开头 |
| `name` | string | 是 | 模块显示名称 |
| `description` | string | 是 | 模块功能描述 |
| `category` | string | 否 | 分类，通常为"用户脚本" |
| `author` | string | 否 | 作者名称 |
| `version` | string | 否 | 版本号 |
| `inputs` | array | 否 | 输入参数列表 |
| `outputs` | array | 否 | 输出参数列表 |
| `permissions` | array | 否 | 所需权限列表（详见[权限列表](#权限列表)） |

### 输入参数 (inputs)

每个输入参数定义：

```json
{
  "id": "url",
  "name": "目标链接",
  "type": "string",
  "defaultValue": "https://example.com",
  "options": [],
  "magic_variable": true
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `id` | string | 是 | 参数唯一标识符，在Lua中通过 `inputs.id` 访问 |
| `name` | string | 是 | 参数显示名称 |
| `type` | string | 是 | 参数类型：`string`、`number`、`boolean`、`enum`、`any` |
| `defaultValue` | any | 否 | 默认值 |
| `options` | array\<string\> | 否 | 枚举可选项列表。**当 type 为 "enum" 时必填**，值为字符串数组，例如：`["选项1", "选项2"]` |
| `magic_variable` | boolean | 否 | 是否支持魔法变量输入，默认true |

#### 枚举类型 (enum) 示例

当参数类型为 `enum` 时，必须提供 `options` 字段，用户可以从预设的选项中选择：

```json
{
  "inputs": [
    {
      "id": "operation_mode",
      "name": "操作模式",
      "type": "enum",
      "options": ["追加", "覆盖", "删除"],
      "defaultValue": "追加"
    },
    {
      "id": "log_level",
      "name": "日志级别",
      "type": "enum",
      "options": ["调试", "信息", "警告", "错误"],
      "defaultValue": "信息"
    }
  ]
}
```

### 输出参数 (outputs)

每个输出参数定义：

```json
{
  "id": "result",
  "name": "执行结果",
  "type": "text"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `id` | string | 是 | 输出标识符，在Lua中通过 `return { id = value }` 返回 |
| `name` | string | 是 | 输出显示名称 |
| `type` | string | 是 | 输出类型：`text`、`number`、`boolean`、`list`、`dictionary`、`image` |

## script.lua 编写指南

### 访问输入参数

通过 `inputs` 表获取用户在UI中配置的参数值：

```lua
-- 获取字符串参数
local text = inputs.my_text

-- 获取数字参数
local count = inputs.repeat_count

-- 获取布尔参数
local enabled = inputs.is_enabled

-- 获取枚举参数
local mode = inputs.operation_mode

-- 获取列表/字典参数
local items = inputs.my_list  -- 这是一个Lua table
```

### 调用vFlow内置API

vFlow将所有内置模块作为Lua函数暴露出来，调用格式：

```lua
vflow.分类.模块ID({
    参数1 = 值1,
    参数2 = 值2
})
```

### 返回输出

通过 `return` 语句返回输出结果：

```lua
return {
    result = "操作成功",
    count = 100,
    data = { key = "value" }
}
```

**注意**：返回的键名必须与manifest.json中定义的outputs的id对应。

### 错误处理

使用Lua的 `pcall` 进行错误捕获：

```lua
local success, result = pcall(function()
    return vflow.network.http_request({
        url = inputs.url,
        method = "GET"
    })
end)

if not success then
    return {
        error = "请求失败: " .. tostring(result)
    }
end
```

## Lua运行时环境

vFlow使用LuaJ (Lua 5.2语法) 运行用户脚本。在执行脚本前，vFlow会向Lua全局环境注入以下内容：

### 1. context (ApplicationContext)

Android应用上下文，可用于访问系统服务（通常不需要直接使用）。

```lua
-- 很少需要直接使用，vFlow已封装好所有API
```

### 2. inputs (MapProxy)

当前模块的输入参数表。这是用户在UI中配置的参数值（已经过变量解析）。

```lua
-- 在manifest.json中定义：
"inputs": [
    { "id": "message", "type": "string" }
]

-- 在script.lua中访问：
local msg = inputs.message

-- 在工作流UI中配置：message -> {{trigger.shared_content}}
-- inputs.message 已经是解析后的实际值
```

### 3. sys (MapProxy)

访问工作流的魔法变量（前序步骤的输出）。

```lua
-- 访问触发器的输出
local shared_content = sys["trigger.shared_content"]

-- 访问步骤1的输出
local step1_result = sys["step1.output_name"]
```

**注意**：通常不需要直接使用`sys`，因为通过`inputs`配置的魔法变量会自动解析。

### 4. vars (MapProxy)

访问全局命名变量。

```lua
-- 读取命名变量
local username = vars["username"]

-- 修改命名变量（会保存到全局变量表）
vars["counter"] = 100
```

### 5. vflow (模块树)

所有vFlow内置模块的API入口。

```lua
-- 调用格式：vflow.分类.模块ID({ 参数 })

vflow.device.toast({ message = "Hello" })
vflow.network.http_request({ url = "https://example.com" })
vflow.shizuku.shell_command({ command = "ls -l" })
```

## 权限列表

在manifest.json的`permissions`字段中，可以声明以下权限：

### vFlow特殊权限

| 权限ID | 名称 | 说明 |
|--------|------|------|
| `vflow.permission.ACCESSIBILITY_SERVICE` | 无障碍服务 | 实现自动化点击、查找、输入等核心功能 |
| `vflow.permission.SYSTEM_ALERT_WINDOW` | 悬浮窗权限 | 允许在后台显示输入框等窗口 |
| `vflow.permission.NOTIFICATION_LISTENER_SERVICE` | 通知使用权 | 读取和操作状态栏通知 |
| `vflow.permission.STORAGE` | 文件访问权限 | 读写 /sdcard/vFlow 目录 |
| `vflow.permission.WRITE_SETTINGS` | 修改系统设置 | 调整屏幕亮度等系统设置 |
| `vflow.permission.SHIZUKU` | Shizuku | 通过Shizuku执行Shell命令 |
| `vflow.permission.ROOT` | Root权限 | 以超级用户权限执行命令 |
| `vflow.permission.IGNORE_BATTERY_OPTIMIZATIONS` | 后台运行权限 | 加入电池优化白名单 |
| `vflow.permission.SCHEDULE_EXACT_ALARM` | 闹钟和提醒 | 定时触发功能 |

### Android系统权限

| 权限ID | 名称 | 说明 |
|--------|------|------|
| `android.permission.POST_NOTIFICATIONS` | 通知权限 | 显示Toast、发送通知 |
| `android.permission.RECEIVE_SMS` | 短信权限 | 接收和读取短信（包含READ_SMS） |
| `android.permission.BLUETOOTH_CONNECT` | 蓝牙权限 | 控制蓝牙开关 |
| `android.permission.ACCESS_FINE_LOCATION` | 精确定位 | 获取WiFi列表等需要 |
| `android.permission.PACKAGE_USAGE_STATS` | 使用情况访问 | 读取应用使用统计 |
| `android.permission.INTERNET` | 网络访问 | HTTP请求等网络操作 |

### 示例

```json
{
  "permissions": [
    "vflow.permission.ACCESSIBILITY_SERVICE",
    "vflow.permission.SHIZUKU",
    "android.permission.INTERNET"
  ]
}
```

## 内置API参考

详细的内置API文档：

- [触发器API](./triggers.md) - 工作流触发相关
- [界面交互API](./interaction.md) - UI操作和屏幕控制
- [数据处理API](./data.md) - 数据操作
- [文件操作API](./file.md) - 文件处理
- [网络API](./network.md) - 网络请求
- [系统API](./system.md) - 系统功能
- [Shizuku API](./shizuku.md) - Shell命令和快捷方式

## 最佳实践

### 1. 参数验证

始终验证输入参数：

```lua
if not inputs.text or inputs.text == "" then
    vflow.device.toast({
        message = "错误：text参数不能为空"
    })
    return {
        success = false,
        error = "text参数不能为空"
    }
end
```

### 2. 使用注释

为你的代码添加清晰注释：

```lua
-- 解析输入的URL
local url = inputs.url

-- 发送HTTP GET请求
local response = vflow.network.http_request({
    url = url,
    method = "GET"
})

-- 返回响应内容
return {
    body = response.response_body,
    status = response.status_code
}
```

### 3. 错误处理

始终处理可能的错误：

```lua
local success, result = pcall(function()
    return vflow.device.find.text({
        targetText = inputs.search_text
    })
end)

if not success then
    vflow.device.toast({
        message = "查找失败: " .. tostring(result)
    })
    return {
        success = false,
        error = tostring(result)
    }
end
```

### 4. 使用Lua内置功能

Lua提供了完整的逻辑控制功能，不需要调用vFlow的逻辑模块：

```lua
-- 条件判断
if inputs.count > 0 then
    -- 做某事
else
    -- 做其他事
end

-- 循环
for i = 1, 10 do
    vflow.device.toast({
        message = "第 " .. i .. " 次循环"
    })
end

-- 遍历列表
for i, item in ipairs(inputs.items) do
    print(item)
end

-- 遍历字典
for key, value in pairs(inputs.config) do
    print(key .. " = " .. tostring(value))
end

-- 函数
local function process(data)
    return data .. "_processed"
end
```

## 常见问题

### Q: 如何在模块中使用延迟？

```lua
vflow.system.delay({
    duration = 2000  -- 延迟2000毫秒
})
```

### Q: 如何调用其他用户模块？

用户模块不能直接调用其他用户模块，只能调用vFlow内置模块。

### Q: 如何处理数组/列表参数？

```lua
-- 在manifest中定义type为"any"的输入
-- 在Lua中访问：
local items = inputs.my_list  -- 这是一个Lua table

for i, item in ipairs(items) do
    print(item)
end
```

### Q: 如何返回多个值？

```lua
return {
    result1 = "值1",
    result2 = "值2",
    result3 = 100
}
```

### Q: 模块执行超时怎么办？

vFlow的模块执行有默认超时限制。对于耗时操作，建议：
1. 使用异步API
2. 分批处理大量数据
3. 添加进度反馈

### Q: 如何调试模块？

使用 `print` 输出调试信息：

```lua
print("开始处理，输入参数: " .. tostring(inputs.text))
local result = vflow.device.toast({ message = inputs.text })
print("Toast已显示")
```

在vFlow的日志中查看输出。

## 完整示例

### 示例1：批量重命名文件

**manifest.json:**

```json
{
  "id": "user.batch_rename",
  "name": "批量重命名文件",
  "description": "为文件名添加前缀",
  "category": "用户脚本",
  "author": "YourName",
  "version": "1.0.0",
  "inputs": [
    {
      "id": "files",
      "name": "文件列表",
      "type": "any",
      "magic_variable": true
    },
    {
      "id": "prefix",
      "name": "前缀",
      "type": "string",
      "defaultValue": "new_"
    }
  ],
  "outputs": [
    {
      "id": "renamed_count",
      "name": "重命名数量",
      "type": "number"
    }
  ],
  "permissions": ["vflow.permission.SHIZUKU"]
}
```

**script.lua:**

```lua
local files = inputs.files
local prefix = inputs.prefix
local count = 0

-- 遍历文件列表
for i, filepath in ipairs(files) do
    -- 提取文件名
    local filename = filepath:match("/([^/]+)$")
    local new_name = prefix .. filename

    -- 执行重命名命令
    local result = vflow.shizuku.shell_command({
        command = "mv '" .. filepath .. "' /sdcard/" .. new_name
    })

    if result.success then
        count = count + 1
    end
end

-- 显示结果
vflow.device.toast({
    message = "已重命名 " .. count .. " 个文件"
})

return {
    renamed_count = count
}
```

### 示例2：智能填表

**manifest.json:**

```json
{
  "id": "user.smart_fill",
  "name": "智能填表",
  "description": "使用OCR识别表单并自动填充",
  "category": "用户脚本",
  "inputs": [
    {
      "id": "form_data",
      "name": "表单数据",
      "type": "any"
    }
  ],
  "outputs": [
    {
      "id": "filled_count",
      "name": "填充数量",
      "type": "number"
    }
  ],
  "permissions": ["vflow.permission.ACCESSIBILITY_SERVICE"]
}
```

**script.lua:**

```lua
-- 使用OCR识别表单字段
local ocr_result = vflow.interaction.ocr({
    language = "中文"
})

if not ocr_result.success then
    vflow.device.toast({ message = "OCR识别失败" })
    return { filled_count = 0 }
end

local text = ocr_result.text
local data = inputs.form_data
local count = 0

-- 遍历表单数据
for key, value in pairs(data) do
    -- 查找字段标签
    if string.find(text, key) then
        local field = vflow.device.find.text({
            targetText = key,
            outputFormat = "元素"
        })

        if field.count > 0 then
            -- 点击字段
            vflow.device.click({ target = field.first_result })

            -- 输入值
            vflow.interaction.input_text({
                text = tostring(value)
            })

            count = count + 1

            -- 延迟一下，避免过快
            vflow.system.delay({ duration = 500 })
        end
    end
end

vflow.device.toast({
    message = "已填充 " .. count .. " 个字段"
})

return {
    filled_count = count
}
```

### 示例3：文件管理器（使用枚举参数）

这个示例展示了如何使用 `enum` 类型参数让用户从预设选项中选择操作模式。

**manifest.json:**

```json
{
  "id": "user.file_manager",
  "name": "文件管理器",
  "description": "根据选择的模式对文件进行不同操作",
  "category": "用户脚本",
  "author": "YourName",
  "version": "1.0.0",
  "inputs": [
    {
      "id": "file_path",
      "name": "文件路径",
      "type": "string",
      "defaultValue": "/sdcard/test.txt"
    },
    {
      "id": "operation_mode",
      "name": "操作模式",
      "type": "enum",
      "options": ["读取内容", "获取大小", "检查存在", "删除文件"],
      "defaultValue": "读取内容",
      "magic_variable": false
    }
  ],
  "outputs": [
    {
      "id": "result",
      "name": "操作结果",
      "type": "text"
    },
    {
      "id": "success",
      "name": "是否成功",
      "type": "boolean"
    }
  ],
  "permissions": ["vflow.permission.SHIZUKU"]
}
```

**script.lua:**

```lua
local file_path = inputs.file_path
local mode = inputs.operation_mode
local result = ""
local success = false

-- 根据用户选择的操作模式执行不同操作
if mode == "读取内容" then
    -- 使用 cat 命令读取文件
    local cmd_result = vflow.shizuku.shell_command({
        command = "cat '" .. file_path .. "'"
    })

    if cmd_result.success then
        result = cmd_result.output
        success = true
    else
        result = "读取失败: " .. cmd_result.error
    end

elseif mode == "获取大小" then
    -- 使用 ls -l 获取文件大小
    local cmd_result = vflow.shizuku.shell_command({
        command = "ls -l '" .. file_path .. "'"
    })

    if cmd_result.success then
        -- 解析输出获取大小信息
        result = cmd_result.output
        success = true
    else
        result = "获取失败: " .. cmd_result.error
    end

elseif mode == "检查存在" then
    -- 使用 test 命令检查文件是否存在
    local cmd_result = vflow.shizuku.shell_command({
        command = "test -f '" .. file_path .. "' && echo 'exists' || echo 'not_exists'"
    })

    if cmd_result.output:match("exists") then
        result = "文件存在"
        success = true
    else
        result = "文件不存在"
    end

elseif mode == "删除文件" then
    -- 使用 rm 命令删除文件
    local cmd_result = vflow.shizuku.shell_command({
        command = "rm '" .. file_path .. "'"
    })

    if cmd_result.success then
        result = "文件已删除"
        success = true
    else
        result = "删除失败: " .. cmd_result.error
    end
end

-- 显示操作结果
vflow.device.toast({
    message = mode .. " - " .. (success and "成功" or "失败")
})

return {
    result = result,
    success = success
}
```

## 下一步

- 查看[内置API文档](./)了解所有可用的vFlow模块
- 阅读[示例模块](release/module/)学习更多实际案例
- 开始创建你自己的模块吧！

## 技术支持

如果遇到问题：
1. 检查manifest.json格式是否正确
2. 使用print()调试Lua脚本
3. 查看vFlow日志获取错误信息
4. 参考官方示例模块

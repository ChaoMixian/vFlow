# 界面交互API参考

界面交互API用于在Lua脚本中控制Android设备的UI操作，如查找元素、点击、输入文本等。

## 概述

所有界面交互函数都需要无障碍服务权限。在manifest.json中添加：

```json
{
  "permissions": ["vflow.permission.ACCESSIBILITY_SERVICE"]
}
```

## API列表

### vflow.device.find.text()

在屏幕上查找指定文本。

#### Lua调用

```lua
local result = vflow.device.find.text({
    matchMode = "完全匹配",
    targetText = "登录",
    outputFormat = "元素"
})
```

#### 参数

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| `matchMode` | string | 否 | 匹配模式：`完全匹配`、`包含`、`正则`。默认：`完全匹配` |
| `targetText` | string | 是 | 要查找的目标文本 |
| `outputFormat` | string | 否 | 输出格式：`元素`、`坐标`、`视图ID`。默认：`元素` |

#### 返回值

```lua
{
    first_result = ScreenElement | Coordinate | string,
    all_results = {...},  -- 列表
    count = number
}
```

#### 示例

```lua
-- 查找"登录"按钮
local result = vflow.device.find.text({
    matchMode = "完全匹配",
    targetText = "登录",
    outputFormat = "元素"
})

if result.count > 0 then
    print("找到 " .. result.count .. " 个匹配项")
    -- 使用第一个结果
    local element = result.first_result
end
```

```lua
-- 使用正则查找电话号码
local result = vflow.device.find.text({
    matchMode = "正则",
    targetText = "\\d{3}-\\d{4}-\\d{4}",
    outputFormat = "坐标"
})

for i, coord in ipairs(result.all_results) do
    print("找到号码在: " .. coord.x .. ", " .. coord.y)
end
```

---

### vflow.device.click()

点击屏幕元素、坐标或视图ID。

#### Lua调用

```lua
local result = vflow.device.click({
    target = target_value
})
```

#### 参数

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| `target` | ScreenElement/Coordinate/string | 是 | 点击目标：屏幕元素对象、坐标字符串("x,y")或视图ID |

#### 返回值

```lua
{
    success = boolean
}
```

#### 示例

```lua
-- 点击查找文本找到的元素
local find_result = vflow.device.find.text({
    targetText = "确定"
})

if find_result.count > 0 then
    local click_result = vflow.device.click({
        target = find_result.first_result
    })
end
```

```lua
-- 点击指定坐标
local result = vflow.device.click({
    target = "500,1000"
})
```

```lua
-- 点击视图ID
local result = vflow.device.click({
    target = "com.example.app:id/button_confirm"
})
```

---

### vflow.interaction.screen_operation()

在屏幕上执行点击、长按或滑动操作。

#### Lua调用

```lua
-- 点击操作
local result = vflow.interaction.screen_operation({
    operation_type = "点击",
    target = "500,1000"
})

-- 滑动操作
local result = vflow.interaction.screen_operation({
    operation_type = "滑动",
    target = "500,1500",
    target_end = "500,500",
    duration = 500
})

-- 长按操作
local result = vflow.interaction.screen_operation({
    operation_type = "长按",
    target = element,
    duration = 2000
})
```

#### 参数

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| `operation_type` | string | 是 | 操作类型：`点击`、`长按`、`滑动`。默认：`点击` |
| `target` | string/element | 是 | 目标位置或起点。可以是坐标字符串 "x,y"、ScreenElement或视图ID |
| `target_end` | string/element | 条件 | 滑动终点坐标。滑动时必填 |
| `duration` | number | 否 | 持续时间（毫秒）。点击默认50，长按默认1000，滑动默认500 |
| `execution_mode` | string | 否 | 执行方式：`自动`、`无障碍`、`Shell`。默认：`自动` |

#### 返回值

```lua
{
    success = boolean
}
```

#### 示例

```lua
-- 点击坐标
vflow.interaction.screen_operation({
    operation_type = "点击",
    target = "500,1000"
})

-- 滑动屏幕（从下往上）
vflow.interaction.screen_operation({
    operation_type = "滑动",
    target = "500,1500",
    target_end = "500,500",
    duration = 300
})

-- 长按元素
local element = vflow.device.find.text({ text = "按钮" })
vflow.interaction.screen_operation({
    operation_type = "长按",
    target = element,
    duration = 2000
})
```

---

### vflow.device.send_key_event()

执行系统级全局操作。

#### Lua调用

```lua
local result = vflow.device.send_key_event({
    key_action = "返回"
})
```

#### 参数

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| `key_action` | string | 是 | 操作类型：`返回`、`主屏幕`、`最近任务`、`通知中心`、`快速设置`、`电源菜单` |

#### 返回值

```lua
{
    success = boolean
}
```

#### 示例

```lua
-- 返回上一页
vflow.device.send_key_event({
    key_action = "返回"
})

-- 回到主屏幕
vflow.device.send_key_event({
    key_action = "主屏幕"
})

-- 打开通知中心
vflow.device.send_key_event({
    key_action = "通知中心"
})
```

---

### vflow.interaction.input_text()

在当前聚焦的输入框中输入文本。

#### Lua调用

```lua
local result = vflow.interaction.input_text({
    text = "Hello, World!",
    mode = "自动"
})
```

#### 参数

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| `text` | string | 是 | 要输入的文本内容 |
| `mode` | string | 否 | 输入模式：`自动`、`无障碍`、`Shell`。默认：`自动` |

#### 返回值

```lua
{
    success = boolean
}
```

#### 示例

```lua
-- 点击输入框
vflow.device.click({
    target = "com.example.app:id/username"
})

-- 输入文本
vflow.interaction.input_text({
    text = inputs.username
})
```

```lua
-- 使用变量输入
vflow.interaction.input_text({
    text = inputs.message  -- 从inputs获取
})
```

---

### vflow.system.capture_screen()

截取当前屏幕。

#### Lua调用

```lua
local result = vflow.system.capture_screen()
```

#### 参数

无

#### 返回值

```lua
{
    image = ImageVariable,
    success = boolean
}
```

#### 示例

```lua
-- 截屏
local screenshot = vflow.system.capture_screen()

if screenshot.success then
    -- 保存到相册
    vflow.file.save_image({
        image = screenshot.image
    })

    -- 或者传递给AI分析
    local analysis = vflow.ai.agent({
        image = screenshot.image,
        instruction = "描述这个屏幕的内容"
    })
end
```

---

### vflow.interaction.ocr()

使用OCR识别图片中的文字，或在图片中查找指定文字。

#### Lua调用

```lua
local screenshot = vflow.system.capture_screen()

-- 模式1: 识别全文
local result = vflow.interaction.ocr({
    image = screenshot.image,
    mode = "识别全文",
    language = "中英混合"
})

-- 模式2: 查找文本
local result = vflow.interaction.ocr({
    image = screenshot.image,
    mode = "查找文本",
    target_text = "搜索",
    language = "中英混合"
})
```

#### 参数

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| `image` | ImageVariable | 是 | 要识别的图片对象 |
| `mode` | string | 是 | 模式：`识别全文` 或 `查找文本`。默认：`识别全文` |
| `target_text` | string | 否 | 查找模式下要查找的文字内容 |
| `language` | string | 否 | 识别语言：`中英混合`、`中文`、`英文`。默认：`中英混合` |
| `search_strategy` | string | 否 | 查找策略：`默认 (从上到下)`、`最接近中心`、`置信度最高`。默认：`默认 (从上到下)` |

#### 返回值

**识别全文模式：**
```lua
{
    success = boolean,
    full_text = string  -- 识别到的所有文字
}
```

**查找文本模式：**
```lua
{
    success = boolean,
    found = boolean,         -- 是否找到
    first_match = element,   -- 第一个匹配的元素（ScreenElement）
    all_matches = list,      -- 所有匹配结果列表
    count = number           -- 找到的数量
}
```

#### 示例

```lua
-- 识别屏幕上的所有文字
local screenshot = vflow.system.capture_screen()
local ocr_result = vflow.interaction.ocr({
    image = screenshot.image,
    mode = "识别全文"
})

if ocr_result.success then
    print("识别到的文字: " .. ocr_result.full_text)
end
```

```lua
-- 查找特定文字并点击
local screenshot = vflow.system.capture_screen()
local ocr_result = vflow.interaction.ocr({
    image = screenshot.image,
    mode = "查找文本",
    target_text = "登录"
})

if ocr_result.found then
    -- 点击找到的文字位置
    vflow.interaction.click({
        target = ocr_result.first_match
    })
end
```

---

### vflow.ai.agent()

使用AI理解屏幕内容。

#### Lua调用

```lua
local result = vflow.ai.agent({
    instruction = "找出屏幕上所有商品的价格",
    image = image_variable,
    max_steps = 10
})
```

#### 参数

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| `instruction` | string | 是 | 对AI的指令描述 |
| `image` | ImageVariable | 否 | 要分析的图像（可选，不提供则截屏） |
| `max_steps` | number | 否 | 最大执行步数。默认：10 |

#### 返回值

```lua
{
    result = string,
    steps = {...},
    success = boolean
}
```

#### 示例

```lua
-- 分析当前屏幕
local result = vflow.ai.agent({
    instruction = "提取屏幕上的验证码"
})

if result.success then
    print("AI分析结果: " .. result.result)
end
```

```lua
-- 结合截图分析
local screenshot = vflow.system.capture_screen()

local analysis = vflow.ai.agent({
    image = screenshot.image,
    instruction = "这是一个外卖订单页面，请告诉我订单总额和配送费"
})

return {
    order_info = analysis.result
}
```

---

### vflow.ai.autoglm()

使用智谱AI自动生成回复。

#### Lua调用

```lua
local result = vflow.ai.autoglm({
    prompt = "你好，请问有什么可以帮助您的吗？",
    system_prompt = "你是一个专业的客服助手",
    temperature = 0.7
})
```

#### 参数

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| `prompt` | string | 是 | 对话的上下文或问题 |
| `system_prompt` | string | 否 | 系统提示词，定义AI角色 |
| `temperature` | number | 否 | 生成的随机性（0-1）。默认：0.7 |

#### 返回值

```lua
{
    response = string,
    success = boolean
}
```

#### 示例

```lua
-- 自动回复消息
local message = inputs.message

local reply = vflow.ai.autoglm({
    prompt = message,
    system_prompt = "你是一个专业的客服助手，要礼貌、简洁地回答"
})

if reply.success then
    -- 输入回复内容
    vflow.interaction.input_text({
        text = reply.response
    })

    -- 发送消息
    vflow.device.click({ target = "发送" })
end
```

---

### vflow.device.find_until()

持续查找屏幕元素，直到找到或超时。

#### Lua调用

```lua
local result = vflow.device.find_until({
    targetText = "加载完成",
    timeout = 10,
    interval = 1000
})
```

#### 参数

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| `targetText` | string | 是 | 要查找的目标文本 |
| `matchMode` | string | 否 | 匹配模式：`包含`、`完全匹配`、`正则`。默认：`包含` |
| `timeout` | number | 否 | 超时时间（秒）。默认：10 |
| `searchMode` | string | 否 | 查找模式：`自动`、`无障碍`、`OCR`。默认：`自动` |
| `interval` | number | 否 | 检查间隔（毫秒）。默认：1000 |

#### 返回值

```lua
{
    found = boolean,
    result = ScreenElement,
    attempts = number
}
```

#### 示例

```lua
-- 等待页面加载
local find_result = vflow.device.find_until({
    targetText = "加载完成",
    timeout = 10
})

if find_result.found then
    vflow.device.toast({ message = "页面已加载" })
else
    vflow.device.toast({ message = "等待超时" })
end
```

---

### vflow.device.find_image()

在屏幕上查找图像模板。

#### Lua调用

```lua
local result = vflow.device.find_image({
    template = image_variable,
    threshold = 0.85,
    maxResults = 5
})
```

#### 参数

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| `template` | ImageVariable | 是 | 要查找的模板图像 |
| `threshold` | number | 否 | 匹配阈值（0-1）。默认：0.8 |
| `maxResults` | number | 否 | 最大返回结果数。默认：1 |

#### 返回值

```lua
{
    results = {...},  -- Coordinate列表
    count = number,
    best_match = Coordinate,
    success = boolean
}
```

#### 示例

```lua
-- 导入图标模板
local icon = inputs.icon_image

local find_result = vflow.device.find_image({
    template = icon,
    threshold = 0.85
})

if find_result.count > 0 then
    -- 点击找到的图标
    vflow.device.click({
        target = find_result.best_match
    })
end
```

---

## 完整示例

### 示例1：自动登录流程

**manifest.json:**

```json
{
  "id": "user.auto_login",
  "name": "自动登录",
  "description": "自动输入用户名和密码并登录",
  "category": "用户脚本",
  "inputs": [
    {
      "id": "username",
      "name": "用户名",
      "type": "string"
    },
    {
      "id": "password",
      "name": "密码",
      "type": "string"
    }
  ],
  "outputs": [
    {
      "id": "success",
      "name": "是否成功",
      "type": "boolean"
    }
  ],
  "permissions": ["vflow.permission.ACCESSIBILITY_SERVICE"]
}
```

**script.lua:**

```lua
-- 等待登录界面加载
local wait_result = vflow.device.find_until({
    targetText = "登录",
    timeout = 5
})

if not wait_result.found then
    vflow.device.toast({ message = "未找到登录界面" })
    return { success = false }
end

-- 点击用户名输入框
local username_field = vflow.device.find.text({
    targetText = "用户名",
    outputFormat = "元素"
})

if username_field.count > 0 then
    vflow.device.click({ target = username_field.first_result })

    -- 输入用户名
    vflow.interaction.input_text({
        text = inputs.username
    })
end

-- 点击密码输入框
local password_field = vflow.device.find.text({
    targetText = "密码",
    outputFormat = "元素"
})

if password_field.count > 0 then
    vflow.device.click({ target = password_field.first_result })

    -- 输入密码
    vflow.interaction.input_text({
        text = inputs.password
    })
end

-- 点击登录按钮
local login_button = vflow.device.find.text({
    targetText = "登录"
})

if login_button.count > 0 then
    vflow.device.click({ target = login_button.first_result })
end

-- 等待登录完成
vflow.system.delay({ duration = 2000 })

-- 检查是否登录成功
local check = vflow.device.find.text({
    targetText = "欢迎"
})

return {
    success = check.count > 0
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

---

## 注意事项

1. **无障碍权限**：所有界面交互都需要无障碍服务权限
2. **屏幕状态**：确保屏幕已解锁
3. **元素查找**：不同应用的UI结构可能不同，建议使用多种定位方式
4. **错误处理**：始终检查操作的返回结果
5. **性能考虑**：频繁的屏幕操作可能影响性能

---

## 相关文档

- [系统API](./system.md) - 延迟、Toast等系统功能
- [逻辑控制API](./logic.md) - 控制执行流程
- [数据处理API](./data.md) - 数据操作

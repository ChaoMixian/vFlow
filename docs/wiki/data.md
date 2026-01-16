# 数据处理API参考

数据处理API用于在Lua脚本中创建、读取、修改变量，以及执行计算和文本处理操作。

## 概述

数据处理API分为以下几类：
- **变量操作**：创建、读取、修改、生成随机变量
- **计算**：执行数学运算
- **文本处理**：拼接、分割、替换、正则提取

## API列表

### vflow.variable.create

创建一个新的变量，可选择为其命名以便后续修改或读取。

#### Lua调用

```lua
local result = vflow.variable.create({
    type = "文本",
    value = "Hello, World!",
    variableName = "my_var"  -- 可选
})
```

#### 参数

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| `type` | string | 是 | 变量类型：`文本`、`数字`、`布尔`、`字典`、`列表`、`图像` |
| `value` | any | 是 | 变量的值 |
| `variableName` | string | 否 | 变量名称（用于后续读取和修改） |

#### 返回值

```lua
{
    variable = TextVariable | NumberVariable | BooleanVariable | DictionaryVariable | ListVariable | ImageVariable
}
```

#### 示例

```lua
-- 创建文本变量
local text_var = vflow.variable.create({
    type = "文本",
    value = "Hello",
    variableName = "greeting"
})

-- 创建数字变量
local num_var = vflow.variable.create({
    type = "数字",
    value = 42
})

-- 创建列表变量
local list_var = vflow.variable.create({
    type = "列表",
    value = {"apple", "banana", "orange"}
})

-- 创建字典变量
local dict_var = vflow.variable.create({
    type = "字典",
    value = {
        name = "张三",
        age = 25,
        city = "北京"
    }
})
```

---

### vflow.variable.get

读取一个命名变量或魔法变量的值，使其可用于后续步骤。

#### Lua调用

```lua
local result = vflow.variable.get({
    source = [[my_var]]  -- 或 {{stepId.outputId}}
})
```

#### 参数

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| `source` | string | 是 | 来源变量（命名变量格式：`[[变量名]]`，魔法变量格式：`{{步骤ID.输出ID}}`） |

#### 返回值

```lua
{
    value = any  -- 变量值，类型取决于源变量
}
```

#### 示例

```lua
-- 读取命名变量
local my_var = vflow.variable.get({
    source = [[greeting]]
})

print(my_var.value)  -- 输出: Hello

-- 读取魔法变量
local step_output = vflow.variable.get({
    source = "{{step1.result}}"
})
```

---

### vflow.variable.modify

修改一个已存在的命名变量的值。

#### Lua调用

```lua
local result = vflow.variable.modify({
    variable = [[my_var]],
    newValue = "New value"
})
```

#### 参数

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| `variable` | string | 是 | 要修改的变量（命名变量格式：`[[变量名]]`） |
| `newValue` | any | 是 | 新的值 |

#### 返回值

```lua
{
    success = boolean
}
```

#### 示例

```lua
-- 修改变量值
vflow.variable.modify({
    variable = [[greeting]],
    newValue = "Hello, World!"
})

-- 使用魔法变量作为新值
vflow.variable.modify({
    variable = [[counter]],
    newValue = "{{calculation.result}}"
})
```

---

### vflow.variable.random

创建新的随机变量，可选择为其命名以便后续修改或读取。

#### Lua调用

```lua
local result = vflow.variable.random({
    type = "数字",
    min = 1,
    max = 100,
    step = 1
})
```

#### 参数

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| `type` | string | 是 | 随机变量类型：`数字`、`文本` |
| `variableName` | string | 否 | 变量名称（可选） |
| `min` | number | 否 | 随机数最小值（数字类型，默认0） |
| `max` | number | 否 | 随机数最大值（数字类型，默认100） |
| `step` | number | 否 | 步长（数字类型，默认1） |
| `length` | number | 否 | 随机文本长度（文本类型，默认8） |
| `custom_chars` | string | 否 | 自定义字符集（文本类型，默认a-zA-Z0-9） |

#### 返回值

```lua
{
    randomVariable = NumberVariable | TextVariable
}
```

#### 示例

```lua
-- 生成随机数字（1-100）
local random_num = vflow.variable.random({
    type = "数字",
    min = 1,
    max = 100,
    variableName = "random_number"
})

-- 生成随机文本（默认8位）
local random_text = vflow.variable.random({
    type = "文本",
    length = 16
})

-- 使用自定义字符集
local password = vflow.variable.random({
    type = "文本",
    length = 12,
    custom_chars = "!@#$%^&*()_+-=[]{}|;:,.<>?"
})
```

---

### vflow.data.calculation

执行两个数字之间的数学运算。

#### Lua调用

```lua
local result = vflow.data.calculation({
    operand1 = 10,
    operator = "+",
    operand2 = 5
})
```

#### 参数

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| `operand1` | number | 是 | 数字1 |
| `operator` | string | 是 | 运算符：`+`、`-`、`*`、`/`、`%` |
| `operand2` | number | 是 | 数字2 |

#### 返回值

```lua
{
    result = number
}
```

#### 示例

```lua
-- 加法
local sum = vflow.data.calculation({
    operand1 = 10,
    operator = "+",
    operand2 = 5
})
print(sum.result)  -- 15

-- 减法
local diff = vflow.data.calculation({
    operand1 = "{{var1}}",
    operator = "-",
    operand2 = "{{var2}}"
})

-- 乘法
local product = vflow.data.calculation({
    operand1 = 7,
    operator = "*",
    operand2 = 6
})

-- 除法
local quotient = vflow.data.calculation({
    operand1 = 100,
    operator = "/",
    operand2 = 4
})

-- 取模
local remainder = vflow.data.calculation({
    operand1 = 17,
    operator = "%",
    operand2 = 5
})
```

---

### vflow.data.text_processing

执行文本的拼接、分割、替换、正则匹配等操作。

#### Lua调用

```lua
local result = vflow.data.text_processing({
    operation = "拼接",
    join_list = list_variable,
    join_delimiter = ", "
})
```

#### 参数

根据`operation`的不同，需要的参数也不同：

**拼接（join）**：| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| `operation` | string | 是 | 固定值："拼接" |
| `join_prefix` | string | 否 | 前缀 |
| `join_list` | list | 是 | 要拼接的列表 |
| `join_delimiter` | string | 否 | 分隔符（默认","） |
| `join_suffix` | string | 否 | 后缀 |

**分割（split）**：| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| `operation` | string | 是 | 固定值："分割" |
| `source_text` | string | 是 | 源文本 |
| `split_delimiter` | string | 否 | 分隔符（默认","） |

**替换（replace）**：| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| `operation` | string | 是 | 固定值："替换" |
| `source_text` | string | 是 | 源文本 |
| `replace_from` | string | 是 | 查找内容 |
| `replace_to` | string | 是 | 替换为 |

**正则提取（regex）**：| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| `operation` | string | 是 | 固定值："正则提取" |
| `source_text` | string | 是 | 源文本 |
| `regex_pattern` | string | 是 | 正则表达式 |
| `regex_group` | number | 否 | 匹配组号（默认0） |

#### 返回值

**拼接、替换返回**：```lua
{
    result_text = string
}
```

**分割、正则提取返回**：```lua
{
    result_list = list
}
```

#### 示例

```lua
-- 拼接列表
local fruits = {"苹果", "香蕉", "橙子"}
local joined = vflow.data.text_processing({
    operation = "拼接",
    join_list = fruits,
    join_delimiter = "、",
    join_prefix = "我喜欢：",
    join_suffix = "。"
})
print(joined.result_text)  -- 我喜欢：苹果、香蕉、橙子。

-- 分割文本
local parts = vflow.data.text_processing({
    operation = "分割",
    source_text = "apple,banana,orange",
    split_delimiter = ","
})
-- parts.result_list = {"apple", "banana", "orange"}

-- 替换文本
local replaced = vflow.data.text_processing({
    operation = "替换",
    source_text = "Hello World",
    replace_from = "World",
    replace_to = "vFlow"
})
print(replaced.result_text)  -- Hello vFlow

-- 正则提取所有邮箱
local text = "联系：test@example.com 或 admin@test.org"
local emails = vflow.data.text_processing({
    operation = "正则提取",
    source_text = text,
    regex_pattern = "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"
})
-- emails.result_list = {"test@example.com", "admin@test.org"}

-- 正则提取URL中的域名
local url = "https://www.example.com/page"
local domain = vflow.data.text_processing({
    operation = "正则提取",
    source_text = url,
    regex_pattern = "https?://([^/]+)",
    regex_group = 1
})
print(domain.result_list[1])  -- www.example.com
```

---

## 完整示例

### 示例1：数据处理流程

**script.lua:**

```lua
-- 创建变量
vflow.variable.create({
    type = "数字",
    value = 10,
    variableName = "counter"
})

-- 读取变量
local counter = vflow.variable.get({
    source = [[counter]]
})

-- 执行计算
local doubled = vflow.data.calculation({
    operand1 = counter.value,
    operator = "*",
    operand2 = 2
})

-- 更新变量
vflow.variable.modify({
    variable = [[counter]],
    newValue = doubled.result
})

-- 文本处理
local message = vflow.data.text_processing({
    operation = "拼接",
    join_list = {"计数器值为：", tostring(doubled.result)},
    join_delimiter = ""
})

-- 显示结果
vflow.device.toast({
    message = message.result_text
})

return {
    final_value = doubled.result
}
```

### 示例2：列表和字典处理

**script.lua:**

```lua
-- 创建用户列表
local users = vflow.variable.create({
    type = "列表",
    value = {"张三", "李四", "王五"},
    variableName = "user_list"
})

-- 拼接用户名
local user_names = vflow.data.text_processing({
    operation = "拼接",
    join_list = "{{user_list.variable}}",
    join_delimiter = "、",
    join_prefix = "用户：",
    join_suffix = " 共3人"
})

-- 创建用户详情
local user_info = vflow.variable.create({
    type = "字典",
    value = {
        count = 3,
        names = user_names.result_text,
        timestamp = os.time()
    },
    variableName = "user_info"
})

-- 读取完整信息
local info = vflow.variable.get({
    source = [[user_info]]
})

return {
    user_data = info.value
}
```

---

## 注意事项

1. **变量类型**：创建变量时注意选择正确的类型
2. **命名变量**：使用`[[变量名]]`格式引用命名变量
3. **魔法变量**：使用`{{步骤ID.输出ID}}`格式引用步骤输出
4. **正则表达式**：注意Lua中的转义字符，使用双反斜杠`\\`
5. **列表操作**：确保传入的是列表类型的变量

---

## 相关文档

- [界面交互API](./interaction.md) - UI操作
- [系统API](./system.md) - 系统功能
- [文件操作API](./file.md) - 文件处理

# 网络API参考

网络API用于在Lua脚本中执行网络请求、获取IP地址以及调用AI服务。

## 概述

网络API提供以下功能：
- **IP查询**：获取本地或公网IP地址
- **HTTP请求**：发送GET、POST等HTTP请求
- **AI对话**：调用OpenAI/DeepSeek等大模型API

## API列表

### vflow.network.get_ip_address

获取设备的本地（局域网）或外部（公网）IP地址。

#### Lua调用

```lua
local result = vflow.network.get_ip_address({
    type = "本地",
    ip_version = "IPv4"
})
```

#### 参数

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| `type` | string | 否 | IP类型：`本地`、`外部`。默认：`本地` |
| `ip_version` | string | 否 | IP版本：`IPv4`、`IPv6`。默认：`IPv4` |

#### 返回值

```lua
{
    ip_address = string,
    success = boolean
}
```

#### 示例

```lua
-- 获取本地IPv4地址
local local_ip = vflow.network.get_ip_address({
    type = "本地",
    ip_version = "IPv4"
})

print("本机IP: " .. local_ip.ip_address)

-- 获取公网IP地址
local public_ip = vflow.network.get_ip_address({
    type = "外部",
    ip_version = "IPv4"
})

print("公网IP: " .. public_ip.ip_address)

-- 获取IPv6地址
local ipv6 = vflow.network.get_ip_address({
    type = "本地",
    ip_version = "IPv6"
})
```

---

### vflow.network.http_request

发送 HTTP 请求并获取响应。

#### Lua调用

```lua
local result = vflow.network.http_request({
    url = "https://api.example.com/data",
    method = "GET"
})
```

#### 参数

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| `url` | string | 是 | 请求URL |
| `method` | string | 否 | HTTP方法：`GET`、`POST`、`PUT`、`DELETE`、`PATCH`。默认：`GET` |
| `headers` | dictionary | 否 | 请求头 |
| `query_params` | dictionary | 否 | 查询参数 |
| `body_type` | string | 否 | 请求体类型：`无`、`JSON`、`表单`、`原始文本` |
| `body` | any | 否 | 请求体内容 |
| `timeout` | number | 否 | 超时时间（秒）。默认：10 |

#### 返回值

```lua
{
    response_body = string,
    status_code = number,
    response_headers = dictionary,
    error = string,
    success = boolean
}
```

#### 示例

```lua
-- 简单GET请求
local response = vflow.network.http_request({
    url = "https://api.github.com/users/octocat",
    method = "GET"
})

if response.success then
    print("状态码: " .. response.status_code)
    print("响应: " .. response.response_body)
end

-- POST JSON数据
local post_response = vflow.network.http_request({
    url = "https://api.example.com/users",
    method = "POST",
    headers = {
        ["Authorization"] = "Bearer YOUR_TOKEN",
        ["Content-Type"] = "application/json"
    },
    body_type = "JSON",
    body = {
        name = "张三",
        email = "test@example.com"
    }
})

-- 带查询参数的GET请求
local search = vflow.network.http_request({
    url = "https://api.example.com/search",
    method = "GET",
    query_params = {
        q = "vFlow",
        page = 1,
        limit = 10
    }
})

-- 发送表单数据
local form = vflow.network.http_request({
    url = "https://httpbin.org/post",
    method = "POST",
    body_type = "表单",
    body = {
        username = "user123",
        password = "pass456"
    }
})

-- 发送原始文本
local text_req = vflow.network.http_request({
    url = "https://example.com/api",
    method = "POST",
    body_type = "原始文本",
    body = "This is plain text content",
    headers = {
        ["Content-Type"] = "text/plain"
    }
})

-- 使用魔法变量
local token_response = vflow.network.http_request({
    url = "{{auth_api.url}}",
    method = "POST",
    body_type = "JSON",
    body = {
        token = "{{auth_token.value}}"
    }
})
```

---

### vflow.ai.completion

调用大模型 API (OpenAI/DeepSeek) 进行智能对话。

#### Lua调用

```lua
local result = vflow.ai.completion({
    provider = "OpenAI",
    api_key = "sk-...",
    model = "gpt-3.5-turbo",
    prompt = "Hello, how are you?"
})
```

#### 参数

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| `provider` | string | 是 | 服务商：`OpenAI`、`DeepSeek`、`自定义` |
| `base_url` | string | 否 | API Base URL |
| `api_key` | string | 是 | API密钥 |
| `model` | string | 是 | 模型名称 |
| `prompt` | string | 是 | 用户提示词 |
| `system_prompt` | string | 否 | 系统提示词 |
| `temperature` | number | 否 | 随机性（0-1）。默认：0.7 |

#### 返回值

```lua
{
    result = string,  -- AI的回答内容
    success = boolean
}
```

#### 示例

```lua
-- 使用OpenAI GPT-3.5
local chat = vflow.ai.completion({
    provider = "OpenAI",
    base_url = "https://api.openai.com/v1",
    api_key = "sk-your-api-key",
    model = "gpt-3.5-turbo",
    prompt = "解释什么是量子计算？",
    temperature = 0.7
})

if chat.success then
    print(chat.result)
end

-- 使用DeepSeek
local deepseek = vflow.ai.completion({
    provider = "DeepSeek",
    base_url = "https://api.deepseek.com/v1",
    api_key = "your-deepseek-key",
    model = "deepseek-chat",
    prompt = "写一首关于春天的诗"
})

-- 设置系统提示词
local assistant = vflow.ai.completion({
    provider = "OpenAI",
    api_key = "{{api_key.value}}",
    model = "gpt-3.5-turbo",
    system_prompt = "你是一个专业的翻译助手，只返回翻译结果，不要有其他说明。",
    prompt = "请将以下英文翻译成中文：\nHello, how are you today?",
    temperature = 0.3  -- 降低随机性以获得更准确的翻译
})

-- 结合OCR和AI
local screenshot = vflow.system.capture_screen()
local ocr_result = vflow.interaction.ocr({
    image = screenshot.image
})

if ocr_result.success then
    local ai_analysis = vflow.ai.completion({
        provider = "DeepSeek",
        api_key = "your-key",
        model = "deepseek-chat",
        prompt = "请总结以下文本的内容：\n" .. ocr_result.text
    })

    vflow.device.toast({
        message = ai_analysis.result
    })
end

-- 翻译功能
function translate_text(text, from_lang, to_lang)
    local prompt = string.format(
        "请将以下%s翻译成%s，只返回翻译结果：\n%s",
        from_lang,
        to_lang,
        text
    )

    local translation = vflow.ai.completion({
        provider = "DeepSeek",
        api_key = "your-key",
        model = "deepseek-chat",
        prompt = prompt,
        temperature = 0.3
    })

    return translation.result
end

local translated = translate_text("Hello World", "英文", "中文")
print(translated)  -- 你好世界
```

---

## 完整示例

### 示例1：天气查询

**manifest.json:**

```json
{
  "id": "user.weather",
  "name": "天气查询",
  "description": "查询指定城市的天气",
  "category": "用户脚本",
  "inputs": [
    {
      "id": "city",
      "name": "城市名称",
      "type": "string"
    }
  ],
  "outputs": [
    {
      "id": "weather_info",
      "name": "天气信息",
      "type": "string"
    }
  ],
  "permissions": []
}
```

**script.lua:**

```lua
local city = inputs.city or "北京"

-- 使用和风天气API（需要注册获取API key）
local url = string.format(
    "https://devapi.qweather.com/v7/weather/now?location=%s&key=YOUR_API_KEY",
    city
)

local response = vflow.network.http_request({
    url = url,
    method = "GET"
})

if response.success and response.status_code == 200 then
    -- 解析JSON响应（需要使用Lua的JSON库）
    -- 这里简化处理
    vflow.device.toast({
        message = "天气数据获取成功"
    })

    return {
        weather_info = response.response_body
    }
else
    vflow.device.toast({
        message = "获取天气失败"
    })

    return {
        weather_info = "错误: " .. response.error
    }
end
```

### 示例2：AI翻译助手

**manifest.json:**

```json
{
  "id": "user.ai_translator",
  "name": "AI翻译助手",
  "description": "使用AI进行多语言翻译",
  "category": "用户脚本",
  "inputs": [
    {
      "id": "text",
      "name": "要翻译的文本",
      "type": "string"
    },
    {
      "id": "target_lang",
      "name": "目标语言",
      "type": "string"
    }
  ],
  "outputs": [
    {
      "id": "translation",
      "name": "翻译结果",
      "type": "string"
    }
  ],
  "permissions": []
}
```

**script.lua:**

```lua
local text = inputs.text
local target_lang = inputs.target_lang or "中文"

if not text or text == "" then
    -- 如果没有输入文本，使用剪贴板内容
    local clipboard = vflow.system.get_clipboard()
    text = clipboard.text_content
end

if not text or text == "" then
    vflow.device.toast({ message = "没有可翻译的文本" })
    return { translation = "" }
end

-- 构建翻译提示词
local prompt = string.format(
    "请将以下文本翻译成%s，只返回翻译结果，不要有其他说明：\n%s",
    target_lang,
    text
)

-- 调用AI翻译
local ai_result = vflow.ai.completion({
    provider = "DeepSeek",
    api_key = "YOUR_DEEPSEEK_API_KEY",
    model = "deepseek-chat",
    prompt = prompt,
    temperature = 0.3
})

if ai_result.success then
    -- 将翻译结果复制到剪贴板
    vflow.system.set_clipboard({
        content = ai_result.result
    })

    -- 显示结果
    vflow.device.quick_view({
        content = ai_result.result
    })

    return {
        translation = ai_result.result
    }
else
    vflow.device.toast({
        message = "翻译失败"
    })
    return { translation = "" }
end
```

### 示例3：网络状态监控

**script.lua:**

```lua
-- 获取本地IP
local local_ip = vflow.network.get_ip_address({
    type = "本地",
    ip_version = "IPv4"
})

-- 获取公网IP
local public_ip = vflow.network.get_ip_address({
    type = "外部",
    ip_version = "IPv4"
})

-- 测试网络连接
local test = vflow.network.http_request({
    url = "https://www.baidu.com",
    method = "GET",
    timeout = 5
})

local status = "网络连接正常"
if not test.success then
    status = "网络连接失败"
end

-- 组装信息
local info = string.format(
    "本地IP: %s\n公网IP: %s\n状态: %s",
    local_ip.ip_address or "未知",
    public_ip.ip_address or "未知",
    status
)

-- 显示网络状态
vflow.device.quick_view({
    content = info
})

return {
    local_ip = local_ip.ip_address,
    public_ip = public_ip.ip_address,
    status = status
}
```

---

## 注意事项

1. **网络权限**：确保应用有网络访问权限
2. **API密钥**：使用AI服务需要配置API密钥，请妥善保管
3. **超时设置**：网络请求可能超时，建议设置合理的超时时间
4. **错误处理**：网络请求可能失败，需要检查返回的`success`字段
5. **HTTPS**：推荐使用HTTPS协议以确保安全

---

## 相关文档

- [数据处理API](./data.md) - 数据处理
- [系统API](./system.md) - 剪贴板操作
- [界面交互API](./interaction.md) - 显示结果

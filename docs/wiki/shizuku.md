# Shizuku API参考

Shizuku API用于在Lua脚本中通过Shell命令执行需要Root或Shizuku权限的高级操作。

## 概述

Shizuku API提供以下功能：
- **Shell命令**：执行自定义Shell命令
- **支付宝快捷方式**：快速打开支付宝功能
- **微信快捷方式**：快速打开微信功能
- **ColorOS快捷方式**：ColorOS系统快捷操作
- **Gemini助理**：启动Google Gemini语音助理

所有Shizuku操作都需要Shizuku或Root权限。在manifest.json中添加：

```json
{
  "permissions": ["vflow.permission.SHIZUKU"]
}
```

或使用Root权限：

```json
{
  "permissions": ["vflow.permission.ROOT"]
}
```

## API列表

### vflow.shizuku.shell_command

通过 Shell 执行命令 (支持 Root/Shizuku)。

#### Lua调用

```lua
local result = vflow.shizuku.shell_command({
    mode = "自动",
    command = "echo 'Hello World'"
})
```

#### 参数

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| `mode` | string | 是 | 执行方式：`自动`、`Shizuku`、`Root` |
| `command` | string | 是 | 要执行的Shell命令 |

#### 返回值

```lua
{
    result = string,  -- 命令输出
    success = boolean
}
```

#### 示例

```lua
-- 自动模式（优先使用可用的权限）
local result = vflow.shizuku.shell_command({
    mode = "自动",
    command = "ls -l /sdcard"
})

print(result.result)

-- 使用Root权限
local root_result = vflow.shizuku.shell_command({
    mode = "Root",
    command = "su -c 'ls -l /data/data'"
})

-- 使用Shizuku权限
local shizuku_result = vflow.shizuku.shell_command({
    mode = "Shizuku",
    command = "pm list packages"
})

-- 获取设备信息
local device_info = vflow.shizuku.shell_command({
    mode = "自动",
    command = "getprop ro.product.model"
})

print("设备型号: " .. device_info.result)

-- 查看已安装应用
local apps = vflow.shizuku.shell_command({
    mode = "自动",
    command = "pm list packages -3"
})

-- 处理命令输出
local lines = vflow.data.text_processing({
    operation = "分割",
    source_text = apps.result,
    split_delimiter = "\n"
})

print("已安装 " .. #lines.result_list .. " 个第三方应用")

-- 使用变量构建命令
local package_name = "com.tencent.mm"
local app_info = vflow.shizuku.shell_command({
    mode = "自动",
    command = "dumpsys package " .. package_name
})

-- 复杂的Shell命令
local complex = vflow.shizuku.shell_command({
    mode = "自动",
    command = "cat /proc/meminfo | grep MemTotal"
})

-- 使用魔法变量
local target_pkg = vflow.variable.get({
    source = [[target_package]]
})

local check = vflow.shizuku.shell_command({
    mode = "自动",
    command = "pm path " .. target_pkg.value
})

if check.result ~= "" then
    vflow.device.toast({
        message = "应用已安装"
    })
end
```

---

### vflow.shizuku.alipay_shortcuts

快速打开支付宝的扫一扫、付款码、收款码等。

#### Lua调用

```lua
local result = vflow.shizuku.alipay_shortcuts({
    action = "扫一扫"
})
```

#### 参数

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| `action` | string | 是 | 操作类型：`扫一扫`、`付款码`、`收款码` |

#### 返回值

```lua
{
    result = string,  -- 命令输出
    success = boolean
}
```

#### 示例

```lua
-- 打开扫一扫
local scan = vflow.shizuku.alipay_shortcuts({
    action = "扫一扫"
})

-- 打开付款码
local pay = vflow.shizuku.alipay_shortcuts({
    action = "付款码"
})

vflow.system.delay({ duration = 500 })

-- 打开收款码
local receive = vflow.shizuku.alipay_shortcuts({
    action = "收款码"
})

-- 结合其他操作
vflow.device.toast({
    message = "正在打开支付宝..."
})

vflow.shizuku.alipay_shortcuts({
    action = "付款码"
})

vflow.system.delay({ duration = 2000 })

-- 截屏保存付款码
local screenshot = vflow.system.capture_screen()
vflow.file.save_image({
    image = screenshot.image
})
```

---

### vflow.shizuku.wechat_shortcuts

快速打开微信的收款码、付款码。

#### Lua调用

```lua
local result = vflow.shizuku.wechat_shortcuts({
    action = "收款码"
})
```

#### 参数

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| `action` | string | 是 | 操作类型：`收款码`、`付款码` |

#### 返回值

```lua
{
    result = string,  -- 命令输出
    success = boolean
}
```

#### 示例

```lua
-- 打开收款码
local receive = vflow.shizuku.wechat_shortcuts({
    action = "收款码"
})

-- 打开付款码
local pay = vflow.shizuku.wechat_shortcuts({
    action = "付款码"
})

-- 自动切换
vflow.device.toast({
    message = "打开微信收款码"
})

vflow.shizuku.wechat_shortcuts({
    action = "收款码"
})

vflow.system.delay({ duration = 1500 })

-- 识别屏幕内容
local ocr = vflow.interaction.ocr()
if ocr.success then
    vflow.system.quick_view({
        content = "识别到：\n" .. ocr.text
    })
end
```

---

### vflow.shizuku.coloros_shortcuts

执行ColorOS系统相关的一些快捷操作。

#### Lua调用

```lua
local result = vflow.shizuku.coloros_shortcuts({
    action = "小布助手"
})
```

#### 参数

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| `action` | string | 是 | 操作类型：`小布记忆`、`小布助手`、`开始录音` |

#### 返回值

```lua
{
    result = string,  -- 命令输出
    success = boolean
}
```

#### 示例

```lua
-- 打开小布助手
local assistant = vflow.shizuku.coloros_shortcuts({
    action = "小布助手"
})

-- 开启小布记忆
local memory = vflow.shizuku.coloros_shortcuts({
    action = "小布记忆"
})

-- 开始录音
local record = vflow.shizuku.coloros_shortcuts({
    action = "开始录音"
})

-- 快速启动语音助手
vflow.shizuku.coloros_shortcuts({
    action = "小布助手"
})

vflow.system.delay({ duration = 1000 })

-- 模拟语音输入
vflow.device.toast({
    message = "可以开始说话了"
})
```

---

### vflow.shizuku.gemini_assistant

启动 Google Gemini 语音助理。

#### Lua调用

```lua
local result = vflow.shizuku.gemini_assistant()
```

#### 参数

无

#### 返回值

```lua
{
    result = string,  -- 命令输出
    success = boolean
}
```

#### 示例

```lua
-- 启动Gemini助理
local gemini = vflow.shizuku.gemini_assistant()

if gemini.success then
    vflow.device.toast({
        message = "Gemini助理已启动"
    })
end

-- 快捷语音助手
vflow.device.toast({
    message = "启动语音助理..."
})

vflow.shizuku.gemini_assistant()

-- 延迟后自动语音输入
vflow.system.delay({ duration = 2000 })
```

---

## 完整示例

### 示例1：快捷支付流程

**manifest.json:**

```json
{
  "id": "user.quick_pay",
  "name": "快捷支付",
  "description": "快速打开支付宝或微信付款码",
  "category": "用户脚本",
  "inputs": [
    {
      "id": "payment_method",
      "name": "支付方式",
      "type": "string"
    }
  ],
  "outputs": [],
  "permissions": ["vflow.permission.SHIZUKU"]
}
```

**script.lua:**

```lua
local method = inputs.payment_method or "支付宝"

if method == "支付宝" then
    vflow.device.toast({
        message = "正在打开支付宝付款码..."
    })

    vflow.shizuku.alipay_shortcuts({
        action = "付款码"
    })

elseif method == "微信" then
    vflow.device.toast({
        message = "正在打开微信付款码..."
    })

    vflow.shizuku.wechat_shortcuts({
        action = "付款码"
    })

else
    vflow.device.toast({
        message = "不支持的支付方式"
    })
    return
end

-- 等待加载
vflow.system.delay({ duration = 2000 })

vflow.device.toast({
    message = "付款码已准备好"
})
```

### 示例2：应用管理工具

**script.lua:**

```lua
-- 检查应用是否安装
local function check_app(package_name)
    local result = vflow.shizuku.shell_command({
        mode = "自动",
        command = "pm path " .. package_name
    })

    return result.result ~= ""
end

-- 获取应用版本
local function get_app_version(package_name)
    local result = vflow.shizuku.shell_command({
        mode = "自动",
        command = "dumpsys package " .. package_name .. " | grep versionName"
    })

    -- 提取版本号
    local version = string.match(result.result, "versionName=([^\r\n]+)")
    return version
end

-- 清除应用缓存
local function clear_cache(package_name)
    local result = vflow.shizuku.shell_command({
        mode = "自动",
        command = "pm clear " .. package_name
    })

    return result.success
end

-- 使用示例
local apps_to_check = {
    {name = "微信", package = "com.tencent.mm"},
    {name = "QQ", package = "com.tencent.mobileqq"},
    {name = "支付宝", package = "com.eg.android.AlipayGphone"}
}

local report = {}

for _, app in ipairs(apps_to_check) do
    if check_app(app.package) then
        local version = get_app_version(app.package)
        table.insert(report, app.name .. ": " .. (version or "未知版本"))
    else
        table.insert(report, app.name .. ": 未安装")
    end
end

-- 显示报告
local report_text = table.concat(report, "\n")
vflow.system.quick_view({
    content = report_text
})
```

### 示例3：系统信息查询

**script.lua:**

```lua
-- 获取设备信息
local device_model = vflow.shizuku.shell_command({
    mode = "自动",
    command = "getprop ro.product.model"
})

local android_version = vflow.shizuku.shell_command({
    mode = "自动",
    command = "getprop ro.build.version.release"
})

local cpu_info = vflow.shizuku.shell_command({
    mode = "自动",
    command = "cat /proc/cpuinfo | grep 'Hardware' | head -n 1"
})

local total_ram = vflow.shizuku.shell_command({
    mode = "自动",
    command = "cat /proc/meminfo | grep MemTotal"
})

-- 获取存储信息
local storage = vflow.shizuku.shell_command({
    mode = "自动",
    command = "df -h /sdcard | tail -n 1"
})

-- 组装信息
local info = string.format([[
设备信息
━━━━━━━━━━━━━━━━
型号: %s
Android版本: %s
CPU: %s
内存: %s
存储: %s
]], device_model.result, android_version.result,
cpu_info.result, total_ram.result, storage.result)

-- 显示信息
vflow.system.quick_view({
    content = info
})

-- 复制到剪贴板
vflow.system.set_clipboard({
    content = info
})

vflow.device.toast({
    message = "设备信息已复制到剪贴板"
})
```

### 示例4：自动化截图并分享

**script.lua:**

```lua
-- 打开支付宝付款码
vflow.device.toast({
    message = "打开支付宝付款码..."
})

vflow.shizuku.alipay_shortcuts({
    action = "付款码"
})

-- 等待页面加载
vflow.system.delay({ duration = 2500 })

-- 截图
local screenshot = vflow.system.capture_screen()

-- 保存截图
local saved = vflow.file.save_image({
    image = screenshot.image
})

if saved.success then
    vflow.device.toast({
        message = "付款码已保存"
    })

    -- 延迟后自动分享
    vflow.system.delay({ duration = 1000 })

    vflow.system.share({
        content = saved.file_path
    })
end
```

### 示例5：批量启动应用

**script.lua:**

```lua
-- 定义要启动的应用列表
local apps = {
    "com.tencent.mm",           -- 微信
    "com.eg.android.AlipayGphone", -- 支付宝
    "com.taobao.taobao",        -- 淘宝
    "com.jingdong.app.mall"     -- 京东
}

vflow.device.toast({
    message = "将依次启动 " .. #apps .. " 个应用"
})

for i, package in ipairs(apps) do
    vflow.device.toast({
        message = "启动应用 " .. i .. "/" .. #apps
    })

    -- 使用Shell命令启动
    local result = vflow.shizuku.shell_command({
        mode = "自动",
        command = "monkey -p " .. package .. " -c android.intent.category.LAUNCHER 1"
    })

    -- 延迟3秒
    vflow.system.delay({ duration = 3000 })

    -- 返回主屏幕
    vflow.device.send_key_event({
        key_action = "主屏幕"
    })

    vflow.system.delay({ duration = 1000 })
end

vflow.device.toast({
    message = "所有应用启动完成"
})
```

---

## Shell命令参考

### 常用应用管理命令

```lua
-- 列出所有已安装应用
pm list packages

-- 只列出第三方应用
pm list packages -3

-- 获取应用详细信息
dumpsys package com.example.app

-- 启动应用
am start -n com.example.app/.MainActivity

-- 停止应用
am force-stop com.example.app

-- 清除应用数据
pm clear com.example.app

-- 卸载应用（需要Root）
pm uninstall com.example.app
```

### 系统信息命令

```lua
-- 获取系统属性
getprop

-- 获取设备型号
getprop ro.product.model

-- 获取Android版本
getprop ro.build.version.release

-- 获取CPU信息
cat /proc/cpuinfo

-- 获取内存信息
cat /proc/meminfo

-- 获取存储信息
df -h
```

### 文件操作命令

```lua
-- 列出文件
ls -l /sdcard

-- 复制文件
cp /sdcard/file1.txt /sdcard/file2.txt

-- 移动文件
mv /sdcard/old.txt /sdcard/new.txt

-- 删除文件
rm /sdcard/file.txt

-- 创建目录
mkdir /sdcard/new_folder

-- 查看文件内容
cat /sdcard/file.txt
```

### 网络相关命令

```lua
-- 查看网络状态
dumpsys connectivity

-- 查看Wi-Fi信息
dumpsys wifi

-- Ping测试
ping -c 4 www.baidu.com

-- 查看网络配置
ifconfig
```

### 截屏和录屏

```lua
-- 截屏（需要Shell权限）
screencap -p /sdcard/screenshot.png

-- 录屏（需要Shell权限）
screenrecord /sdcard/video.mp4
```

---

## 注意事项

1. **权限要求**：所有Shizuku操作都需要Shizuku或Root权限
2. **命令安全**：执行Shell命令时要小心，避免误操作导致系统问题
3. **延迟处理**：某些操作需要适当的延迟才能完成
4. **错误处理**：检查命令执行的返回结果，处理可能的错误
5. **兼容性**：不同厂商的ROM可能有差异，某些命令可能不可用
6. **性能考虑**：频繁执行Shell命令可能影响性能

---

## 相关文档

- [系统API](./system.md) - 启动应用、系统控制
- [界面交互API](./interaction.md) - UI操作
- [网络API](./network.md) - 网络请求
- [数据处理API](./data.md) - 文本处理

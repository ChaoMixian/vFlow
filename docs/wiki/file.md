# 文件操作API参考

文件操作API用于在Lua脚本中导入、保存、调整图片和应用蒙版等图像处理功能。

## 概述

所有文件操作都需要存储权限。在manifest.json中添加：

```json
{
  "permissions": ["vflow.permission.STORAGE"]
}
```

## API列表

### vflow.file.import_image

从相册或文件中选择一张图片。

#### Lua调用

```lua
local result = vflow.file.import_image()
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
-- 选择图片
local image_result = vflow.file.import_image()

if image_result.success then
    vflow.device.toast({
        message = "图片已选择"
    })

    -- 可以传递给其他模块处理
    return {
        selected_image = image_result.image
    }
end
```

---

### vflow.file.save_image

将图片保存到相册。

#### Lua调用

```lua
local result = vflow.file.save_image({
    image = image_variable
})
```

#### 参数

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| `image` | ImageVariable | 是 | 要保存的图片变量 |

#### 返回值

```lua
{
    success = boolean,
    file_path = string  -- 保存的文件路径
}
```

#### 示例

```lua
-- 导入图片
local imported = vflow.file.import_image()

if imported.success then
    -- 保存到相册
    local saved = vflow.file.save_image({
        image = imported.image
    })

    if saved.success then
        vflow.device.toast({
            message = "已保存至: " .. saved.file_path
        })
    end
end
```

---

### vflow.file.adjust_image

调整图像的曝光、对比度、饱和度等参数。

#### Lua调用

```lua
local result = vflow.file.adjust_image({
    image = image_variable,
    brightness = 20,
    contrast = 10,
    saturation = 15
})
```

#### 参数

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| `image` | ImageVariable | 是 | 源图像 |
| `exposure` | number | 否 | 曝光（-100到100） |
| `contrast` | number | 否 | 对比度（-100到100） |
| `brightness` | number | 否 | 亮度（-100到100） |
| `saturation` | number | 否 | 饱和度（-100到100） |
| `vibrance` | number | 否 | 鲜明度（-100到100） |
| `highlights` | number | 否 | 高光（-100到100） |
| `shadows` | number | 否 | 阴影（-100到100） |
| `blackPoint` | number | 否 | 黑点（0到100） |
| `warmth` | number | 否 | 色温（-100到100） |
| `tint` | number | 否 | 色调（-100到100） |
| `vignette` | number | 否 | 暗角（-100到100） |
| `sharpness` | number | 否 | 锐化（0到100） |
| `clarity` | number | 否 | 清晰度（0到100） |
| `denoise` | number | 否 | 噪点消除（0到100） |

#### 返回值

```lua
{
    image = ImageVariable  -- 处理后的图像
}
```

#### 示例

```lua
-- 增加亮度和对比度
local bright_image = vflow.file.adjust_image({
    image = imported.image,
    brightness = 30,
    contrast = 20
})

-- 应用滤镜效果
local filtered = vflow.file.adjust_image({
    image = imported.image,
    saturation = -50,     -- 降低饱和度（黑白效果）
    contrast: 30,         -- 增加对比度
    clarity: 50           -- 增加清晰度
})

-- 暖色调效果
local warm = vflow.file.adjust_image({
    image = imported.image,
    warmth = 40,
    vibrance = 20
})
```

---

### vflow.file.rotate_image

将图片按指定角度旋转。

#### Lua调用

```lua
local result = vflow.file.rotate_image({
    image = image_variable,
    degrees = 90
})
```

#### 参数

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| `image` | ImageVariable | 是 | 源图像 |
| `degrees` | number | 是 | 旋转角度（度） |

#### 返回值

```lua
{
    image = ImageVariable  -- 旋转后的图像
}
```

#### 示例

```lua
-- 顺时针旋转90度
local rotated = vflow.file.rotate_image({
    image = imported.image,
    degrees = 90
})

-- 旋转180度
local flipped = vflow.file.rotate_image({
    image = imported.image,
    degrees = 180
})

-- 逆时针旋转45度
local rotated_left = vflow.file.rotate_image({
    image = imported.image,
    degrees = -45
})
```

---

### vflow.file.apply_mask

为图片应用 Material You 风格圆角矩形蒙版。

#### Lua调用

```lua
local result = vflow.file.apply_mask({
    image = image_variable,
    mask_shape = "圆角矩形"
})
```

#### 参数

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| `image` | ImageVariable | 是 | 源图像 |
| `mask_shape` | string | 是 | 蒙版形状（目前仅支持"圆角矩形"） |

#### 返回值

```lua
{
    image = ImageVariable  -- 处理后的图像
}
```

#### 示例

```lua
-- 应用圆角蒙版
local masked = vflow.file.apply_mask({
    image = imported.image,
    mask_shape = "圆角矩形"
})

-- 保存处理后的图片
vflow.file.save_image({
    image = masked.image
})
```

---

## 完整示例

### 示例1：照片编辑流程

**manifest.json:**

```json
{
  "id": "user.photo_editor",
  "name": "照片编辑器",
  "description": "导入照片并应用滤镜效果",
  "category": "用户脚本",
  "inputs": [],
  "outputs": [
    {
      "id": "edited_image",
      "name": "编辑后的图片",
      "type": "image"
    }
  ],
  "permissions": ["vflow.permission.STORAGE"]
}
```

**script.lua:**

```lua
-- 1. 导入图片
local imported = vflow.file.import_image()

if not imported.success then
    vflow.device.toast({ message = "未选择图片" })
    return { edited_image = nil }
end

-- 2. 应用滤镜（增加对比度、饱和度、清晰度）
local edited = vflow.file.adjust_image({
    image = imported.image,
    contrast = 20,
    saturation = 15,
    clarity = 30,
    warmth = 10
})

-- 3. 应用圆角蒙版
local final = vflow.file.apply_mask({
    image = edited.image,
    mask_shape = "圆角矩形"
})

-- 4. 保存到相册
local saved = vflow.file.save_image({
    image = final.image
})

if saved.success then
    vflow.device.toast({
        message = "编辑完成并已保存"
    })
end

-- 5. 返回结果
return {
    edited_image = final.image
}
```

### 示例2：批量旋转图片

**script.lua:**

```lua
-- 导入第一张图片
local img1 = vflow.file.import_image()
if img1.success then
    local rotated1 = vflow.file.rotate_image({
        image = img1.image,
        degrees = 90
    })
    vflow.file.save_image({ image = rotated1.image })
end

-- 导入第二张图片
local img2 = vflow.file.import_image()
if img2.success then
    local rotated2 = vflow.file.rotate_image({
        image = img2.image,
        degrees = 180
    })
    vflow.file.save_image({ image = rotated2.image })
end

vflow.device.toast({ message = "批量处理完成" })
```

### 示例3：创建Instagram风格滤镜

**script.lua:**

```lua
-- 导入图片
local photo = vflow.file.import_image()

if photo.success then
    -- 创建复古效果
    local vintage = vflow.file.adjust_image({
        image = photo.image,
        brightness = -10,
        contrast = 10,
        saturation = -30,
        warmth = 40,
        vignette = 30
    })

    -- 创建黑白效果
    local bw = vflow.file.adjust_image({
        image = photo.image,
        saturation = -100,
        contrast = 30,
        clarity = 50
    })

    -- 创建鲜艳效果
    local vivid = vflow.file.adjust_image({
        image = photo.image,
        saturation = 50,
        vibrance = 40,
        contrast: 20,
        sharpness: 30
    })

    -- 保存所有版本
    vflow.file.save_image({ image = vintage.image })
    vflow.file.save_image({ image = bw.image })
    vflow.file.save_image({ image = vivid.image })

    vflow.device.toast({
        message = "已生成3个滤镜版本"
    })
end
```

---

## 注意事项

1. **权限要求**：所有图片操作都需要存储权限
2. **内存管理**：处理大图片时可能消耗较多内存
3. **处理时间**：复杂的调整操作可能需要较长时间
4. **文件路径**：保存的图片路径可能因Android版本而异
5. **图像格式**：支持常见图片格式（JPEG、PNG、WebP等）

---

## 相关文档

- [界面交互API](./interaction.md) - 截屏和OCR
- [数据处理API](./data.md) - 变量操作
- [系统API](./system.md) - 系统功能

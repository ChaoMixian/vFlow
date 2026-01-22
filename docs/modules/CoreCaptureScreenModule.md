# Screenshot Module 使用说明

## 概述

基于scrcpy的原理实现的屏幕捕获功能，支持在高版本Android上获取屏幕截图。分为Core层（底层实现）和App层（工作流模块）两部分。

## 架构说明

### Core层 - 底层截图服务
- 提供3个原始API接口
- 返回Base64编码的图像数据
- 运行在ShellWorker中（端口20001）

### App层 - 工作流模块
- `CoreCaptureScreenModule` - 截图动作模块
- 自动处理Base64解码和文件保存
- 返回VImage对象供后续模块使用

## 实现原理

### 初始化策略（与scrcpy保持一致）

- **DisplayManager**: 启动时必须初始化成功，否则功能不可用
- **SurfaceControl**: 不预初始化，运行时懒加载（按需使用时才初始化）
- **运行时选择**: 优先使用DisplayManager API，失败才降级到SurfaceControl

这样可以避免启动时的不必要错误日志，提高性能。

### 核心技术

1. **DisplayManager API** (优先)
   - 使用`DisplayManager.createVirtualDisplay()`创建虚拟显示器
   - 将屏幕内容渲染到提供的Surface上
   - 支持Android 5.0+

2. **SurfaceControl API** (备用)
   - 当DisplayManager失败时使用
   - 通过反射调用`SurfaceControl.createDisplay()`
   - 设置Surface、投影和Layer Stack
   - 懒加载机制：首次调用时才初始化反射方法

3. **ImageReader**
   - 从Surface读取图像数据
   - 转换为Bitmap
   - 输出为PNG或JPEG格式

### 关键文件

**Core层（底层实现）：**
- `core/src/main/java/com/chaomixian/vflow/server/common/utils/DisplayCaptureUtils.kt`
  - 屏幕捕获工具类
  - DisplayManager和SurfaceControl反射封装
  - 懒加载初始化机制

- `core/src/main/java/com/chaomixian/vflow/server/common/utils/ImageReaderHelper.kt`
  - ImageReader辅助类
  - 图像读取和格式转换

- `core/src/main/java/com/chaomixian/vflow/server/wrappers/shell/IScreenshotWrapper.kt`
  - 截图服务Wrapper
  - 实现3个API方法

- `core/src/main/java/com/chaomixian/vflow/server/wrappers/IWrapper.kt`
  - 简单Wrapper接口

- `core/src/main/java/com/chaomixian/vflow/server/worker/BaseWorker.kt`
  - Worker基类（已扩展支持IWrapper）

**App层（工作流模块）：**
- `app/src/main/java/com/chaomixian/vflow/core/workflow/module/core/CoreCaptureScreenModule.kt`
  - 截图动作模块
  - 自动处理Base64解码和文件保存
  - 返回VImage对象

- `app/src/main/java/com/chaomixian/vflow/services/VFlowCoreBridge.kt`
  - Core服务桥接器
  - 提供captureScreenEx()和getScreenSize()方法

- `app/src/main/java/com/chaomixian/vflow/core/types/complex/VImage.kt`
  - 图像对象类型
  - 提供width、height、path等属性

## API 接口

### 1. captureScreen

截取屏幕并返回Base64编码的图像数据。

**请求格式：**
```json
{
  "target": "screenshot",
  "method": "captureScreen",
  "params": {
    "displayId": 0,
    "format": "png",
    "quality": 90,
    "maxWidth": 1920,
    "maxHeight": 1080,
    "includeBase64": true
  }
}
```

**参数说明：**
- `displayId` (可选): 显示器ID，默认为0（主显示器）
- `format` (可选): 输出格式，"png"或"jpeg"，默认为"png"
- `quality` (可选): JPEG质量（1-100），默认为90，仅对JPEG有效
- `maxWidth` (可选): 最大宽度，0表示不限制，默认为0
- `maxHeight` (可选): 最大高度，0表示不限制，默认为0
- `includeBase64` (可选): 是否包含Base64数据，默认为true

**响应格式：**
```json
{
  "success": true,
  "width": 1920,
  "height": 1080,
  "format": "png",
  "data": "iVBORw0KGgoAAAANSUhEUgAA...",
  "size": 123456
}
```

### 2. captureScreenToFile

截取屏幕并保存到文件。

**请求格式：**
```json
{
  "target": "screenshot",
  "method": "captureScreenToFile",
  "params": {
    "displayId": 0,
    "filePath": "/sdcard/screenshot.png",
    "format": "png",
    "quality": 90
  }
}
```

**错误响应格式：**
```json
{
  "success": false,
  "error": "Failed to capture screen"
}
```

## App层工作流模块使用

### CoreCaptureScreenModule

在vFlow工作流中使用截图功能更加简单，无需手动处理API和Base64解码。

#### 模块输入参数

```json
{
  "output_format": "PNG",      // 输出格式: PNG 或 JPEG
  "quality": 90,                // JPEG质量 (1-100)，仅JPEG有效
  "max_width": 0,               // 最大宽度，0=不限制
  "max_height": 0               // 最大高度，0=不限制
}
```

#### 模块输出

```json
{
  "success": true,              // 是否成功
  "image": VImage,               // 截图图片对象
  "width": 1920,                // 图像宽度
  "height": 1080                // 图像高度
}
```

#### VImage对象属性

```javascript
// 图片对象提供以下属性：
截屏.image.width      // 图片宽度
截屏.image.height     // 图片高度
截屏.image.path       // 文件路径 (workDir/screenshot_xxx.png)
截屏.image.uri        // 完整URI (file://...)
截屏.image.size       // 文件大小 (字节)
截屏.image.name       // 文件名
```

#### 工作流示例

**步骤1：截屏**
```
动作：截屏
├─ 输出格式：PNG
├─ JPEG质量：90
├─ 最大宽度：0
└─ 最大高度：0

输出变量：截屏
```

**步骤2：使用图片**
```
动作：OCR识别
└─ 图片：{截屏.image}

或

动作：发送HTTP请求
├─ URL：https://api.example.com/upload
├─ 方法：POST
└─ 文件：{截屏.image}

或

动作：保存图片到相册
└─ 源文件：{截屏.image.path}
```

**步骤3：访问属性**
```
文本内容：图片宽度是 {截屏.image.width}，高度是 {截屏.image.height}
```

## 工作流程

### 完整的数据流

```
1. 用户操作
   └─ 工作流执行"截屏"动作

2. App层处理
   └─ CoreCaptureScreenModule.execute()
       ├─ 调用 VFlowCoreBridge.captureScreenEx()
       └─ 接收Base64数据

3. Core层处理
   └─ IScreenshotWrapper.handle()
       ├─ 调用 DisplayCaptureUtils.createVirtualDisplay()
       ├─ ImageReaderHelper.acquireLatestImage()
       └─ 返回Base64编码的图像数据

4. App层保存
   └─ CoreCaptureScreenModule
       ├─ 解码Base64数据
       ├─ 保存到 workDir/screenshot_xxx.png
       └─ 创建VImage对象

5. 后续使用
   └─ 其他模块接收VImage对象
       ├─ OCR识别: {截屏.image}
       ├─ HTTP上传: file: {截屏.image}
       └─ 图片处理: source: {截屏.image}
```

### 文件存储

**工作流临时目录：**
```
/data/data/com.chaomixian.vflow/cache/temp/exec_<executionId>/screenshot_<timestamp>.png
```

**特点：**
- ✅ 每个工作流有独立的临时目录
- ✅ 截图自动保存，无需手动处理
- ✅ 工作流结束后自动清理
- ✅ 传递VImage对象，路径自动处理

## API 接口（Core层原始API）

> **注意**: 这些是底层API，通常不需要直接调用。推荐使用App层的CoreCaptureScreenModule工作流模块。

### 1. captureScreen

截取屏幕并返回Base64编码的图像数据。

**请求格式：**
```json
{
  "target": "screenshot",
  "method": "captureScreen",
  "params": {
    "displayId": 0,
    "format": "png",
    "quality": 90,
    "maxWidth": 1920,
    "maxHeight": 1080,
    "includeBase64": true
  }
}
```

**参数说明：**
- `displayId` (可选): 显示器ID，默认为0（主显示器）
- `format` (可选): 输出格式，"png"或"jpeg"，默认为"png"
- `quality` (可选): JPEG质量（1-100），默认为90，仅对JPEG有效
- `maxWidth` (可选): 最大宽度，0表示不限制，默认为0
- `maxHeight` (可选): 最大高度，0表示不限制，默认为0
- `includeBase64` (可选): 是否包含Base64数据，默认为true

**响应格式：**
```json
{
  "success": true,
  "width": 1920,
  "height": 1080,
  "format": "png",
  "data": "iVBORw0KGgoAAAANSUhEUgAA...",
  "size": 123456
}
```

### 2. captureScreenToFile

截取屏幕并保存到文件。

**请求格式：**
```json
{
  "target": "screenshot",
  "method": "captureScreenToFile",
  "params": {
    "displayId": 0,
    "filePath": "/sdcard/screenshot.png",
    "format": "png",
    "quality": 90
  }
}
```

**响应格式：**
```json
{
  "success": true
}
```

### 3. getScreenSize

获取屏幕尺寸信息。

**请求格式：**
```json
{
  "target": "screenshot",
  "method": "getScreenSize",
  "params": {
    "displayId": 0
  }
}
```

**响应格式：**
```json
{
  "success": true,
  "width": 1920,
  "height": 1080,
  "rotation": 0,
  "displayId": 0
}
```

## 使用示例

### Kotlin示例

```kotlin
import com.chaomixian.vflow.server.common.utils.DisplayCaptureUtils
import org.json.JSONObject
import java.net.Socket
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter

fun captureScreen(): String? {
    return try {
        // 连接到ShellWorker
        Socket("127.0.0.1", 20001).use { socket ->
            val writer = PrintWriter(OutputStreamWriter(socket.getOutputStream()), true)
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))

            // 构建请求
            val request = JSONObject().apply {
                put("target", "screenshot")
                put("method", "captureScreen")
                put("params", JSONObject().apply {
                    put("format", "png")
                    put("maxWidth", 1920)
                    put("maxHeight", 1080)
                })
            }

            // 发送请求
            writer.println(request.toString())

            // 读取响应
            val responseStr = reader.readLine()
            val response = JSONObject(responseStr)

            if (response.optBoolean("success", false)) {
                response.optString("data")
            } else {
                null
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

fun captureScreenToFile(filePath: String): Boolean {
    return try {
        Socket("127.0.0.1", 20001).use { socket ->
            val writer = PrintWriter(OutputStreamWriter(socket.getOutputStream()), true)
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))

            val request = JSONObject().apply {
                put("target", "screenshot")
                put("method", "captureScreenToFile")
                put("params", JSONObject().apply {
                    put("filePath", filePath)
                    put("format", "png")
                })
            }

            writer.println(request.toString())

            val responseStr = reader.readLine()
            val response = JSONObject(responseStr)

            response.optBoolean("success", false)
        }
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}
```

### Shell示例

```bash
# 获取屏幕尺寸
echo '{"target":"screenshot","method":"getScreenSize","params":{"displayId":0}}' | nc 127.0.0.1 20001

# 截图到文件
echo '{"target":"screenshot","method":"captureScreenToFile","params":{"filePath":"/sdcard/screenshot.png","format":"png"}}' | nc 127.0.0.1 20001
```

## 权限要求

截图功能需要以下权限：

1. **Shell权限** - 必需
   - 所有操作都在ShellWorker中执行
   - 需要通过app_process或shell启动

2. **Android权限**
   - 无需额外权限（使用隐藏API）
   - Android 12+ 无法创建secure显示器

## 兼容性

- ✅ Android 5.0+ (API 21+)
- ✅ Android 10+ (API 29+) - 使用`getInternalDisplayToken()`
- ✅ Android 12+ (API 31+) - 不支持secure显示器
- ✅ 支持多显示器
- ✅ 支持旋转
- ✅ 支持缩放

## 已知限制

1. **Android 12+ 限制**
   - 无法创建secure显示器
   - 某些受DRM保护的内容可能无法捕获

2. **性能**
   - 截图需要200-500ms
   - 不适合实时视频流
   - 建议单次截图，不连续捕获

3. **内存**
   - 高分辨率截图会占用大量内存
   - 建议使用maxWidth/maxHeight限制尺寸

## 调试

启用调试日志：
```kotlin
Logger.setLevel(Logger.Level.DEBUG)
```

查看详细日志：
```bash
logcat | grep "IScreenshotWrapper\|DisplayCaptureUtils\|ImageReaderHelper"
```

## 最佳实践

### 推荐使用方式

1. **工作流中使用**（推荐）
   - 使用`CoreCaptureScreenModule`模块
   - 自动处理文件保存和VImage对象
   - 无需手动管理文件路径

2. **限制图片尺寸**
   - 设置maxWidth和maxHeight参数
   - 避免高分辨率图片占用过多内存
   - 建议值：1920x1080或更小

3. **选择合适的格式**
   - PNG：无损压缩，适合需要高质量的场景
   - JPEG：有损压缩，文件更小，适合网络传输

### 性能优化

- 避免连续截图（每次200-500ms）
- 使用maxWidth/maxHeight限制图片尺寸
- JPEG格式通常比PNG快20-30%
- 工作流临时目录会自动清理，无需手动删除

### 常见问题

**Q: 为什么启动时看到SurfaceControl初始化失败的错误？**
A: 这是正常的。SurfaceControl是备用方案，使用懒加载机制，只在DisplayManager失败时才初始化。如果DisplayManager工作正常，SurfaceControl永远不会被使用。

**Q: 截图失败怎么办？**
A: 检查以下几点：
1. 确保Core服务已启动（Shizuku或Root权限）
2. 查看logcat日志确认具体错误
3. 某些受DRM保护的内容可能无法截取

**Q: 如何查看截图文件？**
A:
- 工作流截图：保存在工作流临时目录，通过`{截屏.image.path}`访问
- 手动指定路径：使用captureScreenToFile API

## 参考资源

- [scrcpy Server源码](https://github.com/Genymobile/scrcpy/tree/master/server/src/main/java/com/genymobile/scrcpy)
- [Android DisplayManager](https://developer.android.com/reference/android/hardware/display/DisplayManager)
- [Android SurfaceControl](https://developer.android.com/reference/android/view/SurfaceControl)

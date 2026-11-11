# vFlow 项目实现说明

> 本文基于 `vFlow` 仓库当前代码（versionCode 66 / versionName `1.5.3-pr18`）整理，重点说明各功能模块「是怎么实现的」，而不是只列功能。
>
> 文档范围：工程结构、核心类型系统、模块系统、执行引擎、工作流存储、触发器、无障碍自动化、vFlow Core 特权服务、脚本与扩展、权限体系、UI 层，以及 APK 构建打包流程。

---

## 1. 项目定位

vFlow 是一个 Android 端的可视化自动化工具：用户把「触发器 + 若干动作模块」拼成工作流，由 App 内的执行引擎按顺序解释执行。动作可以操作 UI（点击、找控件、输入文本）、读写变量、发网络请求、执行 Shell/JavaScript/Lua、调用 AI/OCR，也可以通过 Shizuku/Root 调用系统隐藏 API。

关键特征：

- **可视化编排**：工作流由 `ActionStep` 列表组成，编辑器支持拖拽、缩进、成块结构（if/loop/menu）。
- **模块化**：150+ 内建模块，统一抽象为 `ActionModule`；支持外部 APK 模块和用户 Lua 模块。
- **统一变量系统**：所有运行期值都是 `VObject`，支持魔法变量 `{{...}}` 与命名变量 `[[...]]`。
- **多触发器**：23 种触发器，覆盖应用启动/切换、通知、短信、时间、位置、按键、剪贴板、语音等。
- **特权能力**：通过 Shizuku 或 Root 启动 `vFlowCore.dex`（`app_process`），以 Shell/root 身份调用系统服务。
- **AI 能力**：ML Kit OCR、OpenCV 图像匹配、ONNX Runtime（PP-OCRv5）、大模型 Chat/AI 动作模块。

技术栈：Kotlin 2.3.20、AGP 9.0.1、Jetpack Compose（BOM 2025.12.01）、Material3、Navigation3、Gson、kotlinx.serialization、LuaJ、Rhino、Shizuku API、OpenCV、ONNX Runtime、ML Kit、OkHttp、NanoHTTPD。

---

## 2. 工程结构

```
vFlow/
├── app/                         # Android 应用模块
│   ├── src/main/java/com/chaomixian/vflow/
│   │   ├── api/                 # 对外 HTTP/WebSocket API（NanoHTTPD）
│   │   ├── core/                # 工作流内核（与 Android 弱耦合）
│   │   │   ├── execution/       # 执行引擎、上下文、变量解析
│   │   │   ├── module/          # 模块抽象、模块注册表
│   │   │   ├── types/           # VObject 类型系统
│   │   │   └── workflow/        # 工作流模型、存储、模块实现
│   │   ├── data/                # 数据仓库、更新检查
│   │   ├── extension/           # 外部 APK 模块加载
│   │   ├── integration/         # 第三方 App 集成
│   │   ├── ocr/                 # ML Kit / ONNX OCR
│   │   ├── permissions/         # 权限定义与管理
│   │   ├── services/            # 前台服务、无障碍、触发器宿主、Core 管理
│   │   ├── speech/              # 语音识别/合成
│   │   └── ui/                  # Compose + 传统 View 混合 UI
│   ├── src/main/cpp/            # PP-OCRv5 JNI / OpenCV 原生代码
│   └── src/main/assets/         # vFlowCore.dex（构建时生成）
├── core/                        # Kotlin JVM 模块，编译为 vFlowCore.dex
├── doc/                         # 本文档
├── docs/                        # 原有设计文档（部分内容较旧）
├── scripts/signing/             # debug→release 签名轮换脚本
├── vFlow.jks                    # release 签名（本地生成，已被 .gitignore）
├── signing.properties           # 签名配置（本地生成，已被 .gitignore）
└── settings.gradle.kts
```

`settings.gradle.kts` 只包含 `:app` 与 `:core` 两个模块。`:core` 是纯 Kotlin/JVM 的 `java-library`，不依赖 Android SDK，构建产物通过 D8 转成 dex 后放进 App assets。

---

## 3. 总体架构

```
┌─────────────────────────────────────────────────────────────┐
│                         UI 层                                │
│  MainActivity / Compose 主页 / 工作流编辑器 / 设置 / Chat     │
└──────────────────────────┬──────────────────────────────────┘
                           │ WorkflowManager 保存/读取
┌──────────────────────────▼──────────────────────────────────┐
│                      工作流数据层                            │
│  Workflow / ActionStep / FolderManager / GlobalVariableStore │
│  SharedPreferences("vflow_workflows") 中保存 JSON 数组        │
└──────────────────────────┬──────────────────────────────────┘
                           │ 触发
┌──────────────────────────▼──────────────────────────────────┐
│                   TriggerService（前台服务）                 │
│  TriggerHandlerRegistry → 各 ITriggerHandler                 │
│  AppStart / Notification / Time / KeyEvent / ...             │
└──────────────────────────┬──────────────────────────────────┘
                           │ execute(workflow, triggerData)
┌──────────────────────────▼──────────────────────────────────┐
│                    WorkflowExecutor                          │
│  pc 解释循环 → ModuleRegistry 取模块 → 解析参数 → 执行        │
│  ExecutionContext / ExecutionStateBus / 通知 / WakeLock      │
└───────┬───────────────────────────────┬─────────────────────┘
        │ 普通模块                       │ 需要特权
┌───────▼──────────────┐        ┌───────▼─────────────────────┐
│ AccessibilityService │        │ VFlowCoreBridge              │
│ 点击/找控件/输入      │        │  → vFlowCore.dex (app_process)│
│ Shizuku / Root Shell │        │  → ShellWorker/RootWorker    │
└──────────────────────┘        └─────────────────────────────┘
```

设计上把「工作流描述」「执行调度」「具体动作能力」分开：

- 工作流只是数据（JSON），不包含代码。
- 执行引擎只负责按 `pc` 推进、解析参数、处理控制流与错误。
- 每个动作是一个 `ActionModule` 子类，自己实现 `execute()`，因此新增能力只需要新增模块并注册。

---

## 4. 核心类型系统：VObject

`core/types/` 定义了运行期统一值模型。所有变量、模块输入输出、触发器数据都使用 `VObject`，避免 `Any?` 在脚本、模板、JSON 之间转换时丢失类型。

主要类型：

| 类型 | 说明 |
|---|---|
| `VNull` | 空值 |
| `VBoolean` | 布尔 |
| `VNumber` | 数字（整数/浮点统一） |
| `VString` | 字符串 |
| `VList` | 列表 |
| `VDictionary` | 字典 |
| `VCoordinate` / `VCoordinateRegion` | 坐标 / 屏幕区域 |
| `VData` | 二进制数据 |
| `VEvent` | 事件 |
| `VFile` | 文件 |
| `VImage` | 图像 |
| `VNotification` | 通知 |
| `VScreenElement` | 屏幕控件 |
| `VTime` | 时间 |
| `VUiComponent` | UI 组件引用 |

配套组件：

- **`TemplateParser`**：解析字符串模板。
- **`VariablePathParser`**：解析 `a.b[0].c` 这类路径。
- **`PropertyRegistry`**：描述每种 VObject 可访问的属性，供变量选择器和路径解析使用。
- **`VObjectGsonAdapter`**：让 Gson 能正确序列化/反序列化多态 VObject。
- **`VariableResolver`**（execution 包）：执行期把参数中的变量引用替换为实际值。

变量引用有两种写法：

- `{{变量名}}`：魔法变量，直接引用当前步骤上下文里的变量/步骤输出。
- `[[变量名]]`：命名变量，引用工作流级命名变量（可在子工作流间共享）。
- 字符串中可混排，例如 `https://example.com/{{path}}?q=[[keyword]]`，解析后仍是字符串。

---

## 5. 模块系统

### 5.1 模块抽象

核心文件：

- `core/module/ActionModule.kt`
- `core/module/BaseModule.kt`
- `core/module/BaseBlockModule.kt`
- `core/module/definitions.kt`
- `core/workflow/module/ModuleRegistry.kt`

一个模块需要提供：

```kotlin
abstract class ActionModule {
    abstract val metadata: ActionMetadata
    abstract fun getInputs(): List<InputDefinition>
    abstract fun getOutputs(): List<OutputDefinition>
    abstract suspend fun execute(
        context: ExecutionContext,
        step: ActionStep
    ): ExecutionResult
}
```

- **`ActionMetadata`**：名称、描述、图标、分类、分类 ID、字符串资源。
- **`InputDefinition`**：输入参数定义，包括：
  - `id`、`name`、`staticType`
  - `defaultValue`、`options`（枚举）
  - `acceptsMagicVariable` / `acceptsNamedVariable` / `acceptsInlineScript`
  - `acceptedMagicVariableTypes`、`supportsRichText`
  - `inputStyle`：DEFAULT / CHIP_GROUP / DROPDOWN / SWITCH / SLIDER
  - `pickerType`：FILE / DIRECTORY / APP / ACTIVITY / MEDIA / DATE / TIME / DATETIME / SCREEN_REGION
  - `visibility`：按其他参数值动态显示/隐藏（whenEquals、In、And/Or/Not、Predicate 等）
  - `legacyValueMap`：旧版本参数值迁移
- **`OutputDefinition`**：输出变量名、类型、列表元素类型、条件输出。
- **`ParameterType`**：STRING / NUMBER / BOOLEAN / ENUM / ANY。

`BaseModule` 提供了大量默认实现：`createSteps()`（用默认参数创建步骤）、`onStepDeleted()`、`validate()`、`getDynamicInputs()` / `getDynamicOutputs()`、`onParameterUpdated()`。多数简单模块只需覆写 `execute()`。

### 5.2 成块模块

If、Loop、Menu 等需要「开始/中间/结束」多个步骤，由 `BaseBlockModule` 和 `BlockBehavior` 支持：

- `BlockType`: NONE / BLOCK_START / BLOCK_MIDDLE / BLOCK_END
- `BlockBehavior(type, pairingId, isIndividuallyDeletable)`

`pairingId` 把同一块的多条步骤关联起来。执行引擎遇到禁用的块起始步骤时，会用 `BlockNavigator.findEndBlockPosition()` 直接跳到块末尾，从而整块跳过。

### 5.3 模块注册表

`ModuleRegistry` 是单例 object：

- `register(module, context)`：注册并注入 `ApplicationContext`。
- `initialize(context)`：清空后集中注册所有内建模块。
- `getModulesByCategory()`：过滤掉 BLOCK_END/BLOCK_MIDDLE（它们不作为独立可添加项展示），再按 `ModuleCategories` 排序分组。
- `reset()`：测试/重载用。

模块按包分类放置在 `core/workflow/module/` 下：

```
triggers/     23 种触发器及其 Handler
interaction/  查找、点击、输入、截图、OCR、AI Agent
logic/        if/else、循环、菜单、跳转、子工作流
data/         变量、文本、数学、编码、加密、JSON/XML
file/         图片导入/保存/缩放/旋转/蒙版
network/      HTTP、推送、AI、飞书
notification/ 通知相关
system/       Toast、应用启动、音量、亮度、短信、TTS 等
integration/  FlClash、支付宝/微信/ColorOS 快捷方式等
core/         Core Beta（Shell、剪贴板、屏幕、uinput 等）
shizuku/      Shizuku Shell 命令
snippet/      可复用片段
scripted/     Lua / JavaScript
ui/           UI 组件块（Activity/FloatWindow/控件）
```

### 5.4 主要模块清单（按类别）

- **触发器（23）**：Manual、ReceiveShare、AppStart、AppSwitch、AppPackage、Clipboard、KeyEvent、BackTap、Time、Interval、Battery、Power、Screen、Wifi、Bluetooth、Sms、Call、Notification、Element、GKD、Location、Pose、Voice。
- **交互**：FindText、FindElement、UiSelector、Click、ScreenOperation、SendKeyEvent、InputText、CaptureScreen、OCR、Agent、AutoGLM、FindTextUntil、FindImage、GetCurrentActivity。
- **逻辑**：If/Else/EndIf、ChooseFromMenu/MenuItem/EndMenu、ChooseFromList、Loop/EndLoop、ForEach/EndForEach、Jump、While/EndWhile、DoWhile/EndDoWhile、BreakLoop、ContinueLoop、StopWorkflow、CallWorkflow、StopAndReturn。
- **数据**：Create/Load/Get/Modify/Random 变量、GetCurrentTime、Calculation、MathExpression、TextProcessing/Split/Replace/Extract、Base64、Hash、URL、AES/DES/RC4/SM4、ParseJson/ParseXml、Comment、FileOperation。
- **文件**：ImportImage、SaveImage、AdjustImage、ScaleImage、RotateImage、ApplyMask。
- **网络**：GetIpAddress、HttpRequest、Bark/Discord/Webhook/Telegram 推送、AIModule、飞书发消息/历史/媒体上传。
- **系统**：Delay、Input、SpeechToText、QuickView、Toast、Lua、Js、FindInstalledApp、LaunchApp、LaunchShortcut、CloseApp、Clipboard、Share、SendNotification、Wifi、Bluetooth、Brightness、ScreenRotation、MobileData、GetScreenState、WakeScreen、WakeAndUnlockScreen、SleepScreen、ReadSms、Find/RemoveNotification、GetAppUsageStats、Invoke、SystemInfo、LocationRangeCheck、PlayAudio、TextToSpeech、CallPhone、DarkMode、DoNotDisturb、Vibration、Flashlight、GetBatteryStatus。
- **Core Beta**：蓝牙/WiFi/热点/NFC 控制与状态、剪贴板、唤醒/息屏/屏幕状态/截屏、屏幕操作、uinput 屏幕操作、输入文本、按键、触摸回放、强制停止应用、Shell 命令、音量控制与状态。
- **UI 组件**：CreateActivity/ShowActivity/EndActivity、CreateFloatWindow/ShowFloatWindow/EndFloatWindow、UiText/UiInput/UiButton/UiSwitch/UiListPicker/ScreenFlash、OnUiEvent/EndOnUiEvent/UpdateUiComponent/GetComponentValue/ExitActivity。

### 5.5 自定义参数编辑器

通用参数表单由 `InputDefinition` 自动生成（文本、数字、开关、下拉、滑块、文件/应用/地区选择器）。复杂模块实现 `ModuleUIProvider`：

- `createPreview()`：返回参数预览 View。
- `createEditor()`：返回自定义编辑 View。
- `readFromEditor()`：从 View 读取参数。
- `getHandledInputIds()` / `hasCustomEditor()`。

例如 FindText/FindElement 的屏幕区域选择、条件分支的嵌套编辑器、UI 组件属性面板都使用自定义编辑器。

---

## 6. 工作流数据模型与存储

### 6.1 模型

`core/workflow/model/Workflow.kt`：

```kotlin
data class Workflow(
    val id: String,
    val name: String,
    val triggers: List<ActionStep>,
    val steps: List<ActionStep>,
    val isEnabled: Boolean,
    val isFavorite: Boolean,
    val folderId: String?,
    val order: Int,
    val shortcutName: String?,
    val shortcutIconRes: Int?,
    val cardIconRes: Int?,
    val cardThemeColor: Int?,
    val modifiedAt: Long,
    val version: Int,
    val vFlowLevel: Int,
    val description: String?,
    val author: String?,
    val homepage: String?,
    val tags: List<String>,
    val maxExecutionTime: Int,
    val reentryBehavior: WorkflowReentryBehavior
) {
    val allSteps get() = triggers + steps
}
```

`ActionStep`：

```kotlin
data class ActionStep(
    val moduleId: String,
    val parameters: Map<String, Any?>,
    val isDisabled: Boolean = false,
    val indentationLevel: Int = 0,
    val id: String = UUID.randomUUID().toString()
)
```

`ActionStepExecutionSettings` 保存每步的错误策略：`policy`（STOP/RETRY/SKIP）、`retryCount`、`retryIntervalMillis`。

### 6.2 存储

`WorkflowManager` 使用 SharedPreferences：

- 文件名：`vflow_workflows`
- Key：`workflow_list`
- 值：整个工作流数组的 JSON 字符串（Gson，注册了 `VObjectGsonAdapter`）

常用方法：`saveWorkflow`、`getAllWorkflows`、`deleteWorkflow`、`duplicateWorkflow`、`clearAll`、`saveAll`、`findShareableWorkflows`、`findAppStartTriggerWorkflows`、`findKeyEventTriggerWorkflows`。

保存时会先经过 `WorkflowNormalizer` 规范化，并通过 `TriggerServiceProxy` 通知触发器服务重载。读取时逐条 try/catch，坏数据不会导致整个列表打不开。

`StorageManager` 管理外部存储根目录 `/sdcard/vFlow`，子目录包括 `scripts/`、`logs/`、`temp/`、`modules/`、`backups/`。执行时每个实例使用独立临时目录 `temp/exec_<instanceId>`，结束后清理；启动时还会清理 24 小时前的旧临时文件。

其他辅助类：

- `FolderManager`：文件夹。
- `GlobalVariableStore`：全局变量（跨工作流持久化）。
- `WorkflowJsonImportParser` / `WorkflowNormalizer` / `WorkflowEnumMigration`：导入、字段兼容、枚举迁移。
- `WorkflowJumpReferenceUpdater`：删除/移动步骤后修正 Jump 目标。
- `WorkflowPermissionRecovery`：权限被收回后恢复工作流启用状态。
- `WorkflowVisuals`：卡片配色/图标。
- `TileManager`：快捷设置磁贴。

---

## 7. 执行引擎

核心文件：`core/execution/WorkflowExecutor.kt`（约 829 行）。

### 7.1 入口与生命周期

`WorkflowExecutor` 是单例 object，内部协程作用域为 `Dispatchers.Default + SupervisorJob()`。

```kotlin
fun execute(
    workflow: Workflow,
    context: Context,
    triggerData: VObject?,
    triggerStepId: String?
): String
```

执行流程：

1. 生成 `executionInstanceId`，记录到 `runningWorkflows`。
2. 启动协程，广播 `ExecutionState.Running(stepIndex = -1)`。
3. 发送「正在运行」前台通知，获取 `WakeLock`，防止息屏休眠。
4. 创建独立工作目录 `StorageManager.tempDir/exec_<instanceId>`。
5. 构建 `ExecutionServices`（无障碍服务、`ExecutionUIService` 等）和初始 `ExecutionContext`。
6. 若工作流设置了 `maxExecutionTime`（秒），用 `withTimeout` 包裹执行；超时后 Toast + 取消通知。
7. 在 `finally` 中清理临时目录、释放 WakeLock、广播 Finished/Cancelled/Failure，并延迟 3 秒取消通知。

重入策略 `WorkflowReentryBehavior`：

- `BLOCK_NEW`（默认）：同一工作流已在运行则忽略新触发。
- `STOP_CURRENT_AND_RUN_NEW`：停止旧实例，启动新实例。
- `ALLOW_PARALLEL`：允许多个实例并行。

### 7.2 解释循环

真正执行步骤的是 `executeWorkflowInternal`。它维护一个程序计数器 `pc`：

```kotlin
while (pc < workflow.steps.size) {
    val step = workflow.steps[pc]
    val module = ModuleRegistry.getModule(step.moduleId)
        ?: ExternalModuleManager.loadModules(force = true)
            .let { ModuleRegistry.getModule(step.moduleId) }
        ?: return failure(...)

    // 1. 处理禁用步骤（成块模块整块跳过）
    // 2. 注入循环变量
    // 3. 广播 Running(stepIndex = pc)
    // 4. 复制 ExecutionContext，解析参数
    // 5. module.execute(...)
    // 6. 根据 ExecutionResult 更新 pc
}
```

关键细节：

- **模块找不到**：先强制重新加载外部模块，再重试一次。
- **禁用步骤**：若步骤是 `BLOCK_START`/`BLOCK_MIDDLE` 且带 `pairingId`，用 `BlockNavigator.findEndBlockPosition` 跳到配对块末尾，实现整块跳过。
- **循环变量**：`loopStack` 非空时，找到当前循环的起始步骤；`Loop` 注入 `loop_index`/`loop_total`，`ForEach` 注入 `index`/`item` 到 `stepOutputs`。
- **参数解析**：每步 copy 一份 `ExecutionContext`，把 `step.parameters` 转成 VObject 放进 `variables`，再用 `VariableResolver` 解析 `{{...}}` 和 `[[...]]`；解析结果放进 `magicVariables`。解析失败直接生成 `Failure("参数解析失败")`。
- **上下文隔离**：每步使用副本，步骤之间只能通过 `stepOutputs`、命名变量、全局变量通信。

### 7.3 控制流信号

模块返回 `ExecutionResult`：

```kotlin
sealed class ExecutionResult {
    data class Success(val outputs: Map<String, Any?>): ExecutionResult()
    data class Failure(
        val errorTitle: String,
        val errorMessage: String,
        val partialOutputs: Map<String, Any?> = emptyMap()
    ): ExecutionResult()
    data class Signal(val signal: ExecutionSignal): ExecutionResult()
}
```

`ExecutionSignal` 及解释器处理：

| 信号 | 行为 |
|---|---|
| `Jump(pc)` | 直接跳转到指定步骤 |
| `Loop(START)` | 压入循环栈，`pc++` |
| `Loop(END)` | 未达次数则跳回循环起始 `+1`，否则弹栈并 `pc++` |
| `Break` | 跳到配对 `End` 块之后 |
| `Continue` | 跳到配对 `End` 块 |
| `Stop` | `pc = steps.size`，结束工作流 |
| `Return(result)` | 保存返回值，结束当前（子）工作流 |

### 7.4 错误处理

每步的错误策略来自 `ActionStepExecutionSettings`：

- **STOP（默认）**：弹出错误 UI、广播 `ExecutionState.Failure`、结束工作流。
- **RETRY**：按 `retryCount` 和 `retryIntervalMillis` 重试。
- **SKIP**：使用 `partialOutputs`；没有则给每个输出填 `VNull`，并写入 `error` / `success=false`，继续下一步。

### 7.5 子工作流

`executeSubWorkflow`：

- copy 父 `ExecutionContext`；
- 与父工作流共享 `namedVariables`；
- 将子工作流压入 `workflowStack`，检测递归调用；
- 用 `seedTriggerOutputs` 注入调用参数；
- 执行完成后返回 `SubWorkflowResult(returnValue, namedVariables)`。

### 7.6 执行上下文

`ExecutionContext` 是一个数据类，主要字段：

```kotlin
data class ExecutionContext(
    val applicationContext: Context,
    val variables: Map<String, VObject>,
    val magicVariables: Map<String, VObject>,
    val services: ExecutionServices,
    val allSteps: List<ActionStep>,
    val currentStepIndex: Int,
    val stepOutputs: Map<String, Map<String, VObject>>,
    val loopStack: List<LoopFrame>,
    val triggerData: VObject?,
    val namedVariables: MutableMap<String, VObject>,
    val workflowStack: List<String>,
    val workDir: File
)
```

变量查找优先级：`magicVariables` > `variables` > `namedVariables` > `VNull`。

提供 `getVariable`、`setVariable`、`setMagicVariable`、`getGlobalVariable`/`setGlobalVariable`/`removeGlobalVariable`、`getVariableAsRaw/Number/Boolean` 等便捷方法。

### 7.7 状态广播与前台通知

- `ExecutionStateBus`：向 UI 广播 Running/Finished/Cancelled/Failure 及当前步骤索引。
- `ExecutionNotificationManager`：前台通知显示 `步骤 x/y: 模块名`。
- `WorkflowExecutionWakeLockManager`：执行期间持有 CPU 唤醒锁。

---

## 8. 触发器与后台服务

### 8.1 TriggerService

`TriggerService` 是长期运行的前台 Service，负责：

- 初始化 `DebugLogger`、`ModuleRegistry`、`ModuleManager`（用户 Lua 模块）、`ExternalModuleManager`、`TriggerHandlerRegistry`、`ExecutionNotificationManager`、`LogManager`、`ExecutionLogger`。
- `registerAndStartHandlers()` 注册所有触发器 Handler。
- `loadAllActiveTriggers()` 读取已启用工作流并建立监听。
- `WorkflowPermissionRecovery` 恢复因权限丢失而停用的工作流。
- `startCoreWatcher()` 监控 vFlow Core 状态。
- 根据设置自动启用无障碍、强制保活（延迟 10 秒，依赖 Shizuku/Root）。

支持的 Intent Action：

- `ACTION_WORKFLOW_CHANGED` / `ACTION_WORKFLOW_REMOVED`：携带 `WorkflowTriggerDelta`，增量更新监听。
- `ACTION_RELOAD_TRIGGERS`：全量重载。
- `ACTION_UPDATE_NOTIFICATION`：更新常驻通知。
- 按键事件直接分发给 KeyEvent Handler。

### 8.2 Handler 体系

`TriggerHandlerRegistry` + `ITriggerHandler`，每个触发器模块对应一个 Handler：

- `AlarmTriggerScheduler`（Time/Interval）
- `AppStartTriggerHandler`、`AppSwitchTriggerHandler`、`AppPackageTriggerHandler`
- `BackTapTriggerHandler`
- `BatteryTriggerHandler`、`PowerTriggerHandler`、`ScreenTriggerHandler`
- `BluetoothTriggerHandler`、`WifiTriggerHandler`
- `CallTriggerHandler`、`SmsTriggerHandler`
- `ClipboardTriggerHandler`
- `ElementTriggerHandler`、`GKDTriggerHandler`
- `KeyEventTriggerHandler`
- `LocationTriggerHandler`
- `NotificationTriggerHandler`
- `PoseTriggerHandler`
- `VoiceTriggerHandler`

配套类：`TriggerExecutionCoordinator`（去重/合并触发）、`TriggerServiceProxy`（跨进程/组件调用 TriggerService）、`TimeTriggerReceiver`、`BootReceiver`、`VoiceTriggerService`。

`TriggerSpec(workflow, triggerStep)` 描述「哪个工作流的哪个触发器」，`Workflow.hasTriggerType` / `triggerStepsByType` 用于查询。

---

## 9. 无障碍与 UI 自动化

- `AccessibilityService`：提供节点树查询、点击、滑动、文本输入、按键注入等基础能力。
- `UiInspectorService`：检查当前界面结构，供编辑器和调试使用。
- 交互模块（FindText/FindElement/UiSelector/Click/InputText 等）通过 `ExecutionServices` 拿到无障碍服务实例后执行。
- 屏幕截图：`CaptureScreen` 优先使用无障碍截屏，必要时走 Core 的 `IScreenshot`。
- 文字识别：ML Kit 中英文 OCR（`ocr/`），以及 ONNX Runtime + JNI 的 PP-OCRv5（`app/src/main/cpp`）。
- 图像匹配：OpenCV `OpenCVImageMatcher` / `OpenCVManager`，支持模板匹配、找图点击。
- 坐标操作：`ScreenOperation`、`Click` 支持坐标/区域；Core 提供 uinput 触摸回放。

---

## 10. vFlow Core（特权服务）

### 10.1 定位

Android 普通 App 无法直接调用很多系统服务（如截屏、注入按键、改亮度、控制热点）。vFlow Core 通过 `app_process` 启动一个 Java 进程，以 Shell 或 root 身份运行，再由 App 通过本地 Socket 调用。

### 10.2 构建方式

`:core` 模块构建流程（`core/build.gradle.kts` 中的 `BuildDexTask`）：

1. Kotlin JVM 编译 `core`。
2. 打包含依赖的 jar，`Main-Class` 为 `com.chaomixian.vflow.server.VFlowCore`。
3. 用 R8 8.13.19 的 `com.android.tools.r8.D8`，以 `android-36/android.jar` 为 lib，把 jar 转成 `classes.dex`。
4. 复制到 `app/src/main/assets/vFlowCore.dex`，版本写入 `app/src/main/assets/vFlowCore.version`。
5. `:app` 的 `preBuild` 和所有 `merge*Assets` 任务依赖 `:core:buildDex`，保证打包时 dex 是最新的。

当前 Core 版本：`vflowCoreVersion = 19`。

### 10.3 进程架构

`VFlowCore.kt` 的 `main` 解析参数：

```
--worker --type shell|root
--shell-launcher
--app-package <pkg>
--ipc-transport tcp|unix
--unix-socket-name <name>
```

Master/Worker 结构：

- **Master**：监听 19999（TCP，`0.0.0.0`）或 Unix `LocalServerSocket`；按 `ROUTING_TABLE` 把请求转发给 ShellWorker（20001）或 RootWorker（20002）。
- **Master 自身**根据是否有 root 权限决定启动哪些 Worker；支持 `shell_launcher`（`vflow_shell_exec`）提权。
- **ShellWorker / RootWorker**：各自加载一组 `ServiceWrapper`，通过反射/隐藏 API 调用系统服务。

路由表（`Config.ROUTING_TABLE`）：

- **SHELL**：clipboard、input、audio、wifi、bluetooth_manager、nfc、power、activity、connectivity、location、alarm、activity_task、screenshot
- **ROOT**：hotspot、uinput、system_root
- **system**：由 Master 动态路由

Unix socket 名称：`vflow_<pkg>_worker_shell` / `vflow_<pkg>_worker_root`。

### 10.4 Wrapper

- 基础：`BaseWorker`、`ShellWorker`、`RootWorker`、`ServiceWrapper`、`StreamingWrapper`。
- Shell 包装：`IActivityManager`、`IActivityTaskManager`、`IAlarmManager`、`IAudioManager`、`IBluetoothManager`、`IClipboard`、`IConnectivityManager`、`IInputManager`、`ILocationManager`、`INfcAdapter`、`IPowerManager`、`IScreenshot`、`IWifiManager`、`IWindowManager`。
- Root 包装：`IHotspotManager`、`Uinput`。

协议：请求 JSON `{target, method, params}`；响应 `{success, text}` 或 `{success:false, error}`。

### 10.5 App 侧

- `CoreLauncher`：把 dex 部署到公共目录（如 `/sdcard/vFlow/temp`），拼 `app_process` 命令，通过 `ShellManager`（Shizuku/Root/Shell）启动，轮询 ping 验证。
- `CoreManager`：Core 生命周期管理。
- `CoreManagementService`：前台服务，防止 Core 被系统回收。
- `VFlowCoreBridge`（约 1120 行）：单例客户端。
  - 连接 `127.0.0.1:19999` 或 LocalSocket（`<pkg with _>_vflow_core`）。
  - 心跳、断线重连。
  - `PrivilegeMode`：NONE / SHELL / ROOT。
  - `ExecMode`：SHELL / ROOT / AUTO。
  - 版本比较（打包版本 vs 运行版本），自动重启旧 Core。
  - 提供 `shellExec`、剪贴板监听流等 API。

> 注意：Core 的 Master 绑定 `0.0.0.0`，设计上用于远程调试；生产环境应限制访问。

---

## 11. 脚本、扩展与外部集成

### 11.1 脚本

- **JavaScript**：Rhino 1.9.0，`JsExecutor` + `JsValueConverter`；`JsModule` 把脚本包装成动作。
- **Lua**：LuaJ 3.0.1，`LuaExecutor` + `LuaValueConverter`；`LuaModule` 提供 Lua 动作。
- **内联脚本**：`InlineScriptEvaluator` 允许在任意参数中嵌入脚本表达式。
- **用户 Lua 模块**：存放于 `/sdcard/vFlow/modules/<id>/`，包含 `manifest.json` 与 `script.lua`；支持 ZIP 安装，并对 Zip Slip 做了防护。

### 11.2 外部 APK 模块

`ExternalModuleManager` 扫描已安装 APK 中声明了以下 Service 的应用：

```
com.chaomixian.vflow.extension.MODULE_PROVIDER
```

通过 AIDL `IVFlowExtensionProvider` 连接，读取模块清单（`moduleId`、名称、参数定义等），封装为 `ExternalActionModule` 注册进 `ModuleRegistry`。会校验 `API_VERSION`，不兼容则拒绝加载。

### 11.3 AI

- `AIModule`：通用大模型调用。
- `AutoGLM` / `AgentModule`：面向 UI 自动化的 Agent。
- `ui/chat/`：Chat UI（`ChatCompletionClient`、`ChatViewModel`、`ChatAgentToolRegistry` 等）。
- `WorkflowAiGenerator`：根据自然语言生成工作流。
- OCR/图像：ML Kit、OpenCV、ONNX Runtime（PP-OCRv5）。

### 11.4 网络与推送

- `HttpRequest`：OkHttp。
- `Bark`、`Discord`、`Webhook`、`Telegram` 推送。
- 飞书：发消息、历史、媒体上传。
- `api/`：NanoHTTPD WebServer/WebSocket，供外部触发或调试。

---

## 12. 权限体系

`permissions/Permission.kt`：

```kotlin
data class Permission(
    val id: String,
    val name: String,
    val description: String,
    val type: Type, // RUNTIME / SPECIAL
    val runtimePermissions: List<String>,
    val nameStringRes: Int,
    val descriptionStringRes: Int
)
```

`PermissionManager`（约 780 行）定义了所有权限项，包括：

- `CORE` / `CORE_ROOT`
- `ACCESSIBILITY`
- `NOTIFICATIONS`
- `MICROPHONE`
- `OVERLAY`
- `NOTIFICATION_LISTENER_SERVICE`
- `NOTIFICATION_POLICY`
- `STORAGE`（`MANAGE_EXTERNAL_STORAGE`）
- `SHIZUKU`
- `ROOT`
- `LOCATION`
- `SMS`
- `CALL`
- `CAMERA`
- `CONTACTS`

工作方式：

1. 每个模块通过 `requiredPermissions` 声明所需权限。
2. 启用工作流前，`PermissionManager` 汇总所有步骤的权限需求。
3. 运行时权限用 Activity Result 请求；特殊权限跳转系统设置页。
4. 检测 Shizuku/Root 可用性，决定 Core 的运行模式。
5. 权限丢失时 `WorkflowPermissionRecovery` 记录并恢复工作流启用状态。

Manifest 中声明的主要权限：精确闹钟、开机自启、前台服务（specialUse/microphone）、忽略电池优化、WakeLock、悬浮窗、通知、通知策略、存储、录音、读图片、网络、电话状态、振动、拨号、通知监听、蓝牙、WiFi、定位（含后台）、`WRITE_SETTINGS`/`WRITE_SECURE_SETTINGS`、Shizuku、短信、使用情况统计等。

---

## 13. UI 层

UI 是 Compose 与传统 View 的混合架构：

- **Compose**：主界面（`MainActivity` + Compose shell）、主页、工作流列表、设置、Chat、引导页、部分弹窗。
- **传统 Activity/View**：工作流编辑器（`WorkflowEditorActivity` + RecyclerView + ViewBinding + `ActionEditorSheet`），因为拖拽、缩进、复杂表单更适合现有 View 体系。
- **Material3 + 动态取色 + Navigation3 + Liquid Glass 底栏**。
- **魔法变量药丸**：`PillRenderer` 把 `{{...}}` 渲染成可点击的胶囊。
- **参数树编辑器**：支持嵌套字典/列表。
- **内联脚本编辑器**：Sora 编辑器。
- **模块选择器**：按分类浏览、搜索、最近使用。
- **导入/导出**：JSON 文件、分享。
- **快捷方式/磁贴/小组件**：`ShortcutExecutorActivity`、`TileService`、Widget。
- **悬浮 UI**：`OverlayUIActivity`（透明、锁屏显示、亮屏）+ `ExecutionUIService`，供 UI 组件块使用。

主要 Activity：

- `MainActivity`（launcher，singleTop，含 Umeng deep link）
- `WorkflowEditorActivity`
- `PoseTriggerRecorderActivity` / `VoiceTriggerRecorderActivity`
- `GlobalVariableConfigActivity`
- `ConsentUpdateActivity`
- `PermissionActivity`
- `OverlayUIActivity`
- `ShortcutExecutorActivity`（exported，`EXECUTE_WORKFLOW_SHORTCUT`）
- `ShareReceiverActivity`（exported，接收 `SEND`/`VIEW`）
- `OnboardingActivity`
- `DynamicUiActivity`
- `ChatBenchmarkHostActivity`

---

## 14. 一次执行的完整链路

以「通知触发 → 找控件 → 点击 → 输入文本」为例：

1. `VFlowNotificationListenerService` 收到通知，交给 `NotificationTriggerHandler`。
2. Handler 从 `TriggerService` 的注册表找到匹配的工作流，构造 `triggerData`（VNotification）。
3. 调用 `TriggerExecutionCoordinator` → `WorkflowExecutor.execute()`。
4. 执行引擎广播 Running，创建 `ExecutionContext`，进入 `pc` 循环。
5. `FindElement` 模块通过 `ExecutionServices.accessibilityService` 查询节点树，返回 `VScreenElement` 到 `stepOutputs`。
6. `Click` 模块读取上一步输出（`{{find_element.element}}`），调用无障碍 `performAction(ACTION_CLICK)`。
7. `InputText` 模块解析参数模板，调用无障碍输入或 Core 的 `IInputManager` 注入。
8. 每步更新前台通知；结束后广播 Finished，释放 WakeLock，清理临时目录。

---

## 15. 构建与打包 APK

### 15.1 环境要求

- JDK 17（本项目使用 `C:\Users\Administrator\.jdks\jdk-17.0.2`）。
- Android SDK：platform 36、build-tools 36.0.0、NDK、CMake 3.22.1。
- Gradle：项目 wrapper 声明 9.2.1；本机构建使用已缓存的 Gradle 9.1.0（wrapper 下载 9.2.1 时网络失败）。
- 根目录 `local.properties` 指向 SDK：

```properties
sdk.dir=E\:\\Android\\sdk
```

### 15.2 签名配置

`app/build.gradle.kts` 的签名逻辑：

- 如果根目录存在 `vFlow.jks` 和 `signing.properties`，release 使用该签名。
- 否则 release 输出 unsigned APK。
- debug 优先复用 release 签名；没有 release 时使用 `debug.keystore`（alias `androiddebugkey`，密码 `android`）。

`signing.properties` 格式：

```properties
KEYSTORE_PASSWORD=...
KEYSTORE_ALIAS=vflow-release
KEY_PASSWORD=...
```

本次构建已在项目根目录生成 `vFlow.jks`（alias `vflow-release`，RSA 4096，有效期 10000 天）和对应的 `signing.properties`，两者均被 `.gitignore` 忽略。

### 15.3 构建命令

```powershell
$env:JAVA_HOME="C:\Users\Administrator\.jdks\jdk-17.0.2"
$env:ANDROID_HOME="E:\Android\sdk"
$env:ANDROID_SDK_ROOT="E:\Android\sdk"

& "C:\Users\Administrator\.gradle\wrapper\dists\gradle-9.1.0-bin\9agqghryom9wkf8r80qlhnts3\gradle-9.1.0\bin\gradle.bat" `
  :app:assembleRelease --stacktrace --no-configuration-cache
```

如果依赖下载失败（Maven Central / Gradle Plugin Portal / Google Maven 在部分网络不可达），可在 `settings.gradle.kts` 的 `pluginManagement.repositories` 与 `dependencyResolutionManagement.repositories` 前临时加入阿里云镜像：

```kotlin
pluginManagement.repositories {
    maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
    maven { url = uri("https://maven.aliyun.com/repository/public") }
    maven { url = uri("https://maven.aliyun.com/repository/google") }
    // google(); mavenCentral(); gradlePluginPortal()
}

dependencyResolutionManagement.repositories {
    maven { url = uri("https://maven.aliyun.com/repository/google") }
    maven { url = uri("https://maven.aliyun.com/repository/public") }
    // google(); mavenCentral()
}
```

本次构建即通过该方式解决 `org.jetbrains.kotlin.plugin.serialization:2.3.20` 与 `com.android.tools:r8:8.13.19` 的解析失败；构建完成后镜像配置已还原，`settings.gradle.kts` 保持仓库原样。

### 15.4 构建产物

`app/build.gradle.kts` 开启了 ABI splits，并设置 `isUniversalApk = true`，因此 release 会生成：

```
app/build/outputs/apk/release/
├── app-armeabi-v7a-release.apk
├── app-arm64-v8a-release.apk
├── app-x86-release.apk
├── app-x86_64-release.apk
└── app-universal-release.apk   # 含全部 ABI，体积最大，通用
```

发布时通常选择：

- 真机用户：`app-arm64-v8a-release.apk`（体积小）。
- 兼容所有设备/模拟器：`app-universal-release.apk`。

### 15.5 本次实际构建结果

构建时间：2026-11-11 17:42；最近一次（首页 UI 录制、录制转工作流 MCP 工具，版本号 `1.5.3-pr18`）`BUILD SUCCESSFUL in 3m 51s`；Gradle 9.1.0 + JDK 17.0.2 + AGP 9.0.1。

发布包已整理到 `release/v1.5.3-pr18/`：

| 文件 | 大小 | SHA-256 |
|---|---:|---|
| `vFlow-1.5.3-pr18-universal.apk` | 394.57 MB | `CCFDCB03E35CD877CCA1212F397E1A2DC44479A1024F94560FE8B190B51E9E95` |
| `vFlow-1.5.3-pr18-arm64-v8a.apk` | 117.01 MB | `F9BE613DEBB902619512E178EE6281791EE1A7BA279D66AFBD045625643A62A8` |
| `vFlow-1.5.3-pr18-armeabi-v7a.apk` | 93.00 MB | `48673ABAF94496B6C12FC8F6974B3C2BCF5E34D299B29F3189762EE46F098F19` |
| `vFlow-1.5.3-pr18-x86.apk` | 147.39 MB | `E281CA0A08C2EB52DD09303ABA748669AD4ED27714830EF9BA23DE1D4F73627E` |
| `vFlow-1.5.3-pr18-x86_64.apk` | 160.87 MB | `ADE77B2D316B5463C2DB831D90697F24C3E338290A89361C59121EA64ED2E4CE` |

同目录另附 `SHA256SUMS.txt` 与 `build-info.txt`（构建时间、工具链版本与签名信息）。aapt2 确认 `versionCode=65` / `versionName=1.5.3-pr18`；apksigner 确认证书 SHA-256 与之前一致。

该 release 包已在真机 23049RAD8C 上安装验证：

- `/health` 返回版本 `1.5.3-pr18`、`bindAddress: "0.0.0.0"` 和 **144 个工具**（142 个模块工具 + `vflow_workflow_export` + `vflow_workflow_from_recording`）。
- 状态工具的 `max_ui_nodes` 默认值为 **0**，表示**不设上限**：返回当前活动窗口全部有效控件；只有显式传 `max_ui_nodes>0` 时才截断，最低按 20 处理。
- 节点保留有文字/描述/hint、可点击、可长按、可滚动、可输入、可勾选、已聚焦或带有效 viewId 的控件，包括无文字的可点击容器和列表容器；不再按父子包含关系删除节点，只合并完全相同 bounds。
- `include_screenshot=true` 仍按需返回截图；完整权限和工具详情需 `include_permission_details=true` + `include_tool_details=true`。
- 新增首页「录制UI结果」入口和录制管理页：可查看/删除录制，点击右下角「录制UI」显示使用说明后启动悬浮窗；悬浮窗支持开始、暂停、继续、结束，结束后可用默认名称或自定义名称保存。
- 录制服务每 300ms 采样当前 UI 和截图；UI 未变化时延长当前时间段，只有变化才新建时间段并异步保存 JPEG 截图，减少重复数据。录制文件存于 `/sdcard/vFlow/recordings/<id>.json`，截图存于同名目录 `seg_N.jpg`。
- 真机录制实测：5,195 ms、2 个时间段、3 个事件、2 张截图；默认名称 `UI录制_20261111_165233` 保存成功，并出现在录制管理列表。
- 新增 MCP 工具 `vflow_workflow_from_recording`：读取录制 JSON，优先按 CLICK/TEXT_CHANGED/SCROLL 事件还原为 `ui_selector` + `click`、`input_text`、`screen_operation` 步骤；选择器优先 GKD，并结合 viewId、class、text、desc、clickable、editable、bounds、depth；步骤保留完整 `recording_node` 和 `fallback_target`。
- 录制转工作流实测：工具耗时 **124 ms**，生成 2 步（`ui_selector` selector=`[vid='action_bar_container']` + `click` target=`{{selector.element}}`），工作流 manual trigger、maxExecutionTime=300，保存到 vFlow 工作流列表和 `/sdcard/vFlow/exports/`；没有任何悬浮窗控制步骤。
- 录制器和 builder 都会过滤 vFlow 自身悬浮窗的开始/暂停/继续/结束事件，避免把录制控件写进目标工作流。
- 修复“最大执行时长”Material Slider 上限 120、历史/导出工作流 `maxExecutionTime=300` 导致 `Slider value(300.0) ... valueTo(120.0)` 崩溃：XML 上限改为 600，加载时按实际值动态扩展并 clamp。
- 远程设备 `10.126.126.3:5555` 目前仍为 pr14；其 QQ 主界面此前实测 67 个节点 / 19.4 KB / 352 ms。

APK 体积较大，主要来自 ML Kit OCR、OpenCV、ONNX Runtime/PP-OCRv5 模型、Compose 及多架构原生库；universal 包包含全部 ABI，因此接近 400 MB。

### 15.6 签名验证

```powershell
$env:JAVA_HOME="C:\Users\Administrator\.jdks\jdk-17.0.2"
& "E:\Android\sdk\build-tools\36.0.0\apksigner.bat" verify --verbose --print-certs `
  release\v1.5.3-pr18\vFlow-1.5.3-pr18-universal.apk
```

本次结果：

```
Verifies
Verified using v1 scheme (JAR signing): false
Verified using v2 scheme (APK Signature Scheme v2): true
Verified using v3 scheme (APK Signature Scheme v3): false
Verified using v4 scheme (APK Signature Scheme v4): false
Number of signers: 1
Signer #1 certificate DN: CN=vFlow, OU=vFlow, O=vFlow, L=Unknown, ST=Unknown, C=CN
Signer #1 certificate SHA-256 digest: fd22db74235cd0d7f712794abc19d4b48a50e60e11186d20d450d1a219d50dc2
Signer #1 key algorithm: RSA
Signer #1 key size (bits): 4096
```

全部 5 个 APK 均通过 `apksigner verify`（v2 签名方案，minSdk 29 满足要求）。

### 15.7 发布注意事项

- `vFlow.jks` 与 `signing.properties` 不要提交到仓库；备份 keystore，否则无法覆盖安装升级。
- 若需要兼容旧版本签名（debug 签名升级到 release），使用 `scripts/signing/rotate_sign_apks.sh` 与 `signing/debug-to-release-lineage.bin`，配合 `apksigner` 生成 lineage 签名 APK。
- CI `.github/workflows/release.yml` 通过 secrets 恢复签名，执行 `:app:assembleDebug :app:assembleRelease`，再跑签名轮换脚本。
- release 开启了 R8 混淆与资源压缩，新增反射/序列化类时记得补充 `app/proguard-rules.pro`。

---

## 16. 设计要点与注意事项

1. **执行引擎是解释器，不是编译器**：工作流是 JSON 步骤数组，按 `pc` 顺序执行，控制流通过 `ExecutionSignal` 改变 `pc`。这让「跳转/循环/子流程」实现简单，也便于断点式 UI 展示。
2. **模块即插件**：新增动作只需继承 `BaseModule`/`BaseBlockModule`，在 `ModuleRegistry` 注册；参数表单由 `InputDefinition` 自动生成。
3. **VObject 统一类型**：避免 `Any?` 在模板、脚本、JSON、UI 之间转换时丢失类型；Gson 适配器保证持久化兼容。
4. **特权能力隔离在 Core**：App 进程不直接持有 root/Shell 能力，通过本地 Socket 调用，Core 崩溃不会拖垮 App。
5. **触发器常驻前台服务**：Android 后台限制下，触发器依赖 `TriggerService` 前台服务 + 精确闹钟 + 开机广播 + 电池优化白名单。
6. **权限是硬门槛**：无障碍、悬浮窗、通知监听、存储、Shizuku/Root 等权限缺失时，对应模块无法工作；启用工作流前会统一检查。
7. **文档与代码存在版本差**：`docs/` 下的旧架构文档写的是 Core 2.1 / 少量 wrapper，当前代码已是 Core 19、更多 wrapper 与模块；以代码和本文为准。

---

## 17. 参考文件

| 主题 | 文件 |
|---|---|
| 执行引擎 | `app/src/main/java/com/chaomixian/vflow/core/execution/WorkflowExecutor.kt` |
| 执行上下文 | `.../core/execution/ExecutionContext.kt` |
| 变量解析 | `.../core/execution/VariableResolver.kt` |
| 模块抽象 | `.../core/module/definitions.kt`、`ActionModule.kt`、`BaseModule.kt` |
| 模块注册 | `.../core/workflow/module/ModuleRegistry.kt` |
| 工作流模型 | `.../core/workflow/model/Workflow.kt`、`ActionStep.kt` |
| 工作流存储 | `.../core/workflow/WorkflowManager.kt` |
| 触发器服务 | `.../services/TriggerService.kt` |
| Core 客户端 | `.../services/VFlowCoreBridge.kt` |
| Core 服务端 | `core/src/main/kotlin/com/chaomixian/vflow/server/VFlowCore.kt` |
| 权限管理 | `.../permissions/PermissionManager.kt` |
| 应用构建 | `app/build.gradle.kts` |
| Core 构建 | `core/build.gradle.kts` |
| Manifest | `app/src/main/AndroidManifest.xml` |

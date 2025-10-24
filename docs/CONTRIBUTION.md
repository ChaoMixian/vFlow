### vFlow 模块开发指南

欢迎来到 vFlow 的世界！本指南将带你从零开始，一步步学习如何为 vFlow 创建一个全新的功能模块。vFlow 的核心魅力在于其高度的可扩展性，而你开发的每一个模块都将成为这个生态系统的一部分。

---

### 0\. 模块是什么？

在 vFlow 中，**模块 (Module)** 是自动化的最小功能单元。它封装了一个具体的操作，比如“延迟 1 秒”、“点击屏幕上的某个位置”或“判断一个条件是否成立”。用户在编辑器中看到的每一个可拖拽的卡片，背后都对应着一个模块。

一个模块的职责包括：

- **自我描述**: 告诉系统它的名字、图标、分类等信息。
- **定义参数**: 声明它需要哪些输入（Inputs）才能工作。
- **声明产出**: 声明它执行后会产生哪些输出（Outputs）。
- **执行核心逻辑**: 在工作流运行时，执行真正的自动化任务。
- **（可选）提供自定义 UI**: 为参数编辑提供比标准输入框更丰富的界面。

---

### 1\. 项目结构概览

理解项目结构是开始贡献的第一步。vFlow 项目主要分为以下几个核心目录：

- `main/java/com/chaomixian/vflow/`
  - `core/`: 项目的核心逻辑。
    - `execution/`: 工作流执行器 (`WorkflowExecutor`)、执行上下文 (`ExecutionContext`) 和 Lua 脚本执行器 (`LuaExecutor`)。
    - `logging/`: 日志管理器，包括面向用户的执行日志 (`LogManager`) 和开发者调试日志 (`DebugLogger`)。
    - `module/`: 模块系统的基础定义，如 `ActionModule` 接口、`BaseModule` 基类和各种数据类型 (`VariableTypes.kt`)。
    - `workflow/`: 工作流的核心管理 (`WorkflowManager`) 和模块的具体实现。
      - `module/`: 所有模块的源代码，按功能分类（`data`, `file`, `logic`, `system`, `triggers` 等）。
  - `services/`: 后台服务，如无障碍服务 (`AccessibilityService`)、触发器服务 (`TriggerService`) 和 Shizuku 服务 (`ShizukuUserService`, `ShizukuManager`)。
  - `ui/`: 应用的所有用户界面（Activity 和 Fragment），按功能划分。
    - `main/`: 主界面，包含底部导航和首页、设置等。
    - `workflow_editor/`: 工作流编辑器界面。
    - `workflow_list/`: 工作流列表界面。
  - `permissions/`: 权限管理相关的逻辑和界面 (`PermissionManager`, `PermissionActivity`)。

---

### 2\. 准备工作：理解核心概念

在开始编码前，我们先了解几个关键的类和接口：

- **`ActionModule.kt`**: 所有模块都必须实现的**核心接口**。它定义了模块的“契约”，规定了模块必须具备的所有能力。
- **`BaseModule.kt`**: 一个抽象基类，提供了 `ActionModule` 接口的**默认实现**。对于大多数简单的、独立的模块（如“延迟”、“显示 Toast”），直接继承它会非常方便。
- **`BaseBlockModule.kt`**: 专用于创建“积木块”类型模块的基类（如 `If...EndIf`, `Loop...EndLoop`）。它自动处理了创建和删除整个代码块的复杂逻辑。
- **`definitions.kt`**: 这个文件包含了所有重要的数据类，是你开发模块时一定会用到的：
  - `ActionMetadata`: 模块的元数据（名称、描述、图标、分类）。
  - `InputDefinition`: 定义一个输入参数（ID、名称、类型、默认值等）。
  - `OutputDefinition`: 定义一个输出参数（ID、名称、类型）。
  - `ExecutionContext`: 模块执行时获取所有上下文信息（如参数值、服务实例）的“上帝对象”。
- **`ModuleRegistry.kt`**: 模块注册表。你开发完的模块需要在这里“登记”，应用才能发现并使用它。

---

### 3\. 实战：创建一个“发送通知”模块

让我们通过一个具体的例子来学习。目标是创建一个新模块，它可以在系统通知栏发送一条指定内容的通知。

#### 第 1 步: 创建模块文件

在 `main/java/com/chaomixian/vflow/core/workflow/module/notification/` 目录下创建一个新的 Kotlin 文件，命名为 `SendNotificationModule.kt`。

#### 第 2 步: 继承 `BaseModule`

让我们的新类继承自 `BaseModule`，因为它是一个简单的独立模块。

```kotlin
// 文件: .../module/notification/SendNotificationModule.kt

package com.chaomixian.vflow.core.workflow.module.notification

import com.chaomixian.vflow.core.module.BaseModule
// ... 其他 imports

class SendNotificationModule : BaseModule() {
    // 模块代码将在这里填充
}
```

#### 第 3 步: 定义模块 ID 和元数据

- **`id`**: 模块的唯一标识符，必须全局唯一，格式为 `vflow.分类.名称`。
- **`metadata`**: 定义模块在 UI 上的表现。

<!-- end list -->

```kotlin
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.module.ActionMetadata

// ...
override val id = "vflow.notification.send_notification"
override val metadata = ActionMetadata(
    name = "发送通知",
    description = "在系统通知栏中创建一个自定义通知。",
    iconRes = R.drawable.rounded_notifications_unread_24, // 使用一个合适的图标
    category = "应用与系统" // 这会决定它在动作选择器中的分组
)
// ...
```

#### 第 4 步: 定义输入参数 (Inputs)

我们的模块需要用户提供通知的“标题”和“内容”。我们通过重写 `getInputs()` 方法来定义它们。

```kotlin
import com.chaomixian.vflow.core.module.InputDefinition
import com.chaomixian.vflow.core.module.ParameterType
import com.chaomixian.vflow.core.module.TextVariable

// ...
override fun getInputs(): List<InputDefinition> = listOf(
    InputDefinition(
        id = "title", // 参数的唯一ID
        name = "标题", // 显示在编辑器中的名称
        staticType = ParameterType.STRING, // 参数的基本类型
        defaultValue = "vFlow 通知", // 默认值
        acceptsMagicVariable = true, // 允许用户连接“魔法变量”
        acceptedMagicVariableTypes = setOf(TextVariable.TYPE_NAME) // 只接受文本类型的变量
    ),
    InputDefinition(
        id = "message",
        name = "内容",
        staticType = ParameterType.STRING,
        defaultValue = "这是一条来自 vFlow 的消息。",
        acceptsMagicVariable = true,
        acceptedMagicVariableTypes = setOf(TextVariable.TYPE_NAME)
    )
)
// ...
```

#### 第 5 步: 定义输出参数 (Outputs)

模块执行后可以产生结果，供后续模块使用。我们的通知模块可以输出一个“是否成功”的布尔值。

```kotlin
import com.chaomixian.vflow.core.module.OutputDefinition
import com.chaomixian.vflow.core.module.BooleanVariable

// ...
override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
    OutputDefinition(
        id = "success", // 输出值的唯一ID
        name = "是否成功", // 在魔法变量选择器中显示的名称
        typeName = BooleanVariable.TYPE_NAME // 输出值的数据类型
    )
)
// ...
```

> **注意**: `getOutputs` 方法可以接收一个 `step` 参数。这意味着你可以根据用户在编辑器里设置的参数，动态地决定模块有哪些输出。

#### 第 6 步: 定义摘要 (Summary)

摘要是显示在工作流卡片上的那段描述性文本，它能让用户一眼看出这个步骤是做什么的。我们使用 `PillUtil` 来创建带“药丸”效果的富文本。

```kotlin
import android.content.Context
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.ui.workflow_editor.PillUtil

// ...
override fun getSummary(context: Context, step: ActionStep): CharSequence {
    val inputs = getInputs()
    val titlePill = PillUtil.createPillFromParam(
        step.parameters["title"],
        inputs.find { it.id == "title" }
    )
    val messagePill = PillUtil.createPillFromParam(
        step.parameters["message"],
        inputs.find { it.id == "message" }
    )

    return PillUtil.buildSpannable(context, "发送通知: ", titlePill, " - ", messagePill)
}
// ...
```

#### 第 7 步: 实现核心执行逻辑 (`execute`)

这是模块最核心的部分。`execute` 是一个 `suspend` 函数，意味着你可以在其中执行耗时操作。

```kotlin
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.execution.ExecutionResult
import com.chaomixian.vflow.core.module.ProgressUpdate

// ...
override suspend fun execute(
    context: ExecutionContext,
    onProgress: suspend (ProgressUpdate) -> Unit
): ExecutionResult {
    // 1. 从 ExecutionContext 获取解析后的参数值
    // 如果用户连接了魔法变量，它会存在于 magicVariables 中，否则在 variables 中
    val title = (context.magicVariables["title"] as? TextVariable)?.value
        ?: context.variables["title"] as? String
        ?: "vFlow 通知"

    val message = (context.magicVariables["message"] as? TextVariable)?.value
        ?: context.variables["message"] as? String
        ?: ""

    // 2. 报告进度，这对于调试很有帮助
    onProgress(ProgressUpdate("准备发送通知: $title"))

    // 3. 执行核心逻辑
    try {
        val appContext = context.applicationContext
        val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "vflow_custom_notifications"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "自定义通知", NotificationManager.IMPORTANCE_DEFAULT)
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(appContext, channelId)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_workflows) // 使用一个已有的图标
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)

        // 4. 返回成功结果，并附带输出值
        return ExecutionResult.Success(outputs = mapOf("success" to BooleanVariable(true)))

    } catch (e: Exception) {
        // 5. 如果发生错误，返回失败结果
        return ExecutionResult.Failure("执行失败", e.localizedMessage ?: "未知错误")
    }
}
// ...
```

#### 第 8 步: 注册模块

最后一步，也是最关键的一步！打开 `ModuleRegistry.kt` 文件，在 `initialize()` 方法中，将你的新模块添加进去。

```kotlin
// 文件: .../core/module/ModuleRegistry.kt

import com.chaomixian.vflow.core.workflow.module.notification.SendNotificationModule // 导入你的新模块

// ...
object ModuleRegistry {
    // ...
    fun initialize() {
        modules.clear()
        // ... 其他模块

        // 应用与系统
        register(SendNotificationModule()) // 在这里注册！

        // ...
    }
}
```

**恭喜！** 你已经成功创建并集成了一个全新的模块。现在重新运行应用，你应该就能在“应用与系统”分类下找到并使用“发送通知”模块了。

---

### 4\. 进阶主题

#### 积木块模块 (Block Module)

对于需要包含其他模块的逻辑块（如 `If` 和 `Loop`），你应该继承 `BaseBlockModule`。

- **`stepIdsInBlock`**: 定义组成这个积木块的所有模块 ID 列表（开始、中间、结束）。
- **`pairingId`**: 定义一个唯一的配对 ID，用于将这些模块关联起来。

vFlow 会自动处理积木块的创建（一次性添加所有部分）和删除（一次性删除整个块）。

#### 动态输入 (`getDynamicInputs`)

`IfModule` 是一个很好的例子。它的输入参数会根据第一个“输入”连接的变量类型而改变，从而只显示适用的比较条件。如果你需要这种动态行为，可以重写 `getDynamicInputs` 方法。

#### 自定义 UI (`ModuleUIProvider`)

对于需要复杂编辑界面的模块（例如“HTTP 请求”模块中的字典编辑器），你可以实现 `ModuleUIProvider` 接口，并重写模块的 `uiProvider` 属性。这允许你完全控制参数的编辑界面，实现标准控件无法完成的功能。

/**
 * 这是一个模块开发的示例模板。
 * 开发者可以复制这个文件，并修改其中的内容来创建自己的模块。
 *
 * 功能：在屏幕上显示一个 Toast (气泡) 提示。
 */
package com.chaomixian.vflow.modules.device

import android.content.Context
import android.widget.Toast
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.modules.variable.BooleanVariable
import com.chaomixian.vflow.modules.variable.TextVariable
import com.chaomixian.vflow.permissions.PermissionManager
import com.chaomixian.vflow.ui.workflow_editor.PillUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 模块主类。
 * 建议继承 BaseModule，它为你处理了大部分通用逻辑（如创建、删除步骤），让你可以专注于核心功能。
 */
class ToastModule : BaseModule() {

    // --- 1. 模块基础信息 (必须实现) ---

    /**
     * 模块的唯一ID。这是模块的“身份证”，在整个应用中必须是唯一的。
     * 命名规则建议：`vflow.<分类>.<功能>`，例如 `vflow.device.toast`。
     */
    override val id = "vflow.other.toast"

    /**
     * 模块的元数据，定义了它在UI上的外观和信息。
     * - name: 显示在动作选择器和卡片标题中的名称。
     * - description: 在动作选择器中显示的详细描述。
     * - iconRes: 显示的图标资源ID (来自 R.drawable)。
     * - category: 模块所属的分类，用于在选择器中分组。
     */
    override val metadata = ActionMetadata(
        name = "显示Toast",
        description = "在屏幕底部弹出一个简短的提示消息。",
        iconRes = R.drawable.ic_workflows, // 你可以选择一个合适的图标
        category = "其他"
    )

    /**
     * 声明此模块运行时需要哪些权限。
     * 执行器在运行工作流前会检查这些权限，如果缺失会提示用户授权。
     */
    override val requiredPermissions = listOf(PermissionManager.NOTIFICATIONS)


    // --- 2. 参数定义 (按需实现) ---

    /**
     * 定义模块的输入参数。
     * 系统会根据这里的定义，在编辑界面为你自动生成输入UI。
     * 返回一个 InputDefinition 对象的列表。
     */
    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "message", // 参数的唯一ID，在 execute 中通过此ID获取值
            name = "消息内容", // 在编辑界面显示的参数名称
            staticType = ParameterType.STRING, // 如果不连接魔法变量，它是一个文本输入框
            defaultValue = "Hello, vFlow!", // 输入框的默认值
            acceptsMagicVariable = true, // 是否允许用户连接一个上游的“魔法变量”
            acceptedMagicVariableTypes = setOf(TextVariable.TYPE_NAME) // 允许连接哪些类型的变量 (使用唯一的类型名称字符串)
        )
    )

    /**
     * 定义模块的输出参数。
     * @param step (可选) 如果你的输出是根据用户的输入动态变化的，可以在这里使用 step.parameters 来判断。
     * 对于大部分模块，这个参数可以忽略。
     * 返回一个 OutputDefinition 对象的列表。
     */
    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition(
            id = "success", // 输出值的唯一ID
            name = "是否成功", // 在魔法变量选择器中显示的名称
            typeName = BooleanVariable.TYPE_NAME // 输出值的数据类型 (使用唯一的类型名称字符串)
        )
    )


    // --- 3. 编辑器UI (可选实现) ---

    /**
     * 定义在工作流编辑器卡片上显示的“摘要”。
     * 这可以让用户在不点开编辑的情况下，就能大致了解这个模块做了什么。
     * 你可以使用 PillUtil 来创建带样式的“药丸”文本，以区分静态值和变量。
     */
    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        // 从步骤的参数中获取用户填写的值
        val message = step.parameters["message"]?.toString() ?: "..."
        // 判断这个值是否是一个魔法变量引用
        val isVariable = message.startsWith("{{")
        // 根据是否是变量，决定药丸里显示的文本
        val pillText = if (isVariable) "变量" else "'$message'"

        // 使用 PillUtil 构建富文本，将普通文本和“药丸”拼接在一起
        return PillUtil.buildSpannable(
            context,
            "显示消息 ", // 普通文本
            PillUtil.Pill(pillText, isVariable, parameterId = "message") // 药丸
        )
    }

    /**
     * 验证用户输入的参数是否合法。
     * 如果验证不通过，用户将无法保存编辑。
     * 继承 BaseModule 后，默认返回“有效”，只有在需要时才重写此方法。
     */
    override fun validate(step: ActionStep): ValidationResult {
        val message = step.parameters["message"]?.toString()
        // 检查消息是否为空 (但要忽略魔法变量，因为变量的值在运行时才知道)
        if (message.isNullOrBlank() && !message.toString().startsWith("{{")) {
            return ValidationResult(isValid = false, errorMessage = "消息内容不能为空")
        }
        // 所有检查都通过，返回有效结果
        return ValidationResult(isValid = true)
    }


    // --- 4. 核心执行逻辑 (必须实现) ---

    /**
     * 这是模块最核心的部分，定义了模块运行时要执行的操作。
     * 这是一个 `suspend` 函数，意味着你可以在这里执行异步操作（如网络请求、延迟等）而不会阻塞线程。
     *
     * @param context 执行时的上下文，你可以从中获取：
     * - `context.variables`: 用户在编辑器里填写的静态参数值。
     * - `context.magicVariables`: 从上游模块连接过来的动态值（魔法变量）。
     * - `context.services`: 一个服务容器，可以从中获取无障碍服务等实例。
     * - `context.applicationContext`: 安卓应用的全局 Context。
     * @param onProgress 一个回调函数，用于向系统报告当前的执行进度，会显示在日志中。
     *
     * @return ExecutionResult 必须返回一个执行结果，可以是：
     * - ExecutionResult.Success: 表示成功，可以附带输出值。
     * - ExecutionResult.Failure: 表示失败，需要提供错误标题和信息。
     * - ExecutionResult.Signal: 表示需要改变执行流程（如跳转），由逻辑控制模块使用。
     */
    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        // 1. 获取输入参数
        // 优先从魔法变量获取，如果未连接，则从静态变量获取。
        // `as? TextVariable` 是一个安全的类型转换，如果魔法变量不是文本类型，会返回null。
        val message = (context.magicVariables["message"] as? TextVariable)?.value
            ?: context.variables["message"] as? String

        // 2. 检查参数有效性
        if (message.isNullOrBlank()) {
            // 返回一个详细的失败结果，这会在日志中显示，帮助用户排查问题。
            return ExecutionResult.Failure(
                errorTitle = "消息为空",
                errorMessage = "需要显示的消息内容为空，无法执行。"
            )
        }

        // 3. 报告进度
        onProgress(ProgressUpdate("准备显示Toast: $message"))

        // 4. 执行核心操作
        // Toast 需要在主线程上显示，我们使用 withContext(Dispatchers.Main) 来切换线程。
        withContext(Dispatchers.Main) {
            Toast.makeText(context.applicationContext, message, Toast.LENGTH_LONG).show()
        }

        // 模拟一个耗时操作，来展示 suspend 函数的能力
        kotlinx.coroutines.delay(1000)

        // 5. 返回成功结果，并附带输出值
        // 输出值是一个 Map，key 是输出参数的ID ("success")，value 是对应的变量对象。
        return ExecutionResult.Success(
            outputs = mapOf("success" to BooleanVariable(true))
        )
    }
}
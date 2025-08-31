/**
 * 这是一个模块开发的示例模板。
 * 开发者可以复制这个文件，并修改其中的内容来创建自己的模块。
 *
 * 功能：在屏幕上显示一个 Toast (气泡) 提示。
 */
package com.chaomixian.vflow.modules.device

import android.content.Context
import android.os.Parcelable
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
import kotlinx.parcelize.Parcelize

// 如果你的模块需要输出自定义类型的数据，你需要定义一个数据类，
// 并用 @Parcelize 注解标记它，同时实现 Parcelable 接口。
// 这使得数据可以在工作流的步骤之间安全地传递。
@Parcelize
data class ToastResultVariable(val isShown: Boolean) : Parcelable


/**
 * 模块主类。
 * 建议继承 BaseModule，这为你处理了大部分通用逻辑，让你可以专注于核心功能。
 */
class ToastModule : BaseModule() {

    // --- 1. 模块基础信息 (必须实现) ---

    /**
     * 模块的唯一ID。这是模块的“身份证”。
     * 命名规则建议：`公司/组织名.分类.模块名`，例如 `vflow.utils.toast`
     */
    override val id = "vflow.other.toast"

    /**
     * 模块的元数据，定义了它在UI上的外观和信息。
     * - name: 显示在动作选择器和卡片标题中的名称。
     * - description: 在动作选择器中显示的详细描述。
     * - iconRes: 显示的图标资源ID。
     * - category: 模块所属的分类，用于在选择器中分组。
     */
    override val metadata = ActionMetadata(
        name = "显示Toast",
        description = "在屏幕底部弹出一个简短的提示消息。",
        iconRes = R.drawable.ic_workflows, // 你可以选择一个合适的图标
        category = "其他"
    )

    // --- 新增权限声明 ---
    override val requiredPermissions = listOf(PermissionManager.NOTIFICATIONS)
    // --- 声明结束 ---


    // --- 2. 参数定义 (按需实现) ---

    /**
     * 定义模块的输入参数。
     * 系统会根据这里的定义，在编辑界面为你自动生成输入框。
     */
    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "message", // 参数的唯一ID，在 execute 中通过此ID获取值
            name = "消息内容", // 在编辑界面显示的参数名称
            staticType = ParameterType.STRING, // 如果不连接魔法变量，它是一个文本输入框
            defaultValue = "Hello, vFlow!", // 输入框的默认值
            acceptsMagicVariable = true, // 是否允许用户连接一个上游的“魔法变量”
            acceptedMagicVariableTypes = setOf(TextVariable::class.java) // 允许连接哪些类型的变量
        )
    )

    /**
     * 定义模块的输出参数。
     * @param step (可选) 如果你的输出是根据用户的输入动态变化的，可以在这里使用 step.parameters 来判断。
     * 对于大部分模块，这个参数可以忽略。
     */
    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition(
            id = "success", // 输出值的唯一ID
            name = "是否成功", // 在魔法变量选择器中显示的名称
            type = BooleanVariable::class.java // 输出值的数据类型
        )
    )


    // --- 3. 编辑器UI (可选实现) ---

    /**
     * 定义在工作流编辑器卡片上显示的“摘要”。
     * 这可以让用户在不点开编辑的情况下，就能大致了解这个模块做了什么。
     * 你可以使用 PillUtil 来创建带样式的“药丸”文本。
     */
    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        // 从步骤的参数中获取用户填写的值
        val message = step.parameters["message"]?.toString() ?: "..."
        val isVariable = message.startsWith("{{")
        val pillText = if (isVariable) "变量" else "'$message'"

        // 使用 PillUtil 构建富文本
        return PillUtil.buildSpannable(
            context,
            "显示消息 ",
            PillUtil.Pill(pillText, isVariable, parameterId = "message")
        )
    }

    /**
     * 验证用户输入的参数是否合法。
     * 如果验证不通过，用户将无法保存编辑。
     * 继承 BaseModule 后，默认返回“有效”，只有在需要时才重写。
     */
    override fun validate(step: ActionStep): ValidationResult {
        val message = step.parameters["message"]?.toString()
        // 检查消息是否为空 (但忽略魔法变量)
        if (message.isNullOrBlank() && !message.toString().startsWith("{{")) {
            return ValidationResult(isValid = false, errorMessage = "消息内容不能为空")
        }
        return ValidationResult(isValid = true)
    }


    // --- 4. 核心执行逻辑 (必须实现) ---

    /**
     * 这是模块最核心的部分，定义了模块运行时要执行的操作。
     * 这是一个 `suspend` 函数，意味着你可以在这里执行异步操作（如网络请求、延迟等）。
     *
     * @param context 执行时的上下文，你可以从中获取：
     * - `context.variables`: 用户在编辑器里填写的静态值。
     * - `context.magicVariables`: 从上游模块连接过来的动态值。
     * - `context.accessibilityService`: 无障碍服务实例，用于模拟点击、查找节点等。
     * @param onProgress 一个回调函数，用于向系统报告当前的执行进度。
     *
     * @return ExecutionResult.Success 或 ExecutionResult.Failure
     */
    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        // 1. 获取输入参数
        // 优先从魔法变量获取，如果未连接，则从静态变量获取。
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
        // Toast 需要一个 Context，我们从 ExecutionContext 中获取
        withContext(Dispatchers.Main) {
            Toast.makeText(context.applicationContext, message, Toast.LENGTH_LONG).show()
        }

        // 模拟一个耗时操作
        kotlinx.coroutines.delay(1000)

        // 5. 返回成功结果，并附带输出值
        return ExecutionResult.Success(
            outputs = mapOf("success" to BooleanVariable(true))
        )
    }

    /*
     * 以下是继承自 BaseModule 的一些方法，你通常不需要关心它们，
     * 但了解它们有助于理解模块的生命周期。

     * override val blockBehavior: BlockBehavior
     * // 定义模块是否是“积木块”的一部分。对于普通模块，保持默认的 BlockType.NONE 即可。

     * override val uiProvider: ModuleUIProvider?
     * // 如果你需要一个非常复杂的、完全自定义的编辑界面（比如“设置变量”模块），
     * // 你需要创建一个类实现 ModuleUIProvider 接口，并在这里返回它的实例。
     * // 对于大部分模块，返回 null 即可，系统会自动生成UI。

     * override fun createSteps(): List<ActionStep>
     * // 当用户添加此模块时，需要创建哪些步骤。BaseModule 已为你实现，
     * // 它会自动创建一个包含默认参数的步骤。只有“积木块”模块需要重写它。

     * override fun onStepDeleted(steps: MutableList<ActionStep>, position: Int): Boolean
     * // 当步骤被删除时调用。BaseModule 已为你实现默认的删除逻辑。
     * // 只有“积木块”模块需要重写它来删除整个块。
     */
}
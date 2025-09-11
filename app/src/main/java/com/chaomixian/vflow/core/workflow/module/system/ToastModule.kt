package com.chaomixian.vflow.core.workflow.module.system

import android.content.Context
import android.widget.Toast
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.model.ActionStep
// 确保从正确的包导入变量类型
import com.chaomixian.vflow.core.module.BooleanVariable
import com.chaomixian.vflow.core.module.TextVariable
import com.chaomixian.vflow.permissions.PermissionManager
import com.chaomixian.vflow.ui.workflow_editor.PillUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

// 文件：ToastModule.kt
// 描述：定义了在屏幕上显示一个简短提示消息（Toast）的模块。

/**
 * 显示Toast消息模块。
 * 用于在屏幕底部弹出一个简短的文本提示。
 */
class ToastModule : BaseModule() {

    // 模块的唯一ID
    override val id = "vflow.device.toast"
    // 模块的元数据，用于在UI中展示
    override val metadata = ActionMetadata(
        name = "显示Toast",
        description = "在屏幕底部弹出一个简短的提示消息。",
        iconRes = R.drawable.rounded_call_to_action_24, // 图标资源
        category = "应用与系统" // 更新分类
    )
    // 此模块可能需要的权限（如果应用的目标SDK版本较高，显示通知可能需要特定权限）
    override val requiredPermissions = listOf(PermissionManager.NOTIFICATIONS)

    /**
     * 定义模块的输入参数。
     */
    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "message",
            name = "消息内容", // 用户需要设置的Toast消息文本
            staticType = ParameterType.STRING, // 参数类型为字符串
            defaultValue = "Hello, vFlow!", // 默认消息内容
            acceptsMagicVariable = true, // 允许使用魔法变量指定消息内容
            acceptedMagicVariableTypes = setOf(TextVariable.TYPE_NAME) // 接受文本类型的魔法变量
        )
    )

    /**
     * 定义模块的输出参数。
     */
    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition(
            id = "success",
            name = "是否成功", // 表示Toast是否成功显示（在此实现中总是成功）
            typeName = BooleanVariable.TYPE_NAME
        )
    )

    /**
     * 生成在工作流编辑器中显示模块摘要的文本。
     * 例如：“显示消息 [Hello, vFlow!]”
     */
    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val messagePill = PillUtil.createPillFromParam(
            step.parameters["message"],
            getInputs().find { it.id == "message" }
        )
        return PillUtil.buildSpannable(context, "显示消息 ", messagePill)
    }

    /**
     * 验证模块参数的有效性。
     * 确保消息内容在不使用魔法变量时不能为空。
     */
    override fun validate(step: ActionStep): ValidationResult {
        val message = step.parameters["message"]?.toString()
        // 如果消息为空白，并且它不是一个魔法变量引用，则验证失败
        if (message.isNullOrBlank() && (message == null || !(message.isMagicVariable()))) {
            return ValidationResult(isValid = false, errorMessage = "消息内容不能为空")
        }
        return ValidationResult(isValid = true) // 默认有效
    }

    /**
     * 执行显示Toast的核心逻辑。
     */
    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        // 获取消息内容，优先从魔法变量，其次从静态变量
        val message = (context.magicVariables["message"] as? TextVariable)?.value
            ?: context.variables["message"] as? String

        // 检查消息内容是否为空白
        if (message.isNullOrBlank()) {
            return ExecutionResult.Failure(
                errorTitle = "消息为空",
                errorMessage = "需要显示的消息内容为空，无法执行。"
            )
        }

        onProgress(ProgressUpdate("准备显示Toast: $message"))

        // 切换到主线程显示Toast，因为UI操作必须在主线程执行
        withContext(Dispatchers.Main) {
            Toast.makeText(context.applicationContext, message, Toast.LENGTH_LONG).show()
        }

        // 返回成功结果
        return ExecutionResult.Success(
            outputs = mapOf("success" to BooleanVariable(true))
        )
    }
}
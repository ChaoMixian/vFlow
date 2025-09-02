// 文件: main/java/com/chaomixian/vflow/core/workflow/module/device/ToastModule.kt

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

class ToastModule : BaseModule() {

    override val id = "vflow.other.toast"
    override val metadata = ActionMetadata(
        name = "显示Toast",
        description = "在屏幕底部弹出一个简短的提示消息。",
        iconRes = R.drawable.ic_workflows,
        category = "其他"
    )
    override val requiredPermissions = listOf(PermissionManager.NOTIFICATIONS)

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "message",
            name = "消息内容",
            staticType = ParameterType.STRING,
            defaultValue = "Hello, vFlow!",
            acceptsMagicVariable = true,
            acceptedMagicVariableTypes = setOf(TextVariable.TYPE_NAME)
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition(
            id = "success",
            name = "是否成功",
            typeName = BooleanVariable.TYPE_NAME
        )
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val message = step.parameters["message"]?.toString() ?: "..."
        val isVariable = message.startsWith("{{")

        return PillUtil.buildSpannable(
            context,
            "显示消息 ",
            PillUtil.Pill(message, isVariable, parameterId = "message")
        )
    }

    override fun validate(step: ActionStep): ValidationResult {
        val message = step.parameters["message"]?.toString()
        if (message.isNullOrBlank() && (message == null || !message.startsWith("{{"))) {
            return ValidationResult(isValid = false, errorMessage = "消息内容不能为空")
        }
        return ValidationResult(isValid = true)
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val message = (context.magicVariables["message"] as? TextVariable)?.value
            ?: context.variables["message"] as? String

        if (message.isNullOrBlank()) {
            return ExecutionResult.Failure(
                errorTitle = "消息为空",
                errorMessage = "需要显示的消息内容为空，无法执行。"
            )
        }

        onProgress(ProgressUpdate("准备显示Toast: $message"))

        withContext(Dispatchers.Main) {
            Toast.makeText(context.applicationContext, message, Toast.LENGTH_LONG).show()
        }

        kotlinx.coroutines.delay(1000)

        return ExecutionResult.Success(
            outputs = mapOf("success" to BooleanVariable(true))
        )
    }
}
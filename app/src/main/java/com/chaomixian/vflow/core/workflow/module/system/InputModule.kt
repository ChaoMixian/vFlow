package com.chaomixian.vflow.core.workflow.module.system

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.permissions.PermissionManager
import com.chaomixian.vflow.services.ExecutionUIService
import com.chaomixian.vflow.ui.workflow_editor.PillUtil
import java.text.SimpleDateFormat
import java.util.*

// 文件：InputModule.kt
// 描述：定义了在工作流执行时请求用户输入的模块。

class InputModule : BaseModule() {

    override val id = "vflow.data.input"
    override val metadata = ActionMetadata(
        name = "请求输入",
        description = "弹出一个窗口，请求用户输入文本、数字、时间或日期。",
        iconRes = R.drawable.rounded_keyboard_external_input_24,
        category = "应用与系统" // 更新分类
    )

    // 新增：声明此模块需要悬浮窗权限
    override val requiredPermissions = listOf(PermissionManager.OVERLAY)

    private val typeOptions = listOf("文本", "数字", "时间", "日期")

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "inputType",
            name = "输入类型",
            staticType = ParameterType.ENUM,
            defaultValue = "文本",
            options = typeOptions,
            acceptsMagicVariable = false
        ),
        InputDefinition(
            id = "prompt",
            name = "提示信息",
            staticType = ParameterType.STRING,
            defaultValue = "请输入内容",
            acceptsMagicVariable = true,
            acceptedMagicVariableTypes = setOf(TextVariable.TYPE_NAME)
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> {
        val inputType = step?.parameters?.get("inputType") as? String ?: "文本"
        val outputTypeName = when (inputType) {
            "数字" -> NumberVariable.TYPE_NAME
            "时间" -> TimeVariable.TYPE_NAME
            "日期" -> DateVariable.TYPE_NAME
            else -> TextVariable.TYPE_NAME
        }
        return listOf(OutputDefinition("userInput", "用户输入 ($inputType)", outputTypeName))
    }

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val inputType = step.parameters["inputType"]?.toString() ?: "文本"
        val prompt = step.parameters["prompt"]?.toString() ?: "..."
        val isVariable = prompt.startsWith("{{") && prompt.endsWith("}}")

        return PillUtil.buildSpannable(
            context,
            "请求 ",
            PillUtil.Pill(inputType, false, parameterId = "inputType", isModuleOption = true),
            " 输入，提示信息为 ",
            PillUtil.Pill(prompt, isVariable, parameterId = "prompt")
        )
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val uiService = context.services.get(ExecutionUIService::class)
            ?: return ExecutionResult.Failure("服务缺失", "无法获取UI服务来请求用户输入。")

        val inputType = context.variables["inputType"] as? String ?: "文本"
        val prompt = (context.magicVariables["prompt"] as? TextVariable)?.value
            ?: context.variables["prompt"] as? String
            ?: "请输入"

        onProgress(ProgressUpdate("等待用户输入 ($inputType)..."))

        val userInput = uiService.requestInput(inputType, prompt)
            ?: return ExecutionResult.Failure("用户取消", "用户取消了输入操作。")

        val resultVariable = when (inputType) {
            "数字" -> NumberVariable((userInput as? Double) ?: 0.0)
            "时间" -> TimeVariable(userInput.toString())
            "日期" -> {
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                // MaterialDatePicker 返回的是UTC毫秒，需要设置时区以保证日期正确
                sdf.timeZone = TimeZone.getTimeZone("UTC")
                val dateString = sdf.format(Date(userInput as Long))
                DateVariable(dateString)
            }
            else -> TextVariable(userInput.toString())
        }

        onProgress(ProgressUpdate("获取到用户输入"))
        return ExecutionResult.Success(mapOf("userInput" to resultVariable))
    }
}
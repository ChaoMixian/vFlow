package com.chaomixian.vflow.core.workflow.module.system

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VNumber
import com.chaomixian.vflow.core.types.basic.VString
import com.chaomixian.vflow.core.types.complex.VDate
import com.chaomixian.vflow.core.types.complex.VTime
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
        nameStringRes = R.string.module_vflow_data_input_name,
        descriptionStringRes = R.string.module_vflow_data_input_desc,
        name = "请求输入",
        description = "弹出一个窗口，请求用户输入文本、数字、时间或日期。",
        iconRes = R.drawable.rounded_keyboard_external_input_24,
        category = "应用与系统"
    )

    // 声明此模块需要悬浮窗权限
    override val requiredPermissions = listOf(PermissionManager.OVERLAY)

    private val typeOptions by lazy {
        listOf(
            appContext.getString(R.string.option_vflow_data_input_type_text),
            appContext.getString(R.string.option_vflow_data_input_type_number),
            appContext.getString(R.string.option_vflow_data_input_type_time),
            appContext.getString(R.string.option_vflow_data_input_type_date)
        )
    }

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "inputType",
            nameStringRes = R.string.param_vflow_data_input_inputType_name,
            name = "输入类型",
            staticType = ParameterType.ENUM,
            defaultValue = R.string.option_vflow_data_input_type_text,
            options = typeOptions,
            acceptsMagicVariable = false
        ),
        InputDefinition(
            id = "prompt",
            nameStringRes = R.string.param_vflow_data_input_prompt_name,
            name = "提示信息",
            staticType = ParameterType.STRING,
            defaultValue = R.string.param_vflow_data_input_prompt_default,
            acceptsMagicVariable = true,
            acceptedMagicVariableTypes = setOf(VTypeRegistry.STRING.id)
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> {
        val textOption = R.string.option_vflow_data_input_type_text
        val numberOption = R.string.option_vflow_data_input_type_number
        val timeOption = R.string.option_vflow_data_input_type_time
        val dateOption = R.string.option_vflow_data_input_type_date

        val inputType = step?.parameters?.get("inputType") as? String ?: textOption
        val outputTypeName = when (inputType) {
            numberOption -> VTypeRegistry.NUMBER.id
            timeOption -> VTypeRegistry.TIME.id
            dateOption -> VTypeRegistry.DATE.id
            else -> VTypeRegistry.STRING.id
        }

        val outputNameBase = R.string.output_vflow_data_input_userInput_name
        return listOf(OutputDefinition("userInput", "$outputNameBase ($inputType)", outputTypeName))
    }

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val inputs = getInputs()
        val inputTypePill = PillUtil.createPillFromParam(
            step.parameters["inputType"],
            inputs.find { it.id == "inputType" },
            isModuleOption = true
        )
        val promptPill = PillUtil.createPillFromParam(
            step.parameters["prompt"],
            inputs.find { it.id == "prompt" }
        )

        val prefix = context.getString(R.string.summary_vflow_data_input_prefix)
        val middle = context.getString(R.string.summary_vflow_data_input_middle)

        return PillUtil.buildSpannable(
            context,
            prefix,
            inputTypePill,
            middle,
            promptPill
        )
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val uiService = context.services.get(ExecutionUIService::class)
            ?: return ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_data_input_service_missing),
                "无法获取UI服务来请求用户输入。"
            )

        val textOption = appContext.getString(R.string.option_vflow_data_input_type_text)
        val numberOption = appContext.getString(R.string.option_vflow_data_input_type_number)
        val timeOption = appContext.getString(R.string.option_vflow_data_input_type_time)
        val dateOption = appContext.getString(R.string.option_vflow_data_input_type_date)

        val inputType = context.getVariableAsString("inputType", textOption)
        val prompt = context.getVariableAsString("prompt").ifBlank { appContext.getString(R.string.param_vflow_data_input_prompt_default) }

        onProgress(ProgressUpdate("等待用户输入 ($inputType)..."))

        val userInput = uiService.requestInput(inputType, prompt)
            ?: return ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_data_input_user_cancelled),
                "用户取消了输入操作。"
            )

        val resultVariable = when (inputType) {
            numberOption -> VNumber((userInput as? Double) ?: 0.0)
            timeOption -> VTime(userInput.toString())
            dateOption -> {
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                // MaterialDatePicker 返回的是UTC毫秒，需要设置时区以保证日期正确
                sdf.timeZone = TimeZone.getTimeZone("UTC")
                val dateString = sdf.format(Date(userInput as Long))
                VDate(dateString)
            }
            else -> VString(userInput.toString())
        }

        onProgress(ProgressUpdate("获取到用户输入"))
        return ExecutionResult.Success(mapOf("userInput" to resultVariable))
    }
}
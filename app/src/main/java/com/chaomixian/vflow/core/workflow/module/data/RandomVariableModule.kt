package com.chaomixian.vflow.core.workflow.module.data

import android.content.Context
import android.os.Parcelable
import android.util.Log
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VNumber
import com.chaomixian.vflow.core.types.basic.VString
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.ui.workflow_editor.PillUtil

class RandomVariableModule : BaseModule() {
    override val id = "vflow.variable.random"
    override val metadata = ActionMetadata(
        nameStringRes = R.string.module_vflow_variable_random_name,
        descriptionStringRes = R.string.module_vflow_variable_random_desc,
        name = "创建随机变量",  // Fallback
        description = "创建新的随机变量，可选择为其命名以便后续修改或读取",  // Fallback
        iconRes = R.drawable.rounded_add_24,
        category = "数据"
    )

    private val typeOptions by lazy {
        listOf(
            appContext.getString(R.string.option_vflow_variable_random_type_number),
            appContext.getString(R.string.option_vflow_variable_random_type_string)
        )
    }

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "type",
            name = "变量类型",
            nameStringRes = R.string.param_vflow_variable_random_type_name,
            staticType = ParameterType.ENUM,
            defaultValue = typeOptions[0],
            options = typeOptions,
            acceptsMagicVariable = false
        ),
        // 可为空，用于存储生成结果的变量名（不带方括号）。
        InputDefinition(
            id = "variableName",
            name = "变量名称 (可选)",
            nameStringRes = R.string.param_vflow_variable_random_variableName_name,
            staticType = ParameterType.STRING,
            defaultValue = "",
            acceptsMagicVariable = false
        ),
        // 随机数的下限（包含）。默认为 0。
        InputDefinition(
            id = "min",
            name = "随机数最小值 (默认为 0)",
            nameStringRes = R.string.param_vflow_variable_random_min_name,
            staticType = ParameterType.NUMBER,
            defaultValue = "",
            acceptsMagicVariable = true,
            acceptedMagicVariableTypes = setOf(VTypeRegistry.NUMBER.id),
            isFolded = true // UI Provider 根据类型显示/折叠
        ),
        // 随机数的上限（包含）。默认为 100。
        InputDefinition(
            id = "max",
            name = "随机数最大值 (默认为 100)",
            nameStringRes = R.string.param_vflow_variable_random_max_name,
            staticType = ParameterType.NUMBER,
            defaultValue = "",
            acceptsMagicVariable = true,
            acceptedMagicVariableTypes = setOf(VTypeRegistry.NUMBER.id),
            isFolded = true
        ),
        // 随机数的步进值。默认为 1。
        InputDefinition(
            id = "step",
            name = "步长 (默认为 1)",
            nameStringRes = R.string.param_vflow_variable_random_step_name,
            staticType = ParameterType.NUMBER,
            defaultValue = "",
            acceptsMagicVariable = true,
            acceptedMagicVariableTypes = setOf(VTypeRegistry.NUMBER.id),
            isFolded = true
        ),
        // 生成随机文本的长度。
        InputDefinition(
            id = "length",
            name = "随机文本长度 (默认为 8)",
            nameStringRes = R.string.param_vflow_variable_random_length_name,
            staticType = ParameterType.NUMBER,
            defaultValue = "",
            acceptsMagicVariable = true,
            acceptedMagicVariableTypes = setOf(VTypeRegistry.NUMBER.id),
            isFolded = true
        ),
        // 可选。如果为空，则使用默认字符集（数字+字母）。
        InputDefinition(
            id = "custom_chars",
            name = "随机文本符集 (默认为 a-zA-Z0-9)",
            nameStringRes = R.string.param_vflow_variable_random_custom_chars_name,
            staticType = ParameterType.STRING,
            defaultValue = "",
            acceptsMagicVariable = true,
            acceptedMagicVariableTypes = setOf(VTypeRegistry.STRING.id),
            isFolded = true
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> {
        val inputType = step?.parameters?.get("type") as? String ?: typeOptions[0]
        val outputTypeName = when (inputType) {
            typeOptions[0] -> VTypeRegistry.NUMBER.id // 数字
            typeOptions[1] -> VTypeRegistry.STRING.id // 文本
            else -> VTypeRegistry.NUMBER.id
        }
        return listOf(OutputDefinition("randomVariable", "随机变量", outputTypeName, nameStringRes = R.string.output_vflow_variable_random_randomVariable_name))
    }

    override fun getSummary(context: Context, step: ActionStep): CharSequence? {
        val type = step.parameters["type"] as? String ?: typeOptions[0]
        val varName = step.parameters["variableName"]?.toString()
        val generate = context.getString(R.string.summary_vflow_variable_random_generate)
        val anonymous = context.getString(R.string.summary_vflow_variable_random_anonymous)
        val named = context.getString(R.string.summary_vflow_variable_random_named)

        return if (varName.isNullOrEmpty()) {
            "$generate $anonymous ($type)"
        } else {
            val namePill = PillUtil.Pill("[[$varName]]", "variableName")
            PillUtil.buildSpannable(context, "$named ", namePill, " ($type)")
        }
    }

    override fun validate(step: ActionStep, allSteps: List<ActionStep>): ValidationResult {
        val varName = step.parameters["variableName"] as? String

        if (!varName.isNullOrBlank()) {
            val count = allSteps.count {
                it.id != step.id && it.moduleId == this.id && (it.parameters["variableName"] as? String) == varName
            }
            if (count > 0) {
                val errorMsg = String.format(appContext.getString(R.string.error_vflow_variable_random_duplicate), varName)
                return ValidationResult(
                    false,
                    errorMsg
                )
            }
        }

        return ValidationResult(true)
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val type = context.getVariableAsString("type", typeOptions[0])
        val varName = context.getVariableAsString("variableName", "")

        val resultVariable: Parcelable = when (type) {
            typeOptions[0] -> { // 数字
                val min = context.getVariableAsString("min", "0").toDoubleOrNull() ?: 0.0
                val max = context.getVariableAsString("max", "100").toDoubleOrNull() ?: 100.0
                val step = context.getVariableAsString("step", "1").toDoubleOrNull() ?: 1.0

                if (min > max) {
                    val title = appContext.getString(R.string.error_vflow_variable_random_param_error)
                    val message = appContext.getString(R.string.error_vflow_variable_random_min_gt_max)
                    return ExecutionResult.Failure(title, message)
                }
                if (step <= 0) {
                    val title = appContext.getString(R.string.error_vflow_variable_random_param_error)
                    val message = appContext.getString(R.string.error_vflow_variable_random_step_positive)
                    return ExecutionResult.Failure(title, message)
                }

                // 生成逻辑
                val range = (max - min)
                // 确保 stepsCount 不会因为浮点数精度问题而小于0
                val stepsCount = (range / step).toInt()
                val randomStep = (0..stepsCount).random()
                val rawResult = min + (randomStep * step)
//                Log.d("RandomVariableModule", "execute: rawResult=$rawResult")

                // 如果所有参数都是整数，则返回整数；否则返回浮点数
                val isIntMode = (min % 1 == 0.0) && (max % 1 == 0.0) && (step % 1 == 0.0)
                if (isIntMode) {
                    VNumber(rawResult.toLong().toDouble()) // VNumber 内部统一 Double
                } else {
                    VNumber(rawResult)
                }
            }
            typeOptions[1] -> { // 文本
                val length = context.getVariableAsString("length", "8").toDoubleOrNull()?.toInt() ?: 8
                val customChars = context.getVariableAsString("custom_chars", "")

                val charPool = if (customChars.isNullOrEmpty()) {
                    (('a'..'z') + ('A'..'Z') + ('0'..'9')).toList()
                } else {
                    customChars.toList()
                }

                if (charPool.isEmpty()) {
                    val title = appContext.getString(R.string.error_vflow_variable_random_param_error)
                    val message = appContext.getString(R.string.error_vflow_variable_random_charset_empty)
                    return ExecutionResult.Failure(title, message)
                }

                val randomString = (1..length)
                    .map { charPool.random() }
                    .joinToString("")

                VString(randomString)
            }
            // 添加 else 分支处理未知类型，保证 when 表达式的完备性
            else -> {
                val title = appContext.getString(R.string.error_vflow_variable_random_unknown_type)
                val message = String.format(appContext.getString(R.string.error_vflow_variable_random_cannot_create), type)
                return ExecutionResult.Failure(title, message)
            }
        }

        // 如果指定了变量名，存储到命名变量中
        if (!varName.isNullOrEmpty()) {
            if (context.namedVariables.containsKey(varName)) {
                val title = appContext.getString(R.string.error_vflow_variable_random_name_conflict)
                val message = String.format(appContext.getString(R.string.error_vflow_variable_random_exists), varName)
                return ExecutionResult.Failure(title, message)
            }
            context.namedVariables[varName] = resultVariable
            onProgress(ProgressUpdate("已创建命名变量 '$varName'"))
        }

//        Log.d("RandomVariableModule", "execute: resultVariable=$resultVariable")
        return ExecutionResult.Success(mapOf("randomVariable" to resultVariable))
    }
}
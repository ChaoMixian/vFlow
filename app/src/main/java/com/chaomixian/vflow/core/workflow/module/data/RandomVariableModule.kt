package com.chaomixian.vflow.core.workflow.module.data

import android.content.Context
import android.os.Parcelable
import android.util.Log
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.ActionMetadata
import com.chaomixian.vflow.core.module.BaseModule
import com.chaomixian.vflow.core.module.ExecutionResult
import com.chaomixian.vflow.core.module.InputDefinition
import com.chaomixian.vflow.core.module.OutputDefinition
import com.chaomixian.vflow.core.module.ProgressUpdate
import com.chaomixian.vflow.core.module.ValidationResult
import com.chaomixian.vflow.core.module.ParameterType
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VNumber
import com.chaomixian.vflow.core.types.basic.VString
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.ui.workflow_editor.PillUtil

class RandomVariableModule : BaseModule() {
    override val id = "vflow.variable.random"
    override val metadata = ActionMetadata(
        name = "创建随机变量",
        description = "创建新的随机变量，可选择为其命名以便后续修改或读取。",
        iconRes = R.drawable.rounded_add_24,
        category = "数据"
    )

    private val typeOptions = listOf("数字", "文本")

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "type",
            name = "变量类型",
            staticType = ParameterType.ENUM,
            defaultValue = "数字",
            options = typeOptions,
            acceptsMagicVariable = false
        ),
        // 可为空，用于存储生成结果的变量名（不带方括号）。
        InputDefinition(
            id = "variableName",
            name = "变量名称 (可选)",
            staticType = ParameterType.STRING,
            defaultValue = "",
            acceptsMagicVariable = false
        ),
        // 随机数的下限（包含）。默认为 0。
        InputDefinition(
            id = "min",
            name = "随机数最小值 (默认为 0)",
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
            staticType = ParameterType.STRING,
            defaultValue = "",
            acceptsMagicVariable = true,
            acceptedMagicVariableTypes = setOf(VTypeRegistry.STRING.id),
            isFolded = true
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> {
        val inputType = step?.parameters?.get("type") as? String ?: "数字"
        val outputTypeName = when (inputType) {
            "数字" -> VTypeRegistry.NUMBER.id
            "文本" -> VTypeRegistry.STRING.id
            else -> VTypeRegistry.NUMBER.id
        }
        return listOf(OutputDefinition("randomVariable", "随机变量", outputTypeName))
    }

    override fun getSummary(context: Context, step: ActionStep): CharSequence? {
        val type = step.parameters["type"] as? String ?: "数字"
        val varName = step.parameters["variableName"]?.toString()
        return if (varName.isNullOrEmpty()) {
            "生成 匿名随机变量 ($type)"
        } else {
            val namePill = PillUtil.Pill("[[$varName]]", "variableName")
            PillUtil.buildSpannable(context, "生成随机变量 ", namePill, " ($type)")
        }
    }

    override fun validate(step: ActionStep, allSteps: List<ActionStep>): ValidationResult {
        val varName = step.parameters["variableName"] as? String

        if (!varName.isNullOrBlank()) {
            val count = allSteps.count {
                it.id != step.id && it.moduleId == this.id && (it.parameters["variableName"] as? String) == varName
            }
            if (count > 0) return ValidationResult(
                false,
                "变量名 '$varName' 已存在，请使用其他名称。"
            )
        }

        return ValidationResult(true)
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val type = context.variables["type"] as? String ?: "数字"
        val varName = context.variables["variableName"]?.toString()

        val resultVariable: Parcelable = when (type) {
            "数字" -> {
                val min = context.variables["min"]?.toString()?.toDoubleOrNull() ?: 0.0
                val max = context.variables["max"]?.toString()?.toDoubleOrNull() ?: 100.0
                val step = context.variables["step"]?.toString()?.toDoubleOrNull() ?: 1.0

                if (min > max) {
                    return ExecutionResult.Failure("参数错误", "最小值不能大于最大值")
                }
                if (step <= 0) {
                    return ExecutionResult.Failure("参数错误", "步长必须大于 0")
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
            "文本" -> {
                val length = context.variables["length"]?.toString()?.toDoubleOrNull()?.toInt() ?: 8
                val customChars = context.variables["custom_chars"]?.toString()

                val charPool = if (customChars.isNullOrEmpty()) {
                    (('a'..'z') + ('A'..'Z') + ('0'..'9')).toList()
                } else {
                    customChars.toList()
                }

                if (charPool.isEmpty()) {
                    return ExecutionResult.Failure("参数错误", "字符集不能为空")
                }

                val randomString = (1..length)
                    .map { charPool.random() }
                    .joinToString("")

                VString(randomString)
            }
            // 添加 else 分支处理未知类型，保证 when 表达式的完备性
            else -> return ExecutionResult.Failure("未知类型", "无法创建类型为 '$type' 的随机变量")
        }

        // 如果指定了变量名，存储到命名变量中
        if (!varName.isNullOrEmpty()) {
            if (context.namedVariables.containsKey(varName)) {
                return ExecutionResult.Failure("命名冲突", "变量 '$varName' 已存在。")
            }
            context.namedVariables[varName] = resultVariable
            onProgress(ProgressUpdate("已创建命名变量 '$varName'"))
        }

//        Log.d("RandomVariableModule", "execute: resultVariable=$resultVariable")
        return ExecutionResult.Success(mapOf("randomVariable" to resultVariable))
    }
}
// 文件: main/java/com/chaomixian/vflow/core/workflow/module/data/CreateVariableModule.kt
package com.chaomixian.vflow.core.workflow.module.data

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.execution.VariableResolver
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.types.VObject
import com.chaomixian.vflow.core.types.VObjectFactory
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.*
import com.chaomixian.vflow.core.types.complex.VCoordinate
import com.chaomixian.vflow.core.types.complex.VImage
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.ui.workflow_editor.PillUtil

class CreateVariableModule : BaseModule() {
    override val id = "vflow.variable.create"
    override val metadata = ActionMetadata(
        nameStringRes = R.string.module_vflow_variable_create_name,
        descriptionStringRes = R.string.module_vflow_variable_create_desc,
        name = "创建变量",
        description = "创建一个新的变量，可选择为其命名以便后续修改或读取。",
        iconRes = R.drawable.rounded_add_24,
        category = "数据"
    )
    private val typeOptions = listOf("文本", "数字", "布尔", "字典", "列表", "图像", "坐标")

    override val uiProvider: ModuleUIProvider? = VariableModuleUIProvider(typeOptions)

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition("variableName", "变量名称 (可选)", ParameterType.STRING, defaultValue = "", acceptsMagicVariable = false),
        InputDefinition(
            id = "type",
            nameStringRes = R.string.param_vflow_variable_create_type_name,
            name = "变量类型",
            staticType = ParameterType.ENUM,
            defaultValue = "文本",
            options = typeOptions,
            acceptsMagicVariable = false
        ),
        InputDefinition(
            id = "value",
            nameStringRes = R.string.param_vflow_variable_create_value_name,
            name = "值",
            staticType = ParameterType.ANY,
            defaultValue = "",
            acceptsMagicVariable = true,
            supportsRichText = true
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> {
        if (step == null) return emptyList()
        val selectedType = step.parameters["type"] as? String
        val outputTypeName = when (selectedType) {
            "文本" -> VTypeRegistry.STRING.id
            "数字" -> VTypeRegistry.NUMBER.id
            "布尔" -> VTypeRegistry.BOOLEAN.id
            "字典" -> VTypeRegistry.DICTIONARY.id
            "列表" -> VTypeRegistry.LIST.id
            "图像" -> VTypeRegistry.IMAGE.id
            "坐标" -> VTypeRegistry.COORDINATE.id
            else -> VTypeRegistry.STRING.id
        }
        return listOf(
            OutputDefinition(
                id = "variable",
                name = "变量值",
                nameStringRes = R.string.output_vflow_variable_create_variable_name,
                typeName = outputTypeName
            )
        )
    }

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val name = step.parameters["variableName"] as? String
        val type = step.parameters["type"]?.toString() ?: "文本"
        val value = step.parameters["value"]
        val rawText = value?.toString() ?: ""

        // 优先检查是否为"复杂内容"。
        // 如果是复杂的（包含多个变量或混合文本），则只返回简单标题。
        // RichTextUIProvider 会负责渲染预览视图。
        if (type == "文本" && VariableResolver.isComplex(rawText)) {
            return if (name.isNullOrBlank()) {
                context.getString(R.string.summary_vflow_data_create_anon, type, "")
            } else {
                val namePill = PillUtil.Pill("[[$name]]", "variableName")
                PillUtil.buildSpannable(context, context.getString(R.string.summary_vflow_data_create_variable, "", type), namePill)
            }
        }

        // 如果是字典、列表或坐标，且不是单纯的变量引用
        // 此时 VariableValueUIProvider 会显示详细预览列表，摘要中隐藏 value pill 以防重复
        if ((type == "字典" || type == "列表" || type == "坐标") && !rawText.isMagicVariable() && !rawText.isNamedVariable()) {
            return buildSimpleSummary(context, name, type)
        }

        // 其他情况（简单文本、数字、布尔、或直接引用变量的字典/列表），摘要中显示完整值
        val valuePill = PillUtil.createPillFromParam(value, getInputs().find { it.id == "value" })
        return if (name.isNullOrBlank()) {
            PillUtil.buildSpannable(context, context.getString(R.string.summary_vflow_data_create_anon, type, ""), valuePill)
        } else {
            val namePill = PillUtil.Pill("[[$name]]", "variableName")
            PillUtil.buildSpannable(context, context.getString(R.string.summary_vflow_data_create_variable, "", type), namePill, context.getString(R.string.summary_vflow_data_create_value_separator), valuePill)
        }
    }

    private fun buildSimpleSummary(context: Context, name: String?, type: String): CharSequence {
        return if (name.isNullOrBlank()) {
            context.getString(R.string.summary_vflow_data_create_anon, type, "")
        } else {
            val namePill = PillUtil.Pill("[[$name]]", "variableName")
            PillUtil.buildSpannable(context, context.getString(R.string.summary_vflow_data_create_variable, "", type), namePill)
        }
    }

    override fun validate(step: ActionStep, allSteps: List<ActionStep>): ValidationResult {
        val variableName = step.parameters["variableName"] as? String
        if (!variableName.isNullOrBlank()) {
            val count = allSteps.count {
                it.id != step.id && it.moduleId == this.id && (it.parameters["variableName"] as? String) == variableName
            }
            if (count > 0) return ValidationResult(false, "变量名 '$variableName' 已存在，请使用其他名称。")
        }
        return ValidationResult(true)
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val type = context.getVariableAsString("type", "文本")
        val rawValue = context.getVariable("value")  // 现在直接返回 VObject
        val variableName = context.getVariableAsString("variableName", "")

        // 使用 VObjectFactory 统一包装值，简化类型转换逻辑
        val variable: VObject = when (type) {
            "文本" -> {
                // 如果是 VString，直接使用；否则解析并创建
                if (rawValue is VString) {
                    rawValue
                } else {
                    val resolvedText = VariableResolver.resolve(rawValue.asString(), context)
                    VString(resolvedText)
                }
            }
            "数字" -> {
                val numValue = rawValue.asNumber() ?: 0.0
                VNumber(numValue)
            }
            "布尔" -> {
                VBoolean(rawValue.asBoolean())
            }
            "字典" -> {
                // 如果已经是 VDictionary，直接使用
                if (rawValue is VDictionary) {
                    rawValue
                } else {
                    // 尝试从 Map 或其他类型转换
                    val mapValue = rawValue.raw as? Map<*, *> ?: emptyMap<String, VObject>()
                    val vMap = mapValue.entries.associate { entry ->
                        entry.key.toString() to VObjectFactory.from(entry.value)
                    }
                    VDictionary(vMap)
                }
            }
            "列表" -> {
                // 如果已经是 VList，直接使用
                if (rawValue is VList) {
                    rawValue
                } else {
                    val listValue = rawValue.raw as? List<*> ?: emptyList<Any?>()
                    VList(listValue.map { VObjectFactory.from(it) })
                }
            }
            "图像" -> {
                VImage(rawValue.asString())
            }
            "坐标" -> {
                // 如果已经是 VCoordinate，直接使用
                if (rawValue is VCoordinate) {
                    rawValue
                } else {
                    // 尝试从 Map 或 List 转换
                    val mapValue = rawValue.raw as? Map<*, *>
                    val listValue = rawValue.raw as? List<*>
                    when {
                        mapValue != null -> {
                            val x = (mapValue["x"] as? Number)?.toInt() ?: 0
                            val y = (mapValue["y"] as? Number)?.toInt() ?: 0
                            VCoordinate(x, y)
                        }
                        listValue != null && listValue.size >= 2 -> {
                            val x = (listValue[0] as? Number)?.toInt() ?: 0
                            val y = (listValue[1] as? Number)?.toInt() ?: 0
                            VCoordinate(x, y)
                        }
                        else -> {
                            // 尝试从字符串解析 "x,y" 格式
                            val str = rawValue.asString()
                            val parts = str.split(",")
                            if (parts.size == 2) {
                                val x = parts[0].trim().toIntOrNull() ?: 0
                                val y = parts[1].trim().toIntOrNull() ?: 0
                                VCoordinate(x, y)
                            } else {
                                VCoordinate(0, 0)
                            }
                        }
                    }
                }
            }
            else -> VString(rawValue.asString())
        }

        if (!variableName.isNullOrBlank()) {
            // 检查变量是否存在
            val existingVar = context.getVariable(variableName)
            if (existingVar !is VNull) {
                return ExecutionResult.Failure("命名冲突", "变量 '$variableName' 已存在。")
            }
            // 现在直接存储 VObject，无需转换
            context.setVariable(variableName, variable)
            onProgress(ProgressUpdate("已创建命名变量 '$variableName'"))
        }

        return ExecutionResult.Success(mapOf("variable" to variable))
    }
}
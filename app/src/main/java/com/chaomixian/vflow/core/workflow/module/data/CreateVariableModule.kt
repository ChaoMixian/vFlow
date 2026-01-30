// 文件: main/java/com/chaomixian/vflow/core/workflow/module/data/CreateVariableModule.kt
package com.chaomixian.vflow.core.workflow.module.data

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.execution.VariableResolver
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.types.VObject
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.*
import com.chaomixian.vflow.core.types.complex.VImage
import com.chaomixian.vflow.core.types.complex.VCoordinate
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
        val type = context.variables["type"] as? String ?: "文本"
        val rawValue = context.magicVariables["value"] ?: context.variables["value"]
        val variableName = context.variables["variableName"] as? String

        val variable = when (type) {
            "文本" -> {
                val resolvedText = VariableResolver.resolve(rawValue?.toString() ?: "", context)
                VString(resolvedText)
            }
            "数字" -> {
                val numValue = when (rawValue) {
                    is VNumber -> rawValue.raw
                    is Number -> rawValue.toDouble()
                    is String -> rawValue.toDoubleOrNull()
                    else -> 0.0
                } ?: 0.0
                VNumber(numValue)
            }
            "布尔" -> VBoolean(
                when (rawValue) {
                    is VBoolean -> rawValue.raw
                    is Boolean -> rawValue
                    else -> rawValue?.toString().toBoolean()
                }
            )
            "字典" -> {
                val map = (rawValue as? Map<*, *>)?.mapKeys { it.key.toString() }?.mapValues { entry ->
                    // Convert Any? to VObject
                    when (val value = entry.value) {
                        is VObject -> value
                        is String -> VString(value)
                        is Number -> VNumber(value.toDouble())
                        is Boolean -> VBoolean(value)
                        is List<*> -> {
                            val items = value.map { item ->
                                when (item) {
                                    is VObject -> item
                                    is String -> VString(item)
                                    is Number -> VNumber(item.toDouble())
                                    is Boolean -> VBoolean(item)
                                    else -> VNull
                                }
                            }
                            VList(items)
                        }
                        else -> VNull
                    }
                } as? Map<String, VObject> ?: emptyMap()
                VDictionary(map)
            }
            "列表" -> {
                val list: List<VObject> = when (rawValue) {
                    is VList -> rawValue.raw
                    is List<*> -> rawValue.map { item ->
                        when (item) {
                            is VObject -> item
                            is String -> VString(item)
                            is Number -> VNumber(item.toDouble())
                            is Boolean -> VBoolean(item)
                            else -> VNull
                        }
                    }
                    is String -> rawValue.lines().filter { it.isNotEmpty() }.map { VString(it) }
                    else -> emptyList()
                }
                VList(list)
            }
            "图像" -> VImage(rawValue?.toString() ?: "")
            "坐标" -> {
                fun resolveCoordValue(value: Any?): Int {
                    return when (value) {
                        is Number -> value.toInt()
                        is String -> {
                            if (value.isMagicVariable() || value.isNamedVariable()) {
                                // 解析变量引用
                                val resolved = VariableResolver.resolveValue(value, context)
                                when (resolved) {
                                    is Number -> resolved.toInt()
                                    else -> resolved.toString().toIntOrNull() ?: 0
                                }
                            } else {
                                value.toIntOrNull() ?: 0
                            }
                        }
                        else -> 0
                    }
                }

                val coordValue = when (rawValue) {
                    is VCoordinate -> rawValue
                    is Map<*, *> -> {
                        val x = resolveCoordValue(rawValue["x"])
                        val y = resolveCoordValue(rawValue["y"])
                        VCoordinate(x, y)
                    }
                    is List<*> -> {
                        val x = resolveCoordValue(rawValue.getOrNull(0))
                        val y = resolveCoordValue(rawValue.getOrNull(1))
                        VCoordinate(x, y)
                    }
                    else -> {
                        // 尝试从字符串解析 "x,y" 格式
                        val str = rawValue?.toString() ?: ""
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
                coordValue
            }
            else -> VString(rawValue?.toString() ?: "")
        }

        if (!variableName.isNullOrBlank()) {
            if (context.namedVariables.containsKey(variableName)) {
                return ExecutionResult.Failure("命名冲突", "变量 '$variableName' 已存在。")
            }
            context.namedVariables[variableName] = variable
            onProgress(ProgressUpdate("已创建命名变量 '$variableName'"))
        }

        return ExecutionResult.Success(mapOf("variable" to variable))
    }
}
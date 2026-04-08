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
    companion object {
        const val TYPE_STRING = "string"
        const val TYPE_NUMBER = "number"
        const val TYPE_BOOLEAN = "boolean"
        const val TYPE_DICTIONARY = "dictionary"
        const val TYPE_LIST = "list"
        const val TYPE_IMAGE = "image"
        const val TYPE_COORDINATE = "coordinate"

        fun normalizeType(value: String?): String {
            return when (value) {
                "文本", "Text", TYPE_STRING -> TYPE_STRING
                "数字", "Number", TYPE_NUMBER -> TYPE_NUMBER
                "布尔", "Boolean", TYPE_BOOLEAN -> TYPE_BOOLEAN
                "字典", "Dictionary", TYPE_DICTIONARY -> TYPE_DICTIONARY
                "列表", "List", TYPE_LIST -> TYPE_LIST
                "图像", "Image", TYPE_IMAGE -> TYPE_IMAGE
                "坐标", "Coordinate", TYPE_COORDINATE -> TYPE_COORDINATE
                else -> TYPE_STRING
            }
        }

        fun getTypeLabel(context: Context, value: String?): String {
            return when (normalizeType(value)) {
                TYPE_STRING -> context.getString(R.string.option_vflow_variable_create_type_string)
                TYPE_NUMBER -> context.getString(R.string.option_vflow_variable_create_type_number)
                TYPE_BOOLEAN -> context.getString(R.string.option_vflow_variable_create_type_boolean)
                TYPE_DICTIONARY -> context.getString(R.string.option_vflow_variable_create_type_dictionary)
                TYPE_LIST -> context.getString(R.string.option_vflow_variable_create_type_list)
                TYPE_IMAGE -> context.getString(R.string.option_vflow_variable_create_type_image)
                TYPE_COORDINATE -> context.getString(R.string.option_vflow_variable_create_type_coordinate)
                else -> context.getString(R.string.option_vflow_variable_create_type_string)
            }
        }
    }
    override val id = "vflow.variable.create"
    override val metadata = ActionMetadata(
        nameStringRes = R.string.module_vflow_variable_create_name,
        descriptionStringRes = R.string.module_vflow_variable_create_desc,
        name = "创建变量",
        description = "创建一个新的变量，可选择为其命名以便后续修改或读取。",
        iconRes = R.drawable.rounded_add_24,
        category = "数据",
        categoryId = "data"
    )
    private val typeOptions = listOf(TYPE_STRING, TYPE_NUMBER, TYPE_BOOLEAN, TYPE_DICTIONARY, TYPE_LIST, TYPE_IMAGE, TYPE_COORDINATE)

    override val uiProvider: ModuleUIProvider? = VariableModuleUIProvider(typeOptions)

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition("variableName", "变量名称 (可选)", ParameterType.STRING, defaultValue = "", acceptsMagicVariable = false, nameStringRes = R.string.param_vflow_variable_create_variableName_name),
        InputDefinition(
            id = "type",
            nameStringRes = R.string.param_vflow_variable_create_type_name,
            name = "变量类型",
            staticType = ParameterType.ENUM,
            defaultValue = TYPE_STRING,
            options = typeOptions,
            acceptsMagicVariable = false,
            optionsStringRes = listOf(
                R.string.option_vflow_variable_create_type_string,
                R.string.option_vflow_variable_create_type_number,
                R.string.option_vflow_variable_create_type_boolean,
                R.string.option_vflow_variable_create_type_dictionary,
                R.string.option_vflow_variable_create_type_list,
                R.string.option_vflow_variable_create_type_image,
                R.string.option_vflow_variable_create_type_coordinate
            ),
            legacyValueMap = mapOf(
                "文本" to TYPE_STRING,
                "Text" to TYPE_STRING,
                "数字" to TYPE_NUMBER,
                "Number" to TYPE_NUMBER,
                "布尔" to TYPE_BOOLEAN,
                "Boolean" to TYPE_BOOLEAN,
                "字典" to TYPE_DICTIONARY,
                "Dictionary" to TYPE_DICTIONARY,
                "列表" to TYPE_LIST,
                "List" to TYPE_LIST,
                "图像" to TYPE_IMAGE,
                "Image" to TYPE_IMAGE,
                "坐标" to TYPE_COORDINATE,
                "Coordinate" to TYPE_COORDINATE
            )
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
        val selectedType = normalizeType(step.parameters["type"] as? String)
        val outputTypeName = when (selectedType) {
            TYPE_STRING -> VTypeRegistry.STRING.id
            TYPE_NUMBER -> VTypeRegistry.NUMBER.id
            TYPE_BOOLEAN -> VTypeRegistry.BOOLEAN.id
            TYPE_DICTIONARY -> VTypeRegistry.DICTIONARY.id
            TYPE_LIST -> VTypeRegistry.LIST.id
            TYPE_IMAGE -> VTypeRegistry.IMAGE.id
            TYPE_COORDINATE -> VTypeRegistry.COORDINATE.id
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
        val type = normalizeType(step.parameters["type"]?.toString())
        val typeLabel = getTypeLabel(context, type)
        val value = step.parameters["value"]
        val rawText = value?.toString() ?: ""

        // 优先检查是否为"复杂内容"。
        // 如果是复杂的（包含多个变量或混合文本），则只返回简单标题。
        // RichTextUIProvider 会负责渲染预览视图。
        if (type == TYPE_STRING && VariableResolver.isComplex(rawText)) {
            return if (name.isNullOrBlank()) {
                context.getString(R.string.summary_vflow_data_create_anon, typeLabel, "")
            } else {
                val namePill = PillUtil.Pill("[[$name]]", "variableName")
                PillUtil.buildSpannable(context, context.getString(R.string.summary_vflow_data_create_variable, "", typeLabel), namePill)
            }
        }

        // 如果是字典、列表或坐标，且不是单纯的变量引用
        // 此时 VariableValueUIProvider 会显示详细预览列表，摘要中隐藏 value pill 以防重复
        if ((type == TYPE_DICTIONARY || type == TYPE_LIST || type == TYPE_COORDINATE) && !rawText.isMagicVariable() && !rawText.isNamedVariable()) {
            return buildSimpleSummary(context, name, type)
        }

        // 其他情况（简单文本、数字、布尔、或直接引用变量的字典/列表），摘要中显示完整值
        val valuePill = PillUtil.createPillFromParam(value, getInputs().find { it.id == "value" })
        return if (name.isNullOrBlank()) {
            PillUtil.buildSpannable(context, context.getString(R.string.summary_vflow_data_create_anon, typeLabel, ""), valuePill)
        } else {
            val namePill = PillUtil.Pill("[[$name]]", "variableName")
            PillUtil.buildSpannable(context, context.getString(R.string.summary_vflow_data_create_variable, "", typeLabel), namePill, context.getString(R.string.summary_vflow_data_create_value_separator), valuePill)
        }
    }

    private fun buildSimpleSummary(context: Context, name: String?, type: String): CharSequence {
        val typeLabel = getTypeLabel(context, type)
        return if (name.isNullOrBlank()) {
            context.getString(R.string.summary_vflow_data_create_anon, typeLabel, "")
        } else {
            val namePill = PillUtil.Pill("[[$name]]", "variableName")
            PillUtil.buildSpannable(context, context.getString(R.string.summary_vflow_data_create_variable, "", typeLabel), namePill)
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
        val type = normalizeType(context.getVariableAsString("type", TYPE_STRING))
        val rawValue = context.getVariable("value")  // 现在直接返回 VObject
        val variableName = context.getVariableAsString("variableName", "")

        // 使用 VObjectFactory 统一包装值，简化类型转换逻辑
        val variable: VObject = when (type) {
            TYPE_STRING -> {
                // 如果是 VString，直接使用；否则解析并创建
                if (rawValue is VString) {
                    rawValue
                } else {
                    val resolvedText = VariableResolver.resolve(rawValue.asString(), context)
                    VString(resolvedText)
                }
            }
            TYPE_NUMBER -> {
                val numValue = rawValue.asNumber() ?: 0.0
                VNumber(numValue)
            }
            TYPE_BOOLEAN -> {
                VBoolean(rawValue.asBoolean())
            }
            TYPE_DICTIONARY -> {
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
            TYPE_LIST -> {
                // 如果已经是 VList，直接使用
                if (rawValue is VList) {
                    rawValue
                } else {
                    val listValue = rawValue.raw as? List<*> ?: emptyList<Any?>()
                    VList(listValue.map { VObjectFactory.from(it) })
                }
            }
            TYPE_IMAGE -> {
                VImage(rawValue.asString())
            }
            TYPE_COORDINATE -> {
                // 如果已经是 VCoordinate，直接使用
                if (rawValue is VCoordinate) {
                    rawValue
                } else {
                    // 尝试从 Map 或 List 转换
                    val mapValue = rawValue.raw as? Map<*, *>
                    val listValue = rawValue.raw as? List<*>
                    when {
                        mapValue != null -> {
                            val x = resolveCoordinateComponent(mapValue["x"], context)
                            val y = resolveCoordinateComponent(mapValue["y"], context)
                            VCoordinate(x, y)
                        }
                        listValue != null && listValue.size >= 2 -> {
                            val x = resolveCoordinateComponent(listValue[0], context)
                            val y = resolveCoordinateComponent(listValue[1], context)
                            VCoordinate(x, y)
                        }
                        else -> {
                            resolveCoordinateFromString(rawValue.asString(), context)
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

    private fun resolveCoordinateComponent(value: Any?, context: ExecutionContext): Int {
        val vObject = VObjectFactory.from(value)
        val numericValue = vObject.asNumber()
        if (numericValue != null) {
            return numericValue.toInt()
        }

        val rawText = vObject.asString().trim()
        if (rawText.isEmpty()) {
            return 0
        }

        val resolvedText = if (VariableResolver.hasVariableReference(rawText)) {
            VariableResolver.resolve(rawText, context).trim()
        } else {
            rawText
        }

        return resolvedText.toDoubleOrNull()?.toInt() ?: 0
    }

    private fun resolveCoordinateFromString(value: String, context: ExecutionContext): VCoordinate {
        val resolvedText = if (VariableResolver.hasVariableReference(value)) {
            VariableResolver.resolve(value, context).trim()
        } else {
            value.trim()
        }

        val parts = resolvedText.split(",")
        if (parts.size != 2) {
            return VCoordinate(0, 0)
        }

        val x = parts[0].trim().toDoubleOrNull()?.toInt() ?: 0
        val y = parts[1].trim().toDoubleOrNull()?.toInt() ?: 0
        return VCoordinate(x, y)
    }
}

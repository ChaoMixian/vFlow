package com.chaomixian.vflow.api.model

/**
 * 模块分类
 */
data class ModuleCategory(
    val id: String,
    val name: String,
    val nameEn: String,
    val icon: String,
    val description: String,
    val descriptionEn: String,
    val order: Int
)

/**
 * 模块元数据
 */
data class ModuleMetadata(
    val name: String,
    val nameEn: String,
    val icon: String,
    val category: String,
    val description: String,
    val descriptionEn: String,
    val helpUrl: String?
)

/**
 * 积木块行为
 */
data class BlockBehavior(
    val blockType: String, // none, if, loop, foreach
    val canStartWorkflow: Boolean,
    val endBlockId: String?
)

/**
 * 模块摘要
 */
data class ModuleSummary(
    val id: String,
    val metadata: ModuleMetadata,
    val blockBehavior: BlockBehavior
)

/**
 * 参数约束
 */
data class ParameterConstraints(
    val min: Number?,
    val max: Number?,
    val step: Number?,
    val pattern: String?,
    val minLength: Int?,
    val maxLength: Int?,
    val options: List<String>?,
    val language: String?
)

/**
 * 参数定义
 */
data class ParameterDefinition(
    val id: String,
    val type: String,
    val label: String,
    val labelEn: String,
    val description: String?,
    val descriptionEn: String?,
    val defaultValue: Any?,
    val required: Boolean,
    val uiType: String,
    val constraints: ParameterConstraints?
)

/**
 * 输出定义
 */
data class OutputDefinition(
    val id: String,
    val type: String,
    val label: String,
    val labelEn: String,
    val description: String?,
    val descriptionEn: String?
)

/**
 * 模块详情
 */
data class ModuleDetail(
    val id: String,
    val metadata: ModuleMetadata,
    val blockBehavior: BlockBehavior,
    val inputs: List<ParameterDefinition>,
    val outputs: List<OutputDefinition>,
    val examples: List<ModuleExample>
)

/**
 * 模块示例
 */
data class ModuleExample(
    val name: String,
    val description: String,
    val parameters: Map<String, Any?>
)

/**
 * UI Schema - 用于前端动态生成表单
 */
data class UiSchema(
    val schema: List<UiFieldSchema>
)

/**
 * UI字段Schema
 */
data class UiFieldSchema(
    val key: String,
    val type: String, // text_field, dropdown, number_slider, key_value_editor, code_editor, etc.
    val label: String,
    val labelEn: String? = null,
    val description: String? = null,
    val descriptionEn: String? = null,
    val placeholder: String? = null,
    val required: Boolean = false,
    val validation: FieldValidation? = null,
    val autocomplete: AutocompleteConfig? = null,
    // Dropdown specific
    val options: List<UiOption>? = null,
    // Number slider specific
    val min: Number? = null,
    val max: Number? = null,
    val step: Number? = null,
    val unit: String? = null,
    // Key-value editor specific
    val keyPlaceholder: String? = null,
    val valuePlaceholder: String? = null,
    val allowVariables: Boolean = false,
    // Code editor specific
    val language: String? = null,
    // Common
    val defaultValue: Any? = null
)

/**
 * UI选项
 */
data class UiOption(
    val value: String,
    val label: String
)

/**
 * 字段验证规则
 */
data class FieldValidation(
    val pattern: String? = null,
    val message: String? = null,
    val min: Number? = null,
    val max: Number? = null
)

/**
 * 自动完成配置
 */
data class AutocompleteConfig(
    val type: String, // variable, file, etc.
    val allowMagicVariables: Boolean = false
)

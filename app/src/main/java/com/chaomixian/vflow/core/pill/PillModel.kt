// 文件: main/java/com/chaomixian/vflow/core/pill/PillModel.kt
// 描述: Pill数据模型，纯数据结构，无UI依赖
package com.chaomixian.vflow.core.pill

/**
 * Pill数据模型（纯数据，无UI依赖）
 *
 * 这个数据类表示一个"药丸"（pill），即UI中显示的参数引用或值。
 * 它不包含任何Android特定的UI类，可以在任何平台使用。
 *
 * @property text Pill上显示的原始文本或变量引用
 * @property parameterId 对应的参数ID，用于UI层处理点击事件
 * @property type Pill类型，区分不同用途的Pill
 * @property metadata 可选的元数据，包含额外的上下文信息
 */
data class Pill(
    val text: String,
    val parameterId: String,
    val type: PillType = PillType.PARAMETER,
    val metadata: PillMetadata? = null
)

/**
 * Pill类型枚举
 */
enum class PillType {
    /** 普通参数，来自用户输入或模块参数 */
    PARAMETER,

    /** 模块选项，如IF模块的操作符（"等于"、"大于"等） */
    MODULE_OPTION,

    /** 变量引用，如魔法变量或命名变量 */
    VARIABLE,

    /** 静态值，硬编码的常量值 */
    STATIC_VALUE
}

/**
 * Pill元数据
 *
 * 包含Pill的额外上下文信息，用于高级渲染和交互。
 *
 * @property sourceModuleId 源模块ID（如果Pill引用了某个模块的输出）
 * @property sourceStepId 源步骤ID（如果Pill引用了某个步骤的输出）
 * @property variableType 变量类型名称（如TextVariable.TYPE_NAME）
 * @property isNamedVariable 是否为命名变量（[[name]]格式）
 */
data class PillMetadata(
    val sourceModuleId: String? = null,
    val sourceStepId: String? = null,
    val variableType: String? = null,
    val isNamedVariable: Boolean = false
)

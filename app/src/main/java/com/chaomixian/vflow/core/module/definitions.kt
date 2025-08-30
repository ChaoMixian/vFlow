package com.chaomixian.vflow.core.module

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue

/**
 * 参数类型枚举。
 * 定义了静态值的类型。
 */
enum class ParameterType {
    STRING,
    NUMBER,
    BOOLEAN,
    ENUM,
    ANY // ANY 类型主要用于“如果”等可以接受任何输入的模块
}

/**
 * 模块的一个输入参数定义。
 * @param staticType 如果未连接魔法变量，UI上应显示的静态输入框类型。
 * @param acceptedMagicVariableTypes 一个类型集合，定义了此输入可以接受哪些魔法变量。
 */
data class InputDefinition(
    val id: String,
    val name: String,
    val staticType: ParameterType,
    val acceptsMagicVariable: Boolean = true,
    val acceptedMagicVariableTypes: Set<Class<out Parcelable>> = emptySet()
)

// OutputDefinition 和其他定义保持不变
data class OutputDefinition(
    val id: String,
    val name: String,
    val type: Class<out Parcelable>
)

data class ParameterDefinition(
    val id: String,
    val name: String,
    val type: ParameterType,
    val defaultValue: Any? = null,
    val options: List<String> = emptyList()
)

enum class BlockType {
    NONE,
    BLOCK_START,
    BLOCK_MIDDLE,
    BLOCK_END
}

data class BlockBehavior(
    val type: BlockType,
    val pairingId: String? = null,
    val isIndividuallyDeletable: Boolean = false
)
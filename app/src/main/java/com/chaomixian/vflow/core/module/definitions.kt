// main/java/com/chaomixian/vflow/core/module/definitions.kt

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
 * 使用 Class<out Parcelable> 来接受任何 Parcelable 的子类型。
 */
data class InputDefinition(
    val id: String,
    val name: String,
    val staticType: ParameterType,
    val acceptsMagicVariable: Boolean = true,
    val acceptedMagicVariableTypes: Set<Class<out Parcelable>> = emptySet()
)

/**
 * 模块的一个输出参数定义。
 * @param type 输出变量的具体类型，必须是 Parcelable。
 */
data class OutputDefinition(
    val id: String,
    val name: String,
    val type: Class<out Parcelable>
)

/**
 * 模块的一个静态参数（在编辑器内配置）的定义。
 */
data class ParameterDefinition(
    val id: String,
    val name: String,
    val type: ParameterType,
    val defaultValue: Any? = null,
    val options: List<String> = emptyList()
)

/**
 * 积木块的类型，用于控制缩进和拖放逻辑。
 */
enum class BlockType {
    NONE,        // 普通原子模块
    BLOCK_START, // 块的开始（如 If, Loop）
    BLOCK_MIDDLE,  // 块的中间（如 Else）
    BLOCK_END      // 块的结束（如 EndIf, EndLoop）
}

/**
 * 定义了积木块的行为。
 * @param type 积木块的类型。
 * @param pairingId 用于将块的 start, middle, end 配对的唯一ID。
 * @param isIndividuallyDeletable 标记该积木块是否可以被单独删除（主要用于 Else）。
 */
data class BlockBehavior(
    val type: BlockType,
    val pairingId: String? = null,
    val isIndividuallyDeletable: Boolean = false
)
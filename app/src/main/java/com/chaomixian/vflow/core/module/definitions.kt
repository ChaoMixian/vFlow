// main/java/com/chaomixian/vflow/core/module/definitions.kt

package com.chaomixian.vflow.core.module

import android.os.Parcelable
import com.chaomixian.vflow.core.workflow.model.ActionStep
import kotlinx.parcelize.Parcelize

/**
 * 参数类型枚举。
 */
enum class ParameterType {
    STRING,
    NUMBER,
    BOOLEAN,
    ENUM,
    ANY
}

/**
 * 模块的一个输入参数定义。这个类现在统一了之前的“静态参数”和“输入”。
 * @param staticType 如果未连接魔法变量，UI上应显示的静态输入框类型。
 * @param defaultValue 此输入的默认值。
 * @param options 如果 staticType 是 ENUM，这里提供下拉选项。
 * @param acceptsMagicVariable 控制是否显示魔法变量连接按钮。
 * @param acceptedMagicVariableTypes 定义了此输入可以接受哪些类型的魔法变量。
 */
data class InputDefinition(
    val id: String,
    val name: String,
    val staticType: ParameterType,
    val defaultValue: Any? = null,
    val options: List<String> = emptyList(),
    val acceptsMagicVariable: Boolean = true,
    val acceptedMagicVariableTypes: Set<Class<out Parcelable>> = emptySet()
)

/**
 * 模块的一个输出参数定义。
 */
data class OutputDefinition(
    val id: String,
    val name: String,
    val type: Class<out Parcelable>
)

/**
 * 模块执行时的进度更新信息。
 */
data class ProgressUpdate(
    val message: String,
    val progressPercent: Int? = null
)

/**
 * 模块参数的验证结果。
 */
data class ValidationResult(
    val isValid: Boolean,
    val errorMessage: String? = null
)


/**
 * 积木块的类型，用于控制缩进和拖放逻辑。
 */
enum class BlockType {
    NONE,
    BLOCK_START,
    BLOCK_MIDDLE,
    BLOCK_END
}

/**
 * 定义了积木块的行为。
 */
data class BlockBehavior(
    val type: BlockType,
    val pairingId: String? = null,
    val isIndividuallyDeletable: Boolean = false
)
// main/java/com/chaomixian/vflow/core/module/definitions.kt

package com.chaomixian.vflow.core.module

import android.content.Context
import android.os.Parcelable
import android.view.View
import android.view.ViewGroup
import com.chaomixian.vflow.core.workflow.model.ActionStep
import kotlinx.parcelize.Parcelize

// --- 核心数据类 ---

data class ActionMetadata(
    val name: String,
    val description: String,
    val iconRes: Int,
    val category: String
)

/**
 * ViewHolder 模式，用于持有自定义编辑器视图的引用。
 */
abstract class CustomEditorViewHolder(val view: View)


// --- UI 提供者接口 ---

/**
 * 定义了模块UI的提供者。
 * 将所有 Android View 相关的逻辑从 ActionModule 中解耦出来。
 */
interface ModuleUIProvider {
    /**
     * 创建在 ActionStep 卡片中显示的自定义预览视图。
     */
    fun createPreview(context: Context, parent: ViewGroup, step: ActionStep): View?

    /**
     * 创建用于模块参数的自定义编辑器UI。
     */
    fun createEditor(
        context: Context,
        parent: ViewGroup,
        currentParameters: Map<String, Any?>,
        onParametersChanged: () -> Unit
    ): CustomEditorViewHolder

    /**
     * 从自定义编辑器UI中读取用户输入的参数。
     */
    fun readFromEditor(holder: CustomEditorViewHolder): Map<String, Any?>

    /**
     * 声明此UI提供者处理了哪些输入的UI渲染。
     * ActionEditorSheet 将不会为这些ID创建通用的UI。
     * @return 一个包含输入ID的Set。
     */
    fun getHandledInputIds(): Set<String>
}


// --- 参数与执行结果 ---

enum class ParameterType {
    STRING,
    NUMBER,
    BOOLEAN,
    ENUM,
    ANY
}

data class InputDefinition(
    val id: String,
    val name: String,
    val staticType: ParameterType,
    val defaultValue: Any? = null,
    val options: List<String> = emptyList(),
    val acceptsMagicVariable: Boolean = true,
    // 修改：不再依赖 Class，而是使用 typeName 字符串
    val acceptedMagicVariableTypes: Set<String> = emptySet(),
    val isHidden: Boolean = false
)

/**
 * 新增：代表一个条件选项的数据类
 * @param displayName 显示给用户的名称 (例如 "存在")
 * @param value 内部用于逻辑判断的值 (通常与 displayName 相同)
 */
@Parcelize
data class ConditionalOption(val displayName: String, val value: String) : Parcelable

data class OutputDefinition(
    val id: String,
    val name: String,
    // 修改：不再依赖 Class，而是使用 typeName 字符串
    val typeName: String,
    // --- 新增：定义该输出在作为条件时，有哪些可选项 ---
    // (新增了注释，解释其作用)
    // 例如，"查找文本" 的输出可以定义为 [("存在", "存在"), ("不存在", "不存在")]
    val conditionalOptions: List<ConditionalOption>? = null
)


data class ProgressUpdate(
    val message: String,
    val progressPercent: Int? = null
)

data class ValidationResult(
    val isValid: Boolean,
    val errorMessage: String? = null
)

// --- 信号定义 ---
sealed class ExecutionSignal {
    /** 跳转到指定的程序计数器位置 */
    data class Jump(val pc: Int) : ExecutionSignal()
    /** 新增：循环控制信号，用于结束或继续循环 */
    data class Loop(val action: LoopAction) : ExecutionSignal()
}

/**
 * 循环动作的枚举
 */
enum class LoopAction {
    START, END
}

/**
 * 模块执行结果的密封类 (重构)。
 */
sealed class ExecutionResult {
    data class Success(val outputs: Map<String, Any?> = emptyMap()) : ExecutionResult()
    data class Failure(val errorTitle: String, val errorMessage: String) : ExecutionResult()
    /** 新增：模块可以返回一个信号来控制执行流程 */
    data class Signal(val signal: ExecutionSignal) : ExecutionResult()
}


// --- 积木块行为 ---

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
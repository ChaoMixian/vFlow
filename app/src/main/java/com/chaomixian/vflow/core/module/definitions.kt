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
    val acceptedMagicVariableTypes: Set<String> = emptySet()
)

data class OutputDefinition(
    val id: String,
    val name: String,
    // 修改：不再依赖 Class，而是使用 typeName 字符串
    val typeName: String
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
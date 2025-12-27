// 文件: main/java/com/chaomixian/vflow/core/module/definitions.kt
package com.chaomixian.vflow.core.module

import android.content.Context
import android.content.Intent
import android.os.Parcelable
import android.view.View
import android.view.ViewGroup
import com.chaomixian.vflow.core.workflow.model.ActionStep
import kotlinx.parcelize.Parcelize

/**
 * 模块的元数据。
 * 定义了模块在用户界面中的显示信息。
 * @param name 模块的显示名称。
 * @param description 模块的功能描述。
 * @param iconRes 模块的图标资源ID。
 * @param category 模块所属的分类。
 */
data class ActionMetadata(
    val name: String,
    val description: String,
    val iconRes: Int,
    val category: String
)

/**
 * 自定义编辑器视图的 ViewHolder 基类。
 * 用于持有模块参数编辑界面的视图引用，方便管理。
 * @param view ViewHolder 的根视图。
 */
abstract class CustomEditorViewHolder(val view: View)


/**
 * 模块用户界面提供者接口。
 * 将模块的 Android View 相关逻辑从核心模块逻辑中分离出来，实现解耦。
 */
interface ModuleUIProvider {
    /**
     * 创建在工作流步骤卡片中显示的自定义预览视图。
     * 如果返回 null，则通常会回退到使用模块的 getSummary() 方法。
     * @param context Android 上下文。
     * @param parent 父视图组。
     * @param step 当前的动作步骤数据。
     * @param allSteps 整个工作流的所有步骤列表，用于解析变量名称。
     * @param onStartActivityForResult 一个回调函数，允许预览视图请求启动一个新的Activity并接收其结果。
     * @return 自定义预览视图，或 null。
     */
    fun createPreview(
        context: Context,
        parent: ViewGroup,
        step: ActionStep,
        allSteps: List<ActionStep>,
        onStartActivityForResult: ((Intent, (resultCode: Int, data: Intent?) -> Unit) -> Unit)? = null
    ): View?

    /**
     * 创建用于编辑模块参数的自定义用户界面。
     * @param context Android 上下文。
     * @param parent 父视图组。
     * @param currentParameters 当前步骤已保存的参数值。
     * @param onParametersChanged 当参数发生变化时需要调用的回调函数。
     * @param onMagicVariableRequested 当自定义UI需要请求魔法变量选择器时调用的回调。
     * @param allSteps 整个工作流的所有步骤列表，用于上下文分析。
     * @param onStartActivityForResult 一个回调函数，允许编辑器视图请求启动一个新的Activity并接收其结果。
     * @return 持有自定义编辑器视图的 CustomEditorViewHolder 实例。
     */
    fun createEditor(
        context: Context,
        parent: ViewGroup,
        currentParameters: Map<String, Any?>,
        onParametersChanged: () -> Unit,
        onMagicVariableRequested: ((inputId: String) -> Unit)? = null,
        allSteps: List<ActionStep>? = null,
        onStartActivityForResult: ((Intent, (resultCode: Int, data: Intent?) -> Unit) -> Unit)? = null
    ): CustomEditorViewHolder

    /**
     * 从自定义编辑器界面中读取用户输入的参数值。
     * @param holder 包含编辑器视图的 CustomEditorViewHolder 实例。
     * @return 一个包含参数ID和对应值的 Map。
     */
    fun readFromEditor(holder: CustomEditorViewHolder): Map<String, Any?>

    /**
     * 声明此 UI 提供者具体处理了哪些输入参数的界面渲染。
     * 对于在此返回的输入ID，通用的参数编辑界面将不会为它们创建默认的UI控件。
     * @return 一个包含已处理的输入参数ID的集合。
     */
    fun getHandledInputIds(): Set<String>
}

/**
 * 参数类型的枚举。
 * 用于定义模块输入参数的静态类型。
 */
enum class ParameterType {
    STRING,  // 字符串类型
    NUMBER,  // 数字类型
    BOOLEAN, // 布尔类型
    ENUM,    // 枚举类型 (通常配合 options 使用)
    ANY      // 任意类型 (通常由 ModuleUIProvider 处理其具体UI)
}

/**
 * 模块输入参数的定义。
 * @param id 参数的唯一标识符。
 * @param name 参数在UI中显示的名称。
 * @param staticType 参数的静态类型。
 * @param defaultValue 参数的默认值。
 * @param options 如果 staticType 是 ENUM，则这些是可选项列表。
 * @param acceptsMagicVariable 此参数是否接受魔法变量作为输入。
 * @param acceptsNamedVariable 此参数是否接受命名变量作为输入。
 * @param acceptedMagicVariableTypes 如果接受魔法变量，这里定义了可接受的魔法变量的类型名称集合。
 * @param supportsRichText 此文本输入是否支持富文本编辑（内嵌变量药丸）。
 * @param isHidden 此参数是否在UI中隐藏 (例如，内部使用的参数)。
 * @param isFolded 此参数是否归类到“更多设置”折叠区域中。
 */
data class InputDefinition(
    val id: String,
    val name: String,
    val staticType: ParameterType,
    val defaultValue: Any? = null,
    val options: List<String> = emptyList(),
    val acceptsMagicVariable: Boolean = true,
    val acceptsNamedVariable: Boolean = true,
    val acceptedMagicVariableTypes: Set<String> = emptySet(),
    val supportsRichText: Boolean = false,
    val isHidden: Boolean = false,
    val isFolded: Boolean = false
)

/**
 * 条件选项的数据类。
 * 用于定义模块输出在作为条件判断时的可选项，例如 "存在" 或 "不存在"。
 * @param displayName 显示给用户的名称 (例如 "等于", "不等于")。
 * @param value 内部用于逻辑判断的值 (可以与 displayName 不同，例如 "==", "!=")。
 */
@Parcelize
data class ConditionalOption(val displayName: String, val value: String) : Parcelable

/**
 * 模块输出参数的定义。
 * @param id 输出参数的唯一标识符。
 * @param name 输出参数在UI中显示的名称。
 * @param typeName 输出参数的类型名称 (例如 TextVariable.TYPE_NAME)。
 * @param conditionalOptions 如果此输出可以作为条件分支的依据，这里定义了可供选择的条件及其对应的值。
 */
data class OutputDefinition(
    val id: String,
    val name: String,
    val typeName: String,
    val conditionalOptions: List<ConditionalOption>? = null
)

/**
 * 模块执行过程中的进度更新信息。
 * @param message 描述当前进度的消息。
 * @param progressPercent 可选的进度百分比 (0-100)。
 */
data class ProgressUpdate(
    val message: String,
    val progressPercent: Int? = null
)

/**
 * 模块参数验证的结果。
 * @param isValid 参数是否有效。
 * @param errorMessage 如果参数无效，则包含相应的错误消息。
 */
data class ValidationResult(
    val isValid: Boolean,
    val errorMessage: String? = null
)

/**
 * 模块执行信号的密封类。
 * 用于模块在执行完毕后，向执行引擎发出特定的控制信号，例如跳转或循环控制。
 */
sealed class ExecutionSignal {
    /** 表示需要跳转到工作流中指定索引 (pc: program counter) 的步骤。 */
    data class Jump(val pc: Int) : ExecutionSignal()
    /** 表示循环控制相关的动作。 */
    data class Loop(val action: LoopAction) : ExecutionSignal()
    /** 表示需要跳出当前积木块。 */
    object Break : ExecutionSignal()
    /** 表示需要跳过当前循环的剩余步骤，直接进入下一次迭代。 */
    object Continue : ExecutionSignal()
    /** 表示需要正常地、无条件地终止工作流。 */
    object Stop : ExecutionSignal()
    /** 表示需要从子工作流返回一个值。 */
    data class Return(val result: Any?) : ExecutionSignal()
}

/**
 * 循环动作的类型枚举。
 * 用于配合 ExecutionSignal.Loop，指示循环的开始或结束。
 */
enum class LoopAction {
    START, // 标记循环块的开始
    END    // 标记循环块的结束
}

/**
 * 模块执行结果的密封类。
 * 表示模块执行后的不同状态。
 */
sealed class ExecutionResult {
    /** 表示模块成功执行。可以包含输出参数。 */
    data class Success(val outputs: Map<String, Any?> = emptyMap()) : ExecutionResult()
    /** 表示模块执行失败。包含错误标题和详细信息。 */
    data class Failure(val errorTitle: String, val errorMessage: String) : ExecutionResult()
    /** 表示模块执行完毕后发出一个控制信号，用于影响工作流的执行流程。 */
    data class Signal(val signal: ExecutionSignal) : ExecutionResult()
}

/**
 * 积木块类型的枚举。
 * 用于定义模块是否构成一个积木块结构 (如 If/Else, Loop)。
 */
enum class BlockType {
    NONE,          // 非积木块模块
    BLOCK_START,   // 积木块的开始 (如 If, Loop Start)
    BLOCK_MIDDLE,  // 积木块的中间部分 (如 Else If, Else)
    BLOCK_END      // 积木块的结束 (如 End If, Loop End)
}

/**
 * 模块的积木块行为定义。
 * @param type 积木块的类型。
 * @param pairingId 如果模块是积木块的一部分，此ID用于将相关的积木块模块（如Start和End）配对。
 * @param isIndividuallyDeletable 标记积木块的某个部分（通常是Middle或End）是否可以被独立删除。
 * 例如，If 的 End 块通常不能独立删除，必须与 Start 一起。
 */
data class BlockBehavior(
    val type: BlockType,
    val pairingId: String? = null,
    val isIndividuallyDeletable: Boolean = false
)
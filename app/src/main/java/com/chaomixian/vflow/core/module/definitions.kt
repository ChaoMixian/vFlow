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
 * @param nameStringRes 模块显示名称的字符串资源ID（用于国际化，可选）
 * @param descriptionStringRes 模块功能描述的字符串资源ID（用于国际化，可选）
 */
data class ActionMetadata(
    val name: String,
    val description: String,
    val iconRes: Int,
    val category: String,
    val nameStringRes: Int? = null,
    val descriptionStringRes: Int? = null
) {
    /**
     * 获取本地化的模块名称
     * @param context Android上下文
     * @return 本地化的名称，优先使用字符串资源
     */
    fun getLocalizedName(context: Context): String {
        return if (nameStringRes != null) context.getString(nameStringRes) else name
    }

    /**
     * 获取本地化的模块描述
     * @param context Android上下文
     * @return 本地化的描述，优先使用字符串资源
     */
    fun getLocalizedDescription(context: Context): String {
        return if (descriptionStringRes != null) context.getString(descriptionStringRes) else description
    }
}

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
 * 输入控件的风格类型。
 * 用于声明式地指定输入参数在编辑器中的显示方式。
 */
enum class InputStyle {
    /** 默认风格 - 根据类型自动选择（Spinner/Switch/TextInput） */
    DEFAULT,
    /** 芯片按钮组 - 适合少量选项的选择（如过滤器条件） */
    CHIP_GROUP,
    /** 下拉选择器 - 适合中等数量的选项 */
    DROPDOWN,
    /** 开关 - 适合布尔值 */
    SWITCH,
    /** 滑块 - 适合数值范围 */
    SLIDER
}

/**
 * 选择器类型。
 * 用于需要弹出对话框选择的场景（如文件、应用、Activity 等）。
 * 与 inputStyle 配合使用，pickerType 决定点击输入框时的行为。
 */
enum class PickerType {
    /** 无选择器，使用标准输入（默认） */
    NONE,
    /** 文件选择器 */
    FILE,
    /** 应用选择器 */
    APP,
    /** Activity 选择器（用于分享等场景） */
    ACTIVITY,
    /** 图片/媒体选择器 */
    MEDIA,
    /** 日期选择器 */
    DATE,
    /** 时间选择器 */
    TIME,
    /** 日期时间选择器 */
    DATETIME
}

/**
 * 模块输入参数的可见性条件。
 * 用于声明式地定义输入参数何时应该在UI中显示。
 *
 * 使用示例:
 * ```
 * InputDefinition(
 *     id = "count",
 *     name = "字符数量",
 *     visibility = InputVisibility.whenEquals("mode", "提取字符")
 * )
 * ```
 */
sealed class InputVisibility {
    companion object {
        /**
         * 该输入始终可见。
         */
        fun always(): InputVisibility = Always

        /**
         * 当指定参数等于给定值时显示。
         */
        fun whenEquals(dependsOn: String, value: Any): InputVisibility = WhenEquals(dependsOn, value)

        /**
         * 当指定参数不等于给定值时显示。
         */
        fun whenNotEquals(dependsOn: String, value: Any): InputVisibility = WhenNotEquals(dependsOn, value)

        /**
         * 当指定参数不等于给定值时显示（whenNotEquals 的别名）。
         */
        fun notEquals(dependsOn: String, value: Any): InputVisibility = WhenNotEquals(dependsOn, value)

        /**
         * 当指定参数在给定值列表中时显示。
         */
        fun whenIn(dependsOn: String, values: List<Any>): InputVisibility = WhenIn(dependsOn, values)

        /**
         * 当指定参数在给定值列表中时显示（whenIn 的别名）。
         */
        fun `in`(dependsOn: String, values: List<Any>): InputVisibility = WhenIn(dependsOn, values)

        /**
         * 当指定参数不在给定值列表中时显示。
         */
        fun whenNotIn(dependsOn: String, values: List<Any>): InputVisibility = WhenNotIn(dependsOn, values)

        /**
         * 当指定参数不在给定值列表中时显示（whenNotIn 的别名）。
         */
        fun notIn(dependsOn: String, values: List<Any>): InputVisibility = WhenNotIn(dependsOn, values)

        /**
         * 当指定参数为 true 时显示（适用于布尔类型）。
         */
        fun whenTrue(dependsOn: String): InputVisibility = WhenTrue(dependsOn)

        /**
         * 当指定参数为 false 时显示（适用于布尔类型）。
         */
        fun whenFalse(dependsOn: String): InputVisibility = WhenFalse(dependsOn)

        /**
         * 所有条件都必须满足时才显示。
         */
        fun and(vararg conditions: InputVisibility): InputVisibility = And(conditions.toList())

        /**
         * 任意一个条件满足就显示。
         */
        fun or(vararg conditions: InputVisibility): InputVisibility = Or(conditions.toList())

        /**
         * 取反条件。
         */
        fun not(condition: InputVisibility): InputVisibility = Not(condition)

        /**
         * 使用自定义函数判断可见性。
         * @param predicate 接收当前所有参数，返回是否显示该输入
         */
        fun whenPredicate(predicate: (Map<String, Any?>) -> Boolean): InputVisibility = Predicate(predicate)
    }

    /**
     * 评估此可见性条件是否满足。
     * @param currentParameters 当前步骤的参数值
     * @return 是否应该显示该输入
     */
    abstract fun isVisible(currentParameters: Map<String, Any?>): Boolean

    /**
     * 始终可见。
     */
    data object Always : InputVisibility() {
        override fun isVisible(currentParameters: Map<String, Any?>): Boolean = true
    }

    /**
     * 当指定参数等于给定值时显示。
     */
    data class WhenEquals(val dependsOn: String, val value: Any) : InputVisibility() {
        override fun isVisible(currentParameters: Map<String, Any?>): Boolean {
            val currentValue = currentParameters[dependsOn]
            return currentValue == value
        }
    }

    /**
     * 当指定参数不等于给定值时显示。
     */
    data class WhenNotEquals(val dependsOn: String, val value: Any) : InputVisibility() {
        override fun isVisible(currentParameters: Map<String, Any?>): Boolean {
            val currentValue = currentParameters[dependsOn]
            return currentValue != value
        }
    }

    /**
     * 当指定参数在给定值列表中时显示。
     */
    data class WhenIn(val dependsOn: String, val values: List<Any>) : InputVisibility() {
        override fun isVisible(currentParameters: Map<String, Any?>): Boolean {
            val currentValue = currentParameters[dependsOn]
            return currentValue in values
        }
    }

    /**
     * 当指定参数不在给定值列表中时显示。
     */
    data class WhenNotIn(val dependsOn: String, val values: List<Any>) : InputVisibility() {
        override fun isVisible(currentParameters: Map<String, Any?>): Boolean {
            val currentValue = currentParameters[dependsOn]
            return currentValue !in values
        }
    }

    /**
     * 当指定参数为 true 时显示。
     */
    data class WhenTrue(val dependsOn: String) : InputVisibility() {
        override fun isVisible(currentParameters: Map<String, Any?>): Boolean {
            return currentParameters[dependsOn] == true
        }
    }

    /**
     * 当指定参数为 false 时显示。
     */
    data class WhenFalse(val dependsOn: String) : InputVisibility() {
        override fun isVisible(currentParameters: Map<String, Any?>): Boolean {
            return currentParameters[dependsOn] == false
        }
    }

    /**
     * 所有条件都必须满足时才显示。
     */
    data class And(val conditions: List<InputVisibility>) : InputVisibility() {
        override fun isVisible(currentParameters: Map<String, Any?>): Boolean {
            return conditions.all { it.isVisible(currentParameters) }
        }
    }

    /**
     * 任意一个条件满足就显示。
     */
    data class Or(val conditions: List<InputVisibility>) : InputVisibility() {
        override fun isVisible(currentParameters: Map<String, Any?>): Boolean {
            return conditions.any { it.isVisible(currentParameters) }
        }
    }

    /**
     * 取反条件。
     */
    data class Not(val condition: InputVisibility) : InputVisibility() {
        override fun isVisible(currentParameters: Map<String, Any?>): Boolean {
            return !condition.isVisible(currentParameters)
        }
    }

    /**
     * 使用自定义函数判断可见性。
     */
    data class Predicate(val predicate: (Map<String, Any?>) -> Boolean) : InputVisibility() {
        override fun isVisible(currentParameters: Map<String, Any?>): Boolean {
            return predicate(currentParameters)
        }
    }
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
 * @param isHidden 此参数是否在UI中隐藏 (例如，内部使用的参数)。已废弃，请使用 visibility。
 * @param isFolded 此参数是否归类到"更多设置"折叠区域中。
 * @param visibility 参数的可见性条件，用于声明式控制输入何时显示。
 * @param hint 输入框的提示文本（如 placeholder）。
 * @param nameStringRes 参数显示名称的字符串资源ID（用于国际化，可选）
 * @param optionsStringRes 如果 staticType 是 ENUM，这些是可选项的字符串资源ID列表（用于国际化，可选）
 * @param hintStringRes 输入框提示文本的字符串资源ID（用于国际化，可选）
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
    val isFolded: Boolean = false,
    val inputStyle: InputStyle = InputStyle.DEFAULT,
    /** 滑块配置：(最小值, 最大值, 步长)，仅在 inputStyle 为 SLIDER 时有效 */
    val sliderConfig: Triple<Float, Float, Float>? = null,
    /** 选择器类型，点击输入框时触发对应的选择对话框 */
    val pickerType: PickerType = PickerType.NONE,
    val visibility: InputVisibility? = null,
    val hint: String? = null,
    val nameStringRes: Int? = null,
    val optionsStringRes: List<Int> = emptyList(),
    val hintStringRes: Int? = null
) {
    /**
     * 获取本地化的参数名称
     * @param context Android上下文
     * @return 本地化的名称，优先使用字符串资源
     */
    fun getLocalizedName(context: Context): String {
        return if (nameStringRes != null) context.getString(nameStringRes) else name
    }

    /**
     * 获取本地化的选项列表
     * @param context Android上下文
     * @return 本地化的选项列表，优先使用字符串资源
     */
    fun getLocalizedOptions(context: Context): List<String> {
        return if (optionsStringRes.isNotEmpty()) {
            optionsStringRes.map { context.getString(it) }
        } else {
            options
        }
    }

    /**
     * 获取本地化的提示文本
     * @param context Android上下文
     * @return 本地化的提示文本，优先使用字符串资源
     */
    fun getLocalizedHint(context: Context): String? {
        return if (hintStringRes != null) context.getString(hintStringRes) else hint
    }

    companion object {
        /**
         * 创建一个滑块配置
         * @param min 最小值
         * @param max 最大值
         * @param step 步长
         */
        fun slider(min: Float, max: Float, step: Float): Triple<Float, Float, Float> {
            return Triple(min, max, step)
        }
    }
}

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
 * @param listElementType 如果 typeName 是列表类型，此字段指定列表元素的类型（可选）。
 *                         例如：typeName = "vflow.type.list", listElementType = "vflow.type.screen_element"
 *                         表示输出是 List<VScreenElement>
 * @param nameStringRes 输出参数显示名称的字符串资源ID（用于国际化，可选）
 */
data class OutputDefinition(
    val id: String,
    val name: String,
    val typeName: String,
    val conditionalOptions: List<ConditionalOption>? = null,
    val listElementType: String? = null,
    val nameStringRes: Int? = null
) {
    /**
     * 获取本地化的输出名称
     * @param context Android上下文
     * @return 本地化的名称，优先使用字符串资源
     */
    fun getLocalizedName(context: Context): String {
        return if (nameStringRes != null) context.getString(nameStringRes) else name
    }
}

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
    /**
     * 表示模块执行失败。
     * @param errorTitle 错误标题
     * @param errorMessage 错误详细信息
     * @param partialOutputs 可选的部分输出。当"异常处理策略"为"跳过此步骤继续"时，
     *                       这些输出会被使用（而不是默认的 VNull）。
     *                       这允许模块在失败时提供语义化的默认值，如 count=0、list=[]。
     */
    data class Failure(
        val errorTitle: String,
        val errorMessage: String,
        val partialOutputs: Map<String, Any?> = emptyMap()
    ) : ExecutionResult()
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
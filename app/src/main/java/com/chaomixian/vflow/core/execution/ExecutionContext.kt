// 文件: main/java/com/chaomixian/vflow/core/execution/ExecutionContext.kt
package com.chaomixian.vflow.core.execution

import android.content.Context
import android.os.Parcelable
import com.chaomixian.vflow.core.types.VObject
import com.chaomixian.vflow.core.types.VObjectFactory
import com.chaomixian.vflow.core.types.basic.*
import com.chaomixian.vflow.core.workflow.model.ActionStep
import java.io.File
import java.util.*

/**
 * 循环控制流状态的密封类。
 */
sealed class LoopState {
    data class CountLoopState(
        val totalIterations: Long,
        var currentIteration: Long = 0
    ) : LoopState()

    data class ForEachLoopState(
        val itemList: List<VObject>,
        var currentIndex: Int = 0
    ) : LoopState()
}

/**
 * 执行时传递的上下文。
 *
 * 设计原则：所有变量（variables, magicVariables, namedVariables）都统一存储为 VObject。
 * 这类似于 Python 的 PyObject 统一类型系统，避免了 Kotlin 原生类型和 VObject 混用的复杂性。
 *
 * @param applicationContext 应用的全局上下文。
 * @param variables 存储用户在编辑器中设置的静态参数值（VObject）。
 * @param magicVariables 存储上游模块传递下来的动态魔法变量（VObject）。
 * @param services 一个服务容器，模块可以从中按需获取所需的服务实例。
 * @param allSteps 整个工作流的步骤列表。
 * @param currentStepIndex 当前正在执行的步骤的索引。
 * @param stepOutputs 存储所有已执行步骤的输出结果。
 * @param loopStack 一个用于管理嵌套循环状态的堆栈。
 * @param triggerData 触发器传入的外部数据（例如分享的内容）。
 * @param namedVariables 存储在整个工作流执行期间有效的命名变量（VObject）。
 * @param workflowStack 用于跟踪工作流调用栈，防止无限递归。
 * @param workDir 工作流执行时的工作文件夹。
 */
data class ExecutionContext(
    val applicationContext: Context,
    val variables: MutableMap<String, VObject>,
    val magicVariables: MutableMap<String, VObject>,
    val services: ExecutionServices,
    val allSteps: List<ActionStep>,
    val currentStepIndex: Int,
    val stepOutputs: Map<String, Map<String, VObject>>,
    val loopStack: Stack<LoopState>,
    val triggerData: Parcelable? = null,
    val namedVariables: MutableMap<String, VObject>,
    val workflowStack: Stack<String> = Stack(),
    val workDir: File
) {
    /**
     * 获取变量值，自动递归解析变量引用。
     * 优先从 magicVariables 获取，然后从 variables 获取。
     *
     * @param key 变量名
     * @return VObject，如果变量不存在则返回 VNull
     */
    fun getVariable(key: String): VObject {
        // 优先从 magicVariables 获取（已解析的魔法变量）
        val value = magicVariables[key] ?: variables[key]

        return when {
            value != null -> value
            // 检查 namedVariables
            namedVariables.containsKey(key) -> namedVariables[key]!!
            // 如果变量不存在，返回 VNull（而不是 null）
            else -> VNull
        }
    }

    /**
     * 设置命名变量的值（自动包装为 VObject）。
     *
     * @param key 变量名
     * @param value 变量值（任意类型，会自动包装为 VObject）
     */
    fun setVariable(key: String, value: Any?) {
        namedVariables[key] = VObjectFactory.from(value)
    }

    /**
     * 设置魔法变量的值（自动包装为 VObject）。
     *
     * @param key 变量名
     * @param value 变量值（任意类型，会自动包装为 VObject）
     */
    fun setMagicVariable(key: String, value: Any?) {
        magicVariables[key] = VObjectFactory.from(value)
    }

    /**
     * 获取变量值的原始类型值，自动解包 VObject。
     *
     * 适用于需要原始值而不是 VObject 包装的场景。
     * 注意：返回的 Any? 仍然需要通过 VObjectFactory 重新包装才能参与条件判断。
     *
     * @param key 变量名
     * @return 原始类型的值，如果变量不存在则返回 null
     */
    fun getVariableAsRaw(key: String): Any? {
        val value = getVariable(key)
        return if (value is VNull) null else value.raw
    }

    /**
     * 获取变量值作为数字。
     *
     * @param key 变量名
     * @return 数字值，如果无法转换则返回 null
     */
    fun getVariableAsNumber(key: String): Double? {
        val value = getVariable(key)
        return value.asNumber()
    }

    /**
     * 获取变量值作为布尔值。
     *
     * @param key 变量名
     * @return 布尔值，VNull 或无法转换时返回 null
     */
    fun getVariableAsBoolean(key: String): Boolean? {
        val value = getVariable(key)
        return if (value is VNull) null else value.asBoolean()
    }

    /**
     * 获取变量值作为整数。
     *
     * @param key 变量名
     * @return 整数值，如果无法转换则返回 null
     */
    fun getVariableAsInt(key: String): Int? {
        return getVariableAsNumber(key)?.toInt()
    }

    /**
     * 获取变量值作为长整数。
     *
     * @param key 变量名
     * @return 长整数值，如果无法转换则返回 null
     */
    fun getVariableAsLong(key: String): Long? {
        return getVariableAsNumber(key)?.toLong()
    }

    /**
     * 获取变量值作为字符串。
     * 此方法始终成功，因为任何 VObject 都可以转换为字符串。
     *
     * @param key 变量名
     * @param defaultValue 默认值（仅在 VNull 时使用）
     * @return 字符串值
     */
    fun getVariableAsString(key: String, defaultValue: String = ""): String {
        val value = getVariable(key)
        return when {
            value is VNull -> defaultValue
            value is VString -> value.raw
            else -> value.asString()
        }
    }

    /**
     * 获取参数的原始字符串值，不进行命名变量的自动解引用。
     *
     * 用于需要保留变量引用格式 [[varName]] 的场景，
     * 例如 GetVariableModule 读取指定变量、ModifyVariableModule 指定要修改变量名。
     *
     * @param key 参数名
     * @return 原始字符串值，如果不存在或不是字符串类型则返回 null
     */
    fun getParameterRaw(key: String): String? {
        return when (val vobj = variables[key]) {
            is VString -> vobj.raw
            else -> null
        }
    }

    /**
     * 获取步骤输出的 VObject
     *
     * @param stepId 步骤ID
     * @param outputKey 输出键
     * @return VObject 对象，如果不存在则返回 VNull
     */
    fun getOutput(stepId: String, outputKey: String): VObject {
        return stepOutputs[stepId]?.get(outputKey) ?: VNull
    }

    companion object {
        /**
         * 便捷方法：将 Map<String, Any?> 转换为 Map<String, VObject>
         * 用于从 JSON 或其他数据源加载变量配置
         */
        fun mapToVObjectMap(source: Map<String, Any?>): Map<String, VObject> {
            return source.mapValues { (_, value) -> VObjectFactory.from(value) }.toMutableMap()
        }

        /**
         * 便捷方法：将 MutableMap<String, Any?> 转换为 MutableMap<String, VObject>
         */
        fun mutableMapToVObjectMap(source: MutableMap<String, Any?>): MutableMap<String, VObject> {
            return source.mapValuesTo(mutableMapOf()) { (_, value) -> VObjectFactory.from(value) }
        }
    }
}

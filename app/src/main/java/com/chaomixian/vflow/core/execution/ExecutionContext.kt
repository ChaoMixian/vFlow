// 文件: main/java/com/chaomixian/vflow/core/execution/ExecutionContext.kt
package com.chaomixian.vflow.core.execution

import android.content.Context
import android.os.Parcelable
import com.chaomixian.vflow.core.types.VObject
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
        val itemList: List<Any?>,
        var currentIndex: Int = 0
    ) : LoopState()
}

/**
 * 执行时传递的上下文。
 * @param applicationContext 应用的全局上下文。
 * @param variables 存储用户在编辑器中设置的静态参数值。
 * @param magicVariables 存储上游模块传递下来的动态魔法变量。
 * @param services 一个服务容器，模块可以从中按需获取所需的服务实例。
 * @param allSteps 整个工作流的步骤列表。
 * @param currentStepIndex 当前正在执行的步骤的索引。
 * @param stepOutputs 存储所有已执行步骤的输出结果。
 * @param loopStack 一个用于管理嵌套循环状态的堆栈。
 * @param triggerData 触发器传入的外部数据（例如分享的内容）。
 * @param namedVariables 存储在整个工作流执行期间有效的命名变量。
 * @param workflowStack 用于跟踪工作流调用栈，防止无限递归。
 * @param workDir 工作流执行时的工作文件夹。
 */
data class ExecutionContext(
    val applicationContext: Context,
    val variables: MutableMap<String, Any?>,
    val magicVariables: MutableMap<String, Any?>,
    val services: ExecutionServices,
    val allSteps: List<ActionStep>,
    val currentStepIndex: Int,
    val stepOutputs: Map<String, Map<String, VObject>>,
    val loopStack: Stack<LoopState>,
    val triggerData: Parcelable? = null,
    val namedVariables: MutableMap<String, Any?>,
    val workflowStack: Stack<String> = Stack(),
    val workDir: File
) {
    /**
     * 获取变量值，自动递归解析变量引用。
     * 优先从 magicVariables 获取，然后从 variables 获取。
     *
     * 这个方法统一处理变量解析逻辑，避免在各个模块中重复代码。
     *
     * @param key 变量名
     * @return 解析后的值，如果变量不存在则返回 null
     */
    fun getVariable(key: String): Any? {
        // 优先从 magicVariables 获取（已解析的魔法变量）
        val value = magicVariables[key] ?: variables[key]

        // 如果是字符串且包含变量引用，递归解析
        // 这解决了字典值是变量引用时需要递归解析的问题
        return when (value) {
            is String -> {
                if (VariableResolver.hasVariableReference(value)) {
                    VariableResolver.resolve(value, this)
                } else {
                    value
                }
            }
            else -> value
        }
    }

    /**
     * 获取变量值作为字符串，自动处理类型转换。
     *
     * @param key 变量名
     * @param defaultValue 默认值（当变量不存在时返回）
     * @return 字符串形式的变量值
     */
    fun getVariableAsString(key: String, defaultValue: String = ""): String {
        val value = getVariable(key)
        return when (value) {
            is String -> value
            is VObject -> value.asString()
            null -> defaultValue
            else -> value.toString()
        }
    }

    /**
     * 获取步骤输出的 VObject
     *
     * @param stepId 步骤ID
     * @param outputKey 输出键
     * @return VObject 对象，如果不存在则返回 null
     */
    fun getOutput(stepId: String, outputKey: String): VObject? {
        return stepOutputs[stepId]?.get(outputKey)
    }
}
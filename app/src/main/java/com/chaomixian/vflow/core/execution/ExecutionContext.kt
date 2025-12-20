// 文件: main/java/com/chaomixian/vflow/core/execution/ExecutionContext.kt
package com.chaomixian.vflow.core.execution

import android.content.Context
import android.os.Parcelable
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
    val stepOutputs: Map<String, Map<String, Any?>>,
    val loopStack: Stack<LoopState>,
    val triggerData: Parcelable? = null,
    val namedVariables: MutableMap<String, Any?>,
    val workflowStack: Stack<String> = Stack(),
    val workDir: File
)
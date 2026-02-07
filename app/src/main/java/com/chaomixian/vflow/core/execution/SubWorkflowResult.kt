// 文件: main/java/com/chaomixian/vflow/core/execution/SubWorkflowResult.kt
package com.chaomixian.vflow.core.execution

import com.chaomixian.vflow.core.types.VObject

/**
 * 子工作流执行结果。
 * 包含子工作流的返回值以及所有命名变量。
 */
data class SubWorkflowResult(
    /** 子工作流的返回值（来自"停止并返回"模块） */
    val returnValue: Any?,
    /** 子工作流产生的所有命名变量 */
    val namedVariables: Map<String, VObject>
)

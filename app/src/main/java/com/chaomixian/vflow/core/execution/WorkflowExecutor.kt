// main/java/com/chaomixian/vflow/core/execution/WorkflowExecutor.kt

package com.chaomixian.vflow.core.execution

import android.util.Log
import com.chaomixian.vflow.core.module.ModuleRegistry
import com.chaomixian.vflow.core.workflow.model.Workflow
import com.chaomixian.vflow.services.AccessibilityService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object WorkflowExecutor {

    private val executorScope = CoroutineScope(Dispatchers.Default)

    fun execute(workflow: Workflow, service: AccessibilityService) {
        executorScope.launch {
            Log.d("WorkflowExecutor", "开始执行工作流: ${workflow.name}")

            val stepOutputs = mutableMapOf<String, Map<String, Any?>>()

            for (step in workflow.steps) {
                val module = ModuleRegistry.getModule(step.moduleId)
                if (module == null) {
                    Log.w("WorkflowExecutor", "未找到模块: ${step.moduleId}，跳过。")
                    continue
                }

                val context = ExecutionContext(
                    variables = step.parameters.toMutableMap(),
                    magicVariables = mutableMapOf(),
                    accessibilityService = service
                )

                step.parameters.forEach { (key, value) ->
                    if (value is String && value.startsWith("{{") && value.endsWith("}}")) {
                        val parts = value.removeSurrounding("{{", "}}").split('.')
                        val sourceStepId = parts.getOrNull(0)
                        val sourceOutputId = parts.getOrNull(1)

                        if (sourceStepId != null && sourceOutputId != null) {
                            val sourceOutputsMap = stepOutputs[sourceStepId]
                            val sourceOutputValue = sourceOutputsMap?.get(sourceOutputId)
                            if (sourceOutputValue != null) {
                                context.magicVariables[key] = sourceOutputValue
                            }
                        }
                    }
                }

                Log.d("WorkflowExecutor", " -> 执行: ${module.metadata.name}")

                // --- 核心修改：更新 execute 调用以包含进度回调 ---
                val result = module.execute(context) { progress ->
                    // 在这里可以处理进度更新，例如通过回调更新UI
                    Log.d("WorkflowExecutor", "[进度] ${module.metadata.name}: ${progress.message}")
                }

                if (result.success) {
                    if (result.outputs.isNotEmpty()) {
                        stepOutputs[step.id] = result.outputs
                    }
                } else {
                    Log.e("WorkflowExecutor", "模块 ${module.metadata.name} 执行失败，工作流中断。")
                    break
                }
            }
            Log.d("WorkflowExecutor", "工作流 '${workflow.name}' 执行完毕。")
        }
    }
}
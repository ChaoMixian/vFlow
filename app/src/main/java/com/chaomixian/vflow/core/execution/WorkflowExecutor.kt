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

            // 保存所有步骤的输出结果，以步骤的唯一ID作为键
            // 值现在是一个 Map<String, Any?>，对应模块的多个输出
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

                // --- 魔法变量解析 (已优化) ---
                step.parameters.forEach { (key, value) ->
                    if (value is String && value.startsWith("{{") && value.endsWith("}}")) {
                        // 引用格式为: "{{步骤ID.输出ID}}"
                        val parts = value.removeSurrounding("{{", "}}").split('.')
                        val sourceStepId = parts.getOrNull(0)
                        val sourceOutputId = parts.getOrNull(1) // 获取具名的输出ID

                        if (sourceStepId != null && sourceOutputId != null) {
                            val sourceOutputsMap = stepOutputs[sourceStepId]
                            val sourceOutputValue = sourceOutputsMap?.get(sourceOutputId)
                            if (sourceOutputValue != null) {
                                // 将解析到的值放入魔法变量map，供模块执行时使用
                                context.magicVariables[key] = sourceOutputValue
                            }
                        }
                    }
                }

                Log.d("WorkflowExecutor", " -> 执行: ${module.metadata.name}")

                val result = module.execute(context)

                if (result.success) {
                    // **【核心修复】**
                    // 1. 将 result.output 改为 result.outputs
                    // 2. 判断 .isNotEmpty() 而不是 != null
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
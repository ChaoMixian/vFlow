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

            // 1. 创建本次执行的上下文
            val context = ExecutionContext(
                variables = mutableMapOf(),
                accessibilityService = service
            )

            // 2. 遍历工作流的每一步
            for (step in workflow.steps) {
                // 3. 从注册表中查找对应的模块
                val module = ModuleRegistry.getModule(step.moduleId)
                if (module == null) {
                    Log.w("WorkflowExecutor", "未找到模块: ${step.moduleId}，跳过此步骤。")
                    continue
                }

                // 4. 准备模块的输入参数
                context.variables.putAll(step.parameters)

                Log.d("WorkflowExecutor", " -> 正在执行模块: ${module.metadata.name} (${module.id})")

                // 5. 执行模块
                val result = module.execute(context)

                // 6. 检查执行结果
                if (!result.success) {
                    Log.e("WorkflowExecutor", "模块 ${module.metadata.name} 执行失败，工作流中断。")
                    break // 中断工作流
                }
            }
            Log.d("WorkflowExecutor", "工作流 '${workflow.name}' 执行完毕。")
        }
    }
}
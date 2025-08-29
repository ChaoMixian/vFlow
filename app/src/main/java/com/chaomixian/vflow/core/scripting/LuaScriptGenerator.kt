package com.chaomixian.vflow.core.scripting

import com.chaomixian.vflow.core.workflow.model.Workflow

// 这是一个简化的初始版本，未来会更复杂
object LuaScriptGenerator {

    fun generate(workflow: Workflow): String {
        val sb = StringBuilder()
        sb.append("-- Workflow: ${workflow.name}\n")
        sb.append("-- ID: ${workflow.id}\n\n")

        // 暂时只处理 steps，不处理触发器
        workflow.steps.drop(1).forEach { step ->
            val moduleId = step.moduleId
            val params = step.parameters.entries.joinToString(", ") {
                // 简单处理参数，后续需要更复杂的类型判断
                "${it.key} = \"${it.value}\""
            }
            // 将模块ID转换为函数调用
            val functionCall = moduleId.replace('.', '(') + "{" + params + "})"
            sb.append("$functionCall\n")
        }

        return sb.toString()
    }
}
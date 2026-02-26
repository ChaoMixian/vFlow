package com.chaomixian.vflow.core.workflow.module.data

import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.types.VObject
import com.chaomixian.vflow.core.types.VObjectFactory
import com.chaomixian.vflow.core.workflow.WorkflowManager
import com.chaomixian.vflow.core.workflow.model.Workflow

class LoadVariablesModule : BaseModule() {
    override val id = "vflow.variable.load"
    override val metadata = ActionMetadata(
        nameStringRes = R.string.module_vflow_variable_load_name,
        descriptionStringRes = R.string.module_vflow_variable_load_desc,
        name = "载入变量",
        description = "从另一个工作流获取命名变量的使用权。",
        iconRes = R.drawable.rounded_system_update_alt_24,
        category = "数据"
    )

    override val uiProvider: ModuleUIProvider = LoadVariablesModuleUIProvider()

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "workflow_id",
            nameStringRes = R.string.param_vflow_variable_load_workflow_id_name,
            name = "工作流",
            staticType = ParameterType.STRING,
            acceptsMagicVariable = false,
            acceptsNamedVariable = false
        ),
        InputDefinition(
            id = "variable_names",
            nameStringRes = R.string.param_vflow_variable_load_variable_names_name,
            name = "变量",
            staticType = ParameterType.STRING,
            acceptsMagicVariable = false,
            acceptsNamedVariable = false
        ),
        InputDefinition(
            id = "mode",
            name = "模式",
            staticType = ParameterType.ENUM,
            defaultValue = "share",
            options = listOf("share", "copy"),
            acceptsMagicVariable = false
        )
    )

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val workflowId = context.getVariableAsString("workflow_id", "")
        val variableNamesStr = context.getVariableAsString("variable_names", "")
        val mode = context.getVariableAsString("mode", "share")

        if (workflowId.isBlank() || variableNamesStr.isBlank()) {
            return ExecutionResult.Success(emptyMap())
        }

        val variableNames = variableNamesStr.split(",").map { it.trim() }.filter { it.isNotBlank() }

        if (mode == "share") {
            // 共享模式：无需操作，namedVariables 本已共享
            return ExecutionResult.Success(emptyMap())
        }

        // 复制模式：从源工作流获取变量的初始值
        if (mode == "copy") {
            val workflowManager = WorkflowManager(context.applicationContext)
            val sourceWorkflow = workflowManager.getWorkflow(workflowId)

            if (sourceWorkflow == null) {
                return ExecutionResult.Success(emptyMap())
            }

            // 从源工作流的 CreateVariableModule 获取变量初始值
            for (varName in variableNames) {
                val varValue = getVariableInitialValue(sourceWorkflow, varName, context)
                if (varValue != null) {
                    context.setVariable(varName, varValue)
                }
            }

            return ExecutionResult.Success(emptyMap())
        }

        return ExecutionResult.Success(emptyMap())
    }

    /**
     * 从源工作流的 CreateVariableModule 获取变量的初始值
     */
    private fun getVariableInitialValue(workflow: Workflow, varName: String, context: ExecutionContext): VObject? {
        val createVarStep = workflow.steps.find { step ->
            step.moduleId == "vflow.variable.create" &&
            (step.parameters["variableName"] as? String) == varName
        } ?: return null

        // 获取初始值
        val rawValue = createVarStep.parameters["value"]

        // 如果有初始值，解析并返回
        if (rawValue != null) {
            return when (rawValue) {
                is String -> {
                    // 尝试解析魔法变量引用
                    if (rawValue.isMagicVariable()) {
                        context.getVariable(rawValue.removeSurrounding("{{", "}}"))
                    } else if (rawValue.isNamedVariable()) {
                        context.getVariable(rawValue.removeSurrounding("[[", "]]"))
                    } else {
                        VObjectFactory.from(rawValue)
                    }
                }
                else -> VObjectFactory.from(rawValue)
            }
        }

        // 返回默认值
        val type = createVarStep.parameters["type"] as? String ?: "文本"
        return getDefaultValue(type)
    }

    /**
     * 获取类型的默认值
     */
    private fun getDefaultValue(type: String): VObject {
        return when (type) {
            "文本" -> VObjectFactory.from("")
            "数字" -> VObjectFactory.from(0)
            "布尔" -> VObjectFactory.from(false)
            "字典" -> VObjectFactory.from(emptyMap<String, Any>())
            "列表" -> VObjectFactory.from(emptyList<Any>())
            "图像" -> VObjectFactory.from("")
            "坐标" -> VObjectFactory.from(mapOf("x" to 0, "y" to 0))
            else -> VObjectFactory.from("")
        }
    }
}

package com.chaomixian.vflow.core.workflow.module.system

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.execution.VariableResolver
import com.chaomixian.vflow.core.execution.JsExecutor
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.types.VObjectFactory
import com.chaomixian.vflow.core.types.basic.VDictionary
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.ui.workflow_editor.PillUtil

/**
 * "JavaScript 脚本" 模块。
 * 允许用户编写和执行 JavaScript 脚本，可以调用其他模块作为函数，
 * 并处理输入输出，实现复杂的自定义逻辑。
 */
class JsModule : BaseModule() {

    override val id = "vflow.system.js"
    override val metadata = ActionMetadata(
        name = "JavaScript脚本",  // Fallback
        nameStringRes = R.string.module_vflow_system_js_name,
        description = "执行一段JavaScript脚本，可调用其他模块功能。",  // Fallback
        descriptionStringRes = R.string.module_vflow_system_js_desc,
        iconRes = R.drawable.rounded_js_24,
        category = "应用与系统"
    )

    override val uiProvider: ModuleUIProvider? = JsModuleUIProvider()

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "script",
            name = "JavaScript 脚本",
            staticType = ParameterType.STRING,
            defaultValue = "// vflow.device.toast({ message: 'Hello from JavaScript!' })\n\n// 从输入变量获取值\n// var myVar = inputs.my_variable\n\n// 直接返回值作为输出\nvar r = {};\nr.result = 1+1;\nr;",
            acceptsMagicVariable = false
        ),
        InputDefinition(
            id = "inputs",
            name = "脚本输入",
            staticType = ParameterType.ANY,
            defaultValue = emptyMap<String, Any>(),
            acceptsMagicVariable = true
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("outputs", "脚本返回值", VTypeRegistry.DICTIONARY.id)
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val script = step.parameters["script"] as? String ?: "..."
        val firstLine = script.trim().lines().firstOrNull { it.isNotBlank() && !it.trim().startsWith("//") }
            ?: context.getString(R.string.summary_empty_script)

        val scriptPill = PillUtil.Pill(firstLine, "script")

        return PillUtil.buildSpannable(context,
            context.getString(R.string.summary_vflow_system_js_prefix),
            scriptPill
        )
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val script = context.getVariableAsString("script", "")
        if (script.isNullOrBlank()) {
            return ExecutionResult.Failure("脚本错误", "JavaScript脚本内容不能为空。")
        }

        val scriptInputs = mutableMapOf<String, Any?>()
        val inputsObj = context.getVariable("inputs")
        val inputMappings = when (inputsObj) {
            is VDictionary -> inputsObj.raw.mapValues { it.value.asString() }
            else -> emptyMap()
        }

        inputMappings.forEach { (varName, magicVarRef) ->
            if (magicVarRef.isMagicVariable()) {
                // 使用公共解析方法，支持嵌套属性如 .round
                scriptInputs[varName] = VariableResolver.resolveValue(magicVarRef, context)
            } else {
                scriptInputs[varName] = magicVarRef
            }
        }

        onProgress(ProgressUpdate("正在准备JavaScript环境..."))
        val jsExecutor = JsExecutor(context)

        return try {
            onProgress(ProgressUpdate("正在执行JavaScript脚本..."))
            val resultTable = jsExecutor.execute(script, scriptInputs)
            onProgress(ProgressUpdate("脚本执行完成"))
            val vMap = resultTable.mapValues { VObjectFactory.from(it.value) }
            ExecutionResult.Success(mapOf("outputs" to VDictionary(vMap)))
        } catch (e: Exception) {
            ExecutionResult.Failure("JavaScript脚本执行失败", e.localizedMessage ?: "未知错误")
        }
    }
}

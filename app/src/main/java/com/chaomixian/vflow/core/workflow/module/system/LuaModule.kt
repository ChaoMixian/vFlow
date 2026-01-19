// 文件: main/java/com/chaomixian/vflow/core/workflow/module/system/LuaModule.kt
package com.chaomixian.vflow.core.workflow.module.system
import com.chaomixian.vflow.core.types.VTypeRegistry

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.execution.LuaExecutor
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.ui.workflow_editor.PillUtil

/**
 * "Lua脚本" 模块。
 * 允许用户编写和执行 Lua 脚本，可以调用其他模块作为函数，
 * 并处理输入输出，实现复杂的自定义逻辑。
 */
class LuaModule : BaseModule() {

    override val id = "vflow.system.lua"
    override val metadata = ActionMetadata(
        name = "Lua脚本",
        description = "执行一段Lua脚本，可调用其他模块功能。",
        iconRes = R.drawable.rounded_lua_24,
        category = "应用与系统"
    )

    override val uiProvider: ModuleUIProvider? = LuaModuleUIProvider()

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "script",
            name = "Lua 脚本",
            staticType = ParameterType.STRING,
            defaultValue = "-- vflow.device.toast({ message = 'Hello from Lua!' })\n\n-- 从输入变量获取值\n-- local my_var = inputs.my_variable\n\n-- 返回一个table作为输出\nreturn { result = 'some value' }",
            acceptsMagicVariable = false
        ),
        InputDefinition(
            id = "inputs",
            name = "脚本输入",
            staticType = ParameterType.ANY,
            defaultValue = emptyMap<String, Any>(),
            acceptsMagicVariable = true // 这里的接受其实是其value接受魔法变量
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("outputs", "脚本返回值", VTypeRegistry.DICTIONARY.id)
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val script = step.parameters["script"] as? String ?: "..."
        val firstLine = script.trim().lines().firstOrNull { it.isNotBlank() && !it.trim().startsWith("--") } ?: "空脚本"

        // 更新 Pill 的构造以匹配新的签名
        val scriptPill = PillUtil.Pill(firstLine, "script")

        return PillUtil.buildSpannable(context,
            "执行Lua脚本: ",
            scriptPill
        )
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val script = context.variables["script"] as? String
        if (script.isNullOrBlank()) {
            return ExecutionResult.Failure("脚本错误", "Lua脚本内容不能为空。")
        }

        val scriptInputs = mutableMapOf<String, Any?>()
        val inputMappings = context.variables["inputs"] as? Map<String, String> ?: emptyMap()

        inputMappings.forEach { (varName, magicVarRef) ->
            if (magicVarRef.isMagicVariable()) {
                val parts = magicVarRef.removeSurrounding("{{", "}}").split('.')
                val sourceStepId = parts.getOrNull(0)
                val sourceOutputId = parts.getOrNull(1)
                if (sourceStepId != null && sourceOutputId != null) {
                    // stepOutputs 现在包含 VObject，需要转换为 raw 值
                    val vObj = context.stepOutputs[sourceStepId]?.get(sourceOutputId)
                    scriptInputs[varName] = vObj?.raw ?: vObj?.asString()
                }
            } else {
                scriptInputs[varName] = magicVarRef
            }
        }

        onProgress(ProgressUpdate("正在准备Lua环境..."))
        val luaExecutor = LuaExecutor(context)

        return try {
            onProgress(ProgressUpdate("正在执行Lua脚本..."))
            val resultTable = luaExecutor.execute(script, scriptInputs)
            onProgress(ProgressUpdate("脚本执行完成"))
            ExecutionResult.Success(mapOf("outputs" to DictionaryVariable(resultTable)))
        } catch (e: Exception) {
            ExecutionResult.Failure("Lua脚本执行失败", e.localizedMessage ?: "未知错误")
        }
    }
}
// 文件: main/java/com/chaomixian/vflow/core/workflow/module/scripted/ScriptedModule.kt
package com.chaomixian.vflow.core.workflow.module.scripted

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.execution.LuaExecutor
import com.chaomixian.vflow.core.execution.VariableResolver
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.permissions.Permission
import com.chaomixian.vflow.permissions.PermissionManager
import com.chaomixian.vflow.permissions.PermissionType
import com.chaomixian.vflow.ui.workflow_editor.PillUtil

/**
 * 动态脚本模块。
 * 基于 manifest.json 定义 UI，基于 script.lua 执行逻辑。
 */
class ScriptedModule(
    private val manifest: ModuleManifest,
    private val scriptContent: String
) : BaseModule() {

    override val id = manifest.id
    override val metadata = ActionMetadata(
        name = manifest.name,
        description = manifest.description,
        iconRes = R.drawable.rounded_lua_24,
        category = manifest.category
    )

    // 动态映射权限
    // 支持动态生成 Runtime 权限对象
    override val requiredPermissions: List<Permission> by lazy {
        val allPerms = PermissionManager.allKnownPermissions

        manifest.permissions?.mapNotNull { reqId ->
            // 尝试从已知列表查找
            val known = allPerms.find { it.id == reqId }
            if (known != null) return@mapNotNull known

            // 如果是标准的 Android 运行时权限，但未在 Manager 中预定义，则动态创建一个
            if (reqId.startsWith("android.permission.")) {
                return@mapNotNull Permission(
                    id = reqId,
                    name = reqId.substringAfterLast('.').replace('_', ' '), // 生成一个可读的名字
                    description = "脚本模块请求的系统权限。",
                    type = PermissionType.RUNTIME
                )
            }

            // 未知权限 ID，忽略
            null
        } ?: emptyList()
    }

    override fun getInputs(): List<InputDefinition> {
        // 处理 inputs 为 null 的情况
        return manifest.inputs?.map { it.toInputDefinition() } ?: emptyList()
    }

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> {
        // 处理 outputs 为 null 的情况
        return manifest.outputs?.map { it.toOutputDefinition() } ?: emptyList()
    }

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val firstInput = getInputs().firstOrNull()
        return if (firstInput != null) {
            val pill = PillUtil.createPillFromParam(
                step.parameters[firstInput.id],
                firstInput
            )
            PillUtil.buildSpannable(context, "${manifest.name} ", pill)
        } else {
            manifest.name
        }
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        // 解析所有输入参数
        val inputs = mutableMapOf<String, Any?>()
        getInputs().forEach { inputDef ->
            val rawValue = context.variables[inputDef.id]
            if (inputDef.acceptsMagicVariable) {
                val magicValue = context.magicVariables[inputDef.id]
                if (magicValue != null) {
                    inputs[inputDef.id] = magicValue
                } else if (rawValue is String) {
                    inputs[inputDef.id] = VariableResolver.resolve(rawValue, context)
                } else {
                    inputs[inputDef.id] = rawValue
                }
            } else {
                inputs[inputDef.id] = rawValue
            }
        }

        // 准备 Lua 环境并执行
        val luaExecutor = LuaExecutor(context)
        return try {
            onProgress(ProgressUpdate("正在执行脚本..."))

            val resultTable = luaExecutor.execute(scriptContent, inputs)

            // 映射输出
            val outputs = mutableMapOf<String, Any?>()
            // 处理 outputs 为 null 的情况
            manifest.outputs?.forEach { outputDef ->
                val value = resultTable[outputDef.id]
                if (value != null) {
                    outputs[outputDef.id] = wrapToVariable(value, outputDef.type)
                }
            }

            onProgress(ProgressUpdate("脚本执行完成"))
            ExecutionResult.Success(outputs)
        } catch (e: Exception) {
            ExecutionResult.Failure("模块执行出错", e.message ?: "未知错误")
        }
    }

    private fun wrapToVariable(value: Any, typeHint: String): Any {
        return when (typeHint.lowercase()) {
            "text" -> TextVariable(value.toString())
            "number" -> {
                val num = value.toString().toDoubleOrNull() ?: 0.0
                NumberVariable(num)
            }
            "boolean" -> BooleanVariable(value.toString().toBoolean())
            "list" -> {
                if (value is List<*>) ListVariable(value) else ListVariable(emptyList())
            }
            "dictionary" -> {
                if (value is Map<*, *>) {
                    @Suppress("UNCHECKED_CAST")
                    DictionaryVariable(value as Map<String, Any?>)
                } else DictionaryVariable(emptyMap())
            }
            else -> TextVariable(value.toString())
        }
    }
}
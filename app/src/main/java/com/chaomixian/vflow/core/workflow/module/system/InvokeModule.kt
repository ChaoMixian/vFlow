// 文件: main/java/com/chaomixian/vflow/core/workflow/module/system/InvokeModule.kt
package com.chaomixian.vflow.core.workflow.module.system

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.execution.VariableResolver
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VBoolean
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.permissions.Permission
import com.chaomixian.vflow.services.ShellManager
import com.chaomixian.vflow.ui.workflow_editor.PillUtil
import androidx.core.net.toUri

class InvokeModule : BaseModule() {

    override val id = "vflow.system.invoke"
    override val metadata = ActionMetadata(
        name = "通用调用",
        description = "执行 Intent、打开链接、发送广播或启动服务。",
        iconRes = R.drawable.rounded_call_to_action_24,
        category = "应用与系统"
    )

    override val uiProvider: ModuleUIProvider = InvokeModuleUIProvider()

    // 定义支持的模式
    private val modes = listOf("链接/Uri", "Activity", "Broadcast", "Service")

    override fun getRequiredPermissions(step: ActionStep?): List<Permission> {
        return ShellManager.getRequiredPermissions(com.chaomixian.vflow.core.logging.LogManager.applicationContext)
    }

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition("mode", "调用方式", ParameterType.ENUM, "链接/Uri", options = modes),
        InputDefinition("uri", "链接/Data", ParameterType.STRING, "", acceptsMagicVariable = true, supportsRichText = true),
        InputDefinition("action", "Action", ParameterType.STRING, "", acceptsMagicVariable = true),
        InputDefinition("package", "Package", ParameterType.STRING, "", acceptsMagicVariable = true),
        InputDefinition("class", "Class", ParameterType.STRING, "", acceptsMagicVariable = true),
        InputDefinition("type", "MIME Type", ParameterType.STRING, "", acceptsMagicVariable = true),
        InputDefinition("flags", "Flags (Int)", ParameterType.STRING, "", acceptsMagicVariable = true),
        InputDefinition("extras", "扩展参数", ParameterType.ANY, defaultValue = emptyMap<String, String>(), acceptsMagicVariable = true),
        InputDefinition("show_advanced", "显示高级", ParameterType.BOOLEAN, false, isHidden = true)
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("success", "是否成功", VTypeRegistry.BOOLEAN.id)
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val mode = step.parameters["mode"] as? String ?: "链接/Uri"

        return when (mode) {
            "链接/Uri" -> {
                val uriPill = PillUtil.createPillFromParam(step.parameters["uri"], getInputs().find { it.id == "uri" })
                PillUtil.buildSpannable(context, "打开链接 ", uriPill)
            }
            "Activity" -> {
                // 如果有包名显示包名，否则显示Action，否则显示"Activity"
                val pkg = step.parameters["package"] as? String
                val action = step.parameters["action"] as? String
                val desc = when {
                    !pkg.isNullOrBlank() -> "应用 ($pkg)"
                    !action.isNullOrBlank() -> "Action ($action)"
                    else -> "Activity"
                }
                PillUtil.buildSpannable(context, "启动 $desc")
            }
            "Broadcast" -> {
                val action = step.parameters["action"] as? String ?: "广播"
                val actionPill = PillUtil.createPillFromParam(action, getInputs().find { it.id == "action" })
                PillUtil.buildSpannable(context, "发送广播 ", actionPill)
            }
            "Service" -> {
                val pkg = step.parameters["package"] as? String ?: "服务"
                val pkgPill = PillUtil.createPillFromParam(pkg, getInputs().find { it.id == "package" })
                PillUtil.buildSpannable(context, "启动服务 ", pkgPill)
            }
            else -> metadata.name
        }
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val mode = context.variables["mode"] as? String ?: "链接/Uri"

        // 解析所有参数
        val rawUri = context.variables["uri"]?.toString() ?: ""
        val uriStr = VariableResolver.resolve(rawUri, context)

        val rawAction = context.variables["action"]?.toString() ?: ""
        val actionStr = VariableResolver.resolve(rawAction, context).ifBlank { null }

        val pkgStr = VariableResolver.resolve(context.variables["package"]?.toString() ?: "", context).ifBlank { null }
        val clsStr = VariableResolver.resolve(context.variables["class"]?.toString() ?: "", context).ifBlank { null }
        val typeStr = VariableResolver.resolve(context.variables["type"]?.toString() ?: "", context).ifBlank { null }

        val rawFlags = context.variables["flags"]?.toString() ?: ""
        val flagsInt = rawFlags.toIntOrNull()

        @Suppress("UNCHECKED_CAST")
        val extrasMap = (context.magicVariables["extras"] as? DictionaryVariable)?.value
            ?: (context.variables["extras"] as? Map<String, Any?>)
            ?: emptyMap()

        val intent = Intent()

        // 基础配置
        if (!uriStr.isBlank()) {
            try {
                val uri = uriStr.toUri()
                if (typeStr != null) {
                    intent.setDataAndType(uri, typeStr)
                } else {
                    intent.data = uri
                }
            } catch (_: Exception) {
                return ExecutionResult.Failure("无效 URI", "无法解析 URI: $uriStr")
            }
        } else if (typeStr != null) {
            intent.type = typeStr
        }

        if (actionStr != null) intent.action = actionStr
        if (pkgStr != null) {
            if (clsStr != null) {
                intent.component = ComponentName(pkgStr, clsStr)
            } else {
                intent.setPackage(pkgStr)
            }
        }

        // Flags 处理
        if (flagsInt != null) {
            intent.flags = flagsInt
        } else if (mode == "Activity" || mode == "链接/Uri") {
            // 默认添加 NEW_TASK，否则从 Service context 启动 Activity 会崩溃
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        // Extras 智能类型推断与注入
        extrasMap.forEach { (k, v) ->
            val strVal = v.toString()
            when {
                strVal.equals("true", ignoreCase = true) -> intent.putExtra(k, true)
                strVal.equals("false", ignoreCase = true) -> intent.putExtra(k, false)
                strVal.toIntOrNull() != null -> intent.putExtra(k, strVal.toInt())
                strVal.toDoubleOrNull() != null -> intent.putExtra(k, strVal.toDouble())
                else -> intent.putExtra(k, strVal) // 默认为字符串
            }
        }

        onProgress(ProgressUpdate("正在执行调用 ($mode)..."))

        try {
            val appContext = context.applicationContext
            when (mode) {
                "链接/Uri" -> {
                    if (intent.action == null) intent.action = Intent.ACTION_VIEW
                    appContext.startActivity(intent)
                }
                "Activity" -> {
                    appContext.startActivity(intent)
                }
                "Broadcast" -> {
                    appContext.sendBroadcast(intent)
                }
                "Service" -> {
                    // 尝试启动服务 (兼容前台服务)
                    try {
                        appContext.startService(intent)
                    } catch (e: IllegalStateException) {
                        // Android 8.0+ 后台启动限制
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            appContext.startForegroundService(intent)
                        } else {
                            throw e
                        }
                    }
                }
            }
            return ExecutionResult.Success(mapOf("success" to VBoolean(true)))
        } catch (e: Exception) {
            return ExecutionResult.Failure("调用失败", "执行 Intent 失败: ${e.message}\nIntent: $intent")
        }
    }
}
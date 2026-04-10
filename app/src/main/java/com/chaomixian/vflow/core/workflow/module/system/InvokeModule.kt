// 文件: main/java/com/chaomixian/vflow/core/workflow/module/system/InvokeModule.kt
package com.chaomixian.vflow.core.workflow.module.system

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.execution.VariableResolver
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.types.VObject
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.*
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.permissions.Permission
import com.chaomixian.vflow.services.ShellManager
import com.chaomixian.vflow.ui.workflow_editor.PillUtil
import androidx.core.net.toUri

class InvokeModule : BaseModule() {
    companion object {
        private const val MODE_URI = "uri"
        private const val MODE_ACTIVITY = "activity"
        private const val MODE_BROADCAST = "broadcast"
        private const val MODE_SERVICE = "service"
    }

    override val id = "vflow.system.invoke"
    override val metadata = ActionMetadata(
        name = "通用调用",  // Fallback
        nameStringRes = R.string.module_vflow_system_invoke_name,
        description = "执行 Intent、打开链接、发送广播或启动服务。",  // Fallback
        descriptionStringRes = R.string.module_vflow_system_invoke_desc,
        iconRes = R.drawable.rounded_call_to_action_24,
        category = "应用与系统",
        categoryId = "device"
    )

    override val uiProvider: ModuleUIProvider = InvokeModuleUIProvider()

    // 定义支持的模式
    private val modes = listOf(MODE_URI, MODE_ACTIVITY, MODE_BROADCAST, MODE_SERVICE)

    override fun getRequiredPermissions(step: ActionStep?): List<Permission> {
        return ShellManager.getRequiredPermissions(com.chaomixian.vflow.core.logging.LogManager.applicationContext)
    }

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            "mode",
            "调用方式",
            ParameterType.ENUM,
            MODE_URI,
            options = modes,
            nameStringRes = R.string.param_vflow_system_invoke_mode_name,
            optionsStringRes = listOf(
                R.string.option_vflow_system_invoke_mode_uri,
                R.string.option_vflow_system_invoke_mode_activity,
                R.string.option_vflow_system_invoke_mode_broadcast,
                R.string.option_vflow_system_invoke_mode_service
            ),
            legacyValueMap = mapOf(
                "链接/Uri" to MODE_URI,
                "Link/Uri" to MODE_URI,
                "Activity" to MODE_ACTIVITY,
                "Broadcast" to MODE_BROADCAST,
                "Service" to MODE_SERVICE
            )
        ),
        InputDefinition("uri", "链接/Data", ParameterType.STRING, "", acceptsMagicVariable = true, supportsRichText = true, nameStringRes = R.string.param_vflow_system_invoke_uri_name),
        InputDefinition("action", "Action", ParameterType.STRING, "", acceptsMagicVariable = true, nameStringRes = R.string.param_vflow_system_invoke_action_name),
        InputDefinition("package", "Package", ParameterType.STRING, "", acceptsMagicVariable = true, nameStringRes = R.string.param_vflow_system_invoke_package_name),
        InputDefinition("class", "Class", ParameterType.STRING, "", acceptsMagicVariable = true, nameStringRes = R.string.param_vflow_system_invoke_class_name),
        InputDefinition("type", "MIME Type", ParameterType.STRING, "", acceptsMagicVariable = true, nameStringRes = R.string.param_vflow_system_invoke_type_name),
        InputDefinition("flags", "Flags (Int)", ParameterType.STRING, "", acceptsMagicVariable = true, nameStringRes = R.string.param_vflow_system_invoke_flags_name),
        InputDefinition("extras", "扩展参数", ParameterType.ANY, defaultValue = emptyMap<String, String>(), acceptsMagicVariable = true, nameStringRes = R.string.param_vflow_system_invoke_extras_name),
        InputDefinition("show_advanced", "显示高级", ParameterType.BOOLEAN, false, isHidden = true, nameStringRes = R.string.param_vflow_system_invoke_show_advanced_name)
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("success", "是否成功", VTypeRegistry.BOOLEAN.id, nameStringRes = R.string.output_vflow_system_invoke_success_name)
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val mode = step.parameters["mode"] as? String ?: MODE_URI

        return when (mode) {
            MODE_URI -> {
                val uriPill = PillUtil.createPillFromParam(step.parameters["uri"], getInputs().find { it.id == "uri" })
                PillUtil.buildSpannable(context, context.getString(R.string.summary_vflow_system_invoke_open_link), uriPill)
            }
            MODE_ACTIVITY -> {
                // 如果有包名显示包名，否则显示Action，否则显示"Activity"
                val pkg = step.parameters["package"] as? String
                val action = step.parameters["action"] as? String
                val desc = when {
                    !pkg.isNullOrBlank() -> context.getString(R.string.summary_vflow_system_invoke_app, pkg)
                    !action.isNullOrBlank() -> "Action ($action)"
                    else -> "Activity"
                }
                PillUtil.buildSpannable(context, context.getString(R.string.summary_vflow_system_invoke_start, desc))
            }
            MODE_BROADCAST -> {
                val action = step.parameters["action"] as? String ?: context.getString(R.string.summary_vflow_system_invoke_broadcast)
                val actionPill = PillUtil.createPillFromParam(action, getInputs().find { it.id == "action" })
                PillUtil.buildSpannable(context, context.getString(R.string.summary_vflow_system_invoke_send_broadcast), actionPill)
            }
            MODE_SERVICE -> {
                val pkg = step.parameters["package"] as? String ?: context.getString(R.string.summary_vflow_system_invoke_service)
                val pkgPill = PillUtil.createPillFromParam(pkg, getInputs().find { it.id == "package" })
                PillUtil.buildSpannable(context, context.getString(R.string.summary_vflow_system_invoke_start_service), pkgPill)
            }
            else -> metadata.getLocalizedName(context)
        }
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val modeInput = getInputs().first { it.id == "mode" }
        val rawMode = context.getVariableAsString("mode", MODE_URI)
        val mode = modeInput.normalizeEnumValue(rawMode) ?: rawMode

        // 解析所有参数
        val rawUri = context.getVariableAsString("uri", "")
        val uriStr = VariableResolver.resolve(rawUri, context)

        val rawAction = context.getVariableAsString("action", "")
        val actionStr = VariableResolver.resolve(rawAction, context).ifBlank { null }

        val pkgStr = VariableResolver.resolve(context.getVariableAsString("package", ""), context).ifBlank { null }
        val clsStr = VariableResolver.resolve(context.getVariableAsString("class", ""), context).ifBlank { null }
        val typeStr = VariableResolver.resolve(context.getVariableAsString("type", ""), context).ifBlank { null }

        val rawFlags = context.getVariableAsString("flags", "")
        val flagsInt = rawFlags.toIntOrNull()

        val extrasMap = context.getVariableAsDictionary("extras")?.raw ?: emptyMap()

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
        } else if (mode == MODE_ACTIVITY || mode == MODE_URI) {
            // 默认添加 NEW_TASK，否则从 Service context 启动 Activity 会崩溃
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        // Extras 需要先解包 VObject，再写入 Intent，避免把 data class 的调试字符串透传出去。
        extrasMap.forEach { (k, v) ->
            putIntentExtra(intent, k, coerceIntentExtraValue(v))
        }

        onProgress(ProgressUpdate("正在执行调用 ($mode)..."))

        try {
            val appContext = context.applicationContext
            when (mode) {
                MODE_URI -> {
                    if (intent.action == null) intent.action = Intent.ACTION_VIEW
                    appContext.startActivity(intent)
                }
                MODE_ACTIVITY -> {
                    appContext.startActivity(intent)
                }
                MODE_BROADCAST -> {
                    appContext.sendBroadcast(intent)
                }
                MODE_SERVICE -> {
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

internal fun coerceIntentExtraValue(value: Any?): Any? = when (value) {
    null, VNull -> null
    is VString -> value.raw
    is VBoolean -> value.raw
    is VNumber -> coerceNumberExtraValue(value.raw)
    is VObject -> value.raw ?: value.asString()
    is String -> coerceStringExtraValue(value)
    is Boolean, is Int, is Long, is Float, is Double -> value
    is Number -> coerceNumberExtraValue(value)
    else -> value.toString()
}

private fun coerceStringExtraValue(value: String): Any = when {
    value.equals("true", ignoreCase = true) -> true
    value.equals("false", ignoreCase = true) -> false
    value.toIntOrNull() != null -> value.toInt()
    value.toLongOrNull() != null -> value.toLong()
    value.toDoubleOrNull() != null -> value.toDouble()
    else -> value
}

private fun coerceNumberExtraValue(value: Number): Any = when (value) {
    is Int, is Long, is Float, is Double -> value
    else -> {
        val doubleValue = value.toDouble()
        if (doubleValue % 1.0 == 0.0) {
            val longValue = doubleValue.toLong()
            if (longValue in Int.MIN_VALUE..Int.MAX_VALUE) longValue.toInt() else longValue
        } else {
            doubleValue
        }
    }
}

private fun putIntentExtra(intent: Intent, key: String, value: Any?) {
    when (value) {
        null -> return
        is String -> intent.putExtra(key, value)
        is Boolean -> intent.putExtra(key, value)
        is Int -> intent.putExtra(key, value)
        is Long -> intent.putExtra(key, value)
        is Float -> intent.putExtra(key, value)
        is Double -> intent.putExtra(key, value)
        else -> intent.putExtra(key, value.toString())
    }
}

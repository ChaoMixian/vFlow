// 文件: main/java/com/chaomixian/vflow/core/workflow/module/shizuku/AppShortcutsModule.kt
package com.chaomixian.vflow.core.workflow.module.shizuku
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.*

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.logging.LogManager
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.permissions.Permission
import com.chaomixian.vflow.permissions.PermissionManager
import com.chaomixian.vflow.services.ShellManager
import com.chaomixian.vflow.ui.workflow_editor.PillUtil

// 抽象基类以减少重复代码
abstract class BaseShortcutModule : BaseModule() {
    override fun getRequiredPermissions(step: ActionStep?): List<Permission> {
        return ShellManager.getRequiredPermissions(LogManager.applicationContext)
    }

    protected suspend fun executeCommand(context: ExecutionContext, command: String, onProgress: suspend (ProgressUpdate) -> Unit): ExecutionResult {
        onProgress(ProgressUpdate("正在执行快捷指令..."))
        val result = ShellManager.execShellCommand(context.applicationContext, command, ShellManager.ShellMode.AUTO)

        return if (result.startsWith("Error:")) {
            ExecutionResult.Failure("执行失败", result)
        } else {
            ExecutionResult.Success(mapOf("result" to VString(result)))
        }
    }

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("result", "命令输出", VTypeRegistry.STRING.id)
    )
}

/**
 * 支付宝快捷方式模块。
 */
class AlipayShortcutsModule : BaseShortcutModule() {
    override val id = "vflow.shizuku.alipay_shortcuts"
    override val metadata = ActionMetadata(
        name = "支付宝",  // Fallback
        nameStringRes = R.string.module_vflow_shizuku_alipay_shortcuts_name,
        description = "快速打开支付宝的扫一扫、付款码、收款码等。",  // Fallback
        descriptionStringRes = R.string.module_vflow_shizuku_alipay_shortcuts_desc,
        iconRes = R.drawable.rounded_adb_24,
        category = "Shizuku"
    )

    private val actions = mapOf(
        "扫一扫" to "am start -a android.intent.action.VIEW -d alipays://platformapi/startapp?appId=10000007",
        "付款码" to "am start -a android.intent.action.VIEW -d alipays://platformapi/startapp?appId=20000056",
        "收款码" to "am start -a android.intent.action.VIEW -d alipays://platformapi/startapp?appId=20000123"
    )

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition("action", "操作", ParameterType.ENUM, actions.keys.first(), options = actions.keys.toList())
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val actionPill = PillUtil.createPillFromParam(step.parameters["action"], getInputs().find { it.id == "action" }, isModuleOption = true)
        return PillUtil.buildSpannable(context, context.getString(R.string.summary_vflow_shizuku_alipay), actionPill)
    }

    override suspend fun execute(context: ExecutionContext, onProgress: suspend (ProgressUpdate) -> Unit): ExecutionResult {
        val action = context.getVariableAsString("action", "")
        if (action.isEmpty()) {
            return ExecutionResult.Failure("参数错误", "未选择操作")
        }
        val command = actions[action] ?: return ExecutionResult.Failure("参数错误", "无效的操作")
        return executeCommand(context, command, onProgress)
    }
}

/**
 * 微信快捷方式模块。
 * 需要使用 am start -n 命令直接启动微信的特定 Activity，必须使用 ROOT 权限。
 */
class WeChatShortcutsModule : BaseModule() {
    override val id = "vflow.shizuku.wechat_shortcuts"
    override val metadata = ActionMetadata(
        name = "微信",  // Fallback
        nameStringRes = R.string.module_vflow_shizuku_wechat_shortcuts_name,
        description = "快速打开微信的收款码、付款码。",  // Fallback
        descriptionStringRes = R.string.module_vflow_shizuku_wechat_shortcuts_desc,
        iconRes = R.drawable.rounded_adb_24,
        category = "Shizuku"
    )

    private val actions = mapOf(
        "收款码" to "am start -n com.tencent.mm/.plugin.collect.ui.CollectMainUI",
        "付款码" to "am start -n com.tencent.mm/.plugin.offline.ui.WalletOfflineCoinPurseUI"
    )

    override fun getRequiredPermissions(step: ActionStep?): List<Permission> {
        return listOf(PermissionManager.ROOT)
    }

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition("action", "操作", ParameterType.ENUM, actions.keys.first(), options = actions.keys.toList())
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val actionPill = PillUtil.createPillFromParam(step.parameters["action"], getInputs().find { it.id == "action" }, isModuleOption = true)
        return PillUtil.buildSpannable(context, context.getString(R.string.summary_vflow_shizuku_wechat), actionPill)
    }

    override suspend fun execute(context: ExecutionContext, onProgress: suspend (ProgressUpdate) -> Unit): ExecutionResult {
        val action = context.getVariableAsString("action", "")
        if (action.isEmpty()) {
            return ExecutionResult.Failure("参数错误", "未选择操作")
        }
        val command = actions[action] ?: return ExecutionResult.Failure("参数错误", "无效的操作")
        onProgress(ProgressUpdate("正在执行快捷指令..."))
        val result = ShellManager.execShellCommand(context.applicationContext, command, ShellManager.ShellMode.ROOT)

        return if (result.startsWith("Error:")) {
            ExecutionResult.Failure("执行失败", result)
        } else {
            ExecutionResult.Success(mapOf("result" to VString(result)))
        }
    }

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("result", "命令输出", VTypeRegistry.STRING.id)
    )
}

/**
 * ColorOS 快捷方式模块。
 * 针对 ColorOS/OPlus 系统的一些快捷操作。
 */
class ColorOSShortcutsModule : BaseShortcutModule() {
    override val id = "vflow.shizuku.coloros_shortcuts"
    override val metadata = ActionMetadata(
        name = "ColorOS",  // Fallback
        nameStringRes = R.string.module_vflow_shizuku_coloros_shortcuts_name,
        description = "执行ColorOS系统相关的一些快捷操作。",  // Fallback
        descriptionStringRes = R.string.module_vflow_shizuku_coloros_shortcuts_desc,
        iconRes = R.drawable.rounded_adb_24,
        category = "Shizuku"
    )

    private val actions = mapOf(
        "小布记忆" to "am start-foreground-service -p \"com.coloros.colordirectservice\" --ei \"triggerType\" 1",
        "小布助手" to "am start -n com.heytap.speechassist/com.heytap.speechassist.business.lockscreen.FloatSpeechActivity",
        "开始录音" to "am start -n com.coloros.soundrecorder/oplus.multimedia.soundrecorder.slidebar.TransparentActivity"
    )

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition("action", "操作", ParameterType.ENUM, actions.keys.first(), options = actions.keys.toList())
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val actionPill = PillUtil.createPillFromParam(step.parameters["action"], getInputs().find { it.id == "action" }, isModuleOption = true)
        return PillUtil.buildSpannable(context, "ColorOS: ", actionPill)
    }

    override suspend fun execute(context: ExecutionContext, onProgress: suspend (ProgressUpdate) -> Unit): ExecutionResult {
        val action = context.getVariableAsString("action", "")
        if (action.isEmpty()) {
            return ExecutionResult.Failure("参数错误", "未选择操作")
        }
        val command = actions[action] ?: return ExecutionResult.Failure("参数错误", "无效的操作")
        return executeCommand(context, command, onProgress)
    }
}

/**
 * Gemini 助理快捷方式模块。
 */
class GeminiAssistantModule : BaseShortcutModule() {
    override val id = "vflow.shizuku.gemini_shortcut"
    override val metadata = ActionMetadata(
        name = "Gemini助理",  // Fallback
        nameStringRes = R.string.module_vflow_shizuku_gemini_shortcut_name,
        description = "启动 Google Gemini 语音助理。",  // Fallback
        descriptionStringRes = R.string.module_vflow_shizuku_gemini_shortcut_desc,
        iconRes = R.drawable.rounded_adb_24,
        category = "Shizuku"
    )

    override fun getInputs(): List<InputDefinition> = emptyList()

    override fun getSummary(context: Context, step: ActionStep): CharSequence = context.getString(R.string.summary_vflow_shizuku_gemini)

    override suspend fun execute(context: ExecutionContext, onProgress: suspend (ProgressUpdate) -> Unit): ExecutionResult {
        val command = "am start -a android.intent.action.VOICE_COMMAND -p com.google.android.googlequicksearchbox"
        return executeCommand(context, command, onProgress)
    }
}
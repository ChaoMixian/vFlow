// 文件: main/java/com/chaomixian/vflow/core/workflow/module/shizuku/x.kt
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
        onProgress(ProgressUpdate(appContext.getString(R.string.msg_vflow_shizuku_alipay_shortcuts_executing)))
        val result = ShellManager.execShellCommand(context.applicationContext, command, ShellManager.ShellMode.AUTO)

        return if (result.startsWith("Error:")) {
            ExecutionResult.Failure(appContext.getString(R.string.error_vflow_shizuku_shell_command_failed), result)
        } else {
            ExecutionResult.Success(mapOf("result" to VString(result)))
        }
    }

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("result", "命令输出", VTypeRegistry.STRING.id, nameStringRes = R.string.output_vflow_shizuku_alipay_shortcuts_result_name)
    )
}

/**
 * 支付宝快捷方式模块。
 */
class AlipayShortcutsModule : BaseShortcutModule() {
    companion object {
        private const val ACTION_SCAN = "scan"
        private const val ACTION_PAY = "pay"
        private const val ACTION_RECEIVE = "receive"
    }
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
        ACTION_SCAN to "am start -a android.intent.action.VIEW -d alipays://platformapi/startapp?appId=10000007",
        ACTION_PAY to "am start -a android.intent.action.VIEW -d alipays://platformapi/startapp?appId=20000056",
        ACTION_RECEIVE to "am start -a android.intent.action.VIEW -d alipays://platformapi/startapp?appId=20000123"
    )

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition("action", "操作", ParameterType.ENUM, ACTION_SCAN, options = actions.keys.toList(), optionsStringRes = listOf(R.string.option_vflow_shizuku_alipay_shortcuts_action_scan, R.string.option_vflow_shizuku_alipay_shortcuts_action_pay, R.string.option_vflow_shizuku_alipay_shortcuts_action_receive), legacyValueMap = mapOf("扫一扫" to ACTION_SCAN, "Scan" to ACTION_SCAN, "付款码" to ACTION_PAY, "Payment Code" to ACTION_PAY, "收款码" to ACTION_RECEIVE, "Collection Code" to ACTION_RECEIVE), nameStringRes = R.string.param_vflow_shizuku_alipay_shortcuts_action_name)
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val actionPill = PillUtil.createPillFromParam(step.parameters["action"], getInputs().find { it.id == "action" }, isModuleOption = true)
        return PillUtil.buildSpannable(context, context.getString(R.string.summary_vflow_shizuku_alipay), actionPill)
    }

    override suspend fun execute(context: ExecutionContext, onProgress: suspend (ProgressUpdate) -> Unit): ExecutionResult {
        val action = context.getVariableAsString("action", "")
        if (action.isEmpty()) {
            return ExecutionResult.Failure(appContext.getString(R.string.error_vflow_interaction_operit_param_error), appContext.getString(R.string.error_vflow_shizuku_alipay_shortcuts_no_action))
        }
        val command = actions[action] ?: return ExecutionResult.Failure(appContext.getString(R.string.error_vflow_interaction_operit_param_error), appContext.getString(R.string.error_vflow_shizuku_alipay_shortcuts_invalid))
        return executeCommand(context, command, onProgress)
    }
}

/**
 * 微信快捷方式模块。
 * 使用 am start 命令直接启动微信的特定功能，不需要 root 权限。
 */
class WeChatShortcutsModule : BaseShortcutModule() {
    companion object {
        private const val ACTION_PAY = "pay"
        private const val ACTION_SCAN = "scan"
    }
    override val id = "vflow.shizuku.wechat_shortcuts"
    override val metadata = ActionMetadata(
        name = "微信",  // Fallback
        nameStringRes = R.string.module_vflow_shizuku_wechat_shortcuts_name,
        description = "快速打开微信的扫一扫、付款码等。",  // Fallback
        descriptionStringRes = R.string.module_vflow_shizuku_wechat_shortcuts_desc,
        iconRes = R.drawable.rounded_adb_24,
        category = "Shizuku"
    )

    private val actions = mapOf(
        ACTION_PAY to "am start -n com.tencent.mm/com.tencent.mm.ui.LauncherUI -a com.tencent.mm.ui.ShortCutDispatchAction --es LauncherUI.Shortcut.LaunchType launch_type_offline_wallet",
        ACTION_SCAN to "am start -n com.tencent.mm/com.tencent.mm.ui.LauncherUI -a com.tencent.mm.ui.ShortCutDispatchAction --es LauncherUI.Shortcut.LaunchType launch_type_scan_qrcode"
    )

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition("action", "操作", ParameterType.ENUM, ACTION_PAY, options = actions.keys.toList(), optionsStringRes = listOf(R.string.option_vflow_shizuku_wechat_shortcuts_action_pay, R.string.option_vflow_shizuku_wechat_shortcuts_action_scan), legacyValueMap = mapOf("微信支付" to ACTION_PAY, "付款码" to ACTION_PAY, "Payment Code" to ACTION_PAY, "扫一扫" to ACTION_SCAN, "Scan" to ACTION_SCAN), nameStringRes = R.string.param_vflow_shizuku_wechat_shortcuts_action_name)
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val actionPill = PillUtil.createPillFromParam(step.parameters["action"], getInputs().find { it.id == "action" }, isModuleOption = true)
        return PillUtil.buildSpannable(context, context.getString(R.string.summary_vflow_shizuku_wechat), actionPill)
    }

    override suspend fun execute(context: ExecutionContext, onProgress: suspend (ProgressUpdate) -> Unit): ExecutionResult {
        val action = context.getVariableAsString("action", "")
        if (action.isEmpty()) {
            return ExecutionResult.Failure(appContext.getString(R.string.error_vflow_interaction_operit_param_error), appContext.getString(R.string.error_vflow_shizuku_alipay_shortcuts_no_action))
        }
        val command = actions[action] ?: return ExecutionResult.Failure(appContext.getString(R.string.error_vflow_interaction_operit_param_error), appContext.getString(R.string.error_vflow_shizuku_alipay_shortcuts_invalid))
        return executeCommand(context, command, onProgress)
    }
}

/**
 * ColorOS 快捷方式模块。
 * 针对 ColorOS/OPlus 系统的一些快捷操作。
 */
class ColorOSShortcutsModule : BaseShortcutModule() {
    companion object {
        private const val ACTION_MEMORY = "memory"
        private const val ACTION_ASSISTANT = "assistant"
        private const val ACTION_RECORD = "record"
    }
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
        ACTION_MEMORY to "am start-foreground-service -p \"com.coloros.colordirectservice\" --ei \"triggerType\" 1",
        ACTION_ASSISTANT to "am start -n com.heytap.speechassist/com.heytap.speechassist.business.lockscreen.FloatSpeechActivity",
        ACTION_RECORD to "am start -n com.coloros.soundrecorder/oplus.multimedia.soundrecorder.slidebar.TransparentActivity"
    )

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition("action", "操作", ParameterType.ENUM, ACTION_MEMORY, options = actions.keys.toList(), optionsStringRes = listOf(R.string.option_vflow_shizuku_coloros_shortcuts_action_memory, R.string.option_vflow_shizuku_coloros_shortcuts_action_assistant, R.string.option_vflow_shizuku_coloros_shortcuts_action_record), legacyValueMap = mapOf("小布记忆" to ACTION_MEMORY, "Xiaobu Memory" to ACTION_MEMORY, "小布助手" to ACTION_ASSISTANT, "Xiaobu Assistant" to ACTION_ASSISTANT, "开始录音" to ACTION_RECORD, "开始录制" to ACTION_RECORD, "Start Recording" to ACTION_RECORD), nameStringRes = R.string.param_vflow_shizuku_coloros_shortcuts_action_name)
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val actionPill = PillUtil.createPillFromParam(step.parameters["action"], getInputs().find { it.id == "action" }, isModuleOption = true)
        return PillUtil.buildSpannable(context, context.getString(R.string.summary_vflow_shizuku_coloros), actionPill)
    }

    override suspend fun execute(context: ExecutionContext, onProgress: suspend (ProgressUpdate) -> Unit): ExecutionResult {
        val action = context.getVariableAsString("action", "")
        if (action.isEmpty()) {
            return ExecutionResult.Failure(appContext.getString(R.string.error_vflow_interaction_operit_param_error), appContext.getString(R.string.error_vflow_shizuku_alipay_shortcuts_no_action))
        }
        val command = actions[action] ?: return ExecutionResult.Failure(appContext.getString(R.string.error_vflow_interaction_operit_param_error), appContext.getString(R.string.error_vflow_shizuku_alipay_shortcuts_invalid))
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

// 文件: main/java/com/chaomixian/vflow/core/workflow/module/shizuku/AppShortcutsModule.kt
package com.chaomixian.vflow.core.workflow.module.shizuku

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.logging.LogManager
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.permissions.Permission
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
            ExecutionResult.Success(mapOf("result" to TextVariable(result)))
        }
    }

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("result", "命令输出", TextVariable.TYPE_NAME)
    )
}

/**
 * 支付宝快捷方式模块。
 */
class AlipayShortcutsModule : BaseShortcutModule() {
    override val id = "vflow.shizuku.alipay_shortcuts"
    override val metadata = ActionMetadata(
        name = "支付宝",
        description = "快速打开支付宝的扫一扫、付款码、收款码等。",
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
        return PillUtil.buildSpannable(context, "支付宝: ", actionPill)
    }

    override suspend fun execute(context: ExecutionContext, onProgress: suspend (ProgressUpdate) -> Unit): ExecutionResult {
        val action = context.variables["action"] as? String ?: return ExecutionResult.Failure("参数错误", "未选择操作")
        val command = actions[action] ?: return ExecutionResult.Failure("参数错误", "无效的操作")
        return executeCommand(context, command, onProgress)
    }
}

/**
 * 微信快捷方式模块。
 */
class WeChatShortcutsModule : BaseShortcutModule() {
    override val id = "vflow.shizuku.wechat_shortcuts"
    override val metadata = ActionMetadata(
        name = "微信",
        description = "快速打开微信的收款码、付款码。",
        iconRes = R.drawable.rounded_adb_24,
        category = "Shizuku"
    )

    private val actions = mapOf(
        "收款码" to "am start -n com.tencent.mm/.plugin.collect.ui.CollectMainUI",
        "付款码" to "am start -n com.tencent.mm/.plugin.collect.ui.CollectMainUI"
    )

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition("action", "操作", ParameterType.ENUM, actions.keys.first(), options = actions.keys.toList())
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val actionPill = PillUtil.createPillFromParam(step.parameters["action"], getInputs().find { it.id == "action" }, isModuleOption = true)
        return PillUtil.buildSpannable(context, "微信: ", actionPill)
    }

    override suspend fun execute(context: ExecutionContext, onProgress: suspend (ProgressUpdate) -> Unit): ExecutionResult {
        val action = context.variables["action"] as? String ?: return ExecutionResult.Failure("参数错误", "未选择操作")
        val command = actions[action] ?: return ExecutionResult.Failure("参数错误", "无效的操作")
        return executeCommand(context, command, onProgress)
    }
}

/**
 * ColorOS 快捷方式模块。
 * 针对 ColorOS/OPlus 系统的一些快捷操作。
 */
class ColorOSShortcutsModule : BaseShortcutModule() {
    override val id = "vflow.shizuku.coloros_shortcuts"
    override val metadata = ActionMetadata(
        name = "ColorOS",
        description = "执行ColorOS系统相关的一些快捷操作。",
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
        val action = context.variables["action"] as? String ?: return ExecutionResult.Failure("参数错误", "未选择操作")
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
        name = "Gemini助理",
        description = "启动 Google Gemini 语音助理。",
        iconRes = R.drawable.rounded_adb_24,
        category = "Shizuku"
    )

    override fun getInputs(): List<InputDefinition> = emptyList()

    override fun getSummary(context: Context, step: ActionStep): CharSequence = "启动 Gemini 助理"

    override suspend fun execute(context: ExecutionContext, onProgress: suspend (ProgressUpdate) -> Unit): ExecutionResult {
        val command = "am start -a android.intent.action.VOICE_COMMAND -p com.google.android.googlequicksearchbox"
        return executeCommand(context, command, onProgress)
    }
}
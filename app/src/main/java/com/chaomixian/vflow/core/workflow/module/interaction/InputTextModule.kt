// 文件: main/java/com/chaomixian/vflow/core/workflow/module/interaction/InputTextModule.kt
package com.chaomixian.vflow.core.workflow.module.interaction

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.execution.VariableResolver
import com.chaomixian.vflow.core.logging.DebugLogger
import com.chaomixian.vflow.core.logging.LogManager
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VBoolean
import com.chaomixian.vflow.core.types.basic.VString
import com.chaomixian.vflow.core.utils.VFlowImeManager
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.permissions.Permission
import com.chaomixian.vflow.permissions.PermissionManager
import com.chaomixian.vflow.services.ServiceStateBus
import com.chaomixian.vflow.services.ShellManager
import com.chaomixian.vflow.ui.workflow_editor.PillUtil
import kotlinx.coroutines.delay

class InputTextModule : BaseModule() {

    override val id = "vflow.interaction.input_text"
    override val metadata = ActionMetadata(
        nameStringRes = R.string.module_vflow_interaction_input_text_name,
        descriptionStringRes = R.string.module_vflow_interaction_input_text_desc,
        name = "输入文本",  // Fallback
        description = "在当前聚焦的输入框中输入文本 (支持无障碍和Shell)",  // Fallback
        iconRes = R.drawable.rounded_keyboard_24,
        category = "界面交互"
    )

    private val modeOptions by lazy {
        listOf(
            appContext.getString(R.string.option_vflow_interaction_input_text_mode_auto),
            appContext.getString(R.string.option_vflow_interaction_input_text_mode_a11y),
            appContext.getString(R.string.option_vflow_interaction_input_text_mode_shell)
        )
    }

    override fun getRequiredPermissions(step: ActionStep?): List<Permission> {
        val autoMode = appContext.getString(R.string.option_vflow_interaction_input_text_mode_auto)
        val a11yMode = appContext.getString(R.string.option_vflow_interaction_input_text_mode_a11y)
        val shellMode = appContext.getString(R.string.option_vflow_interaction_input_text_mode_shell)

        val mode = step?.parameters?.get("mode") as? String ?: autoMode
        val perms = mutableListOf<Permission>()
        if (mode == autoMode || mode == a11yMode) {
            perms.add(PermissionManager.ACCESSIBILITY)
        }
        if (mode == autoMode || mode == shellMode) {
            perms.addAll(ShellManager.getRequiredPermissions(LogManager.applicationContext))
        }
        return perms.distinct()
    }

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "text",
            name = "文本内容",  // Fallback
            staticType = ParameterType.STRING,
            defaultValue = "",
            acceptsMagicVariable = true,
            acceptedMagicVariableTypes = setOf(VTypeRegistry.STRING.id),
            supportsRichText = true,
            nameStringRes = R.string.param_vflow_interaction_input_text_text_name
        ),
        InputDefinition(
            id = "mode",
            name = "输入模式",  // Fallback
            staticType = ParameterType.ENUM,
            defaultValue = appContext.getString(R.string.option_vflow_interaction_input_text_mode_auto),
            options = modeOptions,
            acceptsMagicVariable = false,
            isHidden = true,
            nameStringRes = R.string.param_vflow_interaction_input_text_mode_name
        ),
        InputDefinition(
            id = "show_advanced",
            name = "显示高级选项",  // Fallback
            staticType = ParameterType.BOOLEAN,
            defaultValue = false,
            acceptsMagicVariable = false,
            isHidden = true,
            nameStringRes = R.string.param_vflow_interaction_input_text_show_advanced_name
        )
    )

    override val uiProvider: ModuleUIProvider = InputTextModuleUIProvider()

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition(
            id = "success",
            name = "是否成功",  // Fallback
            typeName = VTypeRegistry.BOOLEAN.id,
            nameStringRes = R.string.output_vflow_interaction_input_text_success_name
        )
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val rawText = step.parameters["text"]?.toString() ?: ""
        val autoMode = appContext.getString(R.string.option_vflow_interaction_input_text_mode_auto)
        val mode = step.parameters["mode"] as? String ?: autoMode

        // 如果内容复杂（包含变量或长文本），只返回简单标题，
        // 详细内容将由 RichTextUIProvider 创建的预览视图在下方显示。
        if (VariableResolver.isComplex(rawText)) {
            return if (mode == autoMode) context.getString(R.string.summary_vflow_interaction_input_text) else context.getString(R.string.summary_vflow_interaction_input_text_with_mode, mode)
        }

        // 内容简单时，在摘要中直接显示药丸
        val textPill = PillUtil.createPillFromParam(step.parameters["text"], getInputs().find { it.id == "text" })
        return if (mode == autoMode) {
            PillUtil.buildSpannable(context, context.getString(R.string.summary_vflow_interaction_input_text), textPill)
        } else {
            PillUtil.buildSpannable(context, context.getString(R.string.summary_vflow_interaction_input_text_with_mode, mode), textPill)
        }
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val rawText = context.getVariableAsString("text", "")
        val text = VariableResolver.resolve(rawText, context)
        val autoMode = appContext.getString(R.string.option_vflow_interaction_input_text_mode_auto)
        val a11yMode = appContext.getString(R.string.option_vflow_interaction_input_text_mode_a11y)
        val shellMode = appContext.getString(R.string.option_vflow_interaction_input_text_mode_shell)
        val mode = context.getVariableAsString("mode", autoMode)

        if (text.isEmpty()) {
            return ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_interaction_input_text_param_error),
                appContext.getString(R.string.error_vflow_interaction_input_text_empty)
            )
        }

        onProgress(ProgressUpdate("准备输入文本..."))

        var success = false

        // 1. 无障碍输入
        if (mode == autoMode || mode == a11yMode) {
            success = performAccessibilityInput(text)
            if (success) {
                onProgress(ProgressUpdate("已通过无障碍输入"))
            } else if (mode == "无障碍") {
                return ExecutionResult.Failure("输入失败", "无法找到聚焦的输入框，或输入框不支持编辑。")
            }
        }

        // 2. Shell 输入 (如果无障碍失败或指定Shell)
        // 策略2：Shell 输入 (集成 VFlowIME)
        if (!success && (mode == "自动" || mode == "Shell")) {
            onProgress(ProgressUpdate("尝试使用 vFlow 输入法输入..."))

            // 尝试使用 IME 输入
            success = VFlowImeManager.inputText(context.applicationContext, text)

            if (success) {
                onProgress(ProgressUpdate("已通过 vFlow IME 输入"))
            } else {
                // 如果 IME 失败 (例如没权限切换)，回退到 剪贴板+粘贴 方案
                onProgress(ProgressUpdate("IME 模式失败，回落到 剪贴板+粘贴 模式..."))
                success = performClipboardPasteInput(context.applicationContext, text)
            }

            if (success) {
                onProgress(ProgressUpdate("输入完成"))
            } else if (mode == "Shell") {
                return ExecutionResult.Failure("输入失败", "Shell 命令执行失败，请检查 Root/Shizuku 权限及输入法设置。")
            }
        }

        return if (success) {
            ExecutionResult.Success(mapOf("success" to VBoolean(true)))
        } else {
            ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_interaction_input_text_failed),
                appContext.getString(R.string.error_vflow_interaction_input_text_no_focus)
            )
        }
    }

    private fun performAccessibilityInput(text: String): Boolean {
        val service = ServiceStateBus.getAccessibilityService() ?: return false
        val focusNode = service.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false

        if (focusNode.isEditable) {
            val args = Bundle()
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            // ACTION_SET_TEXT 返回 true 表示系统接受了该操作 (但不保证一定成功，但在无障碍层面是成功的)
            val result = focusNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            focusNode.recycle()
            return result
        }
        focusNode.recycle()
        return false
    }

    private suspend fun performClipboardPasteInput(context: Context, text: String): Boolean {
        if (!ShellManager.isShizukuActive(context) && !ShellManager.isRootAvailable()) {
            return false
        }

        val isAscii = text.all { it.code < 128 }

        if (isAscii) {
            // ASCII 直接输入、
            val safeText = text.replace("\"", "\\\"").replace("'", "\\'")
                .replace(" ", "%s")
            val result = ShellManager.execShellCommand(context, "input text \"$safeText\"", ShellManager.ShellMode.AUTO)
            return !result.startsWith("Error")
        } else {
            // Unicode (中文)：使用 剪贴板 + 粘贴
            return try {
                // 使用 AccessibilityService 的 Context 来获取 ClipboardManager
                val accService = ServiceStateBus.getAccessibilityService()
                val clipboard = if (accService != null) {
                    accService.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                } else {
                    context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                }

                val clip = ClipData.newPlainText("vFlow Input", text)
                clipboard.setPrimaryClip(clip)

                // 等待剪贴板同步
                delay(200)

                // 发送粘贴键 (KEYCODE_PASTE = 279)
                val result = ShellManager.execShellCommand(context, "input keyevent 279", ShellManager.ShellMode.AUTO)
                !result.startsWith("Error")
            } catch (e: Exception) {
                DebugLogger.e("InputTextModule", "Shell Unicode input failed", e)
                false
            }
        }
    }
}
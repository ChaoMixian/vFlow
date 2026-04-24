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
import com.chaomixian.vflow.services.VFlowIME
import com.chaomixian.vflow.ui.workflow_editor.PillUtil
import kotlinx.coroutines.delay

class InputTextModule : BaseModule() {
    companion object {
        internal const val MODE_AUTO = "auto"
        internal const val MODE_A11Y = "a11y"
        internal const val MODE_SHELL = "shell"

        internal const val ACTION_NONE = "none"
        internal const val ACTION_ENTER = "enter"
        internal const val ACTION_TAB = "tab"
        internal const val ACTION_NEXT = "next"

        private val MODE_LEGACY_MAP = mapOf(
            "自动" to MODE_AUTO,
            "无障碍" to MODE_A11Y
        )

        private val ACTION_LEGACY_MAP = mapOf(
            "无操作" to ACTION_NONE,
            "Enter（回车）" to ACTION_ENTER,
            "Tab（制表符）" to ACTION_TAB,
            "下一个（移动焦点）" to ACTION_NEXT
        )
    }

    override val id = "vflow.interaction.input_text"
    override val metadata = ActionMetadata(
        nameStringRes = R.string.module_vflow_interaction_input_text_name,
        descriptionStringRes = R.string.module_vflow_interaction_input_text_desc,
        name = "输入文本",  // Fallback
        description = "在当前聚焦的输入框中输入文本 (支持无障碍和Shell)",  // Fallback
        iconRes = R.drawable.rounded_keyboard_24,
        category = "界面交互",
        categoryId = "interaction"
    )
    override val aiMetadata = directToolMetadata(
        riskLevel = AiModuleRiskLevel.STANDARD,
        directToolDescription = "Type text into the currently focused input field. Observe and focus the target field first if there is any uncertainty, and verify the resulting screen state before concluding a multi-step UI task.",
        workflowStepDescription = "Type text into the currently focused input field.",
        inputHints = mapOf(
            "text" to "Plain text to type. Do not include focus or click actions here.",
            "action_after" to "Optional trailing key action such as enter or next after typing.",
        ),
        requiredInputIds = setOf("text"),
    )

    override fun getRequiredPermissions(step: ActionStep?): List<Permission> {
        val modeInput = getInputs().first { it.id == "mode" }
        val rawMode = step?.parameters?.get("mode") as? String ?: MODE_AUTO
        val mode = modeInput.normalizeEnumValue(rawMode) ?: rawMode
        val perms = mutableListOf<Permission>()
        if (mode == MODE_AUTO || mode == MODE_A11Y) {
            perms.add(PermissionManager.ACCESSIBILITY)
        }
        if (mode == MODE_AUTO || mode == MODE_SHELL) {
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
            defaultValue = MODE_AUTO,
            options = listOf(MODE_AUTO, MODE_A11Y, MODE_SHELL),
            acceptsMagicVariable = false,
            isHidden = true,
            nameStringRes = R.string.param_vflow_interaction_input_text_mode_name,
            optionsStringRes = listOf(
                R.string.option_vflow_interaction_input_text_mode_auto,
                R.string.option_vflow_interaction_input_text_mode_a11y,
                R.string.option_vflow_interaction_input_text_mode_shell
            ),
            legacyValueMap = MODE_LEGACY_MAP
        ),
        InputDefinition(
            id = "action_after",
            name = "操作后按键",  // Fallback
            staticType = ParameterType.ENUM,
            defaultValue = ACTION_NONE,
            options = listOf(ACTION_NONE, ACTION_ENTER, ACTION_TAB, ACTION_NEXT),
            acceptsMagicVariable = false,
            isHidden = true,
            nameStringRes = R.string.param_vflow_interaction_input_text_action_after_name,
            optionsStringRes = listOf(
                R.string.option_vflow_interaction_input_text_action_none,
                R.string.option_vflow_interaction_input_text_action_enter,
                R.string.option_vflow_interaction_input_text_action_tab,
                R.string.option_vflow_interaction_input_text_action_next
            ),
            legacyValueMap = ACTION_LEGACY_MAP
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
        val modeInput = getInputs().first { it.id == "mode" }
        val rawMode = step.parameters["mode"] as? String ?: MODE_AUTO
        val mode = modeInput.normalizeEnumValue(rawMode) ?: rawMode

        // 如果内容复杂（包含变量或长文本），只返回简单文本标题
        if (VariableResolver.isComplex(rawText)) {
            return if (mode == MODE_AUTO) {
                context.getString(R.string.summary_vflow_interaction_input_text)
            } else {
                context.getString(R.string.summary_vflow_interaction_input_text_with_mode_prefix) +
                    modeInput.getLocalizedOptions(context)[modeInput.options.indexOf(mode).coerceAtLeast(0)] +
                    context.getString(R.string.summary_vflow_interaction_input_text_with_mode_middle)
            }
        }

        // 内容简单时，在摘要中直接显示药丸
        val textPill = PillUtil.createPillFromParam(step.parameters["text"], getInputs().find { it.id == "text" })

        // 使用 PillUtil.buildSpannable 构建摘要
        return if (mode == MODE_AUTO) {
            // "输入文本 [pill]"
            PillUtil.buildSpannable(context, context.getString(R.string.summary_vflow_interaction_input_text), textPill)
        } else {
            // "使用 [mode] 输入文本 [pill]"
            val modePill = PillUtil.createPillFromParam(rawMode, modeInput)
            val prefix = context.getString(R.string.summary_vflow_interaction_input_text_with_mode_prefix)
            val middle = context.getString(R.string.summary_vflow_interaction_input_text_with_mode_middle)
            PillUtil.buildSpannable(context, prefix, modePill, " ", middle, textPill)
        }
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val rawText = context.getVariableAsString("text", "")
        val text = VariableResolver.resolve(rawText, context)
        val modeInput = getInputs().first { it.id == "mode" }
        val actionInput = getInputs().first { it.id == "action_after" }
        val rawMode = context.getVariableAsString("mode", MODE_AUTO)
        val mode = modeInput.normalizeEnumValue(rawMode) ?: rawMode
        val rawActionAfter = context.getVariableAsString("action_after", ACTION_NONE)
        val actionAfter = actionInput.normalizeEnumValue(rawActionAfter) ?: rawActionAfter

        if (text.isEmpty()) {
            return ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_interaction_input_text_param_error),
                appContext.getString(R.string.error_vflow_interaction_input_text_empty)
            )
        }

        onProgress(ProgressUpdate("准备输入文本..."))

        var success = false

        // 1. 无障碍输入
        if (mode == MODE_AUTO || mode == MODE_A11Y) {
            success = performAccessibilityInput(text)
            if (success) {
                onProgress(ProgressUpdate("已通过无障碍输入"))
                // 执行操作后按键
                if (actionAfter != ACTION_NONE) {
                    performAccessibilityKeyAction(actionAfter)
                    onProgress(ProgressUpdate("已发送按键"))
                }
            } else if (mode == MODE_A11Y) {
                return ExecutionResult.Failure("输入失败", "无法找到聚焦的输入框，或输入框不支持编辑。")
            }
        }

        // 2. Shell 输入 (如果无障碍失败或指定Shell)
        // 策略2：Shell 输入 (集成 VFlowIME)
        if (!success && (mode == MODE_AUTO || mode == MODE_SHELL)) {
            onProgress(ProgressUpdate("尝试使用 vFlow 输入法输入..."))

            // 映射按键操作到 IME 支持的格式
            val imeKeyAction = when (actionAfter) {
                ACTION_ENTER -> VFlowIME.KEY_ACTION_ENTER
                ACTION_TAB -> VFlowIME.KEY_ACTION_TAB
                ACTION_NEXT -> VFlowIME.KEY_ACTION_NEXT
                else -> VFlowIME.KEY_ACTION_NONE
            }

            // 尝试使用 IME 输入
            success = VFlowImeManager.inputText(context.applicationContext, text, imeKeyAction)

            if (success) {
                onProgress(ProgressUpdate("已通过 vFlow IME 输入"))
            } else {
                // 如果 IME 失败 (例如没权限切换)，回退到 剪贴板+粘贴 方案
                onProgress(ProgressUpdate("IME 模式失败，回落到 剪贴板+粘贴 模式..."))
                success = performClipboardPasteInput(context.applicationContext, text)

                // 如果剪贴板模式成功，仍然需要发送按键
                if (success && actionAfter != ACTION_NONE) {
                    performShellKeyAction(context.applicationContext, actionAfter)
                    onProgress(ProgressUpdate("已发送按键"))
                }
            }

            if (success) {
                onProgress(ProgressUpdate("输入完成"))
            } else if (mode == MODE_SHELL) {
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

    /**
     * 通过无障碍服务发送按键操作
     */
    private suspend fun performAccessibilityKeyAction(
        actionAfter: String
    ): Boolean {
        val service = ServiceStateBus.getAccessibilityService() ?: return false

        return when (actionAfter) {
            ACTION_ENTER -> {
                // 发送 Enter 键 (KEYCODE_ENTER = 66)
                val focusNode = service.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                if (focusNode != null) {
                    val result = focusNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    focusNode.recycle()
                    result
                } else {
                    // 如果找不到聚焦的输入框，尝试全局发送 Enter
                    val result = ShellManager.execShellCommand(
                        service,
                        "input keyevent 66",
                        ShellManager.ShellMode.AUTO
                    )
                    !result.startsWith("Error")
                }
            }
            ACTION_TAB -> {
                // 发送 Tab 键 (KEYCODE_TAB = 61)
                val result = ShellManager.execShellCommand(
                    service,
                    "input keyevent 61",
                    ShellManager.ShellMode.AUTO
                )
                !result.startsWith("Error")
            }
            ACTION_NEXT -> {
                // 移动焦点到下一个 (ACTION_FOCUS_FORWARD = 2)
                // 或者使用 TAB
                val result = ShellManager.execShellCommand(
                    service,
                    "input keyevent 61",
                    ShellManager.ShellMode.AUTO
                )
                !result.startsWith("Error")
            }
            else -> false
        }
    }

    /**
     * 通过 Shell 发送按键操作
     */
    private suspend fun performShellKeyAction(
        context: Context,
        actionAfter: String
    ): Boolean {
        if (!ShellManager.isShizukuActive(context) && !ShellManager.isRootAvailable()) {
            return false
        }

        val keycode = when (actionAfter) {
            ACTION_ENTER -> 66  // KEYCODE_ENTER
            ACTION_TAB -> 61    // KEYCODE_TAB
            ACTION_NEXT -> 61   // 使用 TAB 移动焦点
            else -> return false
        }

        val result = ShellManager.execShellCommand(
            context,
            "input keyevent $keycode",
            ShellManager.ShellMode.AUTO
        )
        return !result.startsWith("Error")
    }
}

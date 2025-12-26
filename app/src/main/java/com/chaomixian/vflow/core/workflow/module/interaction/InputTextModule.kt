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
        name = "输入文本",
        description = "在当前聚焦的输入框中输入文本 (支持无障碍和Shell)。",
        iconRes = R.drawable.rounded_keyboard_24,
        category = "界面交互"
    )

    private val modeOptions = listOf("自动", "无障碍", "Shell")

    override fun getRequiredPermissions(step: ActionStep?): List<Permission> {
        val mode = step?.parameters?.get("mode") as? String ?: "自动"
        val perms = mutableListOf<Permission>()
        if (mode == "自动" || mode == "无障碍") {
            perms.add(PermissionManager.ACCESSIBILITY)
        }
        if (mode == "自动" || mode == "Shell") {
            perms.addAll(ShellManager.getRequiredPermissions(LogManager.applicationContext))
        }
        return perms.distinct()
    }

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "text",
            name = "文本内容",
            staticType = ParameterType.STRING,
            defaultValue = "",
            acceptsMagicVariable = true,
            acceptedMagicVariableTypes = setOf(TextVariable.TYPE_NAME),
            supportsRichText = true
        ),
        InputDefinition(
            id = "mode",
            name = "输入模式",
            staticType = ParameterType.ENUM,
            defaultValue = "自动",
            options = modeOptions,
            acceptsMagicVariable = false,
            isHidden = true
        ),
        InputDefinition(
            id = "show_advanced",
            name = "显示高级选项",
            staticType = ParameterType.BOOLEAN,
            defaultValue = false,
            acceptsMagicVariable = false,
            isHidden = true
        )
    )

    override val uiProvider: ModuleUIProvider = InputTextModuleUIProvider()

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("success", "是否成功", BooleanVariable.TYPE_NAME)
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val rawText = step.parameters["text"]?.toString() ?: ""
        val mode = step.parameters["mode"] as? String ?: "自动"

        // 如果内容复杂（包含变量或长文本），只返回简单标题，
        // 详细内容将由 RichTextUIProvider 创建的预览视图在下方显示。
        if (VariableResolver.isComplex(rawText)) {
            return if (mode == "自动") "输入文本" else "使用 $mode 输入文本"
        }

        // 内容简单时，在摘要中直接显示药丸
        val textPill = PillUtil.createPillFromParam(step.parameters["text"], getInputs().find { it.id == "text" })
        return if (mode == "自动") {
            PillUtil.buildSpannable(context, "输入文本 ", textPill)
        } else {
            PillUtil.buildSpannable(context, "使用 $mode 输入文本 ", textPill)
        }
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val rawText = context.variables["text"]?.toString() ?: ""
        val text = VariableResolver.resolve(rawText, context)
        val mode = context.variables["mode"] as? String ?: "自动"

        if (text.isEmpty()) {
            return ExecutionResult.Failure("参数错误", "输入文本不能为空")
        }

        onProgress(ProgressUpdate("准备输入文本..."))

        var success = false

        // 1. 无障碍输入
        if (mode == "自动" || mode == "无障碍") {
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
            ExecutionResult.Success(mapOf("success" to BooleanVariable(true)))
        } else {
            ExecutionResult.Failure("输入失败", "无法输入文本，请确保有输入框处于聚焦状态。")
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
// 文件: main/java/com/chaomixian/vflow/core/workflow/module/interaction/FindTextUntilModule.kt
package com.chaomixian.vflow.core.workflow.module.interaction

import android.content.ContentValues.TAG
import android.content.Context
import android.graphics.Rect
import android.net.Uri
import android.view.accessibility.AccessibilityNodeInfo
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.execution.VariableResolver
import com.chaomixian.vflow.core.logging.DebugLogger
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.permissions.Permission
import com.chaomixian.vflow.permissions.PermissionManager
import com.chaomixian.vflow.services.ServiceStateBus
import com.chaomixian.vflow.services.ShellManager
import com.chaomixian.vflow.ui.workflow_editor.PillUtil
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import java.util.regex.Pattern

/**
 * “查找直到出现”原子模块。
 */
class FindTextUntilModule : BaseModule() {
    override val id = "vflow.interaction.find_until"
    override val metadata = ActionMetadata(
        name = "查找直到出现",
        description = "持续查找屏幕上的文本，直到出现或超时。支持 OCR 兜底。",
        iconRes = com.chaomixian.vflow.R.drawable.rounded_search_24,
        category = "界面交互"
    )

    override fun getRequiredPermissions(step: ActionStep?): List<Permission> {
        return listOf(PermissionManager.ACCESSIBILITY) +
                ShellManager.getRequiredPermissions(com.chaomixian.vflow.core.logging.LogManager.applicationContext)
    }

    private val matchModeOptions = listOf("包含", "完全匹配", "正则")
    private val searchModeOptions = listOf("自动", "无障碍", "OCR")

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition("targetText", "目标文本", ParameterType.STRING, "", acceptsMagicVariable = true, supportsRichText = true),
        InputDefinition("matchMode", "匹配模式", ParameterType.ENUM, "包含", options = matchModeOptions, acceptsMagicVariable = false),
        InputDefinition("timeout", "超时时间(秒)", ParameterType.NUMBER, 10.0, acceptsMagicVariable = true, acceptedMagicVariableTypes = setOf(NumberVariable.TYPE_NAME)),

        InputDefinition("searchMode", "查找模式", ParameterType.ENUM, "自动", options = searchModeOptions, acceptsMagicVariable = false, isFolded = true),
        InputDefinition("interval", "轮询间隔(ms)", ParameterType.NUMBER, 1000.0, acceptsMagicVariable = true, isFolded = true)
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("success", "是否找到", BooleanVariable.TYPE_NAME),
        OutputDefinition("element", "找到的元素", ScreenElement.TYPE_NAME),
        OutputDefinition("coordinate", "中心坐标", Coordinate.TYPE_NAME)
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val inputs = getInputs()
        val targetPill = PillUtil.createPillFromParam(step.parameters["targetText"], inputs.find { it.id == "targetText" })
        val timeoutPill = PillUtil.createPillFromParam(step.parameters["timeout"], inputs.find { it.id == "timeout" })
        val searchMode = step.parameters["searchMode"] as? String ?: "自动"
        val modeDesc = if (searchMode == "自动") "" else " ($searchMode)"

        return PillUtil.buildSpannable(context, "等待 ", targetPill, " 出现$modeDesc (超时 ", timeoutPill, " s)")
    }

    override val uiProvider: ModuleUIProvider? = null

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val rawTarget = context.variables["targetText"]?.toString() ?: ""
        val targetText = VariableResolver.resolve(rawTarget, context)
        val matchMode = context.variables["matchMode"] as? String ?: "包含"
        val searchMode = context.variables["searchMode"] as? String ?: "自动"

        val timeoutSec = ((context.magicVariables["timeout"] as? NumberVariable)?.value
            ?: (context.variables["timeout"] as? Number)?.toDouble() ?: 10.0).toLong()

        val interval = ((context.variables["interval"] as? Number)?.toLong() ?: 1000L).coerceAtLeast(100L)

        if (targetText.isEmpty()) return ExecutionResult.Failure("参数错误", "查找文本不能为空")

        val service = ServiceStateBus.getAccessibilityService()
        if (service == null && searchMode != "OCR") {
            return ExecutionResult.Failure("服务未运行", "无障碍服务未运行，且未强制使用 OCR 模式。")
        }

        onProgress(ProgressUpdate("开始查找 '$targetText' (模式: $searchMode, 超时 ${timeoutSec}s)..."))
        DebugLogger.d(TAG, "开始查找 '$targetText' (模式: $searchMode, 超时 ${timeoutSec}s)...")

        val startTime = System.currentTimeMillis()
        val timeoutMs = timeoutSec * 1000

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            var foundElement: ScreenElement? = null

            if (searchMode == "自动" || searchMode == "无障碍") {
                val root = service?.rootInActiveWindow
                if (root != null) {
                    val node = findNode(root, targetText, matchMode)
                    if (node != null) {
                        val bounds = Rect()
                        node.getBoundsInScreen(bounds)
                        foundElement = ScreenElement(bounds, node.text?.toString() ?: node.contentDescription?.toString())
                        node.recycle()
                    }
                }
            }

            if (foundElement == null && (searchMode == "自动" || searchMode == "OCR")) {
                foundElement = performOCR(context, targetText, matchMode)
            }

            if (foundElement != null) {
                val coordinate = Coordinate(foundElement.bounds.centerX(), foundElement.bounds.centerY())
                onProgress(ProgressUpdate("已找到目标，耗时 ${(System.currentTimeMillis() - startTime)/1000.0}s"))
                DebugLogger.d(TAG, "已找到目标，耗时 ${(System.currentTimeMillis() - startTime)/1000.0}s")

                return ExecutionResult.Success(mapOf(
                    "success" to BooleanVariable(true),
                    "element" to foundElement,
                    "coordinate" to coordinate
                ))
            }

            delay(interval)
        }

        onProgress(ProgressUpdate("查找超时"))
        return ExecutionResult.Success(mapOf(
            "success" to BooleanVariable(false)
        ))
    }

    private fun findNode(root: AccessibilityNodeInfo, text: String, mode: String): AccessibilityNodeInfo? {
        val queue = java.util.ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)

        while (!queue.isEmpty()) {
            val node = queue.removeFirst()
            val nodeText = node.text?.toString() ?: node.contentDescription?.toString()

            var isMatch = false
            if (!nodeText.isNullOrEmpty()) {
                isMatch = when (mode) {
                    "完全匹配" -> nodeText == text
                    "包含" -> nodeText.contains(text, ignoreCase = true)
                    "正则" -> try { Pattern.compile(text).matcher(nodeText).find() } catch (e: Exception) { false }
                    else -> nodeText.contains(text, ignoreCase = true)
                }
            }

            if (isMatch && node.isVisibleToUser) {
                return AccessibilityNodeInfo.obtain(node)
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }

    private suspend fun performOCR(context: ExecutionContext, targetText: String, matchMode: String): ScreenElement? {
        val captureModule = ModuleRegistry.getModule("vflow.system.capture_screen") ?: return null
        val tempContext = context.copy(variables = mutableMapOf("mode" to "自动"))
        val result = captureModule.execute(tempContext) { }

        if (result is ExecutionResult.Success) {
            val imageVar = result.outputs["image"] as? ImageVariable ?: return null
            val imageUri = Uri.parse(imageVar.uri)

            try {
                val inputImage = InputImage.fromFilePath(context.applicationContext, imageUri)
                val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
                val visionText = recognizer.process(inputImage).await()
                recognizer.close()

                for (block in visionText.textBlocks) {
                    for (line in block.lines) {
                        val lineText = line.text
                        val isMatch = when (matchMode) {
                            "完全匹配" -> lineText == targetText
                            "包含" -> lineText.contains(targetText, ignoreCase = true)
                            "正则" -> try { Pattern.compile(targetText).matcher(lineText).find() } catch (e: Exception) { false }
                            else -> lineText.contains(targetText, ignoreCase = true)
                        }

                        if (isMatch) {
                            val rect = line.boundingBox
                            if (rect != null) {
                                return ScreenElement(rect, lineText)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                DebugLogger.e(TAG,"OCR失败: ", e)
            }
        }
        return null
    }
}
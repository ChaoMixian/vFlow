// 文件: main/java/com/chaomixian/vflow/core/workflow/module/interaction/FindTextUntilModule.kt
package com.chaomixian.vflow.core.workflow.module.interaction

import android.content.ContentValues.TAG
import android.content.Context
import android.graphics.Rect
import android.net.Uri
import android.view.accessibility.AccessibilityNodeInfo
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.execution.VariableResolver
import com.chaomixian.vflow.core.logging.DebugLogger
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.types.VObjectFactory
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VBoolean
import com.chaomixian.vflow.core.types.basic.VNumber
import com.chaomixian.vflow.core.types.complex.VCoordinate
import com.chaomixian.vflow.core.types.complex.VImage
import com.chaomixian.vflow.core.types.complex.VScreenElement
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
 * "查找直到出现"原子模块。
 */
class FindTextUntilModule : BaseModule() {
    companion object {
        private const val MATCH_CONTAINS = "contains"
        private const val MATCH_EXACT = "exact"
        private const val MATCH_REGEX = "regex"
        private const val SEARCH_AUTO = "auto"
        private const val SEARCH_ACCESSIBILITY = "accessibility"
        private const val SEARCH_OCR = "ocr"
    }
    override val id = "vflow.interaction.find_until"
    override val metadata = ActionMetadata(
        nameStringRes = R.string.module_vflow_interaction_find_until_name,
        descriptionStringRes = R.string.module_vflow_interaction_find_until_desc,
        name = "查找直到出现",
        description = "持续查找屏幕上的文本，直到出现或超时。支持 OCR 兜底。",
        iconRes = R.drawable.rounded_search_24,
        category = "界面交互",
        categoryId = "interaction"
    )

    override fun getRequiredPermissions(step: ActionStep?): List<Permission> {
        return listOf(PermissionManager.ACCESSIBILITY) +
                ShellManager.getRequiredPermissions(com.chaomixian.vflow.core.logging.LogManager.applicationContext)
    }

    private val matchModeOptions = listOf(MATCH_CONTAINS, MATCH_EXACT, MATCH_REGEX)
    private val searchModeOptions = listOf(SEARCH_AUTO, SEARCH_ACCESSIBILITY, SEARCH_OCR)

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition("targetText", "目标文本", ParameterType.STRING, "", acceptsMagicVariable = true, supportsRichText = true, nameStringRes = R.string.param_vflow_interaction_find_until_target_text_name),
        InputDefinition("matchMode", "匹配模式", ParameterType.ENUM, MATCH_CONTAINS, options = matchModeOptions, optionsStringRes = listOf(R.string.option_vflow_interaction_find_until_match_contains, R.string.option_vflow_interaction_find_until_match_exact, R.string.option_vflow_interaction_find_until_match_regex), legacyValueMap = mapOf("包含" to MATCH_CONTAINS, "Contains" to MATCH_CONTAINS, "完全匹配" to MATCH_EXACT, "Exact Match" to MATCH_EXACT, "正则" to MATCH_REGEX, "Regex" to MATCH_REGEX), acceptsMagicVariable = false, nameStringRes = R.string.param_vflow_interaction_find_until_match_mode_name),
        InputDefinition("timeout", "超时时间(秒)", ParameterType.NUMBER, 10.0, acceptsMagicVariable = true, acceptedMagicVariableTypes = setOf(VTypeRegistry.NUMBER.id), nameStringRes = R.string.param_vflow_interaction_find_until_timeout_name),

        InputDefinition("searchMode", "查找模式", ParameterType.ENUM, SEARCH_AUTO, options = searchModeOptions, optionsStringRes = listOf(R.string.option_vflow_interaction_find_until_search_auto, R.string.option_vflow_interaction_find_until_search_accessibility, R.string.option_vflow_interaction_find_until_search_ocr), legacyValueMap = mapOf("自动" to SEARCH_AUTO, "Auto" to SEARCH_AUTO, "无障碍" to SEARCH_ACCESSIBILITY, "Accessibility" to SEARCH_ACCESSIBILITY, "OCR" to SEARCH_OCR), acceptsMagicVariable = false, isFolded = true, nameStringRes = R.string.param_vflow_interaction_find_until_search_mode_name),
        InputDefinition("interval", "轮询间隔(ms)", ParameterType.NUMBER, 1000.0, acceptsMagicVariable = true, isFolded = true, nameStringRes = R.string.param_vflow_interaction_find_until_interval_name)
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition("success", "是否找到", VTypeRegistry.BOOLEAN.id, nameStringRes = R.string.output_vflow_interaction_find_until_success_name),
        OutputDefinition("element", "找到的元素", VTypeRegistry.SCREEN_ELEMENT.id, nameStringRes = R.string.output_vflow_interaction_find_until_element_name),
        OutputDefinition("coordinate", "中心坐标", VTypeRegistry.COORDINATE.id, nameStringRes = R.string.output_vflow_interaction_find_until_coordinate_name)
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val inputs = getInputs()
        val targetPill = PillUtil.createPillFromParam(step.parameters["targetText"], inputs.find { it.id == "targetText" })
        val timeoutPill = PillUtil.createPillFromParam(step.parameters["timeout"], inputs.find { it.id == "timeout" })
        val searchMode = step.parameters["searchMode"] as? String ?: SEARCH_AUTO
        val modeDesc = if (searchMode == SEARCH_AUTO) "" else " (${getSearchModeDisplayName(searchMode)})"

        return PillUtil.buildSpannable(context, context.getString(R.string.summary_vflow_device_wait_for_prefix), targetPill, context.getString(R.string.summary_vflow_device_wait_for_suffix), modeDesc, context.getString(R.string.summary_vflow_device_timeout_prefix), timeoutPill, context.getString(R.string.summary_vflow_device_timeout_suffix))
    }

    override val uiProvider: ModuleUIProvider? = null

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val rawTarget = context.getVariableAsString("targetText", "")
        val targetText = VariableResolver.resolve(rawTarget, context)
        val matchMode = context.getVariableAsString("matchMode", MATCH_CONTAINS)
        val searchMode = context.getVariableAsString("searchMode", SEARCH_AUTO)

        val timeoutSec = context.getVariableAsLong("timeout") ?: 10
        val interval = (context.getVariableAsLong("interval") ?: 1000).coerceAtLeast(100)

        if (targetText.isEmpty()) {
            return ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_interaction_find_until_param_error),
                appContext.getString(R.string.error_vflow_interaction_find_until_target_empty)
            )
        }

        val service = ServiceStateBus.getAccessibilityService()
        if (service == null && searchMode != SEARCH_OCR) {
            return ExecutionResult.Failure(
                appContext.getString(R.string.error_vflow_interaction_find_until_service_unavailable),
                appContext.getString(R.string.error_vflow_interaction_find_until_accessibility_required)
            )
        }

        val searchModeLabel = getSearchModeDisplayName(searchMode)
        val searchStartMessage = appContext.getString(
            R.string.progress_vflow_interaction_find_until_start,
            targetText,
            searchModeLabel,
            timeoutSec
        )
        onProgress(ProgressUpdate(searchStartMessage))
        DebugLogger.d(TAG, searchStartMessage)

        val startTime = System.currentTimeMillis()
        val timeoutMs = timeoutSec * 1000

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            var foundElement: VScreenElement? = null

            if (searchMode == SEARCH_AUTO || searchMode == SEARCH_ACCESSIBILITY) {
                val root = service?.rootInActiveWindow
                if (root != null) {
                    val node = findNode(root, targetText, matchMode)
                    if (node != null) {
                        foundElement = VScreenElement.fromAccessibilityNode(node)
                        node.recycle()
                    }
                }
            }

            if (foundElement == null && (searchMode == SEARCH_AUTO || searchMode == SEARCH_OCR)) {
                foundElement = performOCR(context, targetText, matchMode)
            }

            if (foundElement != null) {
                val coordinate = VCoordinate(foundElement.bounds.centerX(), foundElement.bounds.centerY())
                val elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000.0
                val foundMessage = appContext.getString(
                    R.string.progress_vflow_interaction_find_until_found,
                    elapsedSeconds
                )
                onProgress(ProgressUpdate(foundMessage))
                DebugLogger.d(TAG, foundMessage)

                return ExecutionResult.Success(mapOf(
                    "success" to VBoolean(true),
                    "element" to foundElement,
                    "coordinate" to coordinate
                ))
            }

            delay(interval)
        }

        onProgress(ProgressUpdate(appContext.getString(R.string.progress_vflow_interaction_find_until_timeout)))
        return ExecutionResult.Success(mapOf(
            "success" to VBoolean(false)
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
                    MATCH_EXACT -> nodeText == text
                    MATCH_CONTAINS -> nodeText.contains(text, ignoreCase = true)
                    MATCH_REGEX -> try { Pattern.compile(text).matcher(nodeText).find() } catch (e: Exception) { false }
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

    private suspend fun performOCR(context: ExecutionContext, targetText: String, matchMode: String): VScreenElement? {
        val captureModule = ModuleRegistry.getModule("vflow.system.capture_screen") ?: return null
        val tempContext = context.copy(variables = mutableMapOf("mode" to VObjectFactory.from("自动")))
        val result = captureModule.execute(tempContext) { }

        if (result is ExecutionResult.Success) {
            val imageVar = result.outputs["image"] as? VImage ?: return null
            val imageUri = Uri.parse(imageVar.uriString)

            try {
                val inputImage = InputImage.fromFilePath(context.applicationContext, imageUri)
                val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
                val visionText = recognizer.process(inputImage).await()
                recognizer.close()

                for (block in visionText.textBlocks) {
                    for (line in block.lines) {
                        val lineText = line.text
                        val isMatch = when (matchMode) {
                            MATCH_EXACT -> lineText == targetText
                            MATCH_CONTAINS -> lineText.contains(targetText, ignoreCase = true)
                            MATCH_REGEX -> try { Pattern.compile(targetText).matcher(lineText).find() } catch (e: Exception) { false }
                            else -> lineText.contains(targetText, ignoreCase = true)
                        }

                        if (isMatch) {
                            val rect = line.boundingBox
                            if (rect != null) {
                                // OCR 结果不是无障碍节点，创建最小化的 VScreenElement
                                return VScreenElement(
                                    bounds = rect,
                                    text = lineText,
                                    contentDescription = null,
                                    viewId = null,
                                    className = null,
                                    isClickable = false,
                                    isEnabled = true,
                                    isCheckable = false,
                                    isChecked = false,
                                    isFocusable = false,
                                    isFocused = false,
                                    isScrollable = false,
                                    isLongClickable = false,
                                    isSelected = false,
                                    isEditable = false,
                                    depth = 0,
                                    childCount = 0,
                                    accessibilityId = null
                                )
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

    private fun getSearchModeDisplayName(mode: String): String {
        return when (mode) {
            SEARCH_ACCESSIBILITY -> appContext.getString(R.string.option_vflow_interaction_find_until_search_accessibility)
            SEARCH_OCR -> appContext.getString(R.string.option_vflow_interaction_find_until_search_ocr)
            else -> appContext.getString(R.string.option_vflow_interaction_find_until_search_auto)
        }
    }
}

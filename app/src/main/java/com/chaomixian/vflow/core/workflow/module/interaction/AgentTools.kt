// 文件: main/java/com/chaomixian/vflow/core/workflow/module/interaction/AgentTools.kt
package com.chaomixian.vflow.core.workflow.module.interaction

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.net.Uri
import android.view.accessibility.AccessibilityNodeInfo
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.logging.DebugLogger
import com.chaomixian.vflow.core.module.BooleanVariable
import com.chaomixian.vflow.core.module.ExecutionResult
import com.chaomixian.vflow.core.module.ImageVariable
import com.chaomixian.vflow.core.module.ModuleRegistry
import com.chaomixian.vflow.services.ServiceStateBus
import com.chaomixian.vflow.services.ShizukuManager
import kotlinx.coroutines.CompletableDeferred
import java.util.ArrayDeque
import kotlin.math.abs

/**
 * Agent 专用工具集。
 * 封装了底层的操作逻辑，提供高容错性的原子操作供 AI 调用。
 * 集成 OCR 兜底逻辑，解决微信等 App 无障碍失效的问题。
 */
class AgentTools(private val context: ExecutionContext) {

    private val appContext = context.applicationContext
    private val TAG = "AgentTools"

    /**
     * 智能点击元素。
     * 策略升级：
     * 1. 无障碍查找 (快速，准确，但可能被屏蔽)
     * 2. 全局模糊扫描 (处理文本细微差异)
     * 3. OCR 视觉查找 (终极兜底，专门对付微信/游戏等非标准UI)
     */
    suspend fun clickElement(target: String): String {
        DebugLogger.d(TAG, "Agent请求点击: $target")
        val service = ServiceStateBus.getAccessibilityService()

        // 1. 尝试无障碍查找 (如果服务可用)
        if (service != null) {
            val root = service.rootInActiveWindow
            if (root != null) {
                // 1.1 快速查找
                var bestNode: AccessibilityNodeInfo? = null
                val textNodes = root.findAccessibilityNodeInfosByText(target)
                if (!textNodes.isNullOrEmpty()) {
                    bestNode = textNodes.find { it.isVisibleToUser }
                }
                if (bestNode == null) {
                    val idNodes = root.findAccessibilityNodeInfosByViewId(target)
                    if (!idNodes.isNullOrEmpty()) {
                        bestNode = idNodes.find { it.isVisibleToUser }
                    }
                }

                // 1.2 评分模糊查找
                if (bestNode == null) {
                    val cleanTarget = normalizeText(target)
                    val deepScanResult = findBestNodeFuzzy(root, cleanTarget)
                    if (deepScanResult != null && calculateMatchScore(deepScanResult, cleanTarget) < 40) {
                        bestNode = deepScanResult
                    }
                }

                // 1.3 如果找到节点，执行点击
                if (bestNode != null) {
                    val result = clickAccessibilityNode(bestNode, target)
                    // 如果点击成功，直接返回；如果失败，继续尝试 OCR
                    if (!result.startsWith("Failed")) return result
                }
            }
        }

        // --- 阶段 2: OCR 视觉查找 (无障碍失效时回落) ---
        DebugLogger.w(TAG, "无障碍查找失败，启动 OCR 视觉定位: $target")
        if (clickByOCR(target)) {
            return "Success: 无障碍树中未找到，但通过 OCR 视觉识别并点击了 '$target'。"
        }

        return "Failed: 在屏幕上未找到包含 '$target' 的元素 (无障碍和OCR均失败)。"
    }

    /**
     * 执行无障碍节点的点击逻辑
     */
    private suspend fun clickAccessibilityNode(node: AccessibilityNodeInfo, targetDesc: String): String {
        val rect = Rect()
        node.getBoundsInScreen(rect)

        val displayMetrics = appContext.resources.displayMetrics
        val screenRect = Rect(0, 0, displayMetrics.widthPixels, displayMetrics.heightPixels)

        // 坐标有效性检查
        if (rect.intersect(screenRect)) {
            // 优先物理点击
            if (clickCoordinates(rect.centerX(), rect.centerY())) {
                return "Success: 已点击 '$targetDesc' (物理模拟)。"
            }
        }

        // 回退逻辑点击
        if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            return "Success: 已通过无障碍API点击 '$targetDesc'。"
        }

        // 尝试父节点
        var parent = node.parent
        while (parent != null) {
            if (parent.isClickable && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return "Success: 点击了父容器。"
            }
            parent = parent.parent
        }

        return "Failed"
    }

    /**
     * 使用 OCR 模块查找并点击文字
     */
    private suspend fun clickByOCR(targetText: String): Boolean {
        // 1. 获取截图模块
        val captureModule = ModuleRegistry.getModule("vflow.system.capture_screen") ?: return false
        val ocrModule = ModuleRegistry.getModule("vflow.interaction.ocr") ?: return false

        // 2. 执行截图
        // 使用临时的 Context，避免污染主流程变量
        val captureContext = context.copy(variables = mutableMapOf("mode" to "自动"))
        val captureRes = captureModule.execute(captureContext) { } // 静默执行

        val imagePath = if (captureRes is ExecutionResult.Success) {
            (captureRes.outputs["image"] as? ImageVariable)?.uri
        } else null

        if (imagePath == null) {
            DebugLogger.e(TAG, "OCR 准备失败: 无法截屏")
            return false
        }

        // 3. 执行 OCR 查找
        val ocrParams = mutableMapOf<String, Any?>(
            "mode" to "查找文本",
            "target_text" to targetText,
            "language" to "中英混合",
            "search_strategy" to "默认 (从上到下)"
        )

        // 直接注入 "image" 变量，而不是 "ocr_source_img"
        // 也不要使用 "{{image}}" 引用，因为这里不经过 VariableResolver
        val ocrMagicVars = mutableMapOf<String, Any?>("image" to ImageVariable(imagePath))

        val ocrContext = context.copy(
            variables = ocrParams,
            magicVariables = ocrMagicVars
        )

        DebugLogger.d(TAG, "正在执行 OCR 查找: $targetText")
        val ocrRes = ocrModule.execute(ocrContext) { }

        if (ocrRes is ExecutionResult.Success) {
            val found = (ocrRes.outputs["found"] as? BooleanVariable)?.value == true
            if (found) {
                val firstMatch = ocrRes.outputs["first_match"] as? ScreenElement
                if (firstMatch != null) {
                    val x = firstMatch.bounds.centerX()
                    val y = firstMatch.bounds.centerY()
                    DebugLogger.i(TAG, "OCR 找到 '$targetText' 位于 ($x, $y)，正在点击...")
                    return clickCoordinates(x, y)
                }
            }
        } else if (ocrRes is ExecutionResult.Failure) {
            DebugLogger.e(TAG, "OCR 执行失败: ${ocrRes.errorMessage}")
        }

        DebugLogger.w(TAG, "OCR 未找到文本: $targetText")
        return false
    }

    // --- 辅助逻辑 ---

    private fun calculateMatchScore(node: AccessibilityNodeInfo, cleanTarget: String): Int {
        val text = normalizeText(node.text?.toString() ?: "")
        val desc = normalizeText(node.contentDescription?.toString() ?: "")
        val id = normalizeText(node.viewIdResourceName?.substringAfter(":id/") ?: "")
        val scoreText = getScore(text, cleanTarget)
        val scoreDesc = getScore(desc, cleanTarget)
        val scoreId = if (id.isNotEmpty()) getScore(id, cleanTarget) else Int.MAX_VALUE
        return minOf(scoreText, scoreDesc, scoreId)
    }

    private fun getScore(candidate: String, target: String): Int {
        if (candidate.isEmpty()) return Int.MAX_VALUE
        if (candidate == target) return 0
        val lenDiff = abs(candidate.length - target.length)
        if (target.contains(candidate)) return 10 + lenDiff
        if (candidate.contains(target)) return 50 + lenDiff
        return Int.MAX_VALUE
    }

    private fun findBestNodeFuzzy(root: AccessibilityNodeInfo, cleanTarget: String): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var currentBest: AccessibilityNodeInfo? = null
        var currentMinScore = Int.MAX_VALUE
        while (!queue.isEmpty()) {
            val node = queue.removeFirst()
            if (node.isVisibleToUser) {
                val score = calculateMatchScore(node, cleanTarget)
                if (score < currentMinScore) {
                    currentMinScore = score
                    currentBest = node
                    if (score == 0) return node
                }
            }
            for (i in 0 until node.childCount) node.getChild(i)?.let { queue.add(it) }
        }
        return currentBest
    }

    private fun normalizeText(text: String): String {
        return text.replace(Regex("[^a-zA-Z0-9\\u4e00-\\u9fa5]"), "").lowercase()
    }

    suspend fun clickCoordinates(x: Int, y: Int): Boolean {
        val service = ServiceStateBus.getAccessibilityService()
        if (service != null) {
            val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, 50))
                .build()
            val deferred = CompletableDeferred<Boolean>()
            service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(g: GestureDescription?) { deferred.complete(true) }
                override fun onCancelled(g: GestureDescription?) { deferred.complete(false) }
            }, null)
            if (deferred.await()) return true
        }
        if (ShizukuManager.isShizukuActive(appContext)) {
            val result = ShizukuManager.execShellCommand(appContext, "input tap $x $y")
            return !result.startsWith("Error")
        }
        return false
    }

    suspend fun inputText(text: String): String {
        val service = ServiceStateBus.getAccessibilityService()
        val focusNode = service?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focusNode != null && focusNode.isEditable) {
            val args = android.os.Bundle()
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            if (focusNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) return "Success: 已输入文本。"
        }
        if (ShizukuManager.isShizukuActive(appContext)) {
            val safeText = text.replace("\"", "\\\"").replace("'", "\\'")
            val result = ShizukuManager.execShellCommand(appContext, "input text \"$safeText\"")
            if (!result.startsWith("Error")) return "Success: 通过 Shell 输入了文本。"
        }
        return "Failed: 无法输入文本。"
    }

    suspend fun scroll(direction: String): String {
        val service = ServiceStateBus.getAccessibilityService() ?: return "Error: 服务未运行"
        val displayMetrics = appContext.resources.displayMetrics
        val cx = (displayMetrics.widthPixels / 2).toFloat()
        val h = displayMetrics.heightPixels.toFloat()
        val w = displayMetrics.widthPixels.toFloat()
        val cy = (h / 2)
        val path = Path()
        when (direction.lowercase()) {
            "down" -> { path.moveTo(cx, h * 0.8f); path.lineTo(cx, h * 0.2f) }
            "up" -> { path.moveTo(cx, h * 0.2f); path.lineTo(cx, h * 0.8f) }
            "right" -> { path.moveTo(w * 0.8f, cy); path.lineTo(w * 0.2f, cy) }
            "left" -> { path.moveTo(w * 0.2f, cy); path.lineTo(w * 0.8f, cy) }
            else -> return "Error: 未知方向"
        }
        val gesture = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, 300)).build()
        val deferred = CompletableDeferred<Boolean>()
        service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(g: GestureDescription?) { deferred.complete(true) }
            override fun onCancelled(g: GestureDescription?) { deferred.complete(false) }
        }, null)
        return if (deferred.await()) "Success: 向 $direction 滚动完成。" else "Failed: 滚动被取消。"
    }

    suspend fun launchApp(appNameOrPackage: String): String {
        val pm = appContext.packageManager
        var targetPackage = appNameOrPackage
        if (!targetPackage.contains(".")) {
            val packages = pm.getInstalledPackages(0)
            val match = packages.find { pkg -> pkg.applicationInfo?.let { pm.getApplicationLabel(it).toString().contains(appNameOrPackage, true) } == true }
            if (match != null) targetPackage = match.packageName else return "Failed: 未找到应用。"
        }
        val intent = pm.getLaunchIntentForPackage(targetPackage)
        if (intent != null) {
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            appContext.startActivity(intent)
            return "Success: 已启动应用。"
        }
        return "Failed: 无法启动应用。"
    }

    suspend fun pressKey(action: String): String {
        val service = ServiceStateBus.getAccessibilityService() ?: return "Error: 服务未运行。"
        val key = when(action.lowercase()) {
            "back" -> AccessibilityService.GLOBAL_ACTION_BACK
            "home" -> AccessibilityService.GLOBAL_ACTION_HOME
            "recents" -> AccessibilityService.GLOBAL_ACTION_RECENTS
            else -> return "Error: 未知按键"
        }
        service.performGlobalAction(key)
        return "Success: 执行了按键。"
    }
}
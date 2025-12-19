// 文件: main/java/com/chaomixian/vflow/core/workflow/module/interaction/AgentUtils.kt
package com.chaomixian.vflow.core.workflow.module.interaction

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.net.Uri
import android.util.Base64
import android.util.DisplayMetrics
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import com.chaomixian.vflow.core.logging.DebugLogger
import com.chaomixian.vflow.core.module.ExecutionResult
import com.chaomixian.vflow.core.module.ImageVariable
import com.chaomixian.vflow.core.module.ModuleRegistry
import com.chaomixian.vflow.services.ServiceStateBus
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Agent 感知工具类。
 * 负责获取屏幕截图（视觉感知）和 UI 树（结构感知）。
 */
object AgentUtils {

    private const val TAG = "AgentUtils"

    /**
     * 捕获当前屏幕，并返回 (Base64字符串, 文件路径)。
     */
    suspend fun captureScreen(
        context: Context,
        execContext: com.chaomixian.vflow.core.execution.ExecutionContext
    ): Pair<String?, String?> {
        val captureModule = ModuleRegistry.getModule("vflow.system.capture_screen")
        if (captureModule == null) {
            DebugLogger.e(TAG, "未找到截屏模块 (vflow.system.capture_screen)")
            return null to null
        }

        // 创建一个临时的执行上下文，强制使用 "自动" 模式
        val tempContext = execContext.copy(
            variables = mutableMapOf("mode" to "自动")
        )

        val result = captureModule.execute(tempContext) { /* 不输出子模块日志 */ }

        if (result is ExecutionResult.Success) {
            val imageVar = result.outputs["image"] as? ImageVariable
            if (imageVar != null) {
                val uri = Uri.parse(imageVar.uri)
                val path = uri.path
                val base64 = imageUriToBase64(context, uri)
                return base64 to path
            }
        }
        return null to null
    }

    /**
     * 提取并精简当前屏幕的 UI 树结构。
     * 增加了严格的可见性过滤，防止 AI 看到屏幕外或被遮挡的内容。
     */
    fun dumpHierarchy(context: Context): String {
        val service = ServiceStateBus.getAccessibilityService()
        val root = service?.rootInActiveWindow

        if (root == null) {
            return "UI Hierarchy unavailable (Accessibility Service not connected or restricted)"
        }

        // 获取屏幕物理尺寸
        val metrics = DisplayMetrics()
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        wm.defaultDisplay.getRealMetrics(metrics)
        val screenBounds = Rect(0, 0, metrics.widthPixels, metrics.heightPixels)

        val sb = StringBuilder()
        try {
            dumpNode(root, sb, 0, screenBounds)
        } catch (e: Exception) {
            sb.append("Error dumping hierarchy: ${e.message}")
        }
        return sb.toString()
    }

    private fun dumpNode(node: AccessibilityNodeInfo, sb: StringBuilder, depth: Int, screenBounds: Rect) {
        if (!node.isVisibleToUser) return

        // 1. 几何过滤：获取节点在屏幕上的绝对坐标
        val rect = Rect()
        node.getBoundsInScreen(rect)

        // 如果节点完全在屏幕外，或者与屏幕没有交集，忽略
        // Rect.intersects 判断两个矩形是否相交
        if (!Rect.intersects(rect, screenBounds)) {
            return
        }

        // 2. 尺寸过滤：忽略极其微小的节点 (例如 < 5x5 像素)，通常是不可见的辅助节点
        if (rect.width() < 5 || rect.height() < 5) {
            return
        }

        // 3. 信息过滤
        val text = node.text?.toString()
        val desc = node.contentDescription?.toString()
        val viewId = node.viewIdResourceName
        val isClickable = node.isClickable
        val isEditable = node.isEditable
        val isScrollable = node.isScrollable

        val hasContent = !text.isNullOrBlank() || !desc.isNullOrBlank()
        val isInteractive = isClickable || isEditable || isScrollable
        val hasId = !viewId.isNullOrBlank()

        if (hasContent || isInteractive || hasId) {
            val indent = "  ".repeat(depth)
            val className = node.className?.toString()?.substringAfterLast('.') ?: "View"

            sb.append(indent).append("<").append(className)

            // 对文本进行简单清洗，去除换行符，防止破坏 XML 结构
            if (!text.isNullOrBlank()) sb.append(" text=\"${text.replace("\n", " ").replace("\"", "'")}\"")
            if (!desc.isNullOrBlank()) sb.append(" desc=\"${desc.replace("\n", " ").replace("\"", "'")}\"")

            // 简化 ID，去掉包名部分，只保留 id/xxx
            if (hasId) sb.append(" id=\"${viewId!!.substringAfter(":id/")}\"")

            if (isClickable) sb.append(" clickable=\"true\"")
            if (isEditable) sb.append(" editable=\"true\"")
            if (isScrollable) sb.append(" scrollable=\"true\"")

            // 记录截断后的可视区域坐标
            // 计算与屏幕的交集，告诉 AI 实际可见的区域
            val visibleRect = Rect(rect)
            visibleRect.intersect(screenBounds)
            sb.append(" bounds=\"[${visibleRect.left},${visibleRect.top}][${visibleRect.right},${visibleRect.bottom}]\"")

            sb.append(" />\n")
        }

        val childCount = node.childCount
        for (i in 0 until childCount) {
            val child = node.getChild(i)
            if (child != null) {
                dumpNode(child, sb, depth + 1, screenBounds)
            }
        }
    }

    private fun imageUriToBase64(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val options = BitmapFactory.Options().apply { inSampleSize = 2 }
                val bitmap = BitmapFactory.decodeStream(input, null, options) ?: return null

                // 尺寸限制
                val maxDimension = 1024
                val scale = if (bitmap.width > maxDimension || bitmap.height > maxDimension) {
                    maxDimension.toFloat() / maxOf(bitmap.width, bitmap.height)
                } else 1f

                val finalBitmap = if (scale < 1f) {
                    val scaled = Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
                    if (scaled != bitmap) bitmap.recycle()
                    scaled
                } else bitmap

                val output = ByteArrayOutputStream()
                finalBitmap.compress(Bitmap.CompressFormat.JPEG, 60, output)

                finalBitmap.recycle()
                Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
            }
        } catch (e: Exception) {
            DebugLogger.e(TAG, "图片压缩失败", e)
            null
        }
    }
}
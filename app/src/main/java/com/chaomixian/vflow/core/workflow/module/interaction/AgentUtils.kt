// 文件: main/java/com/chaomixian/vflow/core/workflow/module/interaction/AgentUtils.kt
package com.chaomixian.vflow.core.workflow.module.interaction

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.net.Uri
import android.util.Base64
import android.util.DisplayMetrics
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.app.usage.UsageStatsManager
import com.chaomixian.vflow.core.logging.DebugLogger
import com.chaomixian.vflow.core.module.ExecutionResult
import com.chaomixian.vflow.core.module.ImageVariable
import com.chaomixian.vflow.core.module.ModuleRegistry
import com.chaomixian.vflow.permissions.PermissionManager
import com.chaomixian.vflow.services.ServiceStateBus
import java.io.ByteArrayOutputStream
import java.util.Calendar

/**
 * Agent 感知工具类。
 */
object AgentUtils {

    private const val TAG = "AgentUtils"

    data class ScreenshotResult(
        val base64: String?,
        val path: String?,
        val width: Int,
        val height: Int
    )

    suspend fun captureScreen(
        context: Context,
        execContext: com.chaomixian.vflow.core.execution.ExecutionContext
    ): ScreenshotResult {
        val captureModule = ModuleRegistry.getModule("vflow.system.capture_screen")
        if (captureModule == null) {
            DebugLogger.e(TAG, "未找到截屏模块")
            return ScreenshotResult(null, null, 0, 0)
        }

        val tempContext = execContext.copy(variables = mutableMapOf("mode" to "自动"))
        val result = captureModule.execute(tempContext) { }

        if (result is ExecutionResult.Success) {
            val imageVar = result.outputs["image"] as? ImageVariable
            if (imageVar != null) {
                val uri = Uri.parse(imageVar.uri)
                // 获取原图 Base64 和 原始尺寸
                val (base64, width, height) = imageUriToBase64(context, uri)
                return ScreenshotResult(base64, uri.path, width, height)
            }
        }
        return ScreenshotResult(null, null, 0, 0)
    }

    /**
     * 提取并精简当前屏幕的 UI 树结构。
     * 增加了严格的可见性过滤，防止 AI 看到屏幕外或被遮挡的内容。
     */
    fun dumpHierarchy(context: Context): String {
        val service = ServiceStateBus.getAccessibilityService()
        val root = service?.rootInActiveWindow ?: return "UI Hierarchy unavailable"

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

        // 几何过滤：获取节点在屏幕上的绝对坐标
        val rect = Rect()
        node.getBoundsInScreen(rect)

        if (!Rect.intersects(rect, screenBounds)) return
        // 稍微放宽最小尺寸限制，防止遗漏小按钮
        if (rect.width() < 1 || rect.height() < 1) return

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
            if (!text.isNullOrBlank()) sb.append(" text=\"${text.replace("\n", " ").replace("\"", "'")}\"")
            if (!desc.isNullOrBlank()) sb.append(" desc=\"${desc.replace("\n", " ").replace("\"", "'")}\"")
            if (hasId) sb.append(" id=\"${viewId!!.substringAfter(":id/")}\"")
            if (isClickable) sb.append(" clickable=\"true\"")
            if (isEditable) sb.append(" editable=\"true\"")
            if (isScrollable) sb.append(" scrollable=\"true\"")

            // 记录可见区域坐标
            val visibleRect = Rect(rect)
            visibleRect.intersect(screenBounds)
            sb.append(" bounds=\"[${visibleRect.left},${visibleRect.top}][${visibleRect.right},${visibleRect.bottom}]\"")
            sb.append(" />\n")
        }

        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { dumpNode(it, sb, depth + 1, screenBounds) }
        }
    }

    private fun imageUriToBase64(context: Context, uri: Uri): Triple<String?, Int, Int> {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                // 直接解码原图，不使用 inSampleSize 进行缩放
                val bitmap = BitmapFactory.decodeStream(input) ?: return Triple(null, 0, 0)

                val output = ByteArrayOutputStream()
                // 使用较高质量的 JPEG 压缩，确保文字清晰
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)

                val base64 = Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
                val width = bitmap.width
                val height = bitmap.height

                // 立即回收 Bitmap，释放内存
                bitmap.recycle()

                Triple(base64, width, height)
            } ?: Triple(null, 0, 0)
        } catch (e: Exception) {
            DebugLogger.e(TAG, "图片处理失败", e)
            Triple(null, 0, 0)
        }
    }

    // 获取最近 30 天使用过的应用列表，按时长排序
    fun getRecentApps(context: Context): String {
        // 1. 检查权限
        if (!PermissionManager.isGranted(context, PermissionManager.USAGE_STATS)) {
            // 如果没权限，回退到获取所有应用（原来的逻辑）
            return getInstalledApps(context)
        }

        try {
            val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val pm = context.packageManager

            // 2. 获取过去 30 天的数据
            val calendar = Calendar.getInstance()
            val endTime = calendar.timeInMillis
            calendar.add(Calendar.DAY_OF_YEAR, -30)
            val startTime = calendar.timeInMillis

            // queryAndAggregateUsageStats 返回 Map<packageName, UsageStats>
            val statsMap = usageStatsManager.queryAndAggregateUsageStats(startTime, endTime)

            if (statsMap.isEmpty()) {
                return getInstalledApps(context) // 系统未记录到数据，回退
            }

            // 3. 获取所有可启动的 App (作为白名单，过滤掉系统后台服务)
            val intent = Intent(Intent.ACTION_MAIN, null)
            intent.addCategory(Intent.CATEGORY_LAUNCHER)
            val launchableApps = pm.queryIntentActivities(intent, 0)
            val launchablePackageNames = launchableApps.map { it.activityInfo.packageName }.toSet()

            // 4. 过滤和排序
            val recentAppList = statsMap.values
                .filter {
                    it.totalTimeInForeground > 0 && // 确实使用过
                            launchablePackageNames.contains(it.packageName) // 是用户可见的 App
                }
                .sortedByDescending { it.totalTimeInForeground } // 按使用时长降序
                .mapNotNull { usageStats ->
                    try {
                        val appInfo = pm.getApplicationInfo(usageStats.packageName, 0)
                        val appName = appInfo.loadLabel(pm).toString()
                        // 格式: 微信 (com.tencent.mm)
                        "$appName (${usageStats.packageName})"
                    } catch (e: Exception) {
                        null
                    }
                }

            if (recentAppList.isEmpty()) {
                return getInstalledApps(context)
            }

            return recentAppList.joinToString(", ")

        } catch (e: Exception) {
            DebugLogger.e(TAG, "获取应用使用记录失败", e)
            return getInstalledApps(context) // 出错回退
        }
    }

    // 原有的全量获取方法，作为回退方案
    private fun getInstalledApps(context: Context): String {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null)
        intent.addCategory(Intent.CATEGORY_LAUNCHER)

        val apps = pm.queryIntentActivities(intent, 0)

        val appList = apps.mapNotNull { resolveInfo ->
            try {
                val appName = resolveInfo.loadLabel(pm).toString()
                val packageName = resolveInfo.activityInfo.packageName
                "$appName ($packageName)"
            } catch (e: Exception) {
                null
            }
        }.joinToString(", ")

        return if (appList.isNotEmpty()) appList else "No launchable apps found."
    }
}
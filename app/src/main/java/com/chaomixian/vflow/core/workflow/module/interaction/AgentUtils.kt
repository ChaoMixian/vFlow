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
import android.os.Build
import android.view.accessibility.AccessibilityWindowInfo
import androidx.annotation.RequiresApi
import com.chaomixian.vflow.core.logging.DebugLogger
import com.chaomixian.vflow.core.module.ExecutionResult
import com.chaomixian.vflow.core.types.complex.VImage
import com.chaomixian.vflow.core.module.ModuleRegistry
import com.chaomixian.vflow.core.utils.VirtualDisplayManager
import com.chaomixian.vflow.permissions.PermissionManager
import com.chaomixian.vflow.services.ServiceStateBus
import com.chaomixian.vflow.services.ShellManager
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
        // 检查是否有目标显示 ID
        val displayId = (execContext.variables["_target_display_id"] as? Number)?.toInt() ?: 0

        // 针对虚拟屏幕的处理逻辑 (ID > 0)
        if (displayId > 0) {
            // 优先尝试：从 VirtualDisplayManager 内存抓取 (最快)
            if (displayId == VirtualDisplayManager.getCurrentDisplayId()) {
                val bitmap = VirtualDisplayManager.captureCurrentFrame()
                if (bitmap != null) {
                    // 转 Base64
                    val output = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 80, output) // 80质量足够AI识别
                    val base64 = Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
                    val width = bitmap.width
                    val height = bitmap.height
                    bitmap.recycle()

                    // 虚拟路径，仅作标识
                    return ScreenshotResult(base64, "memory://virtual_display", width, height)
                } else {
                    DebugLogger.w(TAG, "虚拟屏幕 (ID: $displayId) 内存帧为空，准备回退到 Shell...")
                }
            }

            // 回退方案：使用 Shell screencap 指定 Display ID (较慢但稳)
            // 显式指定 -d displayId
            val shellResult = captureShellScreenshot(context, execContext.workDir, displayId)
            if (shellResult != null) {
                return shellResult
            }

            // 3. 如果都失败了，返回空结果，而不是去截主屏
            DebugLogger.e(TAG, "无法获取虚拟屏幕 (ID: $displayId) 的截图。")
            return ScreenshotResult(null, null, 0, 0)
        }

        // 主屏幕处理逻辑 (ID == 0)
        val captureModule = ModuleRegistry.getModule("vflow.system.capture_screen")
        if (captureModule == null) {
            DebugLogger.e(TAG, "未找到截屏模块")
            return ScreenshotResult(null, null, 0, 0)
        }

        val tempContext = execContext.copy(variables = mutableMapOf("mode" to "自动"))
        val result = captureModule.execute(tempContext) { }

        if (result is ExecutionResult.Success) {
            val imageVar = result.outputs["image"] as? VImage
            if (imageVar != null) {
                val uri = Uri.parse(imageVar.uriString)
                // 获取原图 Base64 和 原始尺寸
                val (base64, width, height) = imageUriToBase64(context, uri)
                return ScreenshotResult(base64, uri.path, width, height)
            }
        }
        return ScreenshotResult(null, null, 0, 0)
    }

    /**
     * 使用 Shell screencap 命令截取指定 Display 的屏幕
     */
    private suspend fun captureShellScreenshot(context: Context, workDir: java.io.File, displayId: Int): ScreenshotResult? {
        val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss_SSS", java.util.Locale.getDefault()).format(java.util.Date())
        val fileName = "screenshot_vd_${displayId}_$timestamp.png"
        val cacheFile = java.io.File(workDir, fileName)
        val path = cacheFile.absolutePath

        // [关键] 显式指定 -d displayId，确保截取的是后台虚拟屏幕
        val command = "screencap -d $displayId -p \"$path\""

        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                ShellManager.execShellCommand(context, command, ShellManager.ShellMode.AUTO)
                if (cacheFile.exists() && cacheFile.length() > 0) {
                    val uri = Uri.fromFile(cacheFile)
                    val (base64, width, height) = imageUriToBase64(context, uri)
                    ScreenshotResult(base64, path, width, height)
                } else {
                    DebugLogger.w(TAG, "Shell 截图未能生成文件 (Display $displayId)")
                    null
                }
            } catch (e: Exception) {
                DebugLogger.e(TAG, "Shell 截图异常", e)
                null
            }
        }
    }

    /**
     * 提取并精简当前屏幕的 UI 树结构。
     * 增加了严格的可见性过滤，防止 AI 看到屏幕外或被遮挡的内容。
     * 增加 displayId 参数以支持多屏幕。
     */
    @RequiresApi(Build.VERSION_CODES.R)
    fun dumpHierarchy(context: Context, displayId: Int = 0): String {
        val service = ServiceStateBus.getAccessibilityService()

        // 尝试根据 displayId 获取对应的根节点
        val root = if (displayId > 0) {
            service?.windows?.find { it.displayId == displayId }?.root
        } else {
            service?.rootInActiveWindow
        }

        if (root == null) return "UI Hierarchy unavailable (Service not running or window not found)"

        // 仅供参考的屏幕尺寸（如果是虚拟屏幕，可能需要从别处获取，暂使用默认显示器尺寸）
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

    /**
     * 获取当前界面信息。
     * 1. 优先使用 Shell (dumpsys window) 获取精确的 Activity 名。
     * 2. 如果失败，回退到 AccessibilityService 获取包名。
     */
    @RequiresApi(Build.VERSION_CODES.R)
    suspend fun getCurrentUIInfo(context: Context, displayId: Int = 0): String {
        // 尝试 Shell (Shizuku / Root) 获取 Activity
        if (ShellManager.isShizukuActive(context) || ShellManager.isRootAvailable()) {
            try {
                // 如果是虚拟屏幕，dumpsys 可能需要更复杂的 grep，或者假设焦点正确
                // 暂时使用全局 mCurrentFocus，它通常跟随用户或者最新活动的 display
                val result = ShellManager.execShellCommand(context, "dumpsys window | grep mCurrentFocus", ShellManager.ShellMode.AUTO)

                // 常见输出格式：
                // mCurrentFocus=Window{2b2c9d5 u0 com.package/com.package.Activity}
                // 或者在某些设备上：mCurrentFocus=null
                if (result.contains("/")) {
                    // 尝试提取 com.pkg/com.pkg.Activity
                    // 匹配 "u0 space package/activity" 或者 "package/activity}"
                    val match = Regex("u0\\s+(\\S+)/(\\S+)[}\\s]").find(result)
                    if (match != null) {
                        return "${match.groupValues[1]}/${match.groupValues[2].replace("}", "")}"
                    }

                    // 备用简单正则：匹配斜杠两侧的非空白字符
                    val simpleMatch = Regex("(\\S+\\.\\S+)/(\\S+\\.\\S+)").find(result)
                    if (simpleMatch != null) {
                        return simpleMatch.value.replace("}", "")
                    }
                }
            } catch (e: Exception) {
                DebugLogger.w("AutoGLM", "Shell fetch activity failed: ${e.message}")
            }
        }

        // 尝试无障碍服务 (仅获取包名)
        val service = ServiceStateBus.getAccessibilityService()
        if (service != null) {
            val root = if(displayId > 0) service.windows.find { it.displayId == displayId }?.root else service.rootInActiveWindow
            if (root != null) {
                val pkg = root.packageName?.toString()
                if (!pkg.isNullOrBlank()) {
                    return pkg
                }
            }
        }

        return "Unknown UI"
    }

    /**
     * 在指定屏幕上强制停止顶层应用。
     * 用于在销毁虚拟屏幕前清理现场，防止应用跳回主屏幕。
     */
    suspend fun killTopAppOnDisplay(context: Context, displayId: Int) {
        if (displayId <= 0) return

        DebugLogger.i(TAG, "正在清理虚拟屏幕 (ID: $displayId) 的应用...")
        val foundPackages = mutableSetOf<String>()

        // 1. 尝试无障碍服务 (轻量级)
        val service = ServiceStateBus.getAccessibilityService()
        if (service != null) {
            try {
                // 查找该显示器上的所有应用窗口
                val targetWindows = service.windows.filter {
                    it.displayId == displayId && it.type == AccessibilityWindowInfo.TYPE_APPLICATION
                }
                targetWindows.mapNotNull { it.root?.packageName?.toString() }.forEach { foundPackages.add(it) }
            } catch (e: Exception) {
                DebugLogger.w(TAG, "无障碍获取窗口失败: ${e.message}")
            }
        }

        // 2. 如果无障碍没找到，或者应用不可交互，尝试 Shell Dumpsys (更底层，更可靠)
        if (foundPackages.isEmpty()) {
            if (ShellManager.isShizukuActive(context) || ShellManager.isRootAvailable()) {
                DebugLogger.i(TAG, "无障碍未检测到应用，尝试 Shell Dumpsys 深度查找...")
                try {
                    // 查询 Activity 栈信息，并过滤出指定 Display ID 的部分
                    // grep 参数 -A 50 表示显示匹配行后的 50 行，通常包含了该 Display 下的 Task 和 Activity 信息
                    val cmd = "dumpsys activity activities | grep -A 50 \"Display #$displayId\" | grep \"ActivityRecord{\""
                    val output = ShellManager.execShellCommand(context, cmd, ShellManager.ShellMode.AUTO)

                    // 正则提取包名：ActivityRecord{... com.package.name/...}
                    val regex = Regex("ActivityRecord\\{[^ ]+ [^ ]+ ([^/ ]+)/")
                    regex.findAll(output).forEach { matchResult ->
                        val pkg = matchResult.groupValues[1]
                        DebugLogger.d(TAG, "通过 Dumpsys 发现应用: $pkg")
                        foundPackages.add(pkg)
                    }
                } catch (e: Exception) {
                    DebugLogger.e(TAG, "Shell 查找应用失败", e)
                }
            }
        }

        if (foundPackages.isNotEmpty()) {
            foundPackages.forEach { pkg ->
                // 过滤掉系统UI、vFlow自身、以及桌面Launcher (防止杀掉桌面导致黑屏/闪屏)
                if (pkg != "com.android.systemui" && pkg != context.packageName && !pkg.contains("launcher", ignoreCase = true)) {
                    DebugLogger.w(TAG, "检测到残留应用: $pkg，正在强制停止...")
                    ShellManager.execShellCommand(context, "am force-stop $pkg", ShellManager.ShellMode.AUTO)
                }
            }
        } else {
            DebugLogger.w(TAG, "未在虚拟屏幕检测到活动应用，跳过清理。")
        }
    }
}
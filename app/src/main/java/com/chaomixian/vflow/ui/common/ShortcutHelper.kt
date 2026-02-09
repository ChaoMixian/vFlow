package com.chaomixian.vflow.ui.common

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.workflow.WorkflowManager
import com.chaomixian.vflow.core.workflow.model.Workflow
import com.chaomixian.vflow.core.workflow.module.triggers.ManualTriggerModule

/**
 * 辅助对象，用于管理应用的动态快捷方式和固定快捷方式。
 */
object ShortcutHelper {

    private const val MAX_SHORTCUTS = 3 // 快捷方式最大数量

    /**
     * 更新应用的动态快捷方式 (长按应用图标显示)。
     */
    fun updateShortcuts(context: Context) {
        val workflowManager = WorkflowManager(context)

        // 1. 查找所有手动触发的工作流，并分为收藏和未收藏两组
        val allManualWorkflows = workflowManager.getAllWorkflows()
            .filter { it.steps.firstOrNull()?.moduleId == ManualTriggerModule().id }

        val favoriteWorkflows = allManualWorkflows.filter { it.isFavorite }
        val nonFavoriteWorkflows = allManualWorkflows.filter { !it.isFavorite }

        // 2. 构建最终的快捷方式列表
        val shortcutWorkflows = (favoriteWorkflows + nonFavoriteWorkflows).take(MAX_SHORTCUTS)

        // 3. 为每个工作流创建一个快捷方式信息
        val shortcuts = shortcutWorkflows.map { workflow ->
            createShortcutInfo(context, workflow)
        }

        // 4. 使用 ShortcutManagerCompat 设置（覆盖）现有的动态快捷方式
        ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
    }

    /**
     * 请求创建一个固定在主屏幕的快捷方式。
     *
     * @param context 上下文。
     * @param workflow 要创建快捷方式的工作流。
     */
    fun requestPinnedShortcut(context: Context, workflow: Workflow) {
        if (ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
            val shortcutInfo = createShortcutInfo(context, workflow)

            // 创建回调 Intent，虽然对于简单的固定操作通常不需要复杂的处理，
            // 但系统API要求有一个 PendingIntent 用于接收成功广播。
            // 这里我们创建一个空的 Intent，或者可以指向一个 BroadcastReceiver 来提示用户创建成功。
            val pinnedShortcutCallbackIntent = ShortcutManagerCompat.createShortcutResultIntent(context, shortcutInfo)
            val successCallback = PendingIntent.getBroadcast(
                context,
                0,
                pinnedShortcutCallbackIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            ShortcutManagerCompat.requestPinShortcut(context, shortcutInfo, successCallback.intentSender)

            // 提示用户请求已发送（具体的添加结果取决于Launcher的行为）
            // 注意：Android 8.0+ 通常会弹出一个系统对话框让用户确认
        } else {
            Toast.makeText(context, "当前启动器不支持固定快捷方式", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 内部辅助方法：构建 ShortcutInfoCompat 对象。
     */
    private fun createShortcutInfo(context: Context, workflow: Workflow): ShortcutInfoCompat {
        // 创建点击快捷方式时触发的 Intent
        val intent = Intent(context, ShortcutExecutorActivity::class.java).apply {
            action = ShortcutExecutorActivity.ACTION_EXECUTE_WORKFLOW
            putExtra(ShortcutExecutorActivity.EXTRA_WORKFLOW_ID, workflow.id)
            // 添加 FLAG_ACTIVITY_CLEAR_TASK 以确保每次都创建一个新的任务栈
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        // 获取自定义名称，如果没有则使用工作流名称
        val shortLabel = (workflow.shortcutName?.takeIf { it.isNotEmpty() } ?: workflow.name).take(10)
        val longLabel = workflow.shortcutName?.takeIf { it.isNotEmpty() } ?: workflow.name

        // 获取自定义图标，如果没有则使用默认图标
        val iconCompat = workflow.shortcutIconRes?.let { iconRes ->
            // 检查是否是自定义图片文件路径
            if (iconRes.startsWith("/") || iconRes.startsWith("file://")) {
                try {
                    val path = if (iconRes.startsWith("file://")) {
                        iconRes.substring(7)
                    } else {
                        iconRes
                    }
                    val file = java.io.File(path)
                    if (file.exists()) {
                        android.util.Log.d("ShortcutHelper", "Using custom image from file: $path")
                        loadCenterCroppedBitmap(file.path)?.let { bitmap ->
                            IconCompat.createWithAdaptiveBitmap(bitmap)
                        }
                    } else {
                        android.util.Log.w("ShortcutHelper", "Custom image file not found: $path")
                        null
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ShortcutHelper", "Failed to load custom image", e)
                    null
                }
            } else {
                // 从资源加载
                val resId = context.resources.getIdentifier(
                    iconRes,
                    "drawable",
                    context.packageName
                )
                android.util.Log.d("ShortcutHelper", "Icon resource name: $iconRes, resolved ID: $resId")
                if (resId != 0) {
                    IconCompat.createWithResource(context, resId)
                } else {
                    null
                }
            }
        } ?: IconCompat.createWithResource(context, R.drawable.ic_shortcut_play)

        android.util.Log.d("ShortcutHelper", "Creating shortcut for workflow: ${workflow.name}")
        android.util.Log.d("ShortcutHelper", "Custom name: ${workflow.shortcutName}, shortLabel: $shortLabel, longLabel: $longLabel")
        android.util.Log.d("ShortcutHelper", "Custom icon: ${workflow.shortcutIconRes}")

        return ShortcutInfoCompat.Builder(context, workflow.id) // 使用工作流ID作为快捷方式的唯一ID
            .setShortLabel(shortLabel) // 设置短标签
            .setLongLabel(longLabel) // 设置长标签
            .setIcon(iconCompat) // 设置图标
            .setIntent(intent) // 设置意图
            .build()
    }

    /**
     * 加载并裁剪图片为正方形（中心裁剪），然后缩放到目标尺寸
     */
    private fun loadCenterCroppedBitmap(filePath: String): android.graphics.Bitmap? {
        // 先获取图片尺寸
        val options = android.graphics.BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        android.graphics.BitmapFactory.decodeFile(filePath, options)

        if (options.outWidth <= 0 || options.outHeight <= 0) {
            return null
        }

        // 计算正方形边长（取宽高的较小值）
        val squareSize = minOf(options.outWidth, options.outHeight)

        // 计算采样率，使加载后的图片不超过目标尺寸的2倍（为了更好的质量）
        val targetSize = 192
        var inSampleSize = 1
        if (squareSize > targetSize * 2) {
            inSampleSize = squareSize / (targetSize * 2)
            // 确保是2的幂次方
            inSampleSize = Integer.highestOneBit(inSampleSize)
        }

        // 加载缩放后的图片
        val decodeOptions = android.graphics.BitmapFactory.Options().apply {
            inSampleSize = inSampleSize
        }
        val fullBitmap = android.graphics.BitmapFactory.decodeFile(filePath, decodeOptions) ?: return null

        try {
            // 计算裁剪区域（中心正方形）
            val scaledWidth = fullBitmap.width
            val scaledHeight = fullBitmap.height
            val cropSize = minOf(scaledWidth, scaledHeight)
            val x = (scaledWidth - cropSize) / 2
            val y = (scaledHeight - cropSize) / 2

            // 裁剪出中心正方形
            val croppedBitmap = android.graphics.Bitmap.createBitmap(
                fullBitmap,
                x, y, cropSize, cropSize
            )

            // 如果裁剪后的图片不等于原图，回收原图
            if (croppedBitmap != fullBitmap) {
                fullBitmap.recycle()
            }

            // 缩放到目标尺寸
            val finalBitmap = android.graphics.Bitmap.createScaledBitmap(
                croppedBitmap,
                targetSize,
                targetSize,
                true
            )

            // 如果缩放后的图片不等于裁剪图片，回收裁剪图片
            if (finalBitmap != croppedBitmap) {
                croppedBitmap.recycle()
            }

            return finalBitmap
        } catch (e: Exception) {
            fullBitmap.recycle()
            android.util.Log.e("ShortcutHelper", "Failed to crop bitmap", e)
            return null
        }
    }
}
package com.chaomixian.vflow.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.workflow.WorkflowManager
import com.chaomixian.vflow.ui.common.ShortcutExecutorActivity

class WorkflowWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        // 更新每一个小组件实例
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    // 定义一个简单的数据类来保存插槽的ID对
    data class SlotIds(val containerId: Int, val nameId: Int)

    companion object {
        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val prefs = context.getSharedPreferences(WidgetConfigActivity.PREFS_NAME, Context.MODE_PRIVATE)
            val workflowManager = WorkflowManager(context)
            val views = RemoteViews(context.packageName, R.layout.widget_workflow_grid)

            // --- 动态取色适配 (Android 12+) ---
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val isNightMode = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

                // 设置背景色 (Material You 风格)
                // 浅色模式使用 system_neutral1_50 (接近白色但带有壁纸色调)
                // 深色模式使用 system_neutral1_900 (深灰色带有壁纸色调)
                val bgColorRes = if (isNightMode) android.R.color.system_neutral1_900 else android.R.color.system_neutral1_50
                val bgColor = context.getColor(bgColorRes)
                views.setColorStateList(R.id.widget_root, "setBackgroundTintList", ColorStateList.valueOf(bgColor))

                // 设置插槽项背景色 (与主背景有区分)
                // 浅色模式使用 system_accent2_100 (淡淡的强调色)
                // 深色模式使用 system_accent2_800 (深色的强调色)
                val itemBgColorRes = if (isNightMode) android.R.color.system_accent2_800 else android.R.color.system_accent2_100
                val itemBgColor = context.getColor(itemBgColorRes)
                val itemBgTint = ColorStateList.valueOf(itemBgColor)

                // 设置文字颜色
                val textColorRes = if (isNightMode) android.R.color.system_accent1_100 else android.R.color.system_accent1_900
                val textColor = context.getColor(textColorRes)

                // 应用颜色到所有插槽
                val allContainers = listOf(R.id.slot_container_1, R.id.slot_container_2, R.id.slot_container_3, R.id.slot_container_4)
                val allTexts = listOf(R.id.slot_name_1, R.id.slot_name_2, R.id.slot_name_3, R.id.slot_name_4)

                allContainers.forEach { id ->
                    views.setColorStateList(id, "setBackgroundTintList", itemBgTint)
                }
                allTexts.forEach { id ->
                    views.setTextColor(id, textColor)
                }
            }
            // 对于 Android 11 及以下，使用 XML 中定义的默认颜色 (#F5F5F5 等)

            // 定义 4 个插槽对应的布局 ID，必须与 XML (widget_workflow_list.xml) 中的 ID 完全一致
            val slots = listOf(
                SlotIds(R.id.slot_container_1, R.id.slot_name_1),
                SlotIds(R.id.slot_container_2, R.id.slot_name_2),
                SlotIds(R.id.slot_container_3, R.id.slot_name_3),
                SlotIds(R.id.slot_container_4, R.id.slot_name_4)
            )

            // 遍历 4 个插槽
            for (i in 0 until 4) {
                // 获取保存的工作流 ID
                val workflowId = prefs.getString(WidgetConfigActivity.PREF_PREFIX_KEY + appWidgetId + "_slot_" + i, null)
                val ids = slots[i]

                if (workflowId != null) {
                    val workflow = workflowManager.getWorkflow(workflowId)
                    if (workflow != null) {
                        // 显示插槽并设置名称
                        views.setViewVisibility(ids.containerId, View.VISIBLE)
                        views.setTextViewText(ids.nameId, workflow.name)

                        // 创建点击 Intent，启动 ShortcutExecutorActivity
                        val intent = Intent(context, ShortcutExecutorActivity::class.java).apply {
                            action = ShortcutExecutorActivity.ACTION_EXECUTE_WORKFLOW
                            putExtra(ShortcutExecutorActivity.EXTRA_WORKFLOW_ID, workflow.id)
                            // 关键：添加这些 flag 确保 Activity 能在后台正确启动
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        }

                        // 使用唯一的 request code (appWidgetId * 10 + index) 防止 PendingIntent 被覆盖
                        val uniqueRequestCode = (appWidgetId * 10) + i
                        val pendingIntent = PendingIntent.getActivity(
                            context,
                            uniqueRequestCode,
                            intent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )

                        views.setOnClickPendingIntent(ids.containerId, pendingIntent)
                    } else {
                        // 工作流已删除，显示失效状态
                        views.setViewVisibility(ids.containerId, View.VISIBLE)
                        views.setTextViewText(ids.nameId, "失效")
                        views.setOnClickPendingIntent(ids.containerId, null)
                    }
                } else {
                    // 未配置该插槽，隐藏（Invisible 保持占位，Gone 移除）
                    // 这里使用 INVISIBLE 保持 2x4 的网格结构
                    views.setViewVisibility(ids.containerId, View.INVISIBLE)
                    // 必须清除点击事件，防止误触
                    views.setOnClickPendingIntent(ids.containerId, null)
                }
            }

            // 通知系统更新小组件
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
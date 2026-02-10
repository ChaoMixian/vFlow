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

/**
 * 小组件 Provider - 支持多种布局
 */
class WorkflowWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            WidgetUpdater.updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        const val LAYOUT_4X2 = "4x2"
        const val LAYOUT_2X2 = "2x2"
    }
}

/**
 * 2x2 布局的 Widget Provider
 */
class WorkflowWidgetProvider2x2 : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            WidgetUpdater.updateAppWidget(context, appWidgetManager, appWidgetId, WorkflowWidgetProvider.LAYOUT_2X2)
        }
    }
}

/**
 * 小组件更新逻辑
 */
object WidgetUpdater {
    // 布局常量
    const val LAYOUT_4X2 = WorkflowWidgetProvider.LAYOUT_4X2
    const val LAYOUT_2X2 = WorkflowWidgetProvider.LAYOUT_2X2

    // 布局对应的插槽ID组 (container, icon, name)
    private val slotsMap4x2 = listOf(
        Triple(R.id.slot_container_1, R.id.slot_icon_1, R.id.slot_name_1),
        Triple(R.id.slot_container_2, R.id.slot_icon_2, R.id.slot_name_2),
        Triple(R.id.slot_container_3, R.id.slot_icon_3, R.id.slot_name_3),
        Triple(R.id.slot_container_4, R.id.slot_icon_4, R.id.slot_name_4)
    )

    private val slotsMap2x2 = listOf(
        Triple(R.id.slot_container_1, R.id.slot_icon_1, R.id.slot_name_1),
        Triple(R.id.slot_container_2, R.id.slot_icon_2, R.id.slot_name_2)
    )

    fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, forceLayout: String? = null) {
        val prefs = context.getSharedPreferences(WidgetConfigActivity.PREFS_NAME, Context.MODE_PRIVATE)
        val workflowManager = WorkflowManager(context)

        // 如果指定了布局类型，保存它
        if (forceLayout != null) {
            prefs.edit().putString(WidgetConfigActivity.PREF_PREFIX_KEY + appWidgetId + "_layout", forceLayout).apply()
        }

        // 获取布局类型
        val layoutType = prefs.getString(WidgetConfigActivity.PREF_PREFIX_KEY + appWidgetId + "_layout", WorkflowWidgetProvider.LAYOUT_4X2) ?: WorkflowWidgetProvider.LAYOUT_4X2

        // 根据布局选择布局文件和插槽
        val layoutResId = when (layoutType) {
            WorkflowWidgetProvider.LAYOUT_2X2 -> R.layout.widget_workflow_2x2
            else -> R.layout.widget_workflow_grid
        }

        val slots = when (layoutType) {
            WorkflowWidgetProvider.LAYOUT_2X2 -> slotsMap2x2
            else -> slotsMap4x2
        }
        val slotCount = slots.size

        val views = RemoteViews(context.packageName, layoutResId)

        // --- 动态取色适配 (Android 12+) ---
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val isNightMode = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

            val bgColorRes = if (isNightMode) android.R.color.system_neutral1_900 else android.R.color.system_neutral1_50
            val bgColor = context.getColor(bgColorRes)
            views.setColorStateList(R.id.widget_root, "setBackgroundTintList", ColorStateList.valueOf(bgColor))

            val itemBgColorRes = if (isNightMode) android.R.color.system_accent2_800 else android.R.color.system_accent2_100
            val itemBgColor = context.getColor(itemBgColorRes)
            val itemBgTint = ColorStateList.valueOf(itemBgColor)

            val textColorRes = if (isNightMode) android.R.color.system_accent1_100 else android.R.color.system_accent1_900
            val textColor = context.getColor(textColorRes)

            // 图标使用强调色
            val iconColorRes = if (isNightMode) android.R.color.system_accent1_100 else android.R.color.system_accent1_900
            val iconColor = context.getColor(iconColorRes)

            slots.forEach { (containerId, iconId, nameId) ->
                views.setColorStateList(containerId, "setBackgroundTintList", itemBgTint)
                views.setInt(iconId, "setColorFilter", iconColor)
                views.setTextColor(nameId, textColor)
            }
        }

        // 遍历所有插槽
        for (i in 0 until slotCount) {
            val (containerId, iconId, nameId) = slots[i]
            val workflowId = prefs.getString(WidgetConfigActivity.PREF_PREFIX_KEY + appWidgetId + "_slot_" + i, null)

            if (workflowId != null) {
                val workflow = workflowManager.getWorkflow(workflowId)
                if (workflow != null) {
                    views.setViewVisibility(containerId, View.VISIBLE)
                    views.setViewVisibility(iconId, View.VISIBLE)
                    views.setTextViewText(nameId, workflow.name)

                    val intent = Intent(context, ShortcutExecutorActivity::class.java).apply {
                        action = ShortcutExecutorActivity.ACTION_EXECUTE_WORKFLOW
                        putExtra(ShortcutExecutorActivity.EXTRA_WORKFLOW_ID, workflow.id)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }

                    val uniqueRequestCode = (appWidgetId * 10) + i
                    val pendingIntent = PendingIntent.getActivity(
                        context,
                        uniqueRequestCode,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )

                    views.setOnClickPendingIntent(containerId, pendingIntent)
                } else {
                    views.setViewVisibility(containerId, View.VISIBLE)
                    views.setViewVisibility(iconId, View.VISIBLE)
                    views.setTextViewText(nameId, "失效")
                    views.setOnClickPendingIntent(containerId, null)
                }
            } else {
                views.setViewVisibility(containerId, View.INVISIBLE)
                views.setViewVisibility(iconId, View.INVISIBLE)
                views.setOnClickPendingIntent(containerId, null)
            }
        }

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
}

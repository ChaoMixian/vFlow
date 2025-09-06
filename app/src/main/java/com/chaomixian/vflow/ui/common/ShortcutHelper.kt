package com.chaomixian.vflow.ui.common

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.workflow.WorkflowManager
import com.chaomixian.vflow.core.workflow.module.triggers.ManualTriggerModule

/**
 * 辅助对象，用于管理应用的动态快捷方式。
 */
object ShortcutHelper {

    private const val MAX_SHORTCUTS = 3 // 快捷方式最大数量

    /**
     * 更新应用的动态快捷方式。
     * 优先显示收藏的手动工作流，不足则由未收藏的补充。
     *
     * @param context 上下文对象。
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
            // 创建点击快捷方式时触发的 Intent
            val intent = Intent(context, ShortcutExecutorActivity::class.java).apply {
                action = ShortcutExecutorActivity.ACTION_EXECUTE_WORKFLOW
                putExtra(ShortcutExecutorActivity.EXTRA_WORKFLOW_ID, workflow.id)
                // 添加 FLAG_ACTIVITY_CLEAR_TASK 以确保每次都创建一个新的任务栈
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }

            // 构建 ShortcutInfo
            ShortcutInfoCompat.Builder(context, workflow.id) // 使用工作流ID作为快捷方式的唯一ID
                .setShortLabel(workflow.name) // 设置短标签（显示在菜单中）
                .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_play)) // 设置图标
                .setIntent(intent) // 设置意图
                .build()
        }

        // 4. 使用 ShortcutManagerCompat 设置（覆盖）现有的动态快捷方式
        ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
    }
}
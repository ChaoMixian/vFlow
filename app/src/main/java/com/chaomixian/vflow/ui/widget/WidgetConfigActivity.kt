package com.chaomixian.vflow.ui.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.workflow.WorkflowManager
import com.chaomixian.vflow.core.workflow.model.Workflow
import com.chaomixian.vflow.ui.common.BaseActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * 小组件配置页面。允许用户选择 4 个工作流。
 * 支持新建配置和重新配置。
 */
class WidgetConfigActivity : BaseActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private lateinit var workflowManager: WorkflowManager
    private val selectedWorkflows = arrayOfNulls<String>(4) // 存储选中的4个ID
    private val selectedWorkflowNames = arrayOfNulls<String>(4) // 存储选中的名称用于显示

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_widget_config)

        // 1. 获取 Widget ID
        val extras = intent.extras
        if (extras != null) {
            appWidgetId = extras.getInt(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
            )
        }

        // 如果 ID 无效，直接退出
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        // 默认结果为 CANCELED，以防用户中途退出
        setResult(RESULT_CANCELED)

        workflowManager = WorkflowManager(this)

        // 加载现有配置（如果是重新配置）
        loadExistingConfig()

        setupUI()
    }

    private fun loadExistingConfig() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        for (i in 0 until 4) {
            val workflowId = prefs.getString(PREF_PREFIX_KEY + appWidgetId + "_slot_" + i, null)
            if (workflowId != null) {
                selectedWorkflows[i] = workflowId
                val workflow = workflowManager.getWorkflow(workflowId)
                selectedWorkflowNames[i] = workflow?.name ?: "已删除的工作流"
            }
        }
    }

    private fun setupUI() {
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.title = "配置小组件 (2x4)"
        setSupportActionBar(toolbar)

        // 这里简化处理：使用 4 个按钮分别选择每个插槽
        val slots = listOf(
            findViewById<MaterialButton>(R.id.btn_select_slot_1),
            findViewById<MaterialButton>(R.id.btn_select_slot_2),
            findViewById<MaterialButton>(R.id.btn_select_slot_3),
            findViewById<MaterialButton>(R.id.btn_select_slot_4)
        )

        slots.forEachIndexed { index, button ->
            // 初始化按钮文字
            if (selectedWorkflowNames[index] != null) {
                button.text = "插槽 ${index + 1}: ${selectedWorkflowNames[index]}"
            } else {
                button.text = "选择插槽 ${index + 1}"
            }

            button.setOnClickListener {
                showWorkflowSelectionDialog(index, button)
            }
        }

        findViewById<MaterialButton>(R.id.btn_save_widget).setOnClickListener {
            saveWidgetConfig()
        }
    }

    private fun showWorkflowSelectionDialog(slotIndex: Int, button: MaterialButton) {
        val workflows = workflowManager.getAllWorkflows()
        val names = workflows.map { it.name }.toMutableList()
        val ids = workflows.map { it.id }.toMutableList()

        // 添加一个“清空插槽”的选项
        names.add(0, "[清空插槽]")
        ids.add(0, "") // 使用空字符串标记清空

        MaterialAlertDialogBuilder(this)
            .setTitle("选择插槽 ${slotIndex + 1} 的工作流")
            .setItems(names.toTypedArray()) { _, which ->
                val selectedId = ids[which]

                if (selectedId.isEmpty()) {
                    // 清空逻辑
                    selectedWorkflows[slotIndex] = null
                    selectedWorkflowNames[slotIndex] = null
                    button.text = "选择插槽 ${slotIndex + 1}"
                } else {
                    // 选择逻辑
                    selectedWorkflows[slotIndex] = selectedId
                    selectedWorkflowNames[slotIndex] = names[which]
                    button.text = "插槽 ${slotIndex + 1}: ${names[which]}"
                }
            }
            .show()
    }

    private fun saveWidgetConfig() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        for (i in 0 until 4) {
            val id = selectedWorkflows[i]
            if (id != null) {
                prefs.putString(PREF_PREFIX_KEY + appWidgetId + "_slot_" + i, id)
            } else {
                prefs.remove(PREF_PREFIX_KEY + appWidgetId + "_slot_" + i)
            }
        }
        prefs.apply()

        // 请求更新小组件
        val appWidgetManager = AppWidgetManager.getInstance(this)
        WorkflowWidgetProvider.updateAppWidget(this, appWidgetManager, appWidgetId)

        // 返回成功结果
        val resultValue = Intent()
        resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(Activity.RESULT_OK, resultValue)
        finish()
    }

    companion object {
        const val PREFS_NAME = "com.chaomixian.vflow.ui.widget.WidgetConfig"
        const val PREF_PREFIX_KEY = "appwidget_"
    }
}
package com.chaomixian.vflow.ui.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
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
 * 小组件配置页面。允许用户选择工作流并配置布局。
 * 支持新建配置和重新配置。
 */
class WidgetConfigActivity : BaseActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private lateinit var workflowManager: WorkflowManager

    // 布局类型和对应的插槽数量
    private val layoutOptions = listOf(
        LayoutOption("4x2 网格 (4个)", WidgetUpdater.LAYOUT_4X2, 4),
        LayoutOption("2x2 纵向 (2个)", WidgetUpdater.LAYOUT_2X2, 2)
    )

    private var selectedLayout = WidgetUpdater.LAYOUT_4X2
    private var currentSlotCount = 4
    private val selectedWorkflows = mutableMapOf<Int, String?>() // 存储选中的ID
    private val selectedWorkflowNames = mutableMapOf<Int, String?>() // 存储选中的名称

    data class LayoutOption(val displayName: String, val layoutType: String, val slotCount: Int)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_widget_config)

        // 获取 Widget ID
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

        // 加载布局类型
        val savedLayout = prefs.getString(PREF_PREFIX_KEY + appWidgetId + "_layout", WidgetUpdater.LAYOUT_4X2)
        selectedLayout = savedLayout ?: WidgetUpdater.LAYOUT_4X2
        currentSlotCount = layoutOptions.find { it.layoutType == selectedLayout }?.slotCount ?: 4

        // 加载所有插槽配置（4个都尝试加载，以支持布局切换）
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
        toolbar.title = "配置小组件"
        setSupportActionBar(toolbar)

        // 设置布局选择下拉框
        val layoutSpinner = findViewById<Spinner>(R.id.spinner_layout)
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, layoutOptions.map { it.displayName })
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        layoutSpinner.adapter = adapter

        // 设置当前选中的布局
        val currentLayoutIndex = layoutOptions.indexOfFirst { it.layoutType == selectedLayout }
        if (currentLayoutIndex >= 0) {
            layoutSpinner.setSelection(currentLayoutIndex)
        }

        // 布局选择监听
        layoutSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val newLayout = layoutOptions[position]
                if (newLayout.layoutType != selectedLayout) {
                    selectedLayout = newLayout.layoutType
                    currentSlotCount = newLayout.slotCount
                    updateSlotButtonsVisibility()
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // 初始化插槽按钮
        updateSlotButtonsVisibility()
        updateSlotButtonTexts()

        // 保存按钮
        findViewById<MaterialButton>(R.id.btn_save_widget).setOnClickListener {
            saveWidgetConfig()
        }
    }

    private fun updateSlotButtonsVisibility() {
        val slotButtons = listOf(
            findViewById<MaterialButton>(R.id.btn_select_slot_1),
            findViewById<MaterialButton>(R.id.btn_select_slot_2),
            findViewById<MaterialButton>(R.id.btn_select_slot_3),
            findViewById<MaterialButton>(R.id.btn_select_slot_4)
        )

        val slotCountLabel = findViewById<TextView>(R.id.tv_slot_count)

        slotButtons.forEachIndexed { index, button ->
            if (index < currentSlotCount) {
                button.visibility = View.VISIBLE
            } else {
                // 隐藏按钮时，清除其配置
                selectedWorkflows.remove(index)
                selectedWorkflowNames.remove(index)
                button.visibility = View.GONE
            }
        }

        slotCountLabel.text = "选择工作流 (${currentSlotCount}个)"
    }

    private fun updateSlotButtonTexts() {
        val slotButtons = listOf(
            findViewById<MaterialButton>(R.id.btn_select_slot_1),
            findViewById<MaterialButton>(R.id.btn_select_slot_2),
            findViewById<MaterialButton>(R.id.btn_select_slot_3),
            findViewById<MaterialButton>(R.id.btn_select_slot_4)
        )

        slotButtons.forEachIndexed { index, button ->
            if (index < currentSlotCount) {
                val name = selectedWorkflowNames[index]
                if (name != null) {
                    button.text = "插槽 ${index + 1}: $name"
                } else {
                    button.text = "选择插槽 ${index + 1}"
                }

                button.setOnClickListener {
                    showWorkflowSelectionDialog(index, button)
                }
            }
        }
    }

    private fun showWorkflowSelectionDialog(slotIndex: Int, button: MaterialButton) {
        val workflows = workflowManager.getAllWorkflows()
        val names = workflows.map { it.name }.toMutableList()
        val ids = workflows.map { it.id }.toMutableList()

        // 添加一个"清空插槽"的选项
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

        // 保存布局类型
        prefs.putString(PREF_PREFIX_KEY + appWidgetId + "_layout", selectedLayout)

        // 保存当前布局的插槽配置
        for (i in 0 until currentSlotCount) {
            val id = selectedWorkflows[i]
            if (id != null) {
                prefs.putString(PREF_PREFIX_KEY + appWidgetId + "_slot_" + i, id)
            } else {
                prefs.remove(PREF_PREFIX_KEY + appWidgetId + "_slot_" + i)
            }
        }

        // 清除不在当前插槽范围内的配置
        for (i in currentSlotCount until 4) {
            prefs.remove(PREF_PREFIX_KEY + appWidgetId + "_slot_" + i)
        }

        prefs.apply()

        // 请求更新小组件
        val appWidgetManager = AppWidgetManager.getInstance(this)
        WidgetUpdater.updateAppWidget(this, appWidgetManager, appWidgetId)

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

// 文件: main/java/com/chaomixian/vflow/ui/workflow_editor/AiGenerationSheet.kt
package com.chaomixian.vflow.ui.workflow_editor

import android.app.Dialog
import android.content.ContentValues.TAG
import android.content.Context
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.logging.DebugLogger
import com.chaomixian.vflow.core.workflow.model.Workflow
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class AiGenerationSheet : BottomSheetDialogFragment() {

    var onWorkflowGenerated: ((Workflow) -> Unit)? = null

    private lateinit var etRequirement: TextInputEditText
    private lateinit var cgProvider: ChipGroup
    private lateinit var etBaseUrl: TextInputEditText
    private lateinit var etModel: TextInputEditText
    private lateinit var etApiKey: TextInputEditText
    private lateinit var btnGenerate: Button
    private lateinit var layoutSettingsContent: LinearLayout
    private lateinit var ivExpandArrow: ImageView

    private var generatingJob: Job? = null // 用于跟踪任务以便取消

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            if (bottomSheet != null) {
                val behavior = BottomSheetBehavior.from(bottomSheet)
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.skipCollapsed = true
            }
        }
        return dialog
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.sheet_ai_generation, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        etRequirement = view.findViewById(R.id.et_requirement)
        cgProvider = view.findViewById(R.id.cg_provider)
        etBaseUrl = view.findViewById(R.id.et_base_url)
        etModel = view.findViewById(R.id.et_model)
        etApiKey = view.findViewById(R.id.et_api_key)
        btnGenerate = view.findViewById(R.id.btn_generate)
        layoutSettingsContent = view.findViewById(R.id.layout_settings_content)
        ivExpandArrow = view.findViewById(R.id.iv_expand_arrow)

        val headerLayout = view.findViewById<LinearLayout>(R.id.layout_settings_header)

        headerLayout.setOnClickListener {
            val isVisible = layoutSettingsContent.isVisible
            layoutSettingsContent.isVisible = !isVisible
            ivExpandArrow.rotation = if (!isVisible) 180f else 0f
        }

        loadConfig()

        cgProvider.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener
            when (checkedIds[0]) {
                R.id.chip_bigmodel -> {
                    etBaseUrl.setText("https://open.bigmodel.cn/api/paas/v4")
                    etModel.setText("glm-4.5-flash")
                }
                R.id.chip_dashscope -> {
                    etBaseUrl.setText("https://dashscope.aliyuncs.com/compatible-mode/v1")
                    etModel.setText("qwen3-max")
                }
            }
        }

        btnGenerate.setOnClickListener {
            val requirement = etRequirement.text.toString()
            if (requirement.isBlank()) {
                Toast.makeText(context, "请输入需求", Toast.LENGTH_SHORT).show()
                DebugLogger.i(TAG, "请输入需求")
                return@setOnClickListener
            }

            val apiKey = etApiKey.text.toString()
            if (apiKey.isBlank()) {
                Toast.makeText(context, "请在配置中输入 API Key", Toast.LENGTH_SHORT).show()
                DebugLogger.i(TAG, "请在配置中输入 API Key")
                layoutSettingsContent.isVisible = true // 自动展开配置
                etApiKey.requestFocus()
                return@setOnClickListener
            }

            val config = WorkflowAiGenerator.AiConfig(
                provider = getSelectedProvider(),
                baseUrl = etBaseUrl.text.toString(),
                apiKey = apiKey,
                model = etModel.text.toString()
            )

            saveConfig(config)
            performGeneration(requirement, config)
        }
    }

    private fun performGeneration(requirement: String, config: WorkflowAiGenerator.AiConfig) {
        // 禁止默认的关闭行为
        isCancelable = false
        (dialog as? BottomSheetDialog)?.behavior?.isDraggable = false

        // 拦截返回键
        dialog?.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                showCancelConfirmation()
                true // 消费事件，阻止默认关闭
            } else {
                false
            }
        }

        // 拦截点击外部区域 (蒙层)
        // 获取 Material BottomSheet 默认的 touch_outside View，并覆盖其点击事件
        dialog?.window?.decorView?.findViewById<View>(com.google.android.material.R.id.touch_outside)?.setOnClickListener {
            showCancelConfirmation()
        }

        // 更新界面状态
        btnGenerate.isEnabled = false
        btnGenerate.text = "AI 正在思考并生成工作流..."
        etRequirement.isEnabled = false

        // 执行生成任务
        generatingJob = lifecycleScope.launch {
            val result = WorkflowAiGenerator.generateWorkflow(requirement, config)

            if (isAdded) {
                resetUiState()

                result.onSuccess { workflow ->
                    dismiss()
                    onWorkflowGenerated?.invoke(workflow)
                }.onFailure { e ->
                    val msg = if (e.message?.contains("401") == true) "API Key 无效" else e.message
                    Toast.makeText(context, "生成失败: $msg", Toast.LENGTH_LONG).show()
                    DebugLogger.i(TAG, "生成失败: $msg")
                }
            }
        }
    }

    private fun showCancelConfirmation() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("停止生成？")
            .setMessage("AI 正在生成中，确定要中断吗？")
            .setPositiveButton("继续等待", null)
            .setNegativeButton("停止并关闭") { _, _ ->
                generatingJob?.cancel()
                resetUiState()
                dismiss()
            }
            .show()
    }

    private fun resetUiState() {
        if (!isAdded) return
        isCancelable = true
        (dialog as? BottomSheetDialog)?.behavior?.isDraggable = true
        dialog?.setOnKeyListener(null)
        // 恢复点击外部关闭的默认行为（重新设置为 cancelable=true 应该会自动处理，但为了保险起见，不需要手动恢复 touch_outside 的 listener，因为 dismiss 后重建 Dialog 会重置）

        btnGenerate.isEnabled = true
        btnGenerate.text = "生成工作流"
        etRequirement.isEnabled = true
    }

    private fun getSelectedProvider(): String {
        return when (cgProvider.checkedChipId) {
            R.id.chip_dashscope -> "阿里云"
            R.id.chip_custom -> "自定义"
            else -> "智谱"
        }
    }

    private fun loadConfig() {
        val prefs = requireContext().getSharedPreferences("ai_config", Context.MODE_PRIVATE)
        val provider = prefs.getString("provider", "智谱")

        when (provider) {
            "阿里云" -> view?.findViewById<Chip>(R.id.chip_dashscope)?.isChecked = true
            "自定义" -> view?.findViewById<Chip>(R.id.chip_custom)?.isChecked = true
            else -> view?.findViewById<Chip>(R.id.chip_bigmodel)?.isChecked = true
        }

        etBaseUrl.setText(prefs.getString("base_url", "https://open.bigmodel.cn/api/paas/v4"))
        etApiKey.setText(prefs.getString("api_key", ""))
        etModel.setText(prefs.getString("model", "glm-4.5-flash"))
    }

    private fun saveConfig(config: WorkflowAiGenerator.AiConfig) {
        val prefs = requireContext().getSharedPreferences("ai_config", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString("provider", config.provider)
            putString("base_url", config.baseUrl)
            putString("api_key", config.apiKey)
            putString("model", config.model)
            apply()
        }
    }
}
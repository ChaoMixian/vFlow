package com.chaomixian.vflow.ui.workflow_editor

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.chaomixian.vflow.core.module.ActionModule
import com.chaomixian.vflow.core.module.ParameterDefinition
import com.chaomixian.vflow.core.module.ParameterType
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.chaomixian.vflow.R

class ActionEditorSheet : BottomSheetDialogFragment() {

    private lateinit var module: ActionModule
    private var existingStep: ActionStep? = null
    var onSave: ((ActionStep) -> Unit)? = null

    // 用于存储动态创建的输入视图，以便后续读取它们的值
    private val inputViews = mutableMapOf<String, View>()

    companion object {
        fun newInstance(module: ActionModule, existingStep: ActionStep? = null): ActionEditorSheet {
            val fragment = ActionEditorSheet()
            // 注意：模块不能直接通过 Bundle 传递，所以我们用 ID 来查找
            fragment.arguments = Bundle().apply {
                putString("moduleId", module.id)
                putParcelable("existingStep", existingStep)
            }
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val moduleId = arguments?.getString("moduleId")
        val mod = moduleId?.let { com.chaomixian.vflow.core.module.ModuleRegistry.getModule(it) }
        if (mod == null) {
            dismiss()
            return
        }
        module = mod
        existingStep = arguments?.getParcelable("existingStep")
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.sheet_action_editor, container, false)
        val title = view.findViewById<TextView>(R.id.text_view_bottom_sheet_title)
        val paramsContainer = view.findViewById<LinearLayout>(R.id.container_action_params)
        val saveButton = view.findViewById<Button>(R.id.button_save)

        title.text = "编辑 ${module.metadata.name}"

        // 动态构建UI
        buildUiForParameters(paramsContainer)

        saveButton.setOnClickListener {
            try {
                // 从UI读取参数并构建 ActionStep
                val parameters = readParametersFromUi()
                val newStep = ActionStep(moduleId = module.id, parameters = parameters)
                onSave?.invoke(newStep)
                dismiss()
            } catch (e: Exception) {
                Toast.makeText(context, "输入错误: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        return view
    }

    // --- 这是动态UI魔法发生的地方 ---
    private fun buildUiForParameters(container: LinearLayout) {
        val parameters = module.getParameters()
        parameters.forEach { paramDef ->
            val currentValue = existingStep?.parameters?.get(paramDef.id) ?: paramDef.defaultValue

            when (paramDef.type) {
                ParameterType.STRING -> addTextField(container, paramDef, currentValue, isNumeric = false)
                ParameterType.NUMBER -> addTextField(container, paramDef, currentValue, isNumeric = true)
                ParameterType.BOOLEAN -> addSwitch(container, paramDef, currentValue)
                // 其他类型如 ENUM, NODE_ID 等可以后续添加
                else -> {}
            }
        }
    }

    // --- 这是从动态UI读取数据的地方 ---
    private fun readParametersFromUi(): Map<String, Any?> {
        val parameters = mutableMapOf<String, Any?>()
        inputViews.forEach { (paramId, view) ->
            when (view) {
                is TextInputLayout -> {
                    val text = view.editText?.text?.toString()
                    // 简单的类型转换
                    val paramDef = module.getParameters().find { it.id == paramId }
                    if (paramDef?.type == ParameterType.NUMBER) {
                        parameters[paramId] = text?.toLongOrNull()
                    } else {
                        parameters[paramId] = text
                    }
                }
                is MaterialSwitch -> parameters[paramId] = view.isChecked
            }
        }
        return parameters
    }

    // --- UI 构建辅助函数 ---
    private fun addTextField(container: LinearLayout, paramDef: ParameterDefinition, value: Any?, isNumeric: Boolean) {
        val textInputLayout = TextInputLayout(requireContext()).apply {
            hint = paramDef.name
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 16 }
        }
        val editText = TextInputEditText(requireContext()).apply {
            setText(value?.toString() ?: "")
            inputType = if (isNumeric) InputType.TYPE_CLASS_NUMBER else InputType.TYPE_CLASS_TEXT
        }
        textInputLayout.addView(editText)
        container.addView(textInputLayout)
        inputViews[paramDef.id] = textInputLayout // 存入map以便读取
    }

    private fun addSwitch(container: LinearLayout, paramDef: ParameterDefinition, value: Any?) {
        val switch = MaterialSwitch(requireContext()).apply {
            text = paramDef.name
            isChecked = value as? Boolean ?: false
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 16 }
        }
        container.addView(switch)
        inputViews[paramDef.id] = switch // 存入map以便读取
    }
}
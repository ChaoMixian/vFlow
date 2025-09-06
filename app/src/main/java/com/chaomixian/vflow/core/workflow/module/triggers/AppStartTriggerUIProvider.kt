// 文件: main/java/com/chaomixian/vflow/core/workflow/module/triggers/AppStartTriggerUIProvider.kt
// 描述: 为 AppStartTriggerModule 提供自定义UI交互逻辑。
package com.chaomixian.vflow.core.workflow.module.triggers

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.module.CustomEditorViewHolder
import com.chaomixian.vflow.core.module.ModuleUIProvider
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.ui.app_picker.AppPickerActivity

/**
 * 一个简单的ViewHolder，用于满足ModuleUIProvider接口的要求。
 */
class AppStartTriggerViewHolder(
    view: View,
    val summaryTextView: TextView,
    val pickButton: Button
) : CustomEditorViewHolder(view)


/**
 * AppStartTriggerModule的UI提供者。
 * [修改] 这个类的职责被简化了。由于我们现在希望触发器在列表中看起来和其他步骤一样，
 * 我们不再需要 createPreview 来创建自定义视图。
 * 点击事件的特殊处理将移至 ActionStepAdapter 中。
 */
class AppStartTriggerUIProvider : ModuleUIProvider {

    // 声明此UI提供者将完全处理'packageName'和'activityName'这两个参数的UI，
    // 因此通用的编辑器不应为它们创建默认输入框。
    override fun getHandledInputIds(): Set<String> = setOf("packageName", "activityName")

    /**
     * 创建模块的参数编辑器视图。
     * 对于此模块，我们不希望在底部弹出的编辑器里有任何内容。
     */
    override fun createEditor(
        context: Context,
        parent: ViewGroup,
        currentParameters: Map<String, Any?>,
        onParametersChanged: () -> Unit,
        onMagicVariableRequested: ((inputId: String) -> Unit)?,
        onStartActivityForResult: ((Intent, (resultCode: Int, data: Intent?) -> Unit) -> Unit)?
    ): CustomEditorViewHolder {
        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.partial_app_start_trigger_editor, parent, false)
        val summaryTextView = view.findViewById<TextView>(R.id.text_selected_app_summary)
        val pickButton = view.findViewById<Button>(R.id.button_pick_app)

        updateSummaryText(context, summaryTextView, currentParameters)

        pickButton.setOnClickListener {
            val intent = Intent(context, AppPickerActivity::class.java)
            // 直接调用从ActionEditorSheet传递过来的回调
            onStartActivityForResult?.invoke(intent) { _, _ ->}
        }

        return AppStartTriggerViewHolder(view, summaryTextView, pickButton)
    }


    private fun updateSummaryText(context: Context, textView: TextView, parameters: Map<String, Any?>) {
        val packageName = parameters["packageName"] as? String
        val activityName = parameters["activityName"] as? String

        if (packageName.isNullOrEmpty()) {
            textView.text = "尚未选择"
            return
        }

        val pm = context.packageManager
        val appName = try {
            val appInfo = pm.getApplicationInfo(packageName, 0)
            appInfo.loadLabel(pm).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            packageName // 如果找不到应用，就显示包名
        }

        val displayText = if (activityName == "LAUNCH" || activityName.isNullOrEmpty()) {
            "应用: $appName"
        } else {
            // 如果Activity名称过长，只显示类名
            val simpleActivityName = activityName.substringAfterLast('.')
            "Activity: $appName / $simpleActivityName"
        }
        textView.text = "已选择: $displayText"
    }


    /**
     * 从编辑器视图中读取参数。
     */
    override fun readFromEditor(holder: CustomEditorViewHolder): Map<String, Any?> {
        // 因为参数是通过 onStartActivityForResult 的回调在 WorkflowEditorActivity 中更新的，
        // 所以在这里我们不需要从视图中读取任何内容。返回一个空 Map。
        return emptyMap()
    }

    /**
     * [修改] 让此方法返回 null。
     * 这样一来，ActionStepAdapter 就会自动回退到使用模块的 getSummary() 方法来生成标准样式的摘要文本，
     * 从而解决了样式不统一的问题（如缺少编号、内边距不同等）。
     */
    override fun createPreview(
        context: Context,
        parent: ViewGroup,
        step: ActionStep,
        onStartActivityForResult: ((Intent, (resultCode: Int, data: Intent?) -> Unit) -> Unit)?
    ): View? {
        return null
    }
}
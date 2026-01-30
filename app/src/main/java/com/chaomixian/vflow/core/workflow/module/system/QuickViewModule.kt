// 文件: QuickViewModule.kt
// 描述: 定义了在工作流执行期间快速查看内容的模块。

package com.chaomixian.vflow.core.workflow.module.system

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.execution.VariableResolver
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.VObject
import com.chaomixian.vflow.core.types.basic.VString
import com.chaomixian.vflow.core.types.basic.VNumber
import com.chaomixian.vflow.core.types.basic.VBoolean
import com.chaomixian.vflow.core.types.basic.VDictionary
import com.chaomixian.vflow.core.types.basic.VList
import com.chaomixian.vflow.core.types.complex.VImage
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.permissions.PermissionManager
import com.chaomixian.vflow.services.ExecutionUIService
import com.chaomixian.vflow.ui.workflow_editor.PillUtil
import com.chaomixian.vflow.ui.workflow_editor.RichTextUIProvider

/**
 * “快速查看”模块。
 * 弹出一个悬浮窗来显示文本、数字或任何其他变量的值。
 */
class QuickViewModule : BaseModule() {

    override val id = "vflow.data.quick_view"
    override val metadata = ActionMetadata(
        nameStringRes = R.string.module_vflow_data_quick_view_name,
        descriptionStringRes = R.string.module_vflow_data_quick_view_desc,
        name = "快速查看",  // Fallback
        description = "在悬浮窗中显示文本、数字、图片等各种类型的内容。",  // Fallback
        iconRes = R.drawable.rounded_preview_24,
        category = "应用与系统"
    )

    // 此模块需要悬浮窗权限才能显示UI
    override val requiredPermissions = listOf(PermissionManager.OVERLAY)

    // 使用 RichTextUIProvider 提供富文本预览
    override val uiProvider: ModuleUIProvider? = RichTextUIProvider("content")

    /**
     * 定义模块的输入参数。
     */
    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "content",
            nameStringRes = R.string.param_vflow_data_quick_view_content_name,
            name = "内容",  // Fallback
            staticType = ParameterType.ANY, // 接受任何类型的输入
            defaultValue = "",
            acceptsMagicVariable = true,
            supportsRichText = true
        )
    )

    /**
     * 增加 success 输出以保持统一。
     */
    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition(
            "success",
            "是否成功",
            VTypeRegistry.BOOLEAN.id,
            nameStringRes = R.string.output_vflow_data_quick_view_success_name
        )
    )

    /**
     * 生成模块摘要。
     */
    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val rawContent = step.parameters["content"]?.toString() ?: ""

        // 检查内容是否为复杂内容（包含变量或其他文本的组合）
        if (VariableResolver.isComplex(rawContent)) {
            // 复杂内容：只显示模块名称，详细内容由 UIProvider 在预览中显示
            return metadata.getLocalizedName(context)
        }

        // 简单内容：显示完整的摘要（带药丸）
        val contentPill = PillUtil.createPillFromParam(
            step.parameters["content"],
            getInputs().find { it.id == "content" }
        )
        return PillUtil.buildSpannable(context, context.getString(R.string.summary_vflow_system_quick_view_prefix), contentPill)
    }

    /**
     * 执行模块逻辑。
     * 现在会输出一个布尔值表示操作是否成功完成。
     */
    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val uiService = context.services.get(ExecutionUIService::class)
            ?: return ExecutionResult.Failure("服务缺失", "无法获取UI服务来显示内容。")

        // 使用统一的变量访问方法，自动处理递归解析
        val content = context.getVariable("content")

        onProgress(ProgressUpdate("正在显示内容..."))

        // 检查内容是否为图片变量
        if (content is VImage) {
            // 如果是图片，调用专门显示图片的服务
            uiService.showQuickViewImage("快速查看", content.uriString)
        } else {
            // 否则，将内容转换为字符串并显示
            val contentAsString = when (content) {
                is VString -> content.raw
                is VNumber -> content.raw.toString()
                is VBoolean -> content.raw.toString()
                is VDictionary -> content.raw.entries.joinToString("\n") { "${it.key}: ${it.value.asString()}" }
                is VList -> content.raw.joinToString("\n") { it.asString() }
                is String -> content  // 已经是解析后的字符串
                null -> "[空值]"
                else -> content.toString()
            }
            // 调用UI服务显示文本内容，并等待用户关闭悬浮窗
            uiService.showQuickView("快速查看", contentAsString)
        }

        onProgress(ProgressUpdate("用户已关闭查看窗口"))
        // 返回成功结果
        return ExecutionResult.Success(mapOf("success" to VBoolean(true)))
    }
}
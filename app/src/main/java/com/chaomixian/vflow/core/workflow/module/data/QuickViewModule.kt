// 文件: QuickViewModule.kt
// 描述: 定义了在工作流执行期间快速查看内容的模块。

package com.chaomixian.vflow.core.workflow.module.data

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.permissions.PermissionManager
import com.chaomixian.vflow.services.ExecutionUIService
import com.chaomixian.vflow.ui.workflow_editor.PillUtil

/**
 * “快速查看”模块。
 * 弹出一个悬浮窗来显示文本、数字或任何其他变量的值。
 */
class QuickViewModule : BaseModule() {

    override val id = "vflow.data.quick_view"
    override val metadata = ActionMetadata(
        name = "快速查看",
        description = "在悬浮窗中显示文本、数字、图片等各种类型的内容。",
        iconRes = R.drawable.rounded_preview_24, // 使用新创建的图标
        category = "数据"
    )

    // 此模块需要悬浮窗权限才能显示UI
    override val requiredPermissions = listOf(PermissionManager.OVERLAY)

    /**
     * 定义模块的输入参数。
     */
    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "content",
            name = "内容",
            staticType = ParameterType.ANY, // 接受任何类型的输入
            defaultValue = "",
            acceptsMagicVariable = true // 主要用于接收魔法变量
        )
    )

    // 此模块不产生任何输出
    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = emptyList()

    /**
     * 生成模块摘要。
     */
    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val content = step.parameters["content"]?.toString() ?: "..."
        val isVariable = content.startsWith("{{") && content.endsWith("}}")

        return PillUtil.buildSpannable( // 修复方法名
            context,
            "快速查看 ",
            PillUtil.Pill(content, isVariable, parameterId = "content")
        )
    }

    /**
     * 执行模块逻辑。
     */
    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val uiService = context.services.get(ExecutionUIService::class)
            ?: return ExecutionResult.Failure("服务缺失", "无法获取UI服务来显示内容。")

        // 获取要显示的内容，优先从魔法变量
        val content = context.magicVariables["content"] ?: context.variables["content"]

        // 将任何类型的内容转换为可读的字符串
        val contentAsString = when (content) {
            is TextVariable -> content.value
            is NumberVariable -> content.value.toString()
            is BooleanVariable -> content.value.toString()
            is DictionaryVariable -> content.value.entries.joinToString("\n") { "${it.key}: ${it.value}" }
            is ListVariable -> content.value.joinToString("\n")
            null -> "[空值]"
            else -> content.toString()
        }

        onProgress(ProgressUpdate("正在显示内容..."))

        // 调用UI服务显示内容，并等待用户关闭悬浮窗
        uiService.showQuickView("快速查看", contentAsString)

        onProgress(ProgressUpdate("用户已关闭查看窗口"))
        return ExecutionResult.Success()
    }
}
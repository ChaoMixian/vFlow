package com.chaomixian.vflow.core.workflow.module.system

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.ui.workflow_editor.PillUtil
import android.os.Build

//获取系统信息模块
class SystemInfoModule : BaseModule() {

    // 模块的唯一标识符
    override val id = "vflow.system.systeminfo"

    // 模块的元数据，定义其在编辑器中的显示名称、描述、图标和分类
    override val metadata: ActionMetadata = ActionMetadata(
        name = "获取系统信息",
        description = "获取当前系统的信息。",
        iconRes = R.drawable.baseline_perm_device_information_24,
        category = "应用与系统"
    )

    /**
     * 定义模块的输入参数。
     */
    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "infotype",
            name = "信息类型",
            staticType = ParameterType.ENUM,
            options = listOf("型号", "制造商", "安卓版本"),
            defaultValue = "型号",
            acceptsMagicVariable = false
        )
    )

    /**
     * 定义模块的输出参数。
     */
    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition(
            id = "sysinfo",
            name = "系统信息",
            typeName = TextVariable.TYPE_NAME
        )
    )

    /**
     * 执行模块的核心逻辑。
     */
    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {

        val resultValue: String = when (context.variables["infotype"]) {
            "型号" -> Build.MODEL
            "制造商" -> Build.MANUFACTURER
            "安卓版本" -> Build.VERSION.RELEASE
            else -> return ExecutionResult.Failure("获取失败", "无效的值")
        }
        return ExecutionResult.Success(mapOf("sysinfo" to resultValue))
    }

    /**
     * 验证模块参数的有效性。
     */
    override fun validate(step: ActionStep, allSteps: List<ActionStep>): ValidationResult {
        return ValidationResult(true)
    }

    /**
     * 创建此模块对应的默认动作步骤列表。
     */
    override fun createSteps(): List<ActionStep> = listOf(ActionStep(moduleId = this.id, parameters = emptyMap()))

    /**
     * 生成在工作流编辑器中显示模块摘要的文本。
     */
    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val inputs = getInputs()

        val pillInfoType = PillUtil.createPillFromParam(
            step.parameters["infotype"],
            inputs.find { it.id == "infotype" },
            isModuleOption = true
        )

        return PillUtil.buildSpannable(context,"获取 ",pillInfoType)
    }
}

package com.chaomixian.vflow.core.workflow.module.data

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.execution.VariableResolver
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VString
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.ui.workflow_editor.PillUtil
import java.nio.charset.Charset
import java.util.Base64

/**
 * Base64 编解码模块。
 * 支持将文本进行 Base64 编码或从 Base64 编码解码回文本。
 */
class Base64EncodeOrDecodeModule : BaseModule() {
    companion object {
        private const val OP_ENCODE = "encode"
        private const val OP_DECODE = "decode"
        private val OPERATION_LEGACY_MAP = mapOf(
            "编码" to OP_ENCODE,
            "解码" to OP_DECODE
        )
    }

    override val id: String = "vflow.data.base64"
    
    override val metadata: ActionMetadata = ActionMetadata(
        nameStringRes = R.string.module_vflow_data_base64_name,
        descriptionStringRes = R.string.module_vflow_data_base64_desc,
        name = "Base64 编解码",
        description = "对文本进行 Base64 编码或解码操作。",
        iconRes = R.drawable.rounded_convert_to_text_24,
        category = "数据",
        categoryId = "data"
    )

    private val operations = listOf(OP_ENCODE, OP_DECODE)

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "operation",
            nameStringRes = R.string.param_vflow_data_base64_operation_name,
            name = "操作",
            staticType = ParameterType.ENUM,
            defaultValue = OP_ENCODE,
            options = operations,
            acceptsMagicVariable = false,
            optionsStringRes = listOf(
                R.string.option_vflow_data_base64_encode,
                R.string.option_vflow_data_base64_decode
            ),
            legacyValueMap = OPERATION_LEGACY_MAP
        ),
        InputDefinition(
            id = "source_text",
            nameStringRes = R.string.param_vflow_data_base64_source_text_name,
            name = "源文本",
            staticType = ParameterType.STRING,
            defaultValue = "",
            acceptsMagicVariable = true,
            supportsRichText = true
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition(
            id = "result_text",
            name = "结果文本",
            nameStringRes = R.string.output_vflow_data_base64_result_text_name,
            typeName = VTypeRegistry.STRING.id
        )
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val inputs = getInputs()
        val operation = step.parameters["operation"]?.toString() ?: OP_ENCODE
        val sourceText = step.parameters["source_text"]

        val operationPill = PillUtil.createPillFromParam(
            operation,
            inputs.find { it.id == "operation" },
            isModuleOption = true
        )
        val sourcePill = PillUtil.createPillFromParam(
            sourceText,
            inputs.find { it.id == "source_text" }
        )

        return PillUtil.buildSpannable(
            context,
            operationPill,
            " ",
            sourcePill
        )
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val operationInput = getInputs().first { it.id == "operation" }
        val rawOperation = context.getVariableAsString("operation", OP_ENCODE)
        val operation = operationInput.normalizeEnumValue(rawOperation) ?: rawOperation
        val rawSource = context.getVariableAsString("source_text", "")

        // 解析可能包含变量药丸的文本
        val source = VariableResolver.resolve(rawSource, context)

        return try {
            val result = if (operation == OP_ENCODE) {
                Base64.getEncoder().encodeToString(source.toByteArray(Charset.forName("UTF-8")))
            } else {
                val decodedBytes = Base64.getDecoder().decode(source)
                String(decodedBytes, Charset.forName("UTF-8"))
            }
            ExecutionResult.Success(mapOf("result_text" to VString(result)))
        } catch (e: IllegalArgumentException) {
            ExecutionResult.Failure("解码失败", "输入的文本不是有效的 Base64 编码格式。")
        } catch (e: Exception) {
            ExecutionResult.Failure("执行出错", e.localizedMessage ?: "未知错误")
        }
    }
}

// 文件: main/java/com/chaomixian/vflow/core/workflow/module/interaction/OCRModule.kt
package com.chaomixian.vflow.core.workflow.module.interaction

import android.content.Context
import android.net.Uri
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.execution.VariableResolver
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VBoolean
import com.chaomixian.vflow.core.types.basic.VNull
import com.chaomixian.vflow.core.types.basic.VString
import com.chaomixian.vflow.core.types.basic.VNumber
import com.chaomixian.vflow.core.types.complex.VImage
import com.chaomixian.vflow.core.types.complex.VScreenElement
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.ui.workflow_editor.PillUtil
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import java.io.IOException
import kotlin.math.pow

/**
 * "屏幕文字识别 (OCR)" 模块。
 */
class OCRModule : BaseModule() {

    override val id = "vflow.interaction.ocr"
    override val metadata = ActionMetadata(
        name = "文字识别 (OCR)",
        description = "识别图片中的文字，或查找指定文字的坐标。",
        iconRes = R.drawable.rounded_feature_search_24,
        category = "界面交互"
    )

    override val uiProvider: ModuleUIProvider = OCRModuleUIProvider()

    val modeOptions = listOf("识别全文", "查找文本")
    val languageOptions = listOf("中英混合", "中文", "英文")
    val strategyOptions = listOf("默认 (从上到下)", "最接近中心", "置信度最高")

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition("image", "输入图片", ParameterType.ANY, acceptsMagicVariable = true, acceptedMagicVariableTypes = setOf(VTypeRegistry.IMAGE.id)),
        InputDefinition("mode", "模式", ParameterType.ENUM, "识别全文", options = modeOptions, acceptsMagicVariable = false),
        InputDefinition("target_text", "查找内容", ParameterType.STRING, "", acceptsMagicVariable = true, supportsRichText = true),
        // 高级选项
        InputDefinition("language", "识别语言", ParameterType.ENUM, "中英混合", options = languageOptions, acceptsMagicVariable = false, isHidden = true),
        InputDefinition("search_strategy", "查找策略", ParameterType.ENUM, "默认 (从上到下)", options = strategyOptions, acceptsMagicVariable = false, isHidden = true),
        // 用于保存"更多设置"开关的状态
        InputDefinition("show_advanced", "显示高级选项", ParameterType.BOOLEAN, false, acceptsMagicVariable = false, isHidden = true)
    )

    override fun getDynamicInputs(step: ActionStep?, allSteps: List<ActionStep>?): List<InputDefinition> {
        return getInputs()
    }

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> {
        val mode = step?.parameters?.get("mode") as? String ?: "识别全文"
        return if (mode == "识别全文") {
            listOf(
                OutputDefinition("success", "是否成功", VTypeRegistry.BOOLEAN.id),
                OutputDefinition("full_text", "识别到的文字", VTypeRegistry.STRING.id)
            )
        } else {
            listOf(
                OutputDefinition("success", "是否成功", VTypeRegistry.BOOLEAN.id),
                OutputDefinition("found", "是否找到", VTypeRegistry.BOOLEAN.id),
                OutputDefinition("first_match", "第一个结果 (元素)", VTypeRegistry.UI_ELEMENT.id),
                OutputDefinition("all_matches", "所有结果 (列表)", VTypeRegistry.LIST.id),
                OutputDefinition("count", "找到数量", VTypeRegistry.NUMBER.id)
            )
        }
    }

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val mode = step.parameters["mode"] as? String ?: "识别全文"
        val imagePill = PillUtil.createPillFromParam(step.parameters["image"], getInputs().find { it.id == "image" })

        return if (mode == "查找文本") {
            val targetPill = PillUtil.createPillFromParam(step.parameters["target_text"], getInputs().find { it.id == "target_text" })
            PillUtil.buildSpannable(context, "在 ", imagePill, " 中查找文字 ", targetPill)
        } else {
            PillUtil.buildSpannable(context, "识别 ", imagePill, " 中的文字")
        }
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        // 获取参数
        val imageVar = context.magicVariables["image"] as? VImage
            ?: return ExecutionResult.Failure("参数错误", "请提供一张有效的图片。")
        val mode = context.variables["mode"] as? String ?: "识别全文"
        val language = context.variables["language"] as? String ?: "中英混合"
        val strategy = context.variables["search_strategy"] as? String ?: "默认 (从上到下)"
        val rawTargetText = context.variables["target_text"]?.toString() ?: ""
        val targetText = VariableResolver.resolve(rawTargetText, context)

        if (mode == "查找文本" && targetText.isEmpty()) {
            return ExecutionResult.Failure("参数错误", "查找内容不能为空。")
        }

        val appContext = context.applicationContext

        // 准备识别器
        val recognizer = when (language) {
            "英文" -> TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            else -> TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
        }

        try {
            onProgress(ProgressUpdate("正在处理图片..."))
            val inputImage = InputImage.fromFilePath(appContext, Uri.parse(imageVar.uriString))

            onProgress(ProgressUpdate("正在识别文字..."))
            val result: Text = recognizer.process(inputImage).await()

            // 处理结果 - 识别全文模式
            if (mode == "识别全文") {
                val fullText = result.text
                onProgress(ProgressUpdate("识别完成，文字长度: ${fullText.length}"))
                return ExecutionResult.Success(mapOf(
                    "success" to VBoolean(true),
                    "full_text" to VString(fullText)
                ))
            }

            // 处理结果 - 查找文本模式（使用新的 VScreenElement 类型）
            onProgress(ProgressUpdate("正在查找匹配项: $targetText"))
            val matches = mutableListOf<VScreenElement>()

            for (block in result.textBlocks) {
                for (line in block.lines) {
                    if (line.text.contains(targetText, ignoreCase = true)) {
                        line.boundingBox?.let { rect ->
                            matches.add(VScreenElement(rect, line.text))
                        }
                    }
                }
            }

            if (matches.isEmpty()) {
                // 返回 Failure，让用户通过"异常处理策略"选择行为
                // 用户可以选择：重试（OCR 可能识别不准确）、忽略错误继续、停止工作流
                return ExecutionResult.Failure(
                    "未找到文字",
                    "在图片中未找到指定文字: '$targetText'（语言: $language, 策略: $strategy）",
                    // 提供 partialOutputs，让"跳过此步骤继续"时有语义化的默认值
                    partialOutputs = mapOf(
                        "success" to VBoolean(true),
                        "found" to VBoolean(false),
                        "count" to VNumber(0.0),           // 找到 0 个（语义化）
                        "all_matches" to emptyList<Any>(), // 空列表（语义化）
                        "first_match" to VNull             // 没有"第一个"
                    )
                )
            }

            // 应用排序策略
            val sortedMatches = when (strategy) {
                "最接近中心" -> {
                    val cx = inputImage.width / 2
                    val cy = inputImage.height / 2
                    matches.sortedBy { elem ->
                        val dx = elem.bounds.centerX() - cx
                        val dy = elem.bounds.centerY() - cy
                        dx.toDouble().pow(2) + dy.toDouble().pow(2)
                    }
                }
                "置信度最高" -> {
                    matches.sortedBy { it.bounds.top } // 暂回退到默认排序
                }
                else -> { // "默认 (从上到下)"
                    matches.sortedWith(compareBy({ it.bounds.top }, { it.bounds.left }))
                }
            }

            val firstMatch = sortedMatches.first()
            onProgress(ProgressUpdate("找到 ${matches.size} 个结果，第一个位于 (${firstMatch.bounds.centerX()}, ${firstMatch.bounds.centerY()})"))

            return ExecutionResult.Success(mapOf(
                "success" to VBoolean(true),
                "found" to VBoolean(true),
                "count" to VNumber(matches.size.toDouble()),
                "first_match" to firstMatch,
                "all_matches" to sortedMatches
            ))

        } catch (e: IOException) {
            return ExecutionResult.Failure("图片读取失败", e.message ?: "无法打开图片文件")
        } catch (e: Exception) {
            return ExecutionResult.Failure("OCR 识别异常", e.message ?: "发生了未知错误")
        } finally {
            recognizer.close()
        }
    }
}
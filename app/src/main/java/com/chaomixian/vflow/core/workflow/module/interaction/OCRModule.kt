// 文件: main/java/com/chaomixian/vflow/core/workflow/module/interaction/OCRModule.kt
package com.chaomixian.vflow.core.workflow.module.interaction

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.execution.VariableResolver
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VBoolean
import com.chaomixian.vflow.core.types.basic.VNull
import com.chaomixian.vflow.core.types.basic.VString
import com.chaomixian.vflow.core.types.basic.VNumber
import com.chaomixian.vflow.core.types.complex.VCoordinate
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
        InputDefinition("region_top_left", "识别区域-左上坐标", ParameterType.STRING, "", acceptsMagicVariable = true, isHidden = true),
        InputDefinition("region_bottom_right", "识别区域-右下坐标", ParameterType.STRING, "", acceptsMagicVariable = true, isHidden = true),
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
                OutputDefinition("count", "找到数量", VTypeRegistry.NUMBER.id),
                OutputDefinition("first_match", "第一个结果 (元素)", VTypeRegistry.UI_ELEMENT.id),
                OutputDefinition("first_center", "第一个结果 (坐标)", VTypeRegistry.COORDINATE.id),
                OutputDefinition("all_matches", "所有结果 (元素列表)", VTypeRegistry.LIST.id),
                OutputDefinition("all_centers", "所有结果 (坐标列表)", VTypeRegistry.LIST.id)
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

        // 获取识别区域参数
        val rawTopLeft = context.variables["region_top_left"]?.toString()
        val rawBottomRight = context.variables["region_bottom_right"]?.toString()
        val topLeft = parseCoordinate(rawTopLeft, context)
        val bottomRight = parseCoordinate(rawBottomRight, context)

        // 验证区域参数
        if ((topLeft != null) != (bottomRight != null)) {
            return ExecutionResult.Failure("参数错误", "识别区域需要同时设置左上和右下坐标。")
        }

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
            val inputImage = if (topLeft != null && bottomRight != null) {
                // 使用区域识别
                createInputImageWithRegion(appContext, Uri.parse(imageVar.uriString), topLeft, bottomRight)
            } else {
                // 全屏识别
                InputImage.fromFilePath(appContext, Uri.parse(imageVar.uriString))
            }

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
                            // 如果使用了区域识别，需要将坐标转换回原始图片坐标系
                            val adjustedRect = if (topLeft != null && bottomRight != null) {
                                android.graphics.Rect(
                                    rect.left + topLeft.first,
                                    rect.top + topLeft.second,
                                    rect.right + topLeft.first,
                                    rect.bottom + topLeft.second
                                )
                            } else {
                                rect
                            }
                            matches.add(VScreenElement(adjustedRect, line.text))
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
                        "count" to VNumber(0.0),              // 找到 0 个（语义化）
                        "all_matches" to emptyList<Any>(),    // 空列表（语义化）
                        "first_match" to VNull,              // 没有"第一个"
                        "all_centers" to emptyList<Any>(),   // 空列表（语义化）
                        "first_center" to VNull              // 没有"第一个"
                    )
                )
            }

            // 应用排序策略
            val sortedMatches = when (strategy) {
                "最接近中心" -> {
                    // 如果使用了区域识别，中心点应该是识别区域的中心（在原始图片坐标系中）
                    val cx = if (topLeft != null && bottomRight != null) {
                        (topLeft.first + bottomRight.first) / 2
                    } else {
                        inputImage.width / 2
                    }
                    val cy = if (topLeft != null && bottomRight != null) {
                        (topLeft.second + bottomRight.second) / 2
                    } else {
                        inputImage.height / 2
                    }
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
            val firstCenter = VCoordinate(firstMatch.bounds.centerX(), firstMatch.bounds.centerY())
            val allCenters = sortedMatches.map { VCoordinate(it.bounds.centerX(), it.bounds.centerY()) }

            onProgress(ProgressUpdate("找到 ${matches.size} 个结果，第一个位于 (${firstMatch.bounds.centerX()}, ${firstMatch.bounds.centerY()})"))

            return ExecutionResult.Success(mapOf(
                "success" to VBoolean(true),
                "found" to VBoolean(true),
                "count" to VNumber(matches.size.toDouble()),
                "first_match" to firstMatch,
                "first_center" to firstCenter,
                "all_matches" to sortedMatches,
                "all_centers" to allCenters
            ))

        } catch (e: IOException) {
            return ExecutionResult.Failure("图片读取失败", e.message ?: "无法打开图片文件")
        } catch (e: Exception) {
            return ExecutionResult.Failure("OCR 识别异常", e.message ?: "发生了未知错误")
        } finally {
            recognizer.close()
        }
    }

    /**
     * 解析坐标字符串
     * 支持格式: "x,y" 或变量引用
     */
    private fun parseCoordinate(raw: String?, context: ExecutionContext): Pair<Int, Int>? {
        if (raw.isNullOrBlank()) return null

        // 解析变量引用
        val resolved = VariableResolver.resolve(raw, context)

        // 解析坐标格式 "x,y"
        val parts = resolved.split(",")
        if (parts.size != 2) return null

        val x = parts[0].trim().toIntOrNull() ?: return null
        val y = parts[1].trim().toIntOrNull() ?: return null

        return Pair(x, y)
    }

    /**
     * 创建带区域裁剪的 InputImage
     */
    private suspend fun createInputImageWithRegion(
        context: Context,
        uri: Uri,
        topLeft: Pair<Int, Int>,
        bottomRight: Pair<Int, Int>
    ): InputImage {
        // 加载完整图片
        val bitmap = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val source = android.graphics.ImageDecoder.createSource(context.contentResolver, uri)
            android.graphics.ImageDecoder.decodeBitmap(source)
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }

        try {
            // 计算裁剪区域
            val left = topLeft.first.coerceAtLeast(0)
            val top = topLeft.second.coerceAtLeast(0)
            val right = bottomRight.first.coerceAtMost(bitmap.width)
            val bottom = bottomRight.second.coerceAtMost(bitmap.height)

            if (left >= right || top >= bottom) {
                throw IllegalArgumentException("识别区域无效: ($left,$top)-($right,$bottom)")
            }

            val width = right - left
            val height = bottom - top

            // 裁剪Bitmap
            val croppedBitmap = Bitmap.createBitmap(bitmap, left, top, width, height)

            return InputImage.fromBitmap(croppedBitmap, 0)
        } finally {
            // 回收原始Bitmap
            bitmap.recycle()
        }
    }
}
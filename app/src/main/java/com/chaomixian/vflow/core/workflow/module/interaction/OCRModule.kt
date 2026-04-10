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
import com.chaomixian.vflow.core.types.basic.VNumber
import com.chaomixian.vflow.core.types.basic.VString
import com.chaomixian.vflow.core.types.complex.VCoordinate
import com.chaomixian.vflow.core.types.complex.VCoordinateRegion
import com.chaomixian.vflow.core.types.complex.VImage
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
        nameStringRes = R.string.module_vflow_interaction_ocr_name,
        descriptionStringRes = R.string.module_vflow_interaction_ocr_desc,
        name = "文字识别 (OCR)",
        description = "识别图片中的文字，或查找指定文字的坐标。",
        iconRes = R.drawable.rounded_feature_search_24,
        category = "界面交互",
        categoryId = "interaction"
    )

    override val uiProvider: ModuleUIProvider? = null

    // 序列化值使用与语言无关的标识符
    companion object {
        const val MODE_RECOGNIZE = "recognize"
        const val MODE_FIND = "find"

        const val LANGUAGE_MIXED = "mixed"
        const val LANGUAGE_CHINESE = "chinese"
        const val LANGUAGE_ENGLISH = "english"

        const val STRATEGY_DEFAULT = "default"
        const val STRATEGY_CENTER = "center"
        const val STRATEGY_CONFIDENCE = "confidence"
    }

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "image",
            name = "输入图片",
            staticType = ParameterType.ANY,
            acceptsMagicVariable = true,
            acceptedMagicVariableTypes = setOf(VTypeRegistry.IMAGE.id),
            nameStringRes = R.string.param_vflow_interaction_ocr_image_name
        ),
        InputDefinition(
            id = "mode",
            name = "模式",
            staticType = ParameterType.ENUM,
            defaultValue = MODE_RECOGNIZE,
            options = listOf(MODE_RECOGNIZE, MODE_FIND),
            acceptsMagicVariable = false,
            inputStyle = InputStyle.CHIP_GROUP,
            nameStringRes = R.string.param_vflow_interaction_ocr_mode_name,
            optionsStringRes = listOf(
                R.string.param_vflow_interaction_ocr_mode_opt_recognize,
                R.string.param_vflow_interaction_ocr_mode_opt_find
            ),
            legacyValueMap = mapOf(
                "识别全文" to MODE_RECOGNIZE,
                "查找文本" to MODE_FIND
            )
        ),
        InputDefinition(
            id = "target_text",
            name = "查找内容",
            staticType = ParameterType.STRING,
            defaultValue = "",
            acceptsMagicVariable = true,
            supportsRichText = true,
            visibility = InputVisibility.whenEquals("mode", MODE_FIND),
            nameStringRes = R.string.param_vflow_interaction_ocr_target_text_name,
            hintStringRes = R.string.param_vflow_interaction_ocr_target_text_hint
        ),
        // 折叠到"更多设置"的选项
        InputDefinition(
            id = "language",
            name = "识别语言",
            staticType = ParameterType.ENUM,
            defaultValue = LANGUAGE_MIXED,
            options = listOf(LANGUAGE_MIXED, LANGUAGE_CHINESE, LANGUAGE_ENGLISH),
            acceptsMagicVariable = false,
            isFolded = true,
            nameStringRes = R.string.param_vflow_interaction_ocr_language_name,
            optionsStringRes = listOf(
                R.string.param_vflow_interaction_ocr_language_opt_mixed,
                R.string.param_vflow_interaction_ocr_language_opt_chinese,
                R.string.param_vflow_interaction_ocr_language_opt_english
            ),
            legacyValueMap = mapOf(
                "中英混合" to LANGUAGE_MIXED,
                "中文" to LANGUAGE_CHINESE,
                "英文" to LANGUAGE_ENGLISH
            )
        ),
        InputDefinition(
            id = "search_strategy",
            name = "查找策略",
            staticType = ParameterType.ENUM,
            defaultValue = STRATEGY_DEFAULT,
            options = listOf(STRATEGY_DEFAULT, STRATEGY_CENTER, STRATEGY_CONFIDENCE),
            acceptsMagicVariable = false,
            isFolded = true,
            visibility = InputVisibility.whenEquals("mode", MODE_FIND),
            nameStringRes = R.string.param_vflow_interaction_ocr_search_strategy_name,
            optionsStringRes = listOf(
                R.string.param_vflow_interaction_ocr_search_strategy_opt_default,
                R.string.param_vflow_interaction_ocr_search_strategy_opt_center,
                R.string.param_vflow_interaction_ocr_search_strategy_opt_confidence
            ),
            legacyValueMap = mapOf(
                "默认 (从上到下)" to STRATEGY_DEFAULT,
                "最接近中心" to STRATEGY_CENTER,
                "置信度最高" to STRATEGY_CONFIDENCE
            )
        ),
        InputDefinition(
            id = "region",
            name = "识别区域（可选）",
            staticType = ParameterType.ANY,
            defaultValue = "",
            acceptsMagicVariable = true,
            acceptedMagicVariableTypes = setOf(VTypeRegistry.COORDINATE_REGION.id),
            isFolded = true,
            hint = "留空则识别全屏",
            nameStringRes = R.string.param_vflow_interaction_ocr_region_name,
            hintStringRes = R.string.param_vflow_interaction_ocr_region_hint
        ),
        // 用于保存"更多设置"展开状态的内部参数
        InputDefinition(
            id = "show_advanced",
            name = "显示高级选项",
            staticType = ParameterType.BOOLEAN,
            defaultValue = false,
            acceptsMagicVariable = false,
            isHidden = true
        )
    )

    override fun getDynamicInputs(step: ActionStep?, allSteps: List<ActionStep>?): List<InputDefinition> {
        return getInputs()
    }

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> {
        val modeInput = getInputs().first { it.id == "mode" }
        val rawMode = step?.parameters?.get("mode") as? String ?: MODE_RECOGNIZE
        val mode = modeInput.normalizeEnumValue(rawMode) ?: rawMode
        return if (mode == MODE_RECOGNIZE) {
            listOf(
                OutputDefinition("success", "是否成功", VTypeRegistry.BOOLEAN.id, nameStringRes = R.string.output_vflow_interaction_ocr_success_name),
                OutputDefinition("full_text", "识别到的文字", VTypeRegistry.STRING.id, nameStringRes = R.string.output_vflow_interaction_ocr_full_text_name)
            )
        } else {
            listOf(
                OutputDefinition("success", "是否成功", VTypeRegistry.BOOLEAN.id, nameStringRes = R.string.output_vflow_interaction_ocr_success_name),
                OutputDefinition("found", "是否找到", VTypeRegistry.BOOLEAN.id, nameStringRes = R.string.output_vflow_interaction_ocr_found_name),
                OutputDefinition("count", "找到数量", VTypeRegistry.NUMBER.id, nameStringRes = R.string.output_vflow_interaction_ocr_count_name),
                OutputDefinition("first_match", "第一个结果 (区域)", VTypeRegistry.COORDINATE_REGION.id, nameStringRes = R.string.output_vflow_interaction_ocr_first_match_name),
                OutputDefinition("first_center", "第一个结果 (中心坐标)", VTypeRegistry.COORDINATE.id, nameStringRes = R.string.output_vflow_interaction_ocr_first_center_name),
                OutputDefinition("all_matches", "所有结果 (区域列表)", VTypeRegistry.LIST.id, listElementType = VTypeRegistry.COORDINATE_REGION.id, nameStringRes = R.string.output_vflow_interaction_ocr_all_matches_name),
                OutputDefinition("all_centers", "所有结果 (中心坐标列表)", VTypeRegistry.LIST.id, listElementType = VTypeRegistry.COORDINATE.id, nameStringRes = R.string.output_vflow_interaction_ocr_all_centers_name)
            )
        }
    }

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val mode = step.parameters["mode"] as? String ?: MODE_RECOGNIZE
        val imagePill = PillUtil.createPillFromParam(step.parameters["image"], getInputs().find { it.id == "image" })

        return if (mode == MODE_FIND) {
            val targetPill = PillUtil.createPillFromParam(step.parameters["target_text"], getInputs().find { it.id == "target_text" })
            PillUtil.buildSpannable(context, context.getString(R.string.summary_vflow_device_ocr_find_in), imagePill, context.getString(R.string.summary_vflow_device_ocr_find_middle), targetPill)
        } else {
            PillUtil.buildSpannable(context, context.getString(R.string.summary_vflow_device_ocr_recognize), imagePill, context.getString(R.string.summary_vflow_device_ocr_text_in_image))
        }
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        // 获取参数
        val imageVar = context.getVariable("image") as? VImage
            ?: return ExecutionResult.Failure("参数错误", "请提供一张有效的图片。")
        val inputsById = getInputs().associateBy { it.id }
        val rawMode = context.getVariableAsString("mode", MODE_RECOGNIZE)
        val mode = inputsById["mode"]?.normalizeEnumValue(rawMode) ?: rawMode
        val rawLanguage = context.getVariableAsString("language", LANGUAGE_MIXED)
        val language = inputsById["language"]?.normalizeEnumValue(rawLanguage) ?: rawLanguage
        val rawStrategy = context.getVariableAsString("search_strategy", STRATEGY_DEFAULT)
        val strategy = inputsById["search_strategy"]?.normalizeEnumValue(rawStrategy) ?: rawStrategy
        val rawTargetText = context.getVariableAsString("target_text", "")
        val targetText = VariableResolver.resolve(rawTargetText, context)

        // 获取识别区域参数（支持多种输入方式）
        val region = parseRegionParameter(context)
        val topLeft = if (region != null) Pair(region.left, region.top) else null
        val bottomRight = if (region != null) Pair(region.right, region.bottom) else null

        // 验证区域参数
        if ((topLeft != null) != (bottomRight != null)) {
            return ExecutionResult.Failure("参数错误", "识别区域需要同时设置左上和右下坐标。")
        }

        if (mode == MODE_FIND && targetText.isEmpty()) {
            return ExecutionResult.Failure("参数错误", "查找内容不能为空。")
        }

        val appContext = context.applicationContext

        // 将标识符转换为本地化文本用于显示
        val languageDisplay = when (language) {
            LANGUAGE_ENGLISH -> appContext.getString(R.string.param_vflow_interaction_ocr_language_opt_english)
            LANGUAGE_CHINESE -> appContext.getString(R.string.param_vflow_interaction_ocr_language_opt_chinese)
            else -> appContext.getString(R.string.param_vflow_interaction_ocr_language_opt_mixed)
        }
        val strategyDisplay = when (strategy) {
            STRATEGY_CENTER -> appContext.getString(R.string.param_vflow_interaction_ocr_search_strategy_opt_center)
            STRATEGY_CONFIDENCE -> appContext.getString(R.string.param_vflow_interaction_ocr_search_strategy_opt_confidence)
            else -> appContext.getString(R.string.param_vflow_interaction_ocr_search_strategy_opt_default)
        }

        // 准备识别器
        val recognizer = when (language) {
            LANGUAGE_ENGLISH -> TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
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
            if (mode == MODE_RECOGNIZE) {
                val fullText = result.text
                onProgress(ProgressUpdate("识别完成，文字长度: ${fullText.length}"))
                return ExecutionResult.Success(mapOf(
                    "success" to VBoolean(true),
                    "full_text" to VString(fullText)
                ))
            }

            // 处理结果 - 查找文本模式（使用 VCoordinateRegion 类型）
            onProgress(ProgressUpdate("正在查找匹配项: $targetText"))
            val matches = mutableListOf<VCoordinateRegion>()

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
                            matches.add(VCoordinateRegion.fromRect(adjustedRect))
                        }
                    }
                }
            }

            if (matches.isEmpty()) {
                // 返回 Failure，让用户通过"异常处理策略"选择行为
                // 用户可以选择：重试（OCR 可能识别不准确）、忽略错误继续、停止工作流
                return ExecutionResult.Failure(
                    "未找到文字",
                    "在图片中未找到指定文字: '$targetText'（语言: $languageDisplay, 策略: $strategyDisplay）",
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
                STRATEGY_CENTER -> {
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
                        val dx = elem.centerX - cx
                        val dy = elem.centerY - cy
                        dx.toDouble().pow(2) + dy.toDouble().pow(2)
                    }
                }
                STRATEGY_CONFIDENCE -> {
                    matches.sortedBy { it.top } // 暂回退到默认排序
                }
                else -> { // STRATEGY_DEFAULT
                    matches.sortedWith(compareBy({ it.top }, { it.left }))
                }
            }

            val firstMatch = sortedMatches.first()
            val firstCenter = VCoordinate(firstMatch.centerX, firstMatch.centerY)
            val allCenters = sortedMatches.map { VCoordinate(it.centerX, it.centerY) }

            onProgress(ProgressUpdate("找到 ${matches.size} 个结果，第一个位于 (${firstMatch.centerX}, ${firstMatch.centerY})"))

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
     * 解析识别区域参数（支持多种输入方式）
     * 1. VCoordinateRegion 变量
     * 2. VString 变量（包含坐标文本，格式：x1,y1,x2,y2）
     * 3. 手动输入的坐标字符串（格式：x1,y1,x2,y2）
     */
    private fun parseRegionParameter(context: ExecutionContext): VCoordinateRegion? {
        // 尝试从变量获取 VCoordinateRegion
        val regionObj = context.getVariable("region")
        val magicRegion = regionObj as? VCoordinateRegion
        if (magicRegion != null) return magicRegion

        // 尝试从 variables 获取字符串（可能是变量引用或手动输入）
        val rawRegion = context.getVariableAsString("region", "")
        if (rawRegion.isNullOrBlank()) return null

        // 如果是变量引用，解析变量
        val resolvedText = if (rawRegion.startsWith("{{") && rawRegion.endsWith("}}")) {
            // 变量引用：使用 VariableResolver.resolveValue 获取变量值
            val resolvedValue = VariableResolver.resolveValue(rawRegion, context)

            when (resolvedValue) {
                is VCoordinateRegion -> return resolvedValue
                is VString -> resolvedValue.raw
                is String -> resolvedValue  // 可能直接返回字符串
                else -> null
            }
        } else {
            // 手动输入的字符串
            rawRegion
        }

        // 解析坐标字符串（格式：x1,y1,x2,y2）
        return parseCoordinateString(resolvedText)
    }

    /**
     * 解析坐标字符串
     * 支持格式：x1,y1,x2,y2 或 x1,y1,x2,y2（逗号分隔的四个数字）
     */
    private fun parseCoordinateString(text: String?): VCoordinateRegion? {
        if (text.isNullOrBlank()) return null

        val parts = text.split(",")
        if (parts.size != 4) return null

        val left = parts[0].trim().toIntOrNull() ?: return null
        val top = parts[1].trim().toIntOrNull() ?: return null
        val right = parts[2].trim().toIntOrNull() ?: return null
        val bottom = parts[3].trim().toIntOrNull() ?: return null

        return VCoordinateRegion(left, top, right, bottom)
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

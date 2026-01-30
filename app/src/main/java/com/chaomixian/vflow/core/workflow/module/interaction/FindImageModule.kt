// 文件: FindImageModule.kt
package com.chaomixian.vflow.core.workflow.module.interaction

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.net.Uri
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.logging.DebugLogger
import com.chaomixian.vflow.core.logging.LogManager
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VNull
import com.chaomixian.vflow.core.types.basic.VNumber
import com.chaomixian.vflow.core.types.complex.VCoordinate
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.permissions.Permission
import com.chaomixian.vflow.services.ShellManager
import com.chaomixian.vflow.ui.workflow_editor.PillUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import android.util.Base64
import kotlin.math.abs
import kotlin.math.min

/**
 * "查找图片" 模块。
 * 在当前屏幕上查找与模板图片相似的区域，返回匹配位置的中心坐标。
 */
class FindImageModule : BaseModule() {

    override val id = "vflow.interaction.find_image"
    override val metadata = ActionMetadata(
        nameStringRes = R.string.module_vflow_interaction_find_image_name,
        descriptionStringRes = R.string.module_vflow_interaction_find_image_desc,
        name = "查找图片",
        description = "在屏幕上查找与模板图片相似的区域，返回匹配位置的中心坐标。",
        iconRes = R.drawable.rounded_image_search_24,
        category = "界面交互"
    )

    override val uiProvider: ModuleUIProvider = FindImageModuleUIProvider()

    val thresholdOptions = listOf("90% (精确)", "80% (推荐)", "70% (宽松)", "60% (模糊)")

    override fun getRequiredPermissions(step: ActionStep?): List<Permission> {
        return ShellManager.getRequiredPermissions(LogManager.applicationContext)
    }

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "template_uri",
            name = "模板图片",
            staticType = ParameterType.STRING,
            defaultValue = "",
            acceptsMagicVariable = false
        ),
        InputDefinition(
            id = "threshold",
            name = "匹配相似度",
            staticType = ParameterType.ENUM,
            defaultValue = "80% (推荐)",
            options = thresholdOptions,
            acceptsMagicVariable = false
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> {
        val conditions = listOf(
            ConditionalOption("找到", "找到"),
            ConditionalOption("未找到", "未找到")
        )

        return listOf(
            OutputDefinition("first_result", "最相似坐标", VTypeRegistry.COORDINATE.id, conditions),
            OutputDefinition("all_results", "所有结果", VTypeRegistry.LIST.id, listElementType = VTypeRegistry.COORDINATE.id,
                conditionalOptions = conditions
            ),
            OutputDefinition("count", "结果数量", VTypeRegistry.NUMBER.id, conditions)
        )
    }

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val templateUri = step.parameters["template_uri"] as? String ?: ""
        val thresholdPill = PillUtil.createPillFromParam(
            step.parameters["threshold"],
            getInputs().find { it.id == "threshold" },
            isModuleOption = true
        )
        // 创建"最相似坐标"的 Pill，使用空 parameterId 表示这是一个输出值样式
        val outputPill = PillUtil.Pill("最相似坐标", "")

        return if (templateUri.isNotEmpty()) {
            PillUtil.buildSpannable(context, context.getString(R.string.summary_vflow_interaction_find_image), thresholdPill, context.getString(R.string.summary_vflow_interaction_and_output), outputPill)
        } else {
            context.getString(R.string.summary_vflow_interaction_find_image_no_template)
        }
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val appContext = context.applicationContext

        val templateUri = context.getVariableAsString("template_uri", "")
        if (templateUri.isNullOrEmpty()) {
            return ExecutionResult.Failure("参数错误", "请先设置模板图片。")
        }

        val thresholdStr = context.getVariableAsString("threshold", "80% (推荐)")
        // 将用户阈值转换为内部阈值（允许的最大差异百分比）
        val maxDiffPercent = when {
            thresholdStr.startsWith("90") -> 0.10  // 90%相似 = 最多10%差异
            thresholdStr.startsWith("80") -> 0.20  // 80%相似 = 最多20%差异
            thresholdStr.startsWith("70") -> 0.30  // 70%相似 = 最多30%差异
            thresholdStr.startsWith("60") -> 0.40  // 60%相似 = 最多40%差异
            else -> 0.20
        }

        onProgress(ProgressUpdate("正在加载模板图片..."))

        val templateBitmap = loadBitmap(appContext, templateUri)
            ?: return ExecutionResult.Failure("加载失败", "无法加载模板图片。")

        onProgress(ProgressUpdate("正在截取屏幕..."))

        val screenshotUri = captureScreen(appContext, context.workDir)
        if (screenshotUri == null) {
            templateBitmap.recycle()
            return ExecutionResult.Failure("截图失败", "无法截取屏幕，请检查 Shizuku 或 Root 权限。")
        }

        val screenBitmap = loadBitmap(appContext, screenshotUri.toString())
        if (screenBitmap == null) {
            templateBitmap.recycle()
            return ExecutionResult.Failure("加载失败", "无法加载截图。")
        }

        onProgress(ProgressUpdate("正在进行图片匹配 (模板: ${templateBitmap.width}x${templateBitmap.height})..."))

        val matches = withContext(Dispatchers.Default) {
            ImageMatcher.findAll(screenBitmap, templateBitmap, maxDiffPercent)
        }

        templateBitmap.recycle()
        screenBitmap.recycle()
        try {
            File(screenshotUri.path ?: "").delete()
        } catch (e: Exception) { }

        val outputs = mutableMapOf<String, Any?>()
        outputs["count"] = VNumber(matches.size.toDouble())

        if (matches.isEmpty()) {
            // 返回 Failure，让用户通过"异常处理策略"选择行为
            // 用户可以选择：重试（UI 可能还在加载）、忽略错误继续、停止工作流
            val similarityPercent = ((1.0 - maxDiffPercent) * 100).toInt()
            return ExecutionResult.Failure(
                "未找到图片",
                "在屏幕上未找到与模板图片匹配的区域。相似度要求: ${similarityPercent}%。请检查模板图片是否正确，或降低相似度要求。",
                // 提供 partialOutputs，让"跳过此步骤继续"时有语义化的默认值
                partialOutputs = mapOf(
                    "count" to VNumber(0.0),              // 找到 0 个
                    "all_results" to emptyList<Any>(),   // 空列表
                    "first_result" to VNull             // 没有"第一个"
                )
            )
        }

        // 确保按相似度排序（差异最小的在前面）
        val sortedMatches = matches.sortedBy { it.diffRatio }

        // 转换为坐标列表（使用新的 VCoordinate 类型）
        val allCoordinates = sortedMatches.map { match ->
            VCoordinate(match.centerX, match.centerY)
        }

        // 最相似的结果（第一个）
        val bestMatch = sortedMatches.first()
        val bestCoordinate = allCoordinates.first()

        outputs["all_results"] = allCoordinates
        outputs["first_result"] = bestCoordinate

        val similarityPercent = ((1.0 - bestMatch.diffRatio) * 100).toInt()
        onProgress(ProgressUpdate("找到 ${matches.size} 个匹配，最相似坐标: (${bestCoordinate.x}, ${bestCoordinate.y})，相似度: ${similarityPercent}%"))

        return ExecutionResult.Success(outputs)
    }

    private fun loadBitmap(context: Context, dataString: String): Bitmap? {
        return try {
            // 判断是 Base64 还是旧格式的 URI
            if (dataString.startsWith("data:image") || dataString.length > 1000) {
                // Base64 格式: "data:image/png;base64,{base64_string}" 或纯 Base64 字符串
                val base64Data = if (dataString.startsWith("data:image")) {
                    dataString.substringAfter("base64,")
                } else {
                    dataString
                }
                val byteArray = Base64.decode(base64Data, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
            } else {
                // 旧格式 URI
                val uri = Uri.parse(dataString)
                when {
                    uri.scheme == "file" -> BitmapFactory.decodeFile(uri.path)
                    uri.scheme == "content" -> {
                        context.contentResolver.openInputStream(uri)?.use { inputStream ->
                            BitmapFactory.decodeStream(inputStream)
                        }
                    }
                    dataString.startsWith("/") -> BitmapFactory.decodeFile(dataString)
                    else -> {
                        context.contentResolver.openInputStream(uri)?.use { inputStream ->
                            BitmapFactory.decodeStream(inputStream)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            DebugLogger.e("FindImageModule", "加载图片失败: $dataString", e)
            null
        }
    }

    private suspend fun captureScreen(context: Context, workDir: File): Uri? {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.getDefault()).format(Date())
        val fileName = "find_image_screen_$timestamp.png"
        val cacheFile = File(workDir, fileName)
        val path = cacheFile.absolutePath

        val command = "screencap -p \"$path\""
        DebugLogger.i("FindImageModule", "执行截图命令: $command")

        return withContext(Dispatchers.IO) {
            try {
                val result = ShellManager.execShellCommand(context, command, ShellManager.ShellMode.AUTO)
                DebugLogger.i("FindImageModule", "截图命令执行结果: $result")

                if (cacheFile.exists() && cacheFile.length() > 0) {
                    DebugLogger.i("FindImageModule", "截图成功: ${cacheFile.length()} 字节")
                    Uri.fromFile(cacheFile)
                } else {
                    DebugLogger.w("FindImageModule", "截图文件不存在或为空: exists=${cacheFile.exists()}, length=${cacheFile.length()}")
                    null
                }
            } catch (e: Exception) {
                DebugLogger.e("FindImageModule", "截图异常", e)
                null
            }
        }
    }
}

/**
 * 模板匹配结果
 */
data class MatchResult(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val diffRatio: Double  // 差异比例，越小越相似
) {
    val centerX: Int get() = x + width / 2
    val centerY: Int get() = y + height / 2
}

/**
 * 图片匹配器
 * 使用简单直接的像素差异比较算法
 */
object ImageMatcher {

    /**
     * 查找所有匹配位置
     * @param source 源图像（屏幕截图）
     * @param template 模板图像
     * @param maxDiffPercent 允许的最大差异百分比 (0.0-1.0)
     * @return 匹配结果列表，已按相似度排序（最相似的在前面）
     */
    fun findAll(source: Bitmap, template: Bitmap, maxDiffPercent: Double): List<MatchResult> {
        val sourceWidth = source.width
        val sourceHeight = source.height
        val templateWidth = template.width
        val templateHeight = template.height

        if (templateWidth > sourceWidth || templateHeight > sourceHeight) {
            return emptyList()
        }

        // 获取所有像素数据
        val sourcePixels = IntArray(sourceWidth * sourceHeight)
        source.getPixels(sourcePixels, 0, sourceWidth, 0, 0, sourceWidth, sourceHeight)

        val templatePixels = IntArray(templateWidth * templateHeight)
        template.getPixels(templatePixels, 0, templateWidth, 0, 0, templateWidth, templateHeight)

        val matches = mutableListOf<MatchResult>()

        // 搜索步长
        val step = maxOf(1, min(templateWidth, templateHeight) / 8)

        // 滑动窗口搜索
        var y = 0
        while (y <= sourceHeight - templateHeight) {
            var x = 0
            while (x <= sourceWidth - templateWidth) {
                // 快速检查：先检查四个角和中心点
                if (quickCheck(sourcePixels, sourceWidth, templatePixels, templateWidth, templateHeight, x, y, maxDiffPercent)) {
                    // 通过快速检查，进行完整匹配
                    val diffRatio = calculateDiffRatio(
                        sourcePixels, sourceWidth,
                        templatePixels, templateWidth, templateHeight,
                        x, y
                    )
                    if (diffRatio <= maxDiffPercent) {
                        // 精确定位
                        val refined = refinePosition(
                            sourcePixels, sourceWidth, sourceHeight,
                            templatePixels, templateWidth, templateHeight,
                            x, y, maxDiffPercent
                        )
                        if (refined != null) {
                            matches.add(refined)
                            // 跳过已匹配区域
                            x += templateWidth - step
                        }
                    }
                }
                x += step
            }
            y += step
        }

        // 非极大值抑制，并按相似度排序返回
        return nonMaxSuppression(matches, templateWidth, templateHeight)
    }

    /**
     * 快速检查：检查关键点是否匹配
     */
    private fun quickCheck(
        sourcePixels: IntArray,
        sourceWidth: Int,
        templatePixels: IntArray,
        templateWidth: Int,
        templateHeight: Int,
        offsetX: Int,
        offsetY: Int,
        maxDiffPercent: Double
    ): Boolean {
        // 检查5个关键点：四个角 + 中心
        val checkPoints = listOf(
            0 to 0,                                           // 左上
            templateWidth - 1 to 0,                           // 右上
            0 to templateHeight - 1,                          // 左下
            templateWidth - 1 to templateHeight - 1,          // 右下
            templateWidth / 2 to templateHeight / 2           // 中心
        )

        var totalDiff = 0
        val maxAllowedDiff = (255 * 3 * maxDiffPercent * 2).toInt()  // 放宽一点

        for ((tx, ty) in checkPoints) {
            val sourceIdx = (offsetY + ty) * sourceWidth + (offsetX + tx)
            val templateIdx = ty * templateWidth + tx

            val sp = sourcePixels[sourceIdx]
            val tp = templatePixels[templateIdx]

            val dr = abs(((sp shr 16) and 0xFF) - ((tp shr 16) and 0xFF))
            val dg = abs(((sp shr 8) and 0xFF) - ((tp shr 8) and 0xFF))
            val db = abs((sp and 0xFF) - (tp and 0xFF))

            totalDiff += dr + dg + db
        }

        return totalDiff <= maxAllowedDiff * checkPoints.size
    }

    /**
     * 计算差异比例
     * 使用采样方式加速计算
     */
    private fun calculateDiffRatio(
        sourcePixels: IntArray,
        sourceWidth: Int,
        templatePixels: IntArray,
        templateWidth: Int,
        templateHeight: Int,
        offsetX: Int,
        offsetY: Int
    ): Double {
        var totalDiff = 0L
        var sampleCount = 0

        // 采样步长，平衡精度和速度
        val sampleStep = maxOf(1, min(templateWidth, templateHeight) / 20)

        var ty = 0
        while (ty < templateHeight) {
            var tx = 0
            while (tx < templateWidth) {
                val sourceIdx = (offsetY + ty) * sourceWidth + (offsetX + tx)
                val templateIdx = ty * templateWidth + tx

                val sp = sourcePixels[sourceIdx]
                val tp = templatePixels[templateIdx]

                // RGB 差异
                val dr = abs(((sp shr 16) and 0xFF) - ((tp shr 16) and 0xFF))
                val dg = abs(((sp shr 8) and 0xFF) - ((tp shr 8) and 0xFF))
                val db = abs((sp and 0xFF) - (tp and 0xFF))

                totalDiff += dr + dg + db
                sampleCount++

                tx += sampleStep
            }
            ty += sampleStep
        }

        if (sampleCount == 0) return 1.0

        // 最大可能差异 = 255 * 3 * sampleCount
        val maxDiff = 255.0 * 3 * sampleCount
        return totalDiff / maxDiff
    }

    /**
     * 精确定位：在粗略位置周围搜索最佳匹配点
     */
    private fun refinePosition(
        sourcePixels: IntArray,
        sourceWidth: Int,
        sourceHeight: Int,
        templatePixels: IntArray,
        templateWidth: Int,
        templateHeight: Int,
        coarseX: Int,
        coarseY: Int,
        maxDiffPercent: Double
    ): MatchResult? {
        var bestX = coarseX
        var bestY = coarseY
        var bestDiff = Double.MAX_VALUE

        // 在周围小范围内搜索
        val searchRange = 3
        for (dy in -searchRange..searchRange) {
            for (dx in -searchRange..searchRange) {
                val x = coarseX + dx
                val y = coarseY + dy

                if (x < 0 || y < 0 || x > sourceWidth - templateWidth || y > sourceHeight - templateHeight) {
                    continue
                }

                val diff = calculateDiffRatio(
                    sourcePixels, sourceWidth,
                    templatePixels, templateWidth, templateHeight,
                    x, y
                )

                if (diff < bestDiff) {
                    bestDiff = diff
                    bestX = x
                    bestY = y
                }
            }
        }

        return if (bestDiff <= maxDiffPercent) {
            MatchResult(bestX, bestY, templateWidth, templateHeight, bestDiff)
        } else {
            null
        }
    }

    /**
     * 非极大值抑制
     * 返回按相似度排序的结果（差异最小的在前面）
     */
    private fun nonMaxSuppression(
        matches: List<MatchResult>,
        templateWidth: Int,
        templateHeight: Int
    ): List<MatchResult> {
        if (matches.isEmpty()) return emptyList()

        // 按差异比例升序排序（差异越小越好，即最相似的在前面）
        val sorted = matches.sortedBy { it.diffRatio }.toMutableList()
        val result = mutableListOf<MatchResult>()

        while (sorted.isNotEmpty()) {
            val best = sorted.removeAt(0)
            result.add(best)

            // 移除重叠的匹配
            sorted.removeAll { other ->
                val dx = abs(best.x - other.x)
                val dy = abs(best.y - other.y)
                dx < templateWidth * 0.8 && dy < templateHeight * 0.8
            }
        }

        // 返回时保持按相似度排序
        return result.sortedBy { it.diffRatio }
    }
}

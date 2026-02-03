// 文件: OpenCVImageMatcher.kt
package com.chaomixian.vflow.core.opencv

import android.graphics.Bitmap
import com.chaomixian.vflow.core.logging.DebugLogger
import com.chaomixian.vflow.core.workflow.module.interaction.ImageMatcher
import com.chaomixian.vflow.core.workflow.module.interaction.MatchResult
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc

/**
 * OpenCV 图片匹配器
 * 使用 OpenCV 的模板匹配算法，比原始 RGB 像素差异匹配更快、更准确
 */
object OpenCVImageMatcher {
    private const val TAG = "OpenCVImageMatcher"

    /**
     * 在源图片中查找所有与模板匹配的位置
     * @param source 源图片（屏幕截图）
     * @param template 模板图片
     * @param maxDiffPercent 允许的最大差异百分比 (0.0-1.0)
     * @return 匹配结果列表，已按相似度排序（最相似的在前面）
     */
    fun findAll(
        source: Bitmap,
        template: Bitmap,
        maxDiffPercent: Double
    ): List<MatchResult> {

        // 检查 OpenCV 是否已初始化
        if (!OpenCVManager.isInitialized) {
            DebugLogger.w(TAG, "OpenCV not initialized, using legacy matcher")
            return ImageMatcher.findAll(source, template, maxDiffPercent)
        }

        // 基本验证
        if (template.width > source.width || template.height > source.height) {
            return emptyList()
        }

        try {
            val sourceMat = Mat()
            val templateMat = Mat()

            Utils.bitmapToMat(source, sourceMat)
            Utils.bitmapToMat(template, templateMat)

            // 转灰度提升匹配效果
            val sourceGray = Mat()
            val templateGray = Mat()
            Imgproc.cvtColor(sourceMat, sourceGray, Imgproc.COLOR_RGB2GRAY)
            Imgproc.cvtColor(templateMat, templateGray, Imgproc.COLOR_RGB2GRAY)

            val resultCols = sourceGray.cols() - templateGray.cols() + 1
            val resultRows = sourceGray.rows() - templateGray.rows() + 1
            val result = Mat(resultRows, resultCols, CvType.CV_32FC1)

            // 使用归一化相关系数 (对光照变化鲁棒)
            Imgproc.matchTemplate(sourceGray, templateGray, result, Imgproc.TM_CCOEFF_NORMED)

            // 阈值：maxDiffPercent 0.10 (90% 相似) → threshold 0.90
            val threshold = 1.0 - maxDiffPercent

            val matches = findAllMatches(result, threshold, templateGray.width(), templateGray.height())

            // 释放资源
            sourceMat.release()
            templateMat.release()
            sourceGray.release()
            templateGray.release()
            result.release()

            DebugLogger.d(TAG, "Found ${matches.size} matches with OpenCV")
            return matches

        } catch (e: Exception) {
            DebugLogger.e(TAG, "OpenCV matching failed, falling back to legacy matcher", e)
            return ImageMatcher.findAll(source, template, maxDiffPercent)
        }
    }

    /**
     * 从匹配结果矩阵中提取所有匹配位置
     * 使用非极大值抑制避免重复检测
     */
    private fun findAllMatches(
        result: Mat,
        threshold: Double,
        templateWidth: Int,
        templateHeight: Int
    ): List<MatchResult> {

        val matches = mutableListOf<MatchResult>()
        val resultCopy = Mat()
        result.copyTo(resultCopy)

        while (true) {
            val minMaxLoc = Core.minMaxLoc(resultCopy)

            if (minMaxLoc.maxVal < threshold) break

            val loc = minMaxLoc.maxLoc
            val diffRatio = 1.0 - minMaxLoc.maxVal

            matches.add(MatchResult(
                x = loc.x.toInt(),
                y = loc.y.toInt(),
                width = templateWidth,
                height = templateHeight,
                diffRatio = diffRatio.coerceIn(0.0, 1.0)
            ))

            // 非极大值抑制：屏蔽周围区域
            val suppressRadiusX = (templateWidth * 0.5).toInt().coerceAtLeast(10)
            val suppressRadiusY = (templateHeight * 0.5).toInt().coerceAtLeast(10)

            val topLeft = Point(
                (loc.x - suppressRadiusX).coerceAtLeast(0.0),
                (loc.y - suppressRadiusY).coerceAtLeast(0.0)
            )
            val bottomRight = Point(
                (loc.x + suppressRadiusX).coerceAtMost((resultCopy.cols() - 1).toDouble()),
                (loc.y + suppressRadiusY).coerceAtMost((resultCopy.rows() - 1).toDouble())
            )

            val rect = Rect(topLeft, bottomRight)
            val mask = Mat.zeros(resultCopy.size(), CvType.CV_8UC1)
            Imgproc.rectangle(mask, rect.tl(), rect.br(), Scalar(255.0), -1)
            resultCopy.setTo(Scalar(0.0), mask)
            mask.release()

            if (Core.countNonZero(resultCopy) == 0) break
        }

        resultCopy.release()

        return matches.sortedBy { it.diffRatio }
    }
}

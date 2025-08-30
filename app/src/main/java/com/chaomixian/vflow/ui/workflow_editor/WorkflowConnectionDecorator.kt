package com.chaomixian.vflow.ui.workflow_editor

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.chaomixian.vflow.core.workflow.model.ActionStep

class WorkflowConnectionDecorator(private val steps: List<ActionStep>) : RecyclerView.ItemDecoration() {

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeWidth = 6f
        style = Paint.Style.STROKE
        alpha = 200 // 增加不透明度
        strokeCap = Paint.Cap.ROUND // 圆润的线条端点
        setShadowLayer(8f, 0f, 4f, Color.argb(100, 0, 0, 0)) // 添加阴影
    }

    override fun onDrawOver(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        val stepMap = steps.associateBy { it.id }

        // 遍历所有步骤，查找连接关系
        steps.forEachIndexed { index, currentStep ->
            currentStep.parameters.forEach { (inputId, value) ->
                if (value is String && value.startsWith("{{")) {
                    val parts = value.removeSurrounding("{{", "}}").split('.')
                    val sourceStepId = parts.getOrNull(0) ?: return@forEach
                    val sourceOutputId = parts.getOrNull(1) ?: return@forEach

                    val sourceStep = stepMap[sourceStepId]
                    if (sourceStep != null) {
                        val sourcePosition = steps.indexOf(sourceStep)

                        // 查找当前可见的 ViewHolder
                        val destView = parent.findViewHolderForAdapterPosition(index)?.itemView
                        val sourceView = parent.findViewHolderForAdapterPosition(sourcePosition)?.itemView

                        if (sourceView != null && destView != null) {
                            drawConnection(c, parent, sourceView, destView, sourceOutputId, inputId)
                        }
                    }
                }
            }
        }
    }

    private fun drawConnection(c: Canvas, parent: RecyclerView, sourceView: View, destView: View, sourceOutputId: String, destInputId: String) {
        // --- 核心修复：使用 Tag 来精确查找连接点 ---
        val startNode = sourceView.findViewWithTag<View?>("output_${(sourceView.tag as ActionStep).id}_${sourceOutputId}") ?: return
        val endNode = destView.findViewWithTag<View?>("input_${(destView.tag as ActionStep).id}_${destInputId}") ?: return

        // 获取连接点相对于 RecyclerView 的精确坐标
        val startPos = getRelativePos(startNode, parent)
        val endPos = getRelativePos(endNode, parent)

        val startX = startPos[0] + startNode.width / 2f
        val startY = startPos[1] + startNode.height / 2f
        val endX = endPos[0] + endNode.width / 2f
        val endY = endPos[1] + endNode.height / 2f

        val path = Path()
        path.moveTo(startX, startY)

        // 计算控制点，让曲线更平滑
        val controlPointX1 = startX + (endX - startX) * 0.5f
        val controlPointY1 = startY
        val controlPointX2 = startX + (endX - startX) * 0.5f
        val controlPointY2 = endY

        path.cubicTo(controlPointX1, controlPointY1, controlPointX2, controlPointY2, endX, endY)
        c.drawPath(path, linePaint)
    }

    // 辅助函数，获取 View 相对于其父 RecyclerView 的位置
    private fun getRelativePos(view: View, parent: RecyclerView): IntArray {
        val pos = IntArray(2)
        view.getGlobalVisibleRect(android.graphics.Rect())
        pos[0] = view.left
        pos[1] = view.top

        var currentParent = view.parent
        while (currentParent != parent && currentParent is View) {
            pos[0] += (currentParent as View).left
            pos[1] += (currentParent as View).top
            currentParent = currentParent.parent
        }
        return pos
    }
}
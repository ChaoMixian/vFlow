// main/java/com/chaomixian/vflow/ui/workflow_editor/WorkflowConnectionDecorator.kt

package com.chaomixian.vflow.ui.workflow_editor

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.chaomixian.vflow.core.workflow.model.ActionStep

class WorkflowConnectionDecorator(private val steps: List<ActionStep>) : RecyclerView.ItemDecoration() {

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeWidth = 6f
        style = Paint.Style.STROKE
        alpha = 200
        strokeCap = Paint.Cap.ROUND
        setShadowLayer(8f, 0f, 4f, Color.argb(100, 0, 0, 0))
    }

    override fun onDrawOver(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        val stepMap = steps.associateBy { it.id }

        steps.forEachIndexed { index, currentStep ->
            currentStep.parameters.forEach { (inputId, value) ->
                if (value is String && value.startsWith("{{")) {
                    val parts = value.removeSurrounding("{{", "}}").split('.')
                    val sourceStepId = parts.getOrNull(0) ?: return@forEach
                    val sourceOutputId = parts.getOrNull(1) ?: return@forEach

                    val sourceStep = stepMap[sourceStepId]
                    if (sourceStep != null) {
                        val sourcePosition = steps.indexOf(sourceStep)
                        if (sourcePosition == -1 || sourcePosition >= index) return@forEach

                        val destView = parent.findViewHolderForAdapterPosition(index)?.itemView
                        val sourceView = parent.findViewHolderForAdapterPosition(sourcePosition)?.itemView

                        if (sourceView != null && destView != null) {
                            drawConnection(c, parent, sourceView, destView, sourceStep.id, sourceOutputId, currentStep.id, inputId)
                        }
                    }
                }
            }
        }
    }

    private fun drawConnection(c: Canvas, parent: RecyclerView, sourceView: View, destView: View, sourceStepId: String, sourceOutputId: String, destStepId: String, destInputId: String) {
        val startNode = sourceView.findViewWithTag<View?>("output_${sourceStepId}_${sourceOutputId}") ?: return
        val endNode = destView.findViewWithTag<View?>("input_${destStepId}_${destInputId}") ?: return

        val startPos = getRelativePos(startNode, parent)
        val endPos = getRelativePos(endNode, parent)

        // 如果节点不可见（例如在滚动之外），坐标可能为0，此时不绘制
        if (startPos[0] == 0 && startPos[1] == 0) return
        if (endPos[0] == 0 && endPos[1] == 0) return


        val startX = startPos[0] + startNode.width / 2f
        val startY = startPos[1] + startNode.height / 2f
        val endX = endPos[0] + endNode.width / 2f
        val endY = endPos[1] + endNode.height / 2f

        val path = Path()
        path.moveTo(startX, startY)

        val controlPointOffset = 60f
        val controlPointX1 = startX + controlPointOffset
        val controlPointY1 = startY
        val controlPointX2 = endX + controlPointOffset
        val controlPointY2 = endY

        path.cubicTo(controlPointX1, controlPointY1, controlPointX2, controlPointY2, endX, endY)
        c.drawPath(path, linePaint)
    }

    /**
     * 获取视图在父视图中的相对坐标计算方法。
     * 它计算一个视图相对于其父视图（这里是RecyclerView）的绘制位置。
     */
    private fun getRelativePos(view: View, parent: RecyclerView): IntArray {
        val pos = IntArray(2)
        val rect = Rect()
        view.getDrawingRect(rect)
        // offsetDescendantRectToMyCoords 将子视图的矩形坐标转换为父视图的坐标
        parent.offsetDescendantRectToMyCoords(view, rect)
        pos[0] = rect.left
        pos[1] = rect.top
        return pos
    }
}
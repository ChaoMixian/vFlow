// 文件: main/java/com/chaomixian/vflow/ui/workflow_editor/pill/PillVariableResolver.kt
// 描述: Pill变量解析器，统一处理变量解析和显示名称获取
package com.chaomixian.vflow.ui.workflow_editor.pill

import android.content.Context
import androidx.core.content.ContextCompat
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.VariableInfo
import com.chaomixian.vflow.core.module.ModuleRegistry
import com.chaomixian.vflow.core.workflow.model.ActionStep

/**
 * Pill变量解析器（UI层）
 *
 * 负责解析变量引用并获取显示信息（显示名称、颜色、属性名等）。
 * 提取自旧的PillRenderer，职责更加单一明确。
 */
object PillVariableResolver {

    /**
     * 解析后的变量信息
     *
     * @property displayName 用户可见的显示名称
     * @property color Pill应该使用的颜色Int值
     * @property propertyName 可选的属性名（如"宽度"、"高度"等）
     */
    data class ResolvedInfo(
        val displayName: String,
        val color: Int,
        val propertyName: String? = null
    )

    /**
     * 解析变量引用并获取显示信息
     *
     * 此方法：
     * 1. 使用VariableInfo解析变量引用
     * 2. 获取源模块的颜色
     * 3. 解析属性名（如果有）
     * 4. 构建用户友好的显示名称
     *
     * @param context Android上下文
     * @param variableReference 变量引用字符串（如"{{step1.output}}"或"[[varName]]"）
     * @param allSteps 工作流中的所有步骤
     * @return 解析后的信息，如果解析失败返回null
     */
    fun resolveVariable(
        context: Context,
        variableReference: String,
        allSteps: List<ActionStep>
    ): ResolvedInfo? {
        // 解析属性名
        val propertyName = resolvePropertyName(variableReference)
        
        // 如果是魔法变量且有属性，使用新的方法获取VariableInfo
        val varInfo = if (propertyName != null && variableReference.startsWith("{{")) {
            val content = variableReference.removeSurrounding("{{", "}}")
            val parts = content.split('.')
            if (parts.size >= 2) {
                VariableInfo.fromMagicVariableWithProperty(parts[0], parts[1], propertyName, allSteps)
            } else null
        } else {
            VariableInfo.fromReference(variableReference, allSteps)
        } ?: return null

        // 获取源步骤和模块
        val sourceStep = varInfo.sourceStepId?.let { stepId ->
            allSteps.find { it.id == stepId }
        }
        val sourceModule = sourceStep?.let { ModuleRegistry.getModule(it.moduleId) }

        // 获取颜色
        val color = if (sourceModule != null) {
            PillTheme.getColor(context, PillTheme.getCategoryColor(sourceModule.metadata.category))
        } else {
            PillTheme.getColor(context, R.color.variable_pill_color)
        }

        // 获取步骤序号（用于标识来源）
        val stepIndex = varInfo.sourceStepId?.let { stepId ->
            allSteps.indexOfFirst { it.id == stepId }
        } ?: -1

        // 构建显示名称（使用本地化属性名）
        val stepPrefix = if (stepIndex >= 0) "#$stepIndex " else ""
        val displayName = if (propertyName != null) {
            val localizedPropName = varInfo.getPropertyDisplayName(context, propertyName)
            "${stepPrefix}${varInfo.sourceName} 的 $localizedPropName"
        } else {
            "${stepPrefix}${varInfo.sourceName}"
        }

        return ResolvedInfo(displayName, color, propertyName)
    }

    /**
     * 解析属性名
     *
     * 从变量引用中提取属性名。
     *
     * 示例：
     * - "{{step1.output.width}}" -> "width"
     * - "[[imageVar.height]]" -> "height"
     *
     * @param variableReference 变量引用字符串
     * @return 属性名，如果没有属性则返回null
     */
    private fun resolvePropertyName(variableReference: String): String? {
        // 提取变量引用内容
        val content = when {
            variableReference.startsWith("[[") -> {
                variableReference.removeSurrounding("[[", "]]")
            }
            variableReference.startsWith("{{") -> {
                variableReference.removeSurrounding("{{", "}}")
            }
            else -> return null
        }

        // 解析属性路径
        val parts = content.split('.')
        val propName = when {
            // 命名变量：[[varName.property]]
            variableReference.startsWith("[[") && parts.size > 1 -> parts[1]
            // 魔法变量：{{stepId.outputName.property}}
            variableReference.startsWith("{{") && parts.size > 2 -> parts[2]
            else -> null
        }

        return propName
    }

    /**
     * 解析属性名（带VariableInfo参数的版本，向后兼容）
     *
     * @param variableReference 变量引用字符串
     * @param varInfo 变量信息对象
     * @return 属性名，如果没有属性则返回null
     */
    private fun resolvePropertyName(
        variableReference: String,
        varInfo: VariableInfo
    ): String? {
        return resolvePropertyName(variableReference)
    }
}

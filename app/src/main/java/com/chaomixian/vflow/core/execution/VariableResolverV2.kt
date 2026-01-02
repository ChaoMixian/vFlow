// 文件: main/java/com/chaomixian/vflow/core/execution/VariableResolverV2.kt
package com.chaomixian.vflow.core.execution

import com.chaomixian.vflow.core.types.VObject
import com.chaomixian.vflow.core.types.VObjectFactory
import com.chaomixian.vflow.core.types.basic.VNull
import com.chaomixian.vflow.core.types.parser.TemplateParser
import com.chaomixian.vflow.core.types.parser.TemplateSegment
import java.util.regex.Pattern

/**
 * 第二代变量解析器。
 * 支持对象属性访问 (例如 {{image.width}}) 和类型转换。
 */
object VariableResolverV2 {

    /**
     * 解析文本中的所有变量，返回最终字符串。
     * 适用于: "图片宽度是 {{img.width}} 像素" -> "图片宽度是 1080 像素"
     */
    fun resolve(text: String, context: ExecutionContext): String {
        if (text.isEmpty()) return ""

        val parser = TemplateParser(text)
        val segments = parser.parse()
        val sb = StringBuilder()

        for (segment in segments) {
            when (segment) {
                is TemplateSegment.Text -> sb.append(segment.content)
                is TemplateSegment.Variable -> {
                    val obj = resolveVariableObject(segment, context)
                    sb.append(obj.asString())
                }
            }
        }
        return sb.toString()
    }

    /**
     * 解析单个变量引用，返回对象本身。
     * 适用于: 输入参数需要 Image 对象而不是字符串时。
     * 如果文本只包含一个变量且无其他内容 (例如 "{{step1.image}}")，返回该对象；否则返回 String 包装。
     */
    fun resolveValue(text: String, context: ExecutionContext): Any? {
        if (text.isEmpty()) return null

        val parser = TemplateParser(text)
        val segments = parser.parse()

        // 如果只有一个 Variable 类型的片段，直接返回该对象 (保留类型信息)
        if (segments.size == 1 && segments[0] is TemplateSegment.Variable) {
            val segment = segments[0] as TemplateSegment.Variable
            val vObj = resolveVariableObject(segment, context)
            // 返回 vObj 还是 vObj.raw?
            // 为了兼容旧系统，目前返回 raw，但未来应全面切换到 VObject
            // 这里为了让旧模块(如 SaveImage)能拿到 ImageVariable/String，我们返回 raw
            return vObj.raw ?: vObj.asString()
        }

        // 否则解析为字符串
        return resolve(text, context)
    }

    /**
     * 核心寻址逻辑：Path -> VObject
     */
    private fun resolveVariableObject(
        segment: TemplateSegment.Variable,
        context: ExecutionContext
    ): VObject {
        val path = segment.path
        if (path.isEmpty()) return VNull

        val rootKey = path[0]
        var currentObj: VObject = VNull

        // 1. 寻找根对象 (Root)
        if (segment.isNamedVariable) {
            // [[varName]]: 查 namedVariables
            val raw = context.namedVariables[rootKey]
            currentObj = VObjectFactory.from(raw)
        } else {
            // {{stepId.outputId}}: 查 stepOutputs
            // 路径通常是 [stepId, outputId, prop1, prop2...]
            if (path.size >= 2) {
                val stepId = path[0]
                val outputId = path[1]
                val rawOutput = context.stepOutputs[stepId]?.get(outputId)
                if (rawOutput != null) {
                    currentObj = VObjectFactory.from(rawOutput)
                    // 消耗掉前两个路径节点 (stepId, outputId)
                    // 接下来的循环从 index 2 开始
                    return traverseProperties(currentObj, path, 2)
                }
            }

            // 兼容旧逻辑：如果只是 {{key}} 且在 magicVariables 里有 (例如循环变量 index)
            if (currentObj is VNull && context.magicVariables.containsKey(rootKey)) {
                currentObj = VObjectFactory.from(context.magicVariables[rootKey])
                // 消耗掉第1个节点，从 index 1 开始遍历属性
                return traverseProperties(currentObj, path, 1)
            }
        }

        // 如果找不到根对象
        if (currentObj is VNull) {
            return VObjectFactory.from("{${segment.rawExpression}}") // 保持原样或返回空
        }

        // 命名变量属性遍历 (从 index 1 开始)
        return traverseProperties(currentObj, path, 1)
    }

    /**
     * 递归属性访问
     */
    private fun traverseProperties(root: VObject, path: List<String>, startIndex: Int): VObject {
        var current = root
        for (i in startIndex until path.size) {
            val propName = path[i]
            val next = current.getProperty(propName)
            if (next == null || next is VNull) {
                return VNull // 属性链断裂
            }
            current = next
        }
        return current
    }
}
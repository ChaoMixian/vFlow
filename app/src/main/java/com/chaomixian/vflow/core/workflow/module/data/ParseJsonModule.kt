// 文件: main/java/com/chaomixian/vflow/core/workflow/module/data/ParseJsonModule.kt
package com.chaomixian.vflow.core.workflow.module.data

import android.content.Context
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.execution.VariableResolver
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.types.VObjectFactory
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VList
import com.chaomixian.vflow.core.types.basic.VNull
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.ui.workflow_editor.PillUtil
import com.chaomixian.vflow.ui.workflow_editor.RichTextUIProvider
import org.json.JSONArray
import org.json.JSONObject

/**
 * 解析 JSON 模块
 * 支持类似 iOS 快捷指令的路径语法，如 user.name、users.0.name
 */
class ParseJsonModule : BaseModule() {

    override val id = "vflow.data.parse_json"
    override val metadata = ActionMetadata(
        nameStringRes = R.string.module_vflow_data_parse_json_name,
        descriptionStringRes = R.string.module_vflow_data_parse_json_desc,
        name = "解析 JSON",
        description = "从 JSON 文本中提取数据，支持路径语法（如 user.name、users.0.name）",
        iconRes = R.drawable.rounded_analytics_24,
        category = "数据"
    )

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "json",
            name = "JSON 文本",
            staticType = ParameterType.STRING,
            defaultValue = "",
            acceptsMagicVariable = true,
            acceptedMagicVariableTypes = setOf(VTypeRegistry.STRING.id),
            supportsRichText = true,
            nameStringRes = R.string.param_vflow_data_parse_json_json_name
        ),
        InputDefinition(
            id = "path",
            name = "路径",
            staticType = ParameterType.STRING,
            defaultValue = "",
            acceptsMagicVariable = true,
            acceptedMagicVariableTypes = setOf(VTypeRegistry.STRING.id),
            supportsRichText = false,
            nameStringRes = R.string.param_vflow_data_parse_json_path_name
        )
    )

    override val uiProvider: ModuleUIProvider? = RichTextUIProvider("json")

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition(
            id = "first_value",
            name = "第一个匹配值",
            typeName = VTypeRegistry.ANY.id,
            nameStringRes = R.string.output_vflow_data_parse_json_first_value_name
        ),
        OutputDefinition(
            id = "all_values",
            name = "所有匹配值",
            typeName = VTypeRegistry.LIST.id,
            nameStringRes = R.string.output_vflow_data_parse_json_all_values_name
        )
    )

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val inputs = getInputs()
        val rawJson = step.parameters["json"]?.toString() ?: ""

        // 路径 pill 始终显示
        val pathPill = PillUtil.createPillFromParam(
            step.parameters["path"],
            inputs.find { it.id == "path" }
        )

        // 如果 JSON 是复杂内容（包含变量或长文本），只显示路径 pill
        if (VariableResolver.isComplex(rawJson)) {
            return PillUtil.buildSpannable(
                context,
                "使用",
                pathPill,
                "解析 JSON"
            )
        }

        // 简单 JSON：显示两个 pill
        val jsonPill = PillUtil.createPillFromParam(
            step.parameters["json"],
            inputs.find { it.id == "json" }
        )

        return PillUtil.buildSpannable(
            context,
            "使用",
            pathPill,
            "解析 Json",
            jsonPill
        )
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val rawJson = context.getVariableAsString("json", "")
        val json = VariableResolver.resolve(rawJson, context)
        val path = context.getVariableAsString("path", "")

        if (json.isBlank()) {
            return ExecutionResult.Failure(
                "参数错误",
                "JSON 文本不能为空"
            )
        }

        if (path.isBlank()) {
            return ExecutionResult.Failure(
                "参数错误",
                "路径不能为空"
            )
        }

        onProgress(ProgressUpdate("正在解析 JSON..."))

        try {
            // 解析 JSON
            val parsed = when {
                json.trim().startsWith("{") -> JSONObject(json)
                json.trim().startsWith("[") -> JSONArray(json)
                else -> {
                    return ExecutionResult.Failure(
                        "解析失败",
                        "JSON 格式错误：必须以 { 或 [ 开头"
                    )
                }
            }

            // 解析路径并获取值
            val pathSegments = parsePath(path)
            val result = navigateJson(parsed, pathSegments)

            // 获取所有匹配的原始值
            val allRawValues = result.second
            val firstRawValue = result.first

            // 转换为 VObject
            val firstValue = VObjectFactory.from(firstRawValue)
            val allVObjects = allRawValues.map { VObjectFactory.from(it) }

            onProgress(ProgressUpdate("解析完成，找到 ${allRawValues.size} 个匹配项"))

            return ExecutionResult.Success(mapOf(
                "first_value" to firstValue,
                "all_values" to VList(allVObjects)
            ))

        } catch (e: Exception) {
            return ExecutionResult.Failure(
                "解析失败",
                "JSON 解析错误: ${e.message}"
            )
        }
    }

    /**
     * 解析路径字符串
     * 支持格式：
     * - user.name
     * - users.0.name
     * - users.[0].name
     */
    private fun parsePath(path: String): List<PathSegment> {
        val segments = mutableListOf<PathSegment>()
        val parts = path.split(".")

        parts.forEach { part ->
            when {
                // 处理数组索引：users.[0] 或 users.0
                part.matches(Regex("\\[\\d+\\]")) -> {
                    val index = part.removeSurrounding("[", "]").toInt()
                    segments.add(PathSegment.ArrayIndex(index))
                }
                part.matches(Regex("\\d+")) -> {
                    val index = part.toInt()
                    segments.add(PathSegment.ArrayIndex(index))
                }
                // 通配符：*
                part == "*" -> {
                    segments.add(PathSegment.Wildcard)
                }
                // 普通字段名
                else -> {
                    segments.add(PathSegment.Field(part))
                }
            }
        }

        return segments
    }

    /**
     * 根据路径片段导航 JSON
     * 返回：Pair(第一个匹配值, 所有匹配值列表)
     */
    private tailrec fun navigateJson(
        current: Any?,
        pathSegments: List<PathSegment>,
        index: Int = 0,
        collectAll: Boolean = false
    ): Pair<Any?, List<Any>> {
        if (index >= pathSegments.size) {
            // 路径遍历完成
            return when (current) {
                is JSONArray -> {
                    val list = (0 until current.length()).map { current.get(it) }
                    list.firstOrNull() to list
                }
                else -> current to listOfNotNull(current)
            }
        }

        val segment = pathSegments[index]
        val remainingSegments = pathSegments.subList(index + 1, pathSegments.size)

        return when (segment) {
            is PathSegment.Field -> {
                val value = when (current) {
                    is JSONObject -> {
                        if (current.has(segment.name)) {
                            current.get(segment.name)
                        } else null
                    }
                    else -> null
                }
                if (value != null) {
                    navigateJson(value, pathSegments, index + 1, collectAll)
                } else {
                    null to emptyList()
                }
            }
            is PathSegment.ArrayIndex -> {
                val value = when (current) {
                    is JSONArray -> {
                        if (segment.index < current.length()) {
                            current.get(segment.index)
                        } else null
                    }
                    else -> null
                }
                if (value != null) {
                    navigateJson(value, pathSegments, index + 1, collectAll)
                } else {
                    null to emptyList()
                }
            }
            is PathSegment.Wildcard -> {
                when (current) {
                    is JSONArray -> {
                        // 通配符：收集数组中所有元素的后续路径匹配结果
                        val allResults = mutableListOf<Any>()
                        var firstResult: Any? = null

                        for (i in 0 until current.length()) {
                            val item = current.get(i)
                            val (first, all) = navigateJson(item, pathSegments, index + 1, true)
                            if (first != null && firstResult == null) {
                                firstResult = first
                            }
                            allResults.addAll(all)
                        }

                        firstResult to allResults
                    }
                    is JSONObject -> {
                        // 通配符：收集对象中所有值的后续路径匹配结果
                        val allResults = mutableListOf<Any>()
                        var firstResult: Any? = null

                        val keys = current.keys()
                        while (keys.hasNext()) {
                            val key = keys.next()
                            val item = current.get(key)
                            val (first, all) = navigateJson(item, pathSegments, index + 1, true)
                            if (first != null && firstResult == null) {
                                firstResult = first
                            }
                            allResults.addAll(all)
                        }

                        firstResult to allResults
                    }
                    else -> null to emptyList()
                }
            }
        }
    }

    /**
     * 路径片段类型
     */
    private sealed class PathSegment {
        data class Field(val name: String) : PathSegment()
        data class ArrayIndex(val index: Int) : PathSegment()
        object Wildcard : PathSegment()
    }
}

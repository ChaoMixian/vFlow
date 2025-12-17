// 文件: main/java/com/chaomixian/vflow/core/workflow/module/scripted/ModuleManifest.kt
package com.chaomixian.vflow.core.workflow.module.scripted

import com.chaomixian.vflow.core.module.InputDefinition
import com.chaomixian.vflow.core.module.OutputDefinition
import com.chaomixian.vflow.core.module.ParameterType
import com.google.gson.annotations.SerializedName

/**
 * 模块清单文件 (manifest.json) 的数据模型。
 * 将列表字段改为可空，防止 Gson 在 JSON 缺少字段时注入 null 导致崩溃。
 */
data class ModuleManifest(
    val id: String,
    val name: String,
    val description: String,
    val category: String = "用户脚本",
    val author: String = "Unknown",
    val version: String = "Unknown",
    val inputs: List<JsonInput>? = null,   // 改为可空
    val outputs: List<JsonOutput>? = null, // 改为可空
    val permissions: List<String>? = null  // 改为可空
)

/**
 * JSON 中定义的输入参数。
 */
data class JsonInput(
    val id: String,
    val name: String,
    val type: String, // "string", "number", "boolean", "enum", "any"
    val defaultValue: Any? = null,
    val options: List<String>? = null, // 改为可空，防止 NPE
    @SerializedName("magic_variable") val acceptsMagicVariable: Boolean = true
) {
    fun toInputDefinition(): InputDefinition {
        val staticType = when (type.lowercase()) {
            "number" -> ParameterType.NUMBER
            "boolean" -> ParameterType.BOOLEAN
            "enum" -> ParameterType.ENUM
            "any" -> ParameterType.ANY
            else -> ParameterType.STRING
        }
        return InputDefinition(
            id = id,
            name = name,
            staticType = staticType,
            defaultValue = defaultValue,
            // [核心修复] 如果 options 为 null，则使用空列表
            options = options ?: emptyList(),
            acceptsMagicVariable = acceptsMagicVariable,
            acceptsNamedVariable = acceptsMagicVariable
        )
    }
}

/**
 * JSON 中定义的输出参数。
 */
data class JsonOutput(
    val id: String,
    val name: String,
    val type: String
) {
    fun toOutputDefinition(): OutputDefinition {
        val typeName = when (type.lowercase()) {
            "number" -> "vflow.type.number"
            "boolean" -> "vflow.type.boolean"
            "list" -> "vflow.type.list"
            "dictionary" -> "vflow.type.dictionary"
            "image" -> "vflow.type.image"
            else -> "vflow.type.text"
        }
        return OutputDefinition(id, name, typeName)
    }
}
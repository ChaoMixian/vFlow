package com.chaomixian.vflow.core.utils

import com.chaomixian.vflow.core.module.ActionModule
import com.chaomixian.vflow.core.module.ParameterType
import org.json.JSONArray
import org.json.JSONObject

object ToolSchemaGenerator {

    /**
     * 将 vFlow 模块转换为 OpenAI Function Definition JSON。
     */
    fun generateSchema(module: ActionModule): JSONObject {
        val properties = JSONObject()
        val required = JSONArray()

        module.getInputs().forEach { input ->
            // 跳过隐藏参数 (如 show_advanced, execution_mode 等配置项，这些通常由用户预设，不应由 AI 决定)
            if (input.isHidden) return@forEach

            val prop = JSONObject()

            // 类型映射
            when (input.staticType) {
                ParameterType.ENUM -> {
                    prop.put("type", "string")
                    prop.put("enum", JSONArray(input.options))
                }
                ParameterType.NUMBER -> prop.put("type", "number")
                ParameterType.BOOLEAN -> prop.put("type", "boolean")
                else -> prop.put("type", "string")
            }

            // 描述
            prop.put("description", input.name)

            properties.put(input.id, prop)
            required.put(input.id)
        }

        return JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                // 使用模块 ID 作为函数名，并将点号替换为下划线，符合 OpenAI 命名规范
                put("name", module.id.replace(".", "_"))
                put("description", module.metadata.description)
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", properties)
                    put("required", required)
                })
            })
        }
    }
}
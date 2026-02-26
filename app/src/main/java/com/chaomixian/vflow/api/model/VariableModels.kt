package com.chaomixian.vflow.api.model

/**
 * 魔法变量引用
 */
data class MagicVariableReference(
    val key: String,
    val label: String,
    val type: String,
    val stepId: String?,
    val stepName: String?,
    val outputId: String?,
    val outputName: String?,
    val category: String
)

/**
 * 系统变量
 */
data class SystemVariable(
    val key: String,
    val label: String,
    val type: String,
    val description: String
)

/**
 * 魔法变量列表响应
 */
data class MagicVariablesResponse(
    val magicVariables: List<MagicVariableReference>,
    val systemVariables: List<SystemVariable>
)

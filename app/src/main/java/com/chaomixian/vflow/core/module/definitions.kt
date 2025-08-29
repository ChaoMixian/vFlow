package com.chaomixian.vflow.core.module

// 参数的定义，用于动态生成UI
data class ParameterDefinition(
    val id: String, // e.g., "targetText"
    val name: String, // e.g., "目标文本"
    val type: ParameterType,
    val defaultValue: Any? = null,
    val options: List<String> = emptyList() // 用于 ENUM 类型
)

enum class ParameterType {
    STRING,
    NUMBER,
    BOOLEAN,
    ENUM, // 用于下拉菜单
    NODE_ID,
    COORDINATE
}

// 新增：定义模块在逻辑块中的角色
enum class BlockType {
    NONE,           // 普通原子模块
    BLOCK_START,    // 块的开始 (如 '循环', '如果')
    BLOCK_MIDDLE,   // 块的中间 (如 '否则')
    BLOCK_END       // 块的结束 (虚拟模块，如 '结束循环')
}

// 新增：用于描述模块的块行为
data class BlockBehavior(
    val type: BlockType,
    // 用于配对，例如 'if' 块的所有部分(if, else, endif)都有相同的 pairingId
    val pairingId: String? = null
)
// 文件: main/java/com/chaomixian/vflow/core/execution/VariableInfo.kt
package com.chaomixian.vflow.core.execution

import android.content.Context
import com.chaomixian.vflow.core.module.ModuleRegistry
import com.chaomixian.vflow.core.module.isMagicVariable
import com.chaomixian.vflow.core.module.isNamedVariable
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.workflow.model.ActionStep

/**
 * 变量类型枚举（用户可见的中文类型名）
 */
enum class VariableType(val displayName: String, val typeId: String) {
    STRING("文本", "vflow.type.string"),
    NUMBER("数字", "vflow.type.number"),
    BOOLEAN("布尔", "vflow.type.boolean"),
    DICTIONARY("字典", "vflow.type.dictionary"),
    LIST("列表", "vflow.type.list"),
    IMAGE("图像", "vflow.type.image"),
    COORDINATE("坐标", "vflow.type.coordinate");

    companion object {
        /**
         * 从显示名称获取类型枚举
         */
        fun fromDisplayName(displayName: String): VariableType? {
            return values().find { it.displayName == displayName }
        }

        /**
         * 从 typeId 获取类型枚举
         */
        fun fromTypeId(typeId: String): VariableType? {
            return values().find { it.typeId == typeId }
        }
    }
}

/**
 * 变量信息类
 * 统一封装命名变量和魔法变量的元数据获取逻辑
 *
 * 这个类的作用：
 * 1. 消除 PillRenderer 和 WorkflowEditorActivity 中的重复逻辑
 * 2. 提供统一的变量信息查询接口
 * 3. 分离业务逻辑和 UI 渲染
 *
 * @param sourceName 变量名（命名变量）或输出名（魔法变量）
 * @param typeId VTypeRegistry 类型 ID
 * @param sourceModuleId 创建该变量的模块 ID
 * @param sourceStepId 创建该变量的步骤 ID（如果有）
 */
data class VariableInfo(
    val sourceName: String,
    val typeId: String,
    val sourceModuleId: String,
    val sourceStepId: String? = null
) {
    /**
     * 获取属性的本地化显示名称
     * @param context Android上下文
     * @param propertyName 属性名称（如"width"）
     * @return 本地化的显示名称（如"宽度"或"Width"）
     */
    fun getPropertyDisplayName(context: Context, propertyName: String): String {
        val type = VTypeRegistry.getType(typeId)
        val propDef = type.properties.find { it.name == propertyName }
        return propDef?.getLocalizedName(context) ?: propertyName
    }

    /**
     * 获取属性的中文显示名称（向后兼容方法）
     * @deprecated 使用 getPropertyDisplayName(context, propertyName) 代替
     */
    @Deprecated("Use getPropertyDisplayName(context, propertyName) instead", ReplaceWith("getPropertyDisplayName(context, propertyName)"))
    fun getPropertyDisplayName(propertyName: String): String {
        val type = VTypeRegistry.getType(typeId)
        val propDef = type.properties.find { it.name == propertyName }
        return propDef?.displayName ?: propertyName
    }

    /**
     * 检查属性是否存在
     */
    fun hasProperty(propertyName: String): Boolean {
        val type = VTypeRegistry.getType(typeId)
        return type.properties.any { it.name == propertyName }
    }

    /**
     * 获取所有可用属性
     */
    fun getProperties(): List<com.chaomixian.vflow.core.types.VPropertyDef> {
        val type = VTypeRegistry.getType(typeId)
        return type.properties
    }

    companion object {
        /**
         * 从命名变量引用创建 VariableInfo
         * @return 如果找到对应的 CreateVariableModule 步骤则返回 VariableInfo，否则返回 null
         */
        fun fromNamedVariable(varName: String, allSteps: List<ActionStep>): VariableInfo? {
            val createVarStep = allSteps.find {
                it.moduleId == "vflow.variable.create" &&
                (it.parameters["variableName"] as? String) == varName
            } ?: return null

            val varType = createVarStep.parameters["type"] as? String ?: "文本"
            val typeEnum = VariableType.fromDisplayName(varType) ?: return null

            return VariableInfo(
                sourceName = varName,
                typeId = typeEnum.typeId,
                sourceModuleId = "vflow.variable.create",
                sourceStepId = createVarStep.id
            )
        }

        /**
         * 从魔法变量引用创建 VariableInfo
         * @return 如果找到对应的步骤和输出则返回 VariableInfo，否则返回 null
         */
        fun fromMagicVariable(stepId: String, outputId: String, allSteps: List<ActionStep>): VariableInfo? {
            val sourceStep = allSteps.find { it.id == stepId } ?: return null
            val sourceModule = ModuleRegistry.getModule(sourceStep.moduleId) ?: return null
            // 使用 getDynamicOutputs 而不是 getOutputs，以获取动态类型信息
            val outputDefs = sourceModule.getDynamicOutputs(sourceStep, allSteps)
            val outputDef = outputDefs.find { it.id == outputId } ?: return null

            // 如果输出有 listElementType，则使用它作为 typeId
            // 这样对于 ForEach 的"重复项目"，typeId 会是实际的元素类型（如 SCREEN_ELEMENT）
            val actualTypeId = outputDef.listElementType ?: outputDef.typeName

            return VariableInfo(
                sourceName = outputDef.name,
                typeId = actualTypeId,
                sourceModuleId = sourceStep.moduleId,
                sourceStepId = stepId
            )
        }

        /**
         * 从魔法变量引用创建 VariableInfo，支持属性访问时的类型修正
         * @return 如果找到对应的步骤和输出则返回 VariableInfo，否则返回 null
         */
        fun fromMagicVariableWithProperty(stepId: String, outputId: String, propertyName: String?, allSteps: List<ActionStep>): VariableInfo? {
            val sourceStep = allSteps.find { it.id == stepId } ?: return null
            val sourceModule = ModuleRegistry.getModule(sourceStep.moduleId) ?: return null
            // 使用 getDynamicOutputs 而不是 getOutputs，以获取动态类型信息
            val outputDefs = sourceModule.getDynamicOutputs(sourceStep, allSteps)
            val outputDef = outputDefs.find { it.id == outputId } ?: return null

            // 检查是否是列表类型且正在访问列表属性
            val isListProperty = propertyName != null && outputDef.listElementType != null && isListProperty(propertyName)
            
            // 如果是列表属性访问，使用列表类型；否则使用原来的逻辑
            val actualTypeId = if (isListProperty) {
                outputDef.typeName // 使用列表类型而不是元素类型
            } else {
                outputDef.listElementType ?: outputDef.typeName // 原来的逻辑
            }

            return VariableInfo(
                sourceName = outputDef.name,
                typeId = actualTypeId,
                sourceModuleId = sourceStep.moduleId,
                sourceStepId = stepId
            )
        }

        /**
         * 检查属性是否是列表属性
         */
        private fun isListProperty(propertyName: String): Boolean {
            val listType = VTypeRegistry.getType(VTypeRegistry.LIST.id)
            return listType.properties.any { it.name == propertyName }
        }

        /**
         * 从任意变量引用字符串创建 VariableInfo
         * 支持 [[varName]] 和 [[varName.prop]] 以及 {{stepId.outputId}} 和 {{stepId.outputId.prop}}
         *
         * @return VariableInfo，如果解析失败则返回 null
         */
        fun fromReference(variableRef: String, allSteps: List<ActionStep>): VariableInfo? {
            return when {
                variableRef.isNamedVariable() -> {
                    val content = variableRef.removeSurrounding("[[", "]]")
                    val varName = content.split('.').first()
                    fromNamedVariable(varName, allSteps)
                }
                variableRef.isMagicVariable() -> {
                    val content = variableRef.removeSurrounding("{{", "}}")
                    val parts = content.split('.')
                    if (parts.size >= 2) {
                        fromMagicVariable(parts[0], parts[1], allSteps)
                    } else null
                }
                else -> null
            }
        }
    }
}

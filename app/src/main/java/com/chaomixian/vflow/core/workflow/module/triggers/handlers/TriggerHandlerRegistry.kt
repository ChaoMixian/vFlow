// 文件: main/java/com/chaomixian/vflow/core/workflow/module/triggers/handlers/TriggerHandlerRegistry.kt
// 描述: 触发器处理器的中央注册表，用于解耦 TriggerService 与具体的处理器实现。
// 这里只注册自动触发器及其实现，比如手动触发器和分享触发器本质上是用户主动触发
// 就不考虑在内。
package com.chaomixian.vflow.core.workflow.module.triggers.handlers

import com.chaomixian.vflow.core.workflow.module.triggers.AppStartTriggerModule
import com.chaomixian.vflow.core.workflow.module.triggers.KeyEventTriggerModule

/**
 * 触发器处理器注册表。
 * 这是一个单例对象，在应用启动时收集所有可用的触发器处理器。
 * TriggerService 将从这里动态加载处理器，而不是硬编码创建它们。
 */
object TriggerHandlerRegistry {

    // 存储从“触发器模块ID”到“创建对应Handler实例的函数”的映射。
    // 使用函数（工厂模式）而不是实例，是为了支持懒加载等高级场景。
    private val handlerFactories = mutableMapOf<String, () -> ITriggerHandler>()

    /**
     * 初始化注册表，注册所有已知的触发器处理器。
     * 这个方法应该在应用启动时被调用。
     */
    fun initialize() {
        handlerFactories.clear()

        // 在这里注册所有可用的触发器处理器
        register(KeyEventTriggerModule().id) { KeyEventTriggerHandler() }
        register(AppStartTriggerModule().id) { AppStartTriggerHandler() }

    }

    /**
     * 注册一个触发器处理器。
     * @param triggerModuleId 处理器对应的触发器模块的ID。
     * @param factory 一个无参函数，调用时会返回一个新的处理器实例。
     */
    private fun register(triggerModuleId: String, factory: () -> ITriggerHandler) {
        if (handlerFactories.containsKey(triggerModuleId)) {
            // 在开发阶段打印警告，以防重复注册
            println("警告: 模块ID为 '$triggerModuleId' 的触发器处理器被重复注册。")
        }
        handlerFactories[triggerModuleId] = factory
    }

    /**
     * 获取用于创建指定处理器实例的工厂函数。
     * @param triggerModuleId 触发器模块的ID。
     * @return 返回一个可以创建处理器实例的函数，如果未找到则返回 null。
     */
    fun getHandlerFactory(triggerModuleId: String): (() -> ITriggerHandler)? {
        return handlerFactories[triggerModuleId]
    }

    /**
     * 获取所有已注册的处理器工厂。
     * @return 包含所有已注册工厂的Map。
     */
    fun getAllHandlerFactories(): Map<String, () -> ITriggerHandler> {
        return handlerFactories.toMap()
    }
}
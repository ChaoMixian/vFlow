package com.chaomixian.vflow.core.execution

import com.chaomixian.vflow.services.AccessibilityService
import kotlin.reflect.KClass

/**
 * 一个类型安全的服务容器，用于在工作流执行期间存放和提供各种服务实例。
 *
 * 这种设计将执行上下文与具体服务实现解耦，使得未来可以轻松添加
 * 新的服务类型（如 ShizukuService, NetworkService 等）而无需修改核心执行逻辑。
 */
class ExecutionServices {

    // 使用 Map 来存储服务实例，Key 是服务的 KClass，Value 是服务实例本身。
    private val services = mutableMapOf<KClass<*>, Any>()

    /**
     * 向容器中添加一个服务实例。
     * @param service 要添加的服务实例。
     */
    fun <T : Any> add(service: T) {
        services[service::class] = service
    }

    /**
     * 从容器中获取一个指定类型的服务实例。
     * @param serviceClass 服务的 KClass 对象 (例如 AccessibilityService::class)。
     * @return 如果服务存在，则返回其类型安全的实例；否则返回 null。
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> get(serviceClass: KClass<T>): T? {
        return services[serviceClass] as? T
    }
}
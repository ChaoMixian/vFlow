package com.chaomixian.vflow.server.wrappers

import android.os.IBinder
import org.json.JSONObject

/**
 * Android 系统服务包装基类
 * 自动处理 ServiceManager 反射和 Stub 转换
 */
abstract class ServiceWrapper(
    val serviceName: String,
    val stubClassName: String,
    val targetName: String = serviceName
) {

    protected var serviceInterface: Any? = null
    val isAvailable: Boolean
        get() = serviceInterface != null

    init {
        connect()
    }

    private fun connect() {
        try {
            // 反射 ServiceManager.getService(name)
            val smClass = Class.forName("android.os.ServiceManager")
            val getService = smClass.getDeclaredMethod("getService", String::class.java)
            val binder = getService.invoke(null, serviceName)

            // 检查服务是否可用
            if (binder == null) {
                System.err.println("⚠️  Service unavailable: $serviceName (not running or no permission)")
                return
            }

            // 反射 Stub.asInterface(binder)
            val stubClass = Class.forName(stubClassName)
            val asInterface = stubClass.getDeclaredMethod("asInterface", IBinder::class.java)
            serviceInterface = asInterface.invoke(null, binder as IBinder)

            // 回调子类初始化方法
            if (serviceInterface != null) {
                onServiceConnected(serviceInterface!!)
                System.err.println("✅ Service connected: $serviceName")
            } else {
                System.err.println("Failed to get service interface for: $serviceName")
            }
        } catch (e: Exception) {
            System.err.println("Failed to connect to service: $serviceName")
            e.printStackTrace()
        }
    }

    /**
     * 当服务连接成功时调用，子类在此处反射获取具体的方法引用
     */
    abstract fun onServiceConnected(service: Any)

    /**
     * 处理方法调用
     * 子类通过反射调用具体的方法
     */
    abstract fun handle(method: String, params: JSONObject): JSONObject
}
// 文件: server/src/main/java/com/chaomixian/vflow/server/common/ServiceWrapper.kt
package com.chaomixian.vflow.server.common

import android.os.IBinder
import java.lang.reflect.Method

/**
 * Android 系统服务包装基类
 * 自动处理 ServiceManager 反射和 Stub 转换
 */
abstract class ServiceWrapper(private val serviceName: String, private val stubClassName: String) {

    protected var serviceInterface: Any? = null

    init {
        connect()
    }

    private fun connect() {
        try {
            // 反射 ServiceManager.getService(name)
            val smClass = Class.forName("android.os.ServiceManager")
            val getService = smClass.getDeclaredMethod("getService", String::class.java)
            val binder = getService.invoke(null, serviceName) as IBinder

            // 反射 Stub.asInterface(binder)
            val stubClass = Class.forName(stubClassName)
            val asInterface = stubClass.getDeclaredMethod("asInterface", IBinder::class.java)
            serviceInterface = asInterface.invoke(null, binder)

            // 回调子类初始化方法
            if (serviceInterface != null) {
                onServiceConnected(serviceInterface!!)
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
}
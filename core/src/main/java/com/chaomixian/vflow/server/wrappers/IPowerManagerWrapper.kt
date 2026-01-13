// 文件: server/src/main/java/com/chaomixian/vflow/server/wrappers/IPowerManagerWrapper.kt
package com.chaomixian.vflow.server.wrappers

import android.os.SystemClock
import com.chaomixian.vflow.server.common.ServiceWrapper
import com.chaomixian.vflow.server.utils.ReflectionUtils
import java.lang.reflect.Method

class IPowerManagerWrapper : ServiceWrapper("power", "android.os.IPowerManager\$Stub") {

    private var wakeUpMethod: Method? = null
    private var goToSleepMethod: Method? = null

    override fun onServiceConnected(service: Any) {
        val clazz = service.javaClass
        // wakeUp(long time, int reason, String details, String opPackageName) // 参数随版本变化
        wakeUpMethod = ReflectionUtils.findMethodLoose(clazz, "wakeUp")
        // goToSleep(long time, int reason, int flags)
        goToSleepMethod = ReflectionUtils.findMethodLoose(clazz, "goToSleep")
    }

    fun wakeUp() {
        if (serviceInterface == null || wakeUpMethod == null) return
        try {
            val time = SystemClock.uptimeMillis()
            val args = arrayOfNulls<Any>(wakeUpMethod!!.parameterTypes.size)
            // 填充参数，第一个通常是 time
            if (args.isNotEmpty()) args[0] = time
            // 其他参数默认为 0 或 null 即可
            wakeUpMethod!!.invoke(serviceInterface, *args)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun goToSleep() {
        if (serviceInterface == null || goToSleepMethod == null) return
        try {
            val time = SystemClock.uptimeMillis()
            val args = arrayOfNulls<Any>(goToSleepMethod!!.parameterTypes.size)
            if (args.isNotEmpty()) args[0] = time
            goToSleepMethod!!.invoke(serviceInterface, *args)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
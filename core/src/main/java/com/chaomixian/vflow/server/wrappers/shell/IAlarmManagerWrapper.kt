// 文件: server/src/main/java/com/chaomixian/vflow/server/wrappers/shell/IAlarmManagerWrapper.kt
package com.chaomixian.vflow.server.wrappers.shell

import com.chaomixian.vflow.server.wrappers.ServiceWrapper
import com.chaomixian.vflow.server.common.utils.ReflectionUtils
import org.json.JSONObject
import java.lang.reflect.Method

/**
 * 闹钟管理器 Wrapper
 * 提供闹钟和定时器相关的功能
 */
class IAlarmManagerWrapper : ServiceWrapper("alarm", "android.app.IAlarmManager\$Stub") {

    private var getAllAlarmsMethod: Method? = null

    override fun onServiceConnected(service: Any) {
        // 注意：获取闹钟列表的方法在不同 Android 版本中有所不同
        // Android 12+ 需要 SCHEDULE_EXACT_ALARM 权限
    }

    override fun handle(method: String, params: JSONObject): JSONObject {
        val result = JSONObject()

        if (!isAvailable) {
            result.put("success", false)
            result.put("error", "Alarm service is not available or no permission")
            return result
        }

        when (method) {
            "getNextAlarm" -> {
                val alarm = getNextAlarmTime()
                result.put("success", true)
                result.put("next_alarm", alarm)
            }
            "hasAlarm" -> {
                val hasAlarm = hasScheduledAlarms()
                result.put("success", true)
                result.put("has_alarm", hasAlarm)
            }
            else -> {
                result.put("success", false)
                result.put("error", "Unknown method: $method")
            }
        }
        return result
    }

    /**
     * 获取下一个闹钟的时间
     * 通过广播查询系统设置
     */
    private fun getNextAlarmTime(): JSONObject {
        return try {
            // 使用 Settings.System 获取下一个闹钟
            val settingsClass = Class.forName("android.provider.Settings\$System")
            val contentResolver = Class.forName("android.app.ContextImpl")
                .getDeclaredMethod("getSystemContext")
                .invoke(null)
                .let { context ->
                    context.javaClass.getDeclaredMethod("getContentResolver").invoke(context)
                }

            val getNextAlarmTime = settingsClass.getDeclaredMethod(
                "getString",
                contentResolver.javaClass,
                String::class.java
            )

            val alarmTimeStr = getNextAlarmTime.invoke(null, contentResolver, "next_alarm_time_formatted") as? String

            JSONObject().apply {
                put("available", true)
                put("time", alarmTimeStr ?: "无设置")
                put("set", alarmTimeStr != null)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            JSONObject().put("available", false)
        }
    }

    /**
     * 检查是否有计划的闹钟
     */
    private fun hasScheduledAlarms(): Boolean {
        val alarmInfo = getNextAlarmTime()
        return alarmInfo.optBoolean("set", false)
    }
}

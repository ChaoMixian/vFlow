// 文件: server/src/main/java/com/chaomixian/vflow/server/wrappers/shell/IActivityTaskManagerWrapper.kt
package com.chaomixian.vflow.server.wrappers.shell

import com.chaomixian.vflow.server.wrappers.ServiceWrapper
import com.chaomixian.vflow.server.common.utils.ReflectionUtils
import org.json.JSONObject
import org.json.JSONArray
import java.lang.reflect.Method

/**
 * Activity 任务管理器 Wrapper (Android 10+)
 * 提供应用和任务相关的功能
 */
class IActivityTaskManagerWrapper : ServiceWrapper("activity_task", "android.app.IActivityTaskManager\$Stub") {

    private var getRunningTasksMethod: Method? = null
    private var getAllRunningTasksMethod: Method? = null
    private var removeTaskMethod: Method? = null

    override fun onServiceConnected(service: Any) {
        val clazz = service.javaClass
        // Android 10+ 使用 getRunningTasks (maxTasks, filter)
        getRunningTasksMethod = ReflectionUtils.findMethodLoose(clazz, "getRunningTasks")
        getAllRunningTasksMethod = ReflectionUtils.findMethodLoose(clazz, "getAllRunningTasks")
        removeTaskMethod = ReflectionUtils.findMethodLoose(clazz, "removeTask")
    }

    override fun handle(method: String, params: JSONObject): JSONObject {
        val result = JSONObject()

        if (!isAvailable) {
            result.put("success", false)
            result.put("error", "ActivityTaskManager service is not available (Android 10+ only)")
            return result
        }

        when (method) {
            "getRunningTasks" -> {
                val maxTasks = params.optInt("maxTasks", 20)
                val tasks = getRunningTasks(maxTasks)
                result.put("success", true)
                result.put("tasks", tasks)
            }
            "getForegroundApp" -> {
                val app = getForegroundApp()
                result.put("success", true)
                result.put("app", app)
            }
            "removeTask" -> {
                val taskId = params.getInt("taskId")
                val removed = removeTask(taskId)
                result.put("success", removed)
            }
            else -> {
                result.put("success", false)
                result.put("error", "Unknown method: $method")
            }
        }
        return result
    }

    /**
     * 获取正在运行的任务列表
     */
    private fun getRunningTasks(maxTasks: Int): JSONArray {
        if (serviceInterface == null || getRunningTasksMethod == null) {
            return JSONArray()
        }

        return try {
            val tasks = mutableListOf<JSONObject>()

            // 尝试调用 getRunningTasks
            val method = getRunningTasksMethod!!

            val result = when (method.parameterCount) {
                2 -> {
                    // Android 10+ 有 filter 参数，尝试 null 或 0
                    try {
                        method.invoke(serviceInterface, maxTasks, null)
                    } catch (e: Exception) {
                        method.invoke(serviceInterface, maxTasks, 0)
                    }
                }
                1 -> {
                    method.invoke(serviceInterface, maxTasks)
                }
                else -> {
                    method.invoke(serviceInterface)
                }
            }

            // 解析返回的 List
            if (result != null) {
                @Suppress("UNCHECKED_CAST")
                val list = result as? List<*> ?: return JSONArray()
                for (task in list) {
                    if (task != null) {
                        val taskInfo = parseTaskInfo(task)
                        if (taskInfo.length() > 0) {
                            tasks.add(taskInfo)
                        }
                    }
                }
            }

            val jsonArray = JSONArray()
            tasks.forEach { jsonArray.put(it) }
            jsonArray
        } catch (e: Exception) {
            e.printStackTrace()
            JSONArray()
        }
    }

    /**
     * 获取前台应用信息
     */
    private fun getForegroundApp(): JSONObject {
        if (serviceInterface == null || getRunningTasksMethod == null) {
            return JSONObject().put("available", false)
        }

        return try {
            val tasks = getRunningTasks(1)
            if (tasks.length() > 0) {
                val taskInfo = tasks.getJSONObject(0)
                val packageName = taskInfo.optString("packageName")

                JSONObject().apply {
                    put("available", true)
                    put("packageName", packageName)
                    put("activity", taskInfo.optString("topActivity"))
                }
            } else {
                JSONObject().put("available", false)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            JSONObject().put("available", false)
        }
    }

    /**
     * 移除指定任务
     */
    private fun removeTask(taskId: Int): Boolean {
        if (serviceInterface == null || removeTaskMethod == null) return false
        return try {
            removeTaskMethod!!.invoke(serviceInterface, taskId) as? Boolean ?: false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 解析任务信息
     */
    private fun parseTaskInfo(task: Any): JSONObject {
        return try {
            val taskClass = task.javaClass

            // 获取基本信息
            val taskId = taskClass.getDeclaredMethod("taskId").invoke(task) as? Int ?: -1
            val stackId = taskClass.getDeclaredMethod("stackId").invoke(task) as? Int ?: -1

            // 获取顶层 Activity
            val topActivity = try {
                val topActivityObj = taskClass.getDeclaredMethod("topActivity").invoke(task)
                val componentNameClass = Class.forName("android.content.ComponentName")
                val getPackageName = componentNameClass.getDeclaredMethod("getPackageName")
                val getClassName = componentNameClass.getDeclaredMethod("getClassName")
                val pkg = getPackageName.invoke(topActivityObj) as? String ?: ""
                val cls = getClassName.invoke(topActivityObj) as? String ?: ""
                "$pkg/$cls"
            } catch (e: Exception) {
                ""
            }

            val baseActivityObj = try {
                taskClass.getDeclaredMethod("baseActivity").invoke(task)
            } catch (e: Exception) {
                null
            }

            val basePackageName = if (baseActivityObj != null) {
                try {
                    val componentNameClass = Class.forName("android.content.ComponentName")
                    val getPackageName = componentNameClass.getDeclaredMethod("getPackageName")
                    getPackageName.invoke(baseActivityObj) as? String ?: ""
                } catch (e: Exception) {
                    ""
                }
            } else {
                ""
            }

            JSONObject().apply {
                put("taskId", taskId)
                put("stackId", stackId)
                put("topActivity", topActivity)
                put("packageName", basePackageName)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            JSONObject()
        }
    }
}

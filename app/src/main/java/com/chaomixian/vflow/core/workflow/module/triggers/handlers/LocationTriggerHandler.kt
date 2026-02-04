// 文件: main/java/com/chaomixian/vflow/core/workflow/module/triggers/handlers/LocationTriggerHandler.kt
// 描述: 位置触发器处理器，实现智能位置监听和地理围栏检测
package com.chaomixian.vflow.core.workflow.module.triggers.handlers

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.chaomixian.vflow.core.execution.WorkflowExecutor
import com.chaomixian.vflow.core.logging.DebugLogger
import com.chaomixian.vflow.core.workflow.module.triggers.LocationTriggerData
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

class LocationTriggerHandler : ListeningTriggerHandler() {

    private lateinit var locationManager: LocationManager
    private lateinit var stateStore: GeofenceStateStore
    private var currentLocationListener: LocationListener? = null
    private var currentStrategy: LocationStrategy = LocationStrategy.PASSIVE

    // 位置去重和防抖
    private var lastProcessedLocation: Location? = null
    private var lastProcessTime: Long = 0
    private var lastStrategySwitchTime: Long = 0

    companion object {
        private const val TAG = "LocationTriggerHandler"
        private const val MIN_DISTANCE_UPDATE_PASSIVE = 0f
        private const val MIN_TIME_UPDATE_PASSIVE = 0L
        private const val MIN_DISTANCE_UPDATE_NETWORK = 200f
        private const val MIN_TIME_UPDATE_NETWORK = 5 * 60 * 1000L
        private const val MIN_DISTANCE_UPDATE_GPS = 50f
        private const val MIN_TIME_UPDATE_GPS = 60 * 1000L

        // 位置去重参数
        private const val MIN_LOCATION_CHANGE = 5f // 最小位置变化（米）- 降低门槛
        private const val MIN_UPDATE_INTERVAL = 1000L // 最小更新间隔（毫秒）- 降低门槛
        private const val MIN_STRATEGY_SWITCH_INTERVAL = 10000L // 最小策略切换间隔（毫秒）
    }

    /**
     * 定位策略枚举
     */
    private enum class LocationStrategy {
        PASSIVE,   // 完全被动监听（几乎不耗电）
        NETWORK,   // 网络定位（WiFi + 基站，中等耗电）
        GPS        // GPS 精确定位（高耗电）
    }

    override fun getTriggerModuleId(): String = "vflow.trigger.location"

    override fun startListening(context: Context) {
        DebugLogger.d(TAG, "启动位置监听...")
        locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        stateStore = GeofenceStateStore(context)
        // 初始使用被动监听
        switchToPassiveListening(context)
    }

    override fun stopListening(context: Context) {
        DebugLogger.d(TAG, "停止位置监听...")
        currentLocationListener?.let {
            try {
                locationManager.removeUpdates(it)
            } catch (e: Exception) {
                DebugLogger.e(TAG, "移除位置监听失败", e)
            }
        }
        currentLocationListener = null
    }

    /**
     * 切换到被动监听模式（最省电）
     */
    private fun switchToPassiveListening(context: Context) {
        if (currentStrategy == LocationStrategy.PASSIVE && currentLocationListener != null) return

        DebugLogger.d(TAG, "切换到被动监听模式")
        stopListening(context)
        currentStrategy = LocationStrategy.PASSIVE

        currentLocationListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                handleLocationUpdate(context, location)
            }

            override fun onProviderEnabled(provider: String) {
                DebugLogger.d(TAG, "位置提供者已启用: $provider")
            }

            override fun onProviderDisabled(provider: String) {
                DebugLogger.d(TAG, "位置提供者已禁用: $provider")
            }

            @Deprecated("Deprecated in Android API")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
            }
        }

        try {
            // 被动定位：复用其他应用请求的位置更新，几乎不耗电
            // 必须在主线程调用 requestLocationUpdates
            Handler(Looper.getMainLooper()).post {
                try {
                    locationManager.requestLocationUpdates(
                        LocationManager.PASSIVE_PROVIDER,
                        MIN_TIME_UPDATE_PASSIVE,
                        MIN_DISTANCE_UPDATE_PASSIVE,
                        currentLocationListener!!
                    )
                    DebugLogger.i(TAG, "被动监听已启动")
                } catch (e: Exception) {
                    DebugLogger.e(TAG, "启动被动监听失败", e)
                }
            }
        } catch (e: SecurityException) {
            DebugLogger.e(TAG, "缺少位置权限", e)
        } catch (e: Exception) {
            DebugLogger.e(TAG, "启动被动监听失败", e)
        }
    }

    /**
     * 切换到网络定位模式（中等耗电）
     */
    private fun switchToNetworkListening(context: Context) {
        if (currentStrategy == LocationStrategy.NETWORK && currentLocationListener != null) return

        DebugLogger.d(TAG, "切换到网络定位模式")
        stopListening(context)
        currentStrategy = LocationStrategy.NETWORK

        currentLocationListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                handleLocationUpdate(context, location)
            }

            override fun onProviderEnabled(provider: String) {
                DebugLogger.d(TAG, "网络定位已启用")
            }

            override fun onProviderDisabled(provider: String) {
                DebugLogger.d(TAG, "网络定位已禁用")
            }

            @Deprecated("Deprecated in Android API")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
            }
        }

        try {
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                // 必须在主线程调用 requestLocationUpdates
                Handler(Looper.getMainLooper()).post {
                    try {
                        locationManager.requestLocationUpdates(
                            LocationManager.NETWORK_PROVIDER,
                            MIN_TIME_UPDATE_NETWORK,
                            MIN_DISTANCE_UPDATE_NETWORK,
                            currentLocationListener!!
                        )
                        DebugLogger.i(TAG, "网络定位监听已启动（5分钟或200米）")
                    } catch (e: Exception) {
                        DebugLogger.e(TAG, "启动网络定位失败", e)
                    }
                }
            } else {
                DebugLogger.w(TAG, "网络定位不可用，回退到被动监听")
                switchToPassiveListening(context)
            }
        } catch (e: SecurityException) {
            DebugLogger.e(TAG, "缺少位置权限", e)
        } catch (e: Exception) {
            DebugLogger.e(TAG, "启动网络定位失败", e)
        }
    }

    /**
     * 切换到 GPS 精确定位模式（高耗电）
     */
    private fun switchToGPSListening(context: Context) {
        if (currentStrategy == LocationStrategy.GPS && currentLocationListener != null) return

        DebugLogger.d(TAG, "切换到 GPS 精确定位模式")
        stopListening(context)
        currentStrategy = LocationStrategy.GPS

        currentLocationListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                handleLocationUpdate(context, location)
            }

            override fun onProviderEnabled(provider: String) {
                DebugLogger.d(TAG, "GPS 已启用")
            }

            override fun onProviderDisabled(provider: String) {
                DebugLogger.d(TAG, "GPS 已禁用")
            }

            @Deprecated("Deprecated in Android API")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
            }
        }

        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                // 必须在主线程调用 requestLocationUpdates
                Handler(Looper.getMainLooper()).post {
                    try {
                        locationManager.requestLocationUpdates(
                            LocationManager.GPS_PROVIDER,
                            MIN_TIME_UPDATE_GPS,
                            MIN_DISTANCE_UPDATE_GPS,
                            currentLocationListener!!
                        )
                        DebugLogger.i(TAG, "GPS 监听已启动（1分钟或50米）")
                    } catch (e: Exception) {
                        DebugLogger.e(TAG, "启动 GPS 失败", e)
                    }
                }
            } else {
                DebugLogger.w(TAG, "GPS 不可用，回退到网络定位")
                switchToNetworkListening(context)
            }
        } catch (e: SecurityException) {
            DebugLogger.e(TAG, "缺少位置权限", e)
        } catch (e: Exception) {
            DebugLogger.e(TAG, "启动 GPS 失败", e)
        }
    }

    /**
     * 处理位置更新
     */
    private fun handleLocationUpdate(context: Context, location: Location) {
        val currentTime = System.currentTimeMillis()

        // 打印所有接收到的位置更新（便于调试）
        DebugLogger.d(TAG, "收到位置更新: ${location.latitude}, ${location.longitude}, 精度: ${location.accuracy}m, Provider: ${location.provider}")

        // 位置去重：如果位置变化很小且时间间隔很短，忽略
        if (shouldIgnoreLocationUpdate(location, currentTime)) {
            return
        }

        DebugLogger.i(TAG, "处理位置更新: ${location.latitude}, ${location.longitude}, 精度: ${location.accuracy}m")

        // 更新最后处理的位置和时间
        lastProcessedLocation = location
        lastProcessTime = currentTime

        triggerScope.launch {
            // 1. 计算距离所有围栏的最小距离
            val minDistance = findMinimumDistanceToAnyFence(location)

            // 2. 根据距离动态调整定位策略（带防抖）
            adjustLocationStrategy(context, location, minDistance, currentTime)

            // 3. 检查所有围栏的进入/离开事件
            checkGeofenceEvents(context, location)
        }
    }

    /**
     * 判断是否应该忽略此次位置更新（去重逻辑）
     */
    private fun shouldIgnoreLocationUpdate(location: Location, currentTime: Long): Boolean {
        // 第一次接收位置，永远不忽略
        if (lastProcessedLocation == null) {
            DebugLogger.d(TAG, "首次接收位置更新，不进行去重检查")
            return false
        }

        // 检查时间间隔
        val timeSinceLastUpdate = currentTime - lastProcessTime
        if (timeSinceLastUpdate < MIN_UPDATE_INTERVAL) {
            DebugLogger.d(TAG, "忽略位置更新：时间间隔太短 (${timeSinceLastUpdate}ms < ${MIN_UPDATE_INTERVAL}ms)")
            return true
        }

        // 检查位置变化
        val distance = lastProcessedLocation!!.distanceTo(location)
        if (distance < MIN_LOCATION_CHANGE) {
            DebugLogger.d(TAG, "忽略位置更新：位置变化太小 (${distance}m < ${MIN_LOCATION_CHANGE}m)")
            return true
        }

        DebugLogger.d(TAG, "位置更新通过去重检查：时间间隔=${timeSinceLastUpdate}ms, 位置变化=${distance}m")
        return false
    }

    /**
     * 计算当前位置到所有围栏的最小距离
     */
    private fun findMinimumDistanceToAnyFence(location: Location): Double {
        var minDistance = Double.MAX_VALUE

        listeningWorkflows.forEach { workflow ->
            val config = workflow.triggerConfig ?: return@forEach
            val fenceLat = config["latitude"] as? Double ?: return@forEach
            val fenceLon = config["longitude"] as? Double ?: return@forEach
            val fenceRadius = config["radius"] as? Double ?: return@forEach

            val distance = calculateDistance(
                location.latitude, location.longitude,
                fenceLat, fenceLon
            )

            // 计算距离围栏边缘的距离
            val distanceToEdge = distance - fenceRadius
            if (distanceToEdge < minDistance) {
                minDistance = distanceToEdge
            }
        }

        return minDistance
    }

    /**
     * 根据距离动态调整定位策略（带防抖）
     */
    private fun adjustLocationStrategy(context: Context, location: Location, minDistance: Double, currentTime: Long) {
        val newStrategy = when {
            minDistance > 10000 -> LocationStrategy.PASSIVE      // 10km 外：仅被动监听
            minDistance > 2000 -> LocationStrategy.NETWORK      // 2km 外：网络定位
            minDistance > 500 -> LocationStrategy.NETWORK       // 500m 外：网络定位
            else -> LocationStrategy.GPS                        // 500m 内：启用 GPS
        }

        // 防抖：如果距离上次策略切换时间太短，不切换
        if (newStrategy != currentStrategy) {
            val timeSinceLastSwitch = currentTime - lastStrategySwitchTime

            if (timeSinceLastSwitch < MIN_STRATEGY_SWITCH_INTERVAL) {
                DebugLogger.d(TAG, "策略切换防抖：距离上次切换仅 ${timeSinceLastSwitch}ms，保持当前策略 ${currentStrategy}")
                return
            }

            DebugLogger.d(TAG, "切换定位策略: ${currentStrategy} -> ${newStrategy} (距离围栏: ${minDistance.toInt()}m)")
            lastStrategySwitchTime = currentTime

            when (newStrategy) {
                LocationStrategy.PASSIVE -> switchToPassiveListening(context)
                LocationStrategy.NETWORK -> switchToNetworkListening(context)
                LocationStrategy.GPS -> switchToGPSListening(context)
            }
        }
    }

    /**
     * 检查所有围栏的进入/离开事件
     */
    private suspend fun checkGeofenceEvents(context: Context, location: Location) {
        listeningWorkflows.forEach { workflow ->
            val config = workflow.triggerConfig ?: return@forEach
            val fenceLat = config["latitude"] as? Double ?: return@forEach
            val fenceLon = config["longitude"] as? Double ?: return@forEach
            val fenceRadius = config["radius"] as? Double ?: return@forEach
            val configEvent = config["event"] as? String ?: return@forEach

            // 计算当前位置到围栏中心的距离
            val distance = calculateDistance(
                location.latitude, location.longitude,
                fenceLat, fenceLon
            )

            // 判断是否在围栏内
            val isInFence = distance <= fenceRadius

            // 获取上次的状态
            val fenceId = generateFenceId(workflow.id, fenceLat, fenceLon, fenceRadius)
            val wasInFence = stateStore.isInFence(fenceId)

            // 检查进入事件
            if (!wasInFence && isInFence) {
                stateStore.setInFence(fenceId, true)

                // 判断是否匹配工作流配置的事件
                val enterEvent = context.getString(com.chaomixian.vflow.R.string.option_vflow_trigger_location_event_enter)

                if (configEvent == enterEvent) {
                    DebugLogger.i(TAG, "触发工作流 '${workflow.name}'：进入围栏")
                    executeWorkflow(context, workflow, location)
                }
            }
            // 检查离开事件
            else if (wasInFence && !isInFence) {
                stateStore.setInFence(fenceId, false)

                val exitEvent = context.getString(com.chaomixian.vflow.R.string.option_vflow_trigger_location_event_exit)

                if (configEvent == exitEvent) {
                    DebugLogger.i(TAG, "触发工作流 '${workflow.name}'：离开围栏")
                    executeWorkflow(context, workflow, location)
                }
            }
        }
    }

    /**
     * 执行工作流
     */
    private fun executeWorkflow(context: Context, workflow: com.chaomixian.vflow.core.workflow.model.Workflow, location: Location) {
        val triggerData = LocationTriggerData(
            latitude = location.latitude,
            longitude = location.longitude,
            accuracy = location.accuracy
        )
        WorkflowExecutor.execute(workflow, context.applicationContext, triggerData)
    }

    /**
     * 计算两点之间的距离（单位：米）
     * 使用 Haversine 公式
     */
    private fun calculateDistance(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {
        val R = 6371000.0 // 地球半径（米）
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return R * c
    }

    /**
     * 生成围栏唯一标识
     */
    private fun generateFenceId(workflowId: String, lat: Double, lon: Double, radius: Double): String {
        return "${workflowId}_${(lat * 10000).toInt()}_${(lon * 10000).toInt()}_${radius.toInt()}"
    }

    /**
     * 地理围栏状态存储
     */
    private class GeofenceStateStore(context: Context) {
        private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(
            "location_trigger_geofence_states",
            Context.MODE_PRIVATE
        )

        fun isInFence(fenceId: String): Boolean {
            return prefs.getBoolean(fenceId, false)
        }

        fun setInFence(fenceId: String, inFence: Boolean) {
            prefs.edit().putBoolean(fenceId, inFence).apply()
        }
    }
}

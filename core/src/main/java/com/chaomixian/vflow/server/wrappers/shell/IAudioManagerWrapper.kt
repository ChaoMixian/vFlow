// 文件: server/src/main/java/com/chaomixian/vflow/server/wrappers/shell/IAudioManagerWrapper.kt
package com.chaomixian.vflow.server.wrappers.shell

import com.chaomixian.vflow.server.wrappers.ServiceWrapper
import com.chaomixian.vflow.server.common.utils.ReflectionUtils
import com.chaomixian.vflow.server.common.Logger
import org.json.JSONObject
import java.lang.reflect.Method

/**
 * AudioManager 包装器
 * 用于控制不同音频流的音量
 */
class IAudioManagerWrapper : ServiceWrapper("audio", "android.media.IAudioService\$Stub") {

    // 音频流类型常量
    companion object {
        const val STREAM_VOICE_CALL = 0      // 通话
        const val STREAM_SYSTEM = 1          // 系统
        const val STREAM_RING = 2            // 铃声
        const val STREAM_MUSIC = 3           // 音乐
        const val STREAM_ALARM = 4           // 闹钟
        const val STREAM_NOTIFICATION = 5    // 通知
        const val STREAM_DTMF = 6            // DTMF
        const val STREAM_ACCESSIBILITY = 11  // 辅助功能

        // 音量调整方向
        const val ADJUST_LOWER = -1          // 降低
        const val ADJUST_RAISE = 1           // 升高
        const val ADJUST_SAME = 0            // 保持
    }

    private var setStreamVolumeMethod: Method? = null
    private var getStreamVolumeMethod: Method? = null
    private var getStreamMaxVolumeMethod: Method? = null
    private var adjustStreamVolumeMethod: Method? = null

    override fun onServiceConnected(service: Any) {
        val clazz = service.javaClass
        setStreamVolumeMethod = ReflectionUtils.findMethodLoose(clazz, "setStreamVolume")
        getStreamVolumeMethod = ReflectionUtils.findMethodLoose(clazz, "getStreamVolume")
        getStreamMaxVolumeMethod = ReflectionUtils.findMethodLoose(clazz, "getStreamMaxVolume")
        adjustStreamVolumeMethod = ReflectionUtils.findMethodLoose(clazz, "adjustStreamVolume")

        Logger.debug("AudioManager", "=== Audio Manager Methods ===")
        Logger.debug("AudioManager", "setStreamVolume: ${setStreamVolumeMethod != null}")
        Logger.debug("AudioManager", "getStreamVolume: ${getStreamVolumeMethod != null}")
        Logger.debug("AudioManager", "getStreamMaxVolume: ${getStreamMaxVolumeMethod != null}")
        Logger.debug("AudioManager", "adjustStreamVolume: ${adjustStreamVolumeMethod != null}")
    }

    override fun handle(method: String, params: JSONObject): JSONObject {
        val result = JSONObject()

        // 检查服务是否可用
        if (!isAvailable) {
            result.put("success", false)
            result.put("error", "Audio service is not available or no permission")
            return result
        }

        when (method) {
            "setVolume" -> {
                val streamType = params.optInt("streamType", STREAM_MUSIC)
                val volume = params.optInt("volume", 0)
                val success = setStreamVolume(streamType, volume)
                result.put("success", success)
                if (success) {
                    result.put("currentLevel", getStreamVolume(streamType))
                    result.put("maxLevel", getStreamMaxVolume(streamType))
                }
            }
            "getVolume" -> {
                val streamType = params.optInt("streamType", STREAM_MUSIC)
                val currentLevel = getStreamVolume(streamType)
                val maxLevel = getStreamMaxVolume(streamType)
                result.put("success", true)
                result.put("currentLevel", currentLevel)
                result.put("maxLevel", maxLevel)
            }
            "getAllVolumes" -> {
                val volumes = JSONObject()
                val streamTypes = listOf(STREAM_MUSIC, STREAM_NOTIFICATION, STREAM_RING, STREAM_SYSTEM, STREAM_ALARM, STREAM_VOICE_CALL)
                for (stream in streamTypes) {
                    val streamName = when (stream) {
                        STREAM_MUSIC -> "music"
                        STREAM_NOTIFICATION -> "notification"
                        STREAM_RING -> "ring"
                        STREAM_SYSTEM -> "system"
                        STREAM_ALARM -> "alarm"
                        STREAM_VOICE_CALL -> "call"
                        else -> "unknown"
                    }
                    val streamData = JSONObject()
                    streamData.put("current", getStreamVolume(stream))
                    streamData.put("max", getStreamMaxVolume(stream))
                    volumes.put(streamName, streamData)
                }
                result.put("success", true)
                result.put("volumes", volumes)
            }
            "adjustVolume" -> {
                val streamType = params.optInt("streamType", STREAM_MUSIC)
                val direction = params.optInt("direction", ADJUST_RAISE)
                val success = adjustStreamVolume(streamType, direction)
                result.put("success", success)
                if (success) {
                    result.put("currentLevel", getStreamVolume(streamType))
                    result.put("maxLevel", getStreamMaxVolume(streamType))
                }
            }
            "mute" -> {
                val streamType = params.optInt("streamType", STREAM_MUSIC)
                val mute = params.optBoolean("mute", true)
                val success = setStreamVolume(streamType, if (mute) 0 else getStreamMaxVolume(streamType) / 2)
                result.put("success", success)
                if (success) {
                    result.put("currentLevel", getStreamVolume(streamType))
                    result.put("maxLevel", getStreamMaxVolume(streamType))
                }
            }
            else -> {
                result.put("success", false)
                result.put("error", "Unknown method: $method")
            }
        }
        return result
    }

    /**
     * 设置指定音频流的音量
     * @param streamType 音频流类型
     * @param volume 音量值（0 到最大音量）
     */
    private fun setStreamVolume(streamType: Int, volume: Int): Boolean {
        if (serviceInterface == null || setStreamVolumeMethod == null) return false
        return try {
            val maxVolume = getStreamMaxVolume(streamType)
            val clampedVolume = volume.coerceIn(0, maxVolume)

            val args = arrayOfNulls<Any>(setStreamVolumeMethod!!.parameterTypes.size)
            args[0] = streamType
            args[1] = clampedVolume
            args[2] = 0 // flags

            // 如果方法需要4个参数（包含包名），则添加包名
            if (setStreamVolumeMethod!!.parameterTypes.size >= 4) {
                args[3] = "com.android.shell"
            }

            setStreamVolumeMethod!!.invoke(serviceInterface, *args)
            Logger.info("AudioManager", "Set stream $streamType volume to $clampedVolume/$maxVolume")
            true
        } catch (e: SecurityException) {
            // 勿扰模式或权限限制
            Logger.error("AudioManager", "setStreamVolume blocked: ${e.message}")
            false
        } catch (e: Exception) {
            Logger.error("AudioManager", "setStreamVolume failed: ${e.message}", e)
            false
        }
    }

    /**
     * 获取指定音频流的当前音量
     */
    private fun getStreamVolume(streamType: Int): Int {
        if (serviceInterface == null || getStreamVolumeMethod == null) return 0
        return try {
            val volume = getStreamVolumeMethod!!.invoke(serviceInterface, streamType) as? Int ?: 0
            Logger.debug("AudioManager", "Stream $streamType volume: $volume")
            volume
        } catch (e: Exception) {
            Logger.error("AudioManager", "getStreamVolume failed: ${e.message}", e)
            0
        }
    }

    /**
     * 获取指定音频流的最大音量
     */
    private fun getStreamMaxVolume(streamType: Int): Int {
        if (serviceInterface == null || getStreamMaxVolumeMethod == null) return 0
        return try {
            val maxVolume = getStreamMaxVolumeMethod!!.invoke(serviceInterface, streamType) as? Int ?: 0
            Logger.debug("AudioManager", "Stream $streamType max volume: $maxVolume")
            maxVolume
        } catch (e: Exception) {
            Logger.error("AudioManager", "getStreamMaxVolume failed: ${e.message}", e)
            0
        }
    }

    /**
     * 调整指定音频流的音量（升高/降低）
     * @param streamType 音频流类型
     * @param direction 调整方向（ADJUST_RAISE/ADJUST_LOWER/ADJUST_SAME）
     */
    private fun adjustStreamVolume(streamType: Int, direction: Int): Boolean {
        if (serviceInterface == null || adjustStreamVolumeMethod == null) return false
        return try {
            val args = arrayOfNulls<Any>(adjustStreamVolumeMethod!!.parameterTypes.size)
            args[0] = streamType
            args[1] = direction
            args[2] = 0 // flags

            // 如果方法需要4个参数（包含包名），则添加包名
            if (adjustStreamVolumeMethod!!.parameterTypes.size >= 4) {
                args[3] = "com.android.shell"
            }

            adjustStreamVolumeMethod!!.invoke(serviceInterface, *args)
            val directionName = when (direction) {
                ADJUST_RAISE -> "raise"
                ADJUST_LOWER -> "lower"
                else -> "same"
            }
            Logger.info("AudioManager", "Adjusted stream $streamType volume: $directionName")
            true
        } catch (e: SecurityException) {
            // 勿扰模式或权限限制
            Logger.error("AudioManager", "adjustStreamVolume blocked: ${e.message}")
            false
        } catch (e: Exception) {
            Logger.error("AudioManager", "adjustStreamVolume failed: ${e.message}", e)
            false
        }
    }
}

// 文件: server/src/main/java/com/chaomixian/vflow/server/wrappers/IInputManagerWrapper.kt
package com.chaomixian.vflow.server.wrappers.shell

import android.os.SystemClock
import android.view.InputEvent
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.KeyCharacterMap
import com.chaomixian.vflow.server.wrappers.ServiceWrapper
import org.json.JSONObject
import org.json.JSONArray
import java.lang.reflect.Method

class IInputManagerWrapper : ServiceWrapper("input", "android.hardware.input.IInputManager\$Stub") {

    private var injectInputEventMethod: Method? = null

    // 输入源常量 (InputDevice.SOURCE_TOUCHSCREEN)
    private val SOURCE_TOUCHSCREEN = 4098

    override fun onServiceConnected(service: Any) {
        // 查找 injectInputEvent 方法
        val methods = service.javaClass.methods
        injectInputEventMethod = methods.find {
            it.name == "injectInputEvent" && it.parameterTypes.size >= 2
        }
    }

    override fun handle(method: String, params: JSONObject): JSONObject {
        val result = JSONObject()
        when (method) {
            "tap" -> {
                tap(params.getInt("x").toFloat(), params.getInt("y").toFloat())
                result.put("success", true)
            }
            "swipe" -> {
                swipe(
                    params.getInt("x1").toFloat(), params.getInt("y1").toFloat(),
                    params.getInt("x2").toFloat(), params.getInt("y2").toFloat(),
                    params.optLong("duration", 300)
                )
                result.put("success", true)
            }
            "key" -> {
                key(params.getInt("code"))
                result.put("success", true)
            }
            "inputText" -> {
                inputText(params.getString("text"))
                result.put("success", true)
            }
            "replaySequence" -> {
                val success = replaySequence(
                    params.getString("sequence"),
                    params.optDouble("speedMultiplier", 1.0).toFloat()
                )
                result.put("success", success)
            }
            else -> {
                result.put("success", false)
                result.put("error", "Unknown method: $method")
            }
        }
        return result
    }

    private fun inject(event: InputEvent): Boolean {
        if (serviceInterface == null || injectInputEventMethod == null) return false
        return try {
            // mode = 0 (INJECT_INPUT_EVENT_MODE_ASYNC)
            // 某些版本可能有更多参数，这里简单假设前两个是核心
            val args = arrayOfNulls<Any>(injectInputEventMethod!!.parameterTypes.size)
            args[0] = event
            args[1] = 0 // mode

            injectInputEventMethod!!.invoke(serviceInterface, *args) as Boolean
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // --- 业务逻辑 ---

    fun tap(x: Float, y: Float) {
        val now = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0)
        down.source = SOURCE_TOUCHSCREEN
        inject(down)

        val up = MotionEvent.obtain(now, now + 50, MotionEvent.ACTION_UP, x, y, 0)
        up.source = SOURCE_TOUCHSCREEN
        inject(up)

        down.recycle()
        up.recycle()
    }

    fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, duration: Long) {
        val now = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x1, y1, 0)
        down.source = SOURCE_TOUCHSCREEN
        inject(down)

        val steps = (duration / 10).coerceAtLeast(1)
        for (i in 0..steps) {
            val alpha = i.toFloat() / steps
            val x = x1 + (x2 - x1) * alpha
            val y = y1 + (y2 - y1) * alpha
            val move = MotionEvent.obtain(now, now + (duration * alpha).toLong(), MotionEvent.ACTION_MOVE, x, y, 0)
            move.source = SOURCE_TOUCHSCREEN
            inject(move)
            move.recycle()
            Thread.sleep(10)
        }

        val up = MotionEvent.obtain(now, now + duration, MotionEvent.ACTION_UP, x2, y2, 0)
        up.source = SOURCE_TOUCHSCREEN
        inject(up)

        down.recycle()
        up.recycle()
    }

    fun key(keyCode: Int) {
        val now = SystemClock.uptimeMillis()
        val down = KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0)
        inject(down)

        val up = KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0)
        inject(up)
    }

    fun inputText(text: String) {
        val kcm = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD)
        val events = kcm.getEvents(text.toCharArray())
        if (events != null) {
            for (e in events) {
                inject(e)
            }
        }
    }

    /**
     * 回放触摸序列
     * @param sequenceJson JSON格式的触摸序列数据
     * @param speedMultiplier 回放速度倍率，1.0为正常速度
     * @return 是否成功回放
     */
    fun replaySequence(sequenceJson: String, speedMultiplier: Float = 1.0f): Boolean {
        return try {
            // 解析JSON数据
            val jsonData = JSONObject(sequenceJson)
            val eventsArray = jsonData.optJSONArray("events") ?: return false

            if (eventsArray.length() == 0) {
                return false
            }

            val now = SystemClock.uptimeMillis()

            // 回放所有事件
            for (i in 0 until eventsArray.length()) {
                val eventJson = eventsArray.getJSONObject(i)

                val action = eventJson.getInt("action")
                val x = eventJson.getDouble("x").toFloat()
                val y = eventJson.getDouble("y").toFloat()
                val timestamp = eventJson.getLong("timestamp")

                // 计算延迟时间（考虑速度倍率）
                val adjustedDelay = (timestamp / speedMultiplier).toLong()

                // 创建并注入触摸事件
                val motionEvent = MotionEvent.obtain(
                    now,
                    now + adjustedDelay,
                    action,
                    x,
                    y,
                    0
                )
                motionEvent.source = SOURCE_TOUCHSCREEN
                inject(motionEvent)
                motionEvent.recycle()

                // 如果不是最后一个事件，需要等待
                if (i < eventsArray.length() - 1) {
                    val nextEventJson = eventsArray.getJSONObject(i + 1)
                    val nextTimestamp = nextEventJson.getLong("timestamp")
                    val delayBetweenEvents = ((nextTimestamp - timestamp) / speedMultiplier).toLong()
                    if (delayBetweenEvents > 0) {
                        Thread.sleep(delayBetweenEvents)
                    }
                }
            }

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
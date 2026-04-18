// 文件: server/src/main/java/com/chaomixian/vflow/server/wrappers/IClipboardWrapper.kt
package com.chaomixian.vflow.server.wrappers.shell

import android.content.ClipDescription
import android.content.ClipboardManager
import android.os.Handler
import android.os.HandlerThread
import com.chaomixian.vflow.server.common.FakeContext
import com.chaomixian.vflow.server.wrappers.ServiceWrapper
import com.chaomixian.vflow.server.wrappers.StreamingWrapper
import org.json.JSONObject
import java.io.PrintWriter
import java.lang.reflect.Method

class IClipboardWrapper : ServiceWrapper("clipboard", "android.content.IClipboard\$Stub"), StreamingWrapper {
    companion object {
        private const val EVENT_DEBOUNCE_MS = 80L
    }

    private var setPrimaryClipMethod: Method? = null
    private var getPrimaryClipMethod: Method? = null

    // ClipData 相关反射缓存
    private var newPlainTextMethod: Method? = null
    private var getItemAtMethod: Method? = null
    private var getTextMethod: Method? = null
    private var clipDataClass: Class<*>? = null

    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    private val clipboardEventLock = java.lang.Object()
    @Volatile
    private var clipboardChangeSequence: Long = 0L
    private var listenerThread: HandlerThread? = null
    private var listenerHandler: Handler? = null
    private var listenerManager: ClipboardManager? = null
    private var primaryClipChangedListener: ClipboardManager.OnPrimaryClipChangedListener? = null
    private var pendingEmitRunnable: Runnable? = null
    private var latestEventPayloadJson: String? = null
    private var lastDispatchedSignature: String? = null

    override fun onServiceConnected(service: Any) {
        val methods = service.javaClass.methods
        setPrimaryClipMethod = methods.find { it.name == "setPrimaryClip" }
        getPrimaryClipMethod = methods.find { it.name == "getPrimaryClip" }

        // 初始化 ClipData 反射
        try {
            clipDataClass = Class.forName("android.content.ClipData")
            val itemClass = Class.forName("android.content.ClipData\$Item")
            newPlainTextMethod = clipDataClass!!.getDeclaredMethod("newPlainText", CharSequence::class.java, CharSequence::class.java)
            getItemAtMethod = clipDataClass!!.getDeclaredMethod("getItemAt", Int::class.javaPrimitiveType)
            getTextMethod = itemClass.getDeclaredMethod("getText")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun handle(method: String, params: JSONObject): JSONObject {
        val result = JSONObject()
        when (method) {
            "setClipboard" -> {
                val success = setClipboard(params.getString("text"))
                result.put("success", success)
                if (!success) result.put("error", "Failed to set clipboard")
            }
            "getClipboard" -> {
                result.put("text", getClipboard())
                result.put("success", true)
            }
            else -> {
                result.put("success", false)
                result.put("error", "Unknown method: $method")
            }
        }
        return result
    }

    override fun handleStream(method: String, params: JSONObject, writer: PrintWriter): Boolean {
        if (method != "subscribeClipboardStream") {
            return false
        }

        val success = ensureClipboardListener()
        if (!success) {
            writer.println(JSONObject()
                .put("success", false)
                .put("error", "Failed to register clipboard listener")
                .toString())
            return true
        }

        var currentSequence = clipboardChangeSequence
        writer.println(JSONObject()
            .put("success", true)
            .put("event", "ready")
            .put("sequence", currentSequence)
            .toString())
        if (writer.checkError()) {
            return true
        }

        while (true) {
            val changed = waitForClipboardChange(currentSequence, 0L)
            if (!changed) {
                continue
            }

            val eventPayload = synchronized(clipboardEventLock) {
                currentSequence = clipboardChangeSequence
                JSONObject(latestEventPayloadJson ?: buildClipboardEventPayload().toString())
                    .put("success", true)
                    .put("event", "clipboard_changed")
                    .put("sequence", currentSequence)
            }
            writer.println(eventPayload.toString())
            if (writer.checkError()) {
                return true
            }
        }
    }

    private fun ensureClipboardListener(): Boolean {
        synchronized(this) {
            if (primaryClipChangedListener != null) {
                return true
            }

            return try {
                val thread = HandlerThread("vflow-core-clipboard-listener").also { it.start() }
                val handler = Handler(thread.looper)
                val constructor = ClipboardManager::class.java.getDeclaredConstructor(
                    android.content.Context::class.java,
                    Handler::class.java
                )
                constructor.isAccessible = true

                val manager = constructor.newInstance(
                    FakeContext.get(),
                    handler
                ) as ClipboardManager

                val listener = ClipboardManager.OnPrimaryClipChangedListener {
                    scheduleClipboardEventEmission()
                }

                manager.addPrimaryClipChangedListener(listener)
                listenerThread = thread
                listenerHandler = handler
                listenerManager = manager
                primaryClipChangedListener = listener
                true
            } catch (e: Throwable) {
                e.printStackTrace()
                listenerThread?.quitSafely()
                listenerThread = null
                listenerHandler = null
                listenerManager = null
                primaryClipChangedListener = null
                pendingEmitRunnable = null
                latestEventPayloadJson = null
                lastDispatchedSignature = null
                false
            }
        }
    }

    private fun scheduleClipboardEventEmission() {
        val handler = listenerHandler ?: return
        pendingEmitRunnable?.let(handler::removeCallbacks)
        val runnable = Runnable {
            emitStableClipboardEvent()
        }
        pendingEmitRunnable = runnable
        handler.postDelayed(runnable, EVENT_DEBOUNCE_MS)
    }

    private fun emitStableClipboardEvent() {
        val payload = buildClipboardEventPayload()
        val signature = payload.optString("signature")
        synchronized(clipboardEventLock) {
            if (signature == lastDispatchedSignature) {
                return
            }
            lastDispatchedSignature = signature
            latestEventPayloadJson = payload.toString()
            clipboardChangeSequence += 1
            clipboardEventLock.notifyAll()
        }
    }

    private fun waitForClipboardChange(sinceSequence: Long, timeoutMs: Long): Boolean {
        synchronized(clipboardEventLock) {
            if (clipboardChangeSequence > sinceSequence) {
                return true
            }

            if (timeoutMs == 0L) {
                while (clipboardChangeSequence <= sinceSequence) {
                    clipboardEventLock.wait()
                }
                return true
            }

            val deadline = System.currentTimeMillis() + timeoutMs
            var remaining = timeoutMs
            while (clipboardChangeSequence <= sinceSequence && remaining > 0L) {
                clipboardEventLock.wait(remaining)
                remaining = deadline - System.currentTimeMillis()
            }
            return clipboardChangeSequence > sinceSequence
        }
    }

    private fun buildClipboardEventPayload(): JSONObject {
        val manager = listenerManager
        if (manager == null || !manager.hasPrimaryClip()) {
            return JSONObject()
                .put("signature", "empty")
                .put("text", "")
        }

        val clipData = manager.primaryClip
        if (clipData == null || clipData.itemCount <= 0) {
            return JSONObject()
                .put("signature", "empty")
                .put("text", "")
        }

        val item = clipData.getItemAt(0)
        val payload = JSONObject()
        val signatureParts = mutableListOf<String>()

        if (clipData.description.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN)) {
            val text = item.text?.toString() ?: ""
            payload.put("text", text)
            signatureParts += "text:$text"
        } else {
            payload.put("text", "")
        }

        val uri = item.uri
        if (uri != null) {
            payload.put("imageUri", uri.toString())
            signatureParts += "uri:$uri"
            val mimeType = runCatching { FakeContext.get().contentResolver.getType(uri) }.getOrNull()
            if (!mimeType.isNullOrBlank()) {
                payload.put("mimeType", mimeType)
                signatureParts += "mime:$mimeType"
            }
        }

        if (signatureParts.isEmpty()) {
            val coerced = item.coerceToText(FakeContext.get())?.toString().orEmpty()
            signatureParts += "unknown:$coerced"
            if (payload.optString("text").isBlank()) {
                payload.put("text", coerced)
            }
        }

        payload.put("signature", signatureParts.joinToString("|"))
        return payload
    }

    private fun setClipboard(text: String): Boolean {
        if (serviceInterface == null || setPrimaryClipMethod == null || newPlainTextMethod == null) return false
        return try {
            val clipData = newPlainTextMethod!!.invoke(null, "vFlow", text)

            // 动态参数适配逻辑
            val method = setPrimaryClipMethod!!
            val args = arrayOfNulls<Any>(method.parameterTypes.size)
            var stringArgCount = 0

            for (i in method.parameterTypes.indices) {
                args[i] = when (method.parameterTypes[i]) {
                    clipDataClass -> clipData
                    String::class.java -> if (stringArgCount++ == 0) "com.android.shell" else null
                    Int::class.javaPrimitiveType -> 0
                    else -> null
                }
            }
            method.invoke(serviceInterface, *args)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun getClipboard(): String {
        if (serviceInterface == null || getPrimaryClipMethod == null) return ""
        return try {
            val method = getPrimaryClipMethod!!
            val args = arrayOfNulls<Any>(method.parameterTypes.size)
            var stringArgCount = 0

            for (i in method.parameterTypes.indices) {
                args[i] = when (method.parameterTypes[i]) {
                    String::class.java -> if (stringArgCount++ == 0) "com.android.shell" else null
                    Int::class.javaPrimitiveType -> 0
                    else -> null
                }
            }

            val clipData = method.invoke(serviceInterface, *args) ?: return ""

            // 解析 ClipData
            val item = getItemAtMethod!!.invoke(clipData, 0)
            getTextMethod!!.invoke(item)?.toString() ?: ""
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }
}

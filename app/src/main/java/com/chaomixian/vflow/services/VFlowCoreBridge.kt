// 文件: main/java/com/chaomixian/vflow/services/VFlowCoreBridge.kt
package com.chaomixian.vflow.services

import android.content.Context
import android.content.Intent
import com.chaomixian.vflow.core.logging.DebugLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * vFlowCore 的客户端桥接器。
 * 负责与 vFlowCore 进行 Socket 通信。
 */
object VFlowCoreBridge {
    private const val TAG = "VFlowCoreBridge"
    private const val HOST = "127.0.0.1"
    private const val PORT = 19999

    // 用于在 ping() 中执行网络操作的 IO 线程池
    private val ioExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "VFlowCoreBridge-IO").apply { isDaemon = true }
    }

    private var socket: Socket? = null
    private var writer: PrintWriter? = null
    private var reader: BufferedReader? = null
    private val isConnecting = AtomicBoolean(false)

    var isConnected = false
        private set

    var currentUid: Int = -1
        private set

    /**
     * 核心权限模式
     */
    enum class PrivilegeMode {
        NONE,    // 未连接
        SHELL,   // Shizuku / Shell (uid 2000)
        ROOT     // Root (uid 0)
    }

    val privilegeMode: PrivilegeMode
        get() = when {
            !isConnected -> PrivilegeMode.NONE
            currentUid == 0 -> PrivilegeMode.ROOT
            else -> PrivilegeMode.SHELL // 应该是2000
        }

    /**
     * 确保 vFlow Core 正在运行并已连接。
     * 如果连接失败，会请求 CoreManagementService 启动 vFlow Core，并轮询等待。
     */
    suspend fun connect(context: Context): Boolean = withContext(Dispatchers.IO) {
        // 尝试直接 Ping
        if (ping()) return@withContext true

        // 避免并发重复启动
        if (isConnecting.get()) return@withContext false
        isConnecting.set(true)

        try {
            DebugLogger.i(TAG, "vFlowCore 未响应，请求 Service 启动...")

            // 发送启动 Intent 给 Service
            val intent = Intent(context, CoreManagementService::class.java).apply {
                action = CoreManagementService.ACTION_START_CORE
            }
            context.startService(intent)

            //  轮询等待启动 (最多等待 5 秒)
            for (i in 1..20) { // 20 * 250ms = 5秒
                delay(250)
                if (establishConnection()) {
                    // 再次验证
                    if (checkConnection()) {
                        DebugLogger.i(TAG, "vFlowCore 连接成功 (尝试 $i) 权限: $$privilegeMode")
                        return@withContext true
                    }
                }
            }
            DebugLogger.e(TAG, "vFlowCore 启动超时")
        } catch (e: Exception) {
            DebugLogger.e(TAG, "连接过程异常", e)
        } finally {
            isConnecting.set(false)
        }
        return@withContext false
    }

    /**
     * 发送 Ping 并更新状态 (UID)
     */
    private fun checkConnection(): Boolean {
        if (socket == null || socket!!.isClosed) {
            if (!establishConnection()) return false
        }
        val res = sendRaw(JSONObject().put("target", "system").put("method", "ping"))
        val success = res?.optBoolean("success") == true

        if (success) {
            isConnected = true
            // 更新 UID 缓存
            if (res!!.has("uid")) {
                currentUid = res.optInt("uid")
            }
        } else {
            isConnected = false
            currentUid = -1
        }
        return success
    }

    /**
     * 建立 Socket 连接
     */
    private fun establishConnection(): Boolean {
        return try {
            close() // 清理旧连接
            DebugLogger.d(TAG, "正在建立到 $HOST:$PORT 的连接...")
            socket = Socket(HOST, PORT)
            socket?.soTimeout = 5000 // 读写超时
            socket?.keepAlive = true
            writer = PrintWriter(socket!!.getOutputStream(), true)
            reader = BufferedReader(InputStreamReader(socket!!.getInputStream()))
            DebugLogger.i(TAG, "Socket 连接建立成功")
            true
        } catch (e: Exception) {
            DebugLogger.w(TAG, "Socket 连接失败: ${e.javaClass.simpleName} - ${e.message}")
            // 连接失败很正常（vFlowCore可能还没起来）
            false
        }
    }

    private fun close() {
        try { socket?.close() } catch (e: Exception) {}
        socket = null
        writer = null
        reader = null
        isConnected = false
    }

    /**
     * 发送 ping 包检查连接是否活跃
     * 这个方法现在是 public 的，供 Service 做健康检查使用
     * 注意：此方法会在 IO 线程执行网络操作，不会阻塞调用线程
     */
    fun ping(): Boolean {
        // 使用 Future 在 IO 线程执行，避免在主线程进行网络操作
        val future = ioExecutor.submit<Boolean> {
            // 如果 Socket 对象都不存在，尝试建立一次
            if (socket == null || socket!!.isClosed) {
                DebugLogger.d(TAG, "ping: socket 未建立，尝试连接...")
                if (!establishConnection()) {
                    DebugLogger.w(TAG, "ping: 建立 socket 连接失败")
                    return@submit false
                }
            }

            val req = JSONObject().put("target", "system").put("method", "ping")
            DebugLogger.d(TAG, "ping: 发送 ping 请求: $req")

            val res = sendRaw(req)

            if (res == null) {
                DebugLogger.w(TAG, "ping: 未收到响应")
                return@submit false
            }

            val success = res.optBoolean("success") == true
            DebugLogger.d(TAG, "ping: 收到响应: $res, success=$success")

            if (success) {
                // 更新 UID
                if (res.has("uid")) {
                    currentUid = res.optInt("uid")
                    isConnected = true
                    DebugLogger.i(TAG, "ping: 成功, uid=$currentUid, mode=$privilegeMode")
                }
            } else {
                DebugLogger.w(TAG, "ping: 响应中 success=false 或不存在")
            }

            return@submit success
        }

        return try {
            // 等待结果，最多 5 秒
            future.get(5, java.util.concurrent.TimeUnit.SECONDS)
        } catch (e: java.util.concurrent.TimeoutException) {
            DebugLogger.w(TAG, "ping: 超时")
            future.cancel(true)
            false
        } catch (e: Exception) {
            DebugLogger.w(TAG, "ping: 执行异常: ${e.javaClass.simpleName} - ${e.message}")
            false
        }
    }

    /**
     * 底层发送方法
     */
    @Synchronized
    private fun sendRaw(json: JSONObject): JSONObject? {
        try {
            // 双重检查连接
            if (socket == null || socket!!.isClosed) {
                if (!establishConnection()) return null
            }

            val jsonStr = json.toString()
            DebugLogger.d(TAG, "发送: $jsonStr")
            writer?.println(jsonStr)

            if (writer?.checkError() == true) {
                DebugLogger.w(TAG, "写入数据时发生错误")
                close()
                return null
            }

            val line = reader?.readLine()
            DebugLogger.d(TAG, "接收: $line")

            if (line == null) {
                // 连接被对端关闭
                close()
                return null
            }

            return JSONObject(line)
        } catch (e: Exception) {
            DebugLogger.w(TAG, "通信异常: ${e.javaClass.simpleName} - ${e.message}", e)
            close()
            return null
        }
    }

    // 业务 API 封装
    fun exec(cmd: String): String {
        val req = JSONObject()
            .put("target", "system")
            .put("method", "exec")
            .put("params", JSONObject().put("cmd", cmd))
        val res = sendRaw(req)
        return res?.optString("output", "") ?: ""
    }

    fun performClick(x: Int, y: Int): Boolean {
        val req = JSONObject()
            .put("target", "input")
            .put("method", "tap")
            .put("params", JSONObject().put("x", x).put("y", y))
        return sendRaw(req)?.optBoolean("success") ?: false
    }

    fun performSwipe(x1: Int, y1: Int, x2: Int, y2: Int, duration: Long): Boolean {
        val req = JSONObject()
            .put("target", "input")
            .put("method", "swipe")
            .put("params", JSONObject()
                .put("x1", x1).put("y1", y1)
                .put("x2", x2).put("y2", y2)
                .put("duration", duration))
        return sendRaw(req)?.optBoolean("success") ?: false
    }

    fun inputText(text: String): Boolean {
        val req = JSONObject()
            .put("target", "input")
            .put("method", "inputText")
            .put("params", JSONObject().put("text", text))
        return sendRaw(req)?.optBoolean("success") ?: false
    }

    fun pressKey(keyCode: Int): Boolean {
        val req = JSONObject()
            .put("target", "input")
            .put("method", "key")
            .put("params", JSONObject().put("code", keyCode))
        return sendRaw(req)?.optBoolean("success") ?: false
    }

    fun setClipboard(text: String): Boolean {
        val req = JSONObject()
            .put("target", "clipboard")
            .put("method", "setClipboard")
            .put("params", JSONObject().put("text", text))
        return sendRaw(req)?.optBoolean("success") ?: false
    }

    fun getClipboard(): String {
        val req = JSONObject()
            .put("target", "clipboard")
            .put("method", "getClipboard")
        val res = sendRaw(req)
        return res?.optString("text", "") ?: ""
    }

    // Power Management APIs
    fun wakeUp(): Boolean {
        val req = JSONObject()
            .put("target", "power")
            .put("method", "wakeUp")
        return sendRaw(req)?.optBoolean("success") ?: false
    }

    fun goToSleep(): Boolean {
        val req = JSONObject()
            .put("target", "power")
            .put("method", "goToSleep")
        return sendRaw(req)?.optBoolean("success") ?: false
    }

    // WiFi Management APIs
    fun setWifiEnabled(enabled: Boolean): Boolean {
        val req = JSONObject()
            .put("target", "wifi")
            .put("method", "setWifiEnabled")
            .put("params", JSONObject().put("enabled", enabled))
        return sendRaw(req)?.optBoolean("success", false) ?: false
    }

    // Bluetooth Management APIs
    fun setBluetoothEnabled(enabled: Boolean): Boolean {
        val req = JSONObject()
            .put("target", "bluetooth")
            .put("method", "setBluetoothEnabled")
            .put("params", JSONObject().put("enabled", enabled))
        return sendRaw(req)?.optBoolean("success", false) ?: false
    }

    // Activity Management APIs
    fun forceStopPackage(packageName: String): Boolean {
        val req = JSONObject()
            .put("target", "activity")
            .put("method", "forceStopPackage")
            .put("params", JSONObject().put("package", packageName))
        return sendRaw(req)?.optBoolean("success") ?: false
    }

    // System Control APIs
    /**
     * 请求 vFlowCore 优雅退出
     */
    fun shutdown(): Boolean {
        val req = JSONObject()
            .put("target", "system")
            .put("method", "exit")
        return sendRaw(req)?.optBoolean("success") ?: false
    }
}
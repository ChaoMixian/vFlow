// 文件: server/src/main/java/com/chaomixian/vflow/server/VFlowCore.kt
package com.chaomixian.vflow.server

import com.chaomixian.vflow.server.wrappers.*
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors

/**
 * vFlow Core
 * 运行在 app_process (Root/Shizuku) 进程中的核心服务。
 */
object VFlowCore {
    private const val PORT = 19999
    private var isRunning = true
    private val executor = Executors.newCachedThreadPool()

    // 懒加载 Wrappers
    private val clipboard by lazy { IClipboardWrapper() }
    private val input by lazy { IInputManagerWrapper() }
    private val power by lazy { IPowerManagerWrapper() }
    private val wifi by lazy { IWifiManagerWrapper() }
    private val activity by lazy { IActivityManagerWrapper() }
    private val bluetooth by lazy { IBluetoothManagerWrapper() }

    @JvmStatic
    fun main(args: Array<String>) {
        println(">>> vFlow Core Starting (PID: ${android.os.Process.myPid()}) <<<")

        try {
            // 绑定到 localhost
            val serverSocket = ServerSocket(PORT, 5, InetAddress.getByName("127.0.0.1"))
            println("✅ Core listening on 127.0.0.1:$PORT")

            while (isRunning) {
                try {
                    val client = serverSocket.accept()
                    executor.submit { handleClient(client) }
                } catch (e: Exception) {
                    if (isRunning) e.printStackTrace()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun handleClient(socket: Socket) {
        try {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val writer = PrintWriter(OutputStreamWriter(socket.getOutputStream()), true)

            while (isRunning && !socket.isClosed) {
                val requestStr = reader.readLine() ?: break
                val request = JSONObject(requestStr)
                val response = dispatchRequest(request)
                writer.println(response.toString())
            }
        } catch (e: Exception) {
            // 忽略连接断开
        } finally {
            try { socket.close() } catch (e: Exception) {}
        }
    }

    /**
     * 请求分发中心
     */
    private fun dispatchRequest(req: JSONObject): JSONObject {
        val target = req.optString("target")
        val method = req.optString("method")
        val params = req.optJSONObject("params") ?: JSONObject()

        val result = JSONObject()

        try {
            when (target) {
                "system" -> {
                    // 系统级命令
                    when (method) {
                        "ping" -> {
                            result.put("success", true)
                            result.put("message", "pong")
                            result.put("uid", android.os.Process.myUid())
                        }
                        "exec" -> {
                            val cmd = params.getString("cmd")
                            try {
                                val process = Runtime.getRuntime().exec(cmd)
                                val output = process.inputStream.bufferedReader().readText()
                                val error = process.errorStream.bufferedReader().readText()
                                val exitCode = process.waitFor()

                                result.put("success", true)
                                result.put("output", output)
                                result.put("error", error)
                                result.put("exitCode", exitCode)
                            } catch (e: Exception) {
                                result.put("success", false)
                                result.put("error", e.message)
                                throw e
                            }
                        }
                        "exit" -> {
                            isRunning = false
                            executor.submit { Thread.sleep(500); System.exit(0) }
                        }
                        else -> throw IllegalArgumentException("Unknown system method: $method")
                    }
                }
                "clipboard" -> {
                    when (method) {
                        "setClipboard" -> {
                            if (!clipboard.setClipboard(params.getString("text"))) {
                                throw RuntimeException("Failed to set clipboard")
                            }
                        }
                        "getClipboard" -> result.put("text", clipboard.getClipboard())
                        else -> throw IllegalArgumentException("Unknown clipboard method: $method")
                    }
                }
                "input" -> {
                    when (method) {
                        "tap" -> input.tap(
                            params.getInt("x").toFloat(),
                            params.getInt("y").toFloat()
                        )
                        "swipe" -> input.swipe(
                            params.getInt("x1").toFloat(),
                            params.getInt("y1").toFloat(),
                            params.getInt("x2").toFloat(),
                            params.getInt("y2").toFloat(),
                            params.optLong("duration", 300)
                        )
                        "key" -> input.key(params.getInt("code"))
                        "inputText" -> input.inputText(params.getString("text"))
                        else -> throw IllegalArgumentException("Unknown input method: $method")
                    }
                }
                "power" -> {
                    when (method) {
                        "wakeUp" -> power.wakeUp()
                        "goToSleep" -> power.goToSleep()
                        else -> throw IllegalArgumentException("Unknown power method: $method")
                    }
                }
                "wifi" -> {
                    when (method) {
                        "setWifiEnabled" -> {
                            val success = wifi.setWifiEnabled(params.getBoolean("enabled"))
                            result.put("success", success)
                        }
                        else -> throw IllegalArgumentException("Unknown wifi method: $method")
                    }
                }
                "bluetooth" -> {
                    when (method) {
                        "setBluetoothEnabled" -> {
                            val success = bluetooth.setBluetoothEnabled(params.getBoolean("enabled"))
                            result.put("success", success)
                        }
                        else -> throw IllegalArgumentException("Unknown bluetooth method: $method")
                    }
                }
                "activity" -> {
                    when (method) {
                        "forceStopPackage" -> activity.forceStopPackage(params.getString("package"))
                        else -> throw IllegalArgumentException("Unknown activity method: $method")
                    }
                }
                else -> throw IllegalArgumentException("Unknown target: $target")
            }

            // 默认添加 success=true
            if (!result.has("success")) result.put("success", true)

        } catch (e: Exception) {
            result.put("success", false)
            result.put("error", e.message ?: e.toString())
            e.printStackTrace()
        }
        return result
    }
}
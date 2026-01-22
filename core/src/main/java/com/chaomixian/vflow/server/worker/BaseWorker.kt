// 文件: server/src/main/java/com/chaomixian/vflow/server/worker/BaseWorker.kt
package com.chaomixian.vflow.server.worker

import com.chaomixian.vflow.server.common.Config
import com.chaomixian.vflow.server.wrappers.ServiceWrapper
import com.chaomixian.vflow.server.wrappers.IWrapper
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

abstract class BaseWorker(private val port: Int, private val name: String) {

    @Volatile
    private var isRunning = true
    private val executor = Executors.newCachedThreadPool()

    // 服务注册表: Target Name -> Wrapper 实例 (支持 ServiceWrapper 和 IWrapper)
    protected val serviceWrappers = ConcurrentHashMap<String, ServiceWrapper>()
    protected val simpleWrappers = ConcurrentHashMap<String, IWrapper>()

    /**
     * 子类必须实现此方法以注册支持的 Wrappers
     */
    abstract fun registerWrappers()

    fun start() {
        println(">>> $name Worker Starting (PID: ${android.os.Process.myPid()}, Port: $port) <<<")

        // 注册服务
        try {
            registerWrappers()
            val allTargets = (serviceWrappers.keys + simpleWrappers.keys).joinToString(", ")
            println("✅ $name Worker services registered: $allTargets")
        } catch (e: Exception) {
            System.err.println("❌ $name Worker failed to register wrappers: ${e.message}")
            // 即使部分失败，也继续启动，避免进程崩溃
        }

        // 启动监听
        try {
            // 仅绑定 localhost，确保安全
            val serverSocket = ServerSocket(port, 50, InetAddress.getByName(Config.LOCALHOST))
            // 设置 SO_REUSEADDR 避免重启时端口占用
            serverSocket.reuseAddress = true

            println("✅ $name Worker listening on ${Config.LOCALHOST}:$port")

            while (isRunning) {
                try {
                    val client = serverSocket.accept()
                    // 提交给线程池处理，避免阻塞主循环
                    executor.submit { handleClient(client) }
                } catch (e: Exception) {
                    if (isRunning) {
                        System.err.println("⚠️ Accept failed: ${e.message}")
                        e.printStackTrace()
                    }
                }
            }
        } catch (e: Exception) {
            System.err.println("❌ $name Worker Fatal Error: ${e.message}")
            e.printStackTrace()
            System.exit(1)
        }
    }

    private fun handleClient(socket: Socket) {
        socket.use { s ->
            try {
                // 设置读超时
                s.soTimeout = Config.SOCKET_TIMEOUT

                val reader = BufferedReader(InputStreamReader(s.inputStream))
                val writer = PrintWriter(OutputStreamWriter(s.getOutputStream()), true)

                while (isRunning && !s.isClosed) {
                    val requestStr = reader.readLine() ?: break
                    // 即使发生异常也要确保返回 JSON 格式
                    val response = try {
                        val request = JSONObject(requestStr)
                        processRequest(request)
                    } catch (e: Exception) {
                        val err = JSONObject()
                        err.put("success", false)
                        err.put("error", "Invalid JSON or Internal Error: ${e.message}")
                        err
                    }
                    writer.println(response.toString())
                }
            } catch (e: Exception) {
                // 常见的 Socket 关闭异常忽略，其他打印
                if (e.message?.contains("Socket closed") != true) {
                    e.printStackTrace()
                }
            }
        }
    }

    /**
     * 请求处理分发器
     */
    private fun processRequest(req: JSONObject): JSONObject {
        val target = req.optString("target")
        val method = req.optString("method")
        val params = req.optJSONObject("params") ?: JSONObject()
        val result = JSONObject()

        // 默认标记为失败，成功逻辑中覆盖
        result.put("success", false)

        try {
            // 系统级命令 (Ping)
            if (target == "system" && method == "ping") {
                result.put("success", true)
                result.put("message", "pong from $name")
                result.put("uid", android.os.Process.myUid())
                return result
            }

            // 系统命令执行 (Exec)
            if (target == "system" && method == "exec") {
                val cmd = params.optString("cmd", "")
                if (cmd.isBlank()) {
                    result.put("success", false)
                    result.put("error", "Command is empty")
                    return result
                }

                val output = executeCommand(cmd)
                result.put("success", true)
                result.put("output", output)
                return result
            }

            // 查找 Wrapper (先查找 ServiceWrapper，再查找 IWrapper)
            val serviceWrapper = serviceWrappers[target]
            val simpleWrapper = simpleWrappers[target]

            if (serviceWrapper == null && simpleWrapper == null) {
                result.put("error", "Service not found or not supported in this worker ($target)")
                return result
            }

            // 动态调用
            if (serviceWrapper != null) {
                dispatchToServiceWrapper(serviceWrapper, target, method, params, result)
            } else {
                dispatchToSimpleWrapper(simpleWrapper!!, target, method, params, result)
            }

        } catch (e: Exception) {
            result.put("success", false)
            result.put("error", "Execution failed: ${e.message}")
            e.printStackTrace()
        }
        return result
    }

    /**
     * 将请求分发给 ServiceWrapper 实例
     */
    private fun dispatchToServiceWrapper(wrapper: ServiceWrapper, target: String, method: String, params: JSONObject, result: JSONObject) {
        // 调用 wrapper 的 handle 方法，让它自己处理
        val wrapperResult = wrapper.handle(method, params)

        // 将 wrapper 的结果合并到 result 中
        val keys = wrapperResult.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            result.put(key, wrapperResult.get(key))
        }
    }

    /**
     * 将请求分发给 IWrapper 实例
     */
    private fun dispatchToSimpleWrapper(wrapper: IWrapper, target: String, method: String, params: JSONObject, result: JSONObject) {
        // 调用 wrapper 的 handle 方法，让它自己处理
        val wrapperResult = wrapper.handle(method, params)

        // 将 wrapper 的结果合并到 result 中
        val keys = wrapperResult.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            result.put(key, wrapperResult.get(key))
        }
    }

    /**
     * 执行 Shell 命令
     * @param command 要执行的命令
     * @return 命令输出，失败时返回 "Error: ..." 格式
     */
    private fun executeCommand(command: String): String {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))

            val stdout = process.inputStream.bufferedReader().use { it.readText() }
            val stderr = process.errorStream.bufferedReader().use { it.readText() }
            val exitCode = process.waitFor()

            if (exitCode == 0) {
                stdout.trim()
            } else {
                val errorMsg = if (stderr.isNotBlank()) stderr else if (stdout.isNotBlank()) stdout else "Exit code $exitCode"
                "Error: ${errorMsg.trim()}"
            }
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }
}
// 文件: server/src/main/java/com/chaomixian/vflow/server/VFlowCore.kt
package com.chaomixian.vflow.server

import com.chaomixian.vflow.server.common.Config
import com.chaomixian.vflow.server.common.utils.SystemUtils
import com.chaomixian.vflow.server.worker.RootWorker
import com.chaomixian.vflow.server.worker.ShellWorker
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import kotlin.system.exitProcess

/**
 * vFlow Core 入口
 */
object VFlowCore {
    private var isRunning = true
    private val executor = Executors.newCachedThreadPool()
    private val workerProcesses = mutableListOf<Process>()
    private var shellLauncherPath: String? = null

    @JvmStatic
    fun main(args: Array<String>) {
        var isWorker = false
        var workerType = ""

        var i = 0
        while (i < args.size) {
            when (args[i]) {
                "--worker" -> isWorker = true
                "--type" -> {
                    if (i + 1 < args.size) workerType = args[i + 1]
                    i++
                }
                "--shell-launcher" -> {
                    if (i + 1 < args.size) {
                        shellLauncherPath = args[i + 1]
                        i++
                    }
                }
            }
            i++
        }

        if (isWorker) {
            runAsWorker(workerType)
        } else {
            runAsMaster()
        }
    }

    // ================= Worker 逻辑 =================

    private fun runAsWorker(type: String) {
        // 全局异常捕获
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            System.err.println("❌ Uncaught Exception in Worker [$type]: ${e.message}")
            e.printStackTrace()
        }

        when (type) {
            "shell" -> ShellWorker().run() // 逻辑已移入 ShellWorker
            "root" -> RootWorker().run()
            else -> {
                System.err.println("Unknown worker type: $type")
                exitProcess(1)
            }
        }
    }

    // ================= Master 逻辑 =================

    private fun runAsMaster() {
        val isRoot = SystemUtils.isRoot()
        val debugVersion = 11
        println(">>> vFlow Core MASTER Starting (PID: ${android.os.Process.myPid()}, UID: ${SystemUtils.getMyUid()}) <<<")
        println(">>> Debug Version: $debugVersion <<<")

        Runtime.getRuntime().addShutdownHook(Thread {
            workerProcesses.forEach { SystemUtils.killProcess(it) }
        })

        spawnWorkers(isRoot, shellLauncherPath)
        startMasterServer()
    }

    private fun spawnWorkers(isRoot: Boolean, shellLauncherPath: String?) {
        println("--- Spawning Workers ---")

        // 1. 启动 Shell Worker
        try {
            val p = if (shellLauncherPath != null) {
                SystemUtils.startWorkerProcess("shell", shellLauncherPath)
            } else {
                SystemUtils.startWorkerProcess("shell")
            }
            workerProcesses.add(p)
            setupWorkerLogger(p, "ShellWorker")
        } catch (e: Exception) {
            System.err.println("❌ Failed to start ShellWorker: ${e.message}")
        }

        // 2. 启动 Root Worker (仅 Master 为 Root 时，保持原样，不需要 vflow_shell_exec)
        if (isRoot) {
            try {
                val p = SystemUtils.startWorkerProcess("root")
                workerProcesses.add(p)
                setupWorkerLogger(p, "RootWorker")
            } catch (e: Exception) {
                System.err.println("❌ Failed to start RootWorker: ${e.message}")
            }
        }

        Thread.sleep(1000)
    }

    private fun setupWorkerLogger(process: Process, tag: String) {
        executor.submit {
            try {
                BufferedReader(InputStreamReader(process.inputStream)).forEachLine { println("[$tag] $it") }
            } catch (ignored: Exception) {}
        }
        executor.submit {
            try {
                BufferedReader(InputStreamReader(process.errorStream)).forEachLine { System.err.println("[$tag ERR] $it") }
            } catch (ignored: Exception) {}
        }
    }

    private fun startMasterServer() {
        try {
            val serverSocket = ServerSocket(Config.PORT_MASTER, 50, InetAddress.getByName(Config.BIND_ADDRESS))
            serverSocket.reuseAddress = true
            println("✅ Master listening on ${Config.BIND_ADDRESS}:${Config.PORT_MASTER}")

            while (isRunning) {
                val client = serverSocket.accept()
                executor.submit { handleMasterClient(client) }
            }
        } catch (e: Exception) {
            System.err.println("❌ Master Fatal: ${e.message}")
            exitProcess(1)
        }
    }

    private fun handleMasterClient(socket: Socket) {
        socket.use { s ->
            try {
                s.soTimeout = Config.SOCKET_TIMEOUT
                val reader = BufferedReader(InputStreamReader(s.inputStream))
                val writer = PrintWriter(OutputStreamWriter(s.getOutputStream()), true)

                while (isRunning && !s.isClosed) {
                    val reqStr = reader.readLine() ?: break
                    val req = try { JSONObject(reqStr) } catch(e:Exception) { null }

                    if (req != null) {
                        if (req.optString("target") == "system" && req.optString("method") == "exit") {
                            writer.println(JSONObject().put("success", true).toString())
                            isRunning = false
                            executor.submit { Thread.sleep(500); exitProcess(0) }
                            return
                        }
                        writer.println(routeRequest(req.optString("target"), reqStr))
                    }
                }
            } catch (e: Exception) {}
        }
    }

    private fun routeRequest(target: String, requestStr: String): String {
        val port = Config.ROUTING_TABLE[target]
        if (port == null) {
            if (target == "system") return JSONObject().put("success", true).put("uid", SystemUtils.getMyUid()).toString()
            return JSONObject().put("success", false).put("error", "No route").toString()
        }

        return try {
            Socket(Config.LOCALHOST, port).use { ws ->
                ws.soTimeout = Config.SOCKET_TIMEOUT
                val wWriter = PrintWriter(OutputStreamWriter(ws.getOutputStream()), true)
                val wReader = BufferedReader(InputStreamReader(ws.inputStream))
                wWriter.println(requestStr)
                wReader.readLine() ?: JSONObject().put("success", false).put("error", "Empty response").toString()
            }
        } catch (e: Exception) {
            JSONObject().put("success", false).put("error", "Worker error: ${e.message}").toString()
        }
    }
}
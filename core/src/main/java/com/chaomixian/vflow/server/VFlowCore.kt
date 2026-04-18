// 文件: server/src/main/java/com/chaomixian/vflow/server/VFlowCore.kt
package com.chaomixian.vflow.server

import android.net.LocalServerSocket
import android.net.LocalSocket
import android.net.LocalSocketAddress
import com.chaomixian.vflow.server.common.CoreBuildInfo
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
    private enum class MasterTransport {
        TCP,
        UNIX
    }

    private var isRunning = true
    private val executor = Executors.newCachedThreadPool()
    private val workerProcesses = mutableListOf<Process>()
    private var shellLauncherPath: String? = null
    private var appPackageName: String? = null
    private var appWatcherJob: Thread? = null
    private var masterTransport: MasterTransport = MasterTransport.TCP
    private var unixSocketName: String? = null

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
                "--app-package" -> {
                    if (i + 1 < args.size) {
                        appPackageName = args[i + 1]
                        i++
                    }
                }
                "--ipc-transport" -> {
                    if (i + 1 < args.size) {
                        masterTransport = if (args[i + 1].equals("unix", ignoreCase = true)) {
                            MasterTransport.UNIX
                        } else {
                            MasterTransport.TCP
                        }
                        i++
                    }
                }
                "--unix-socket-name" -> {
                    if (i + 1 < args.size) {
                        unixSocketName = args[i + 1]
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

        val useUnixSocket = masterTransport == MasterTransport.UNIX
        val resolvedUnixSocketName = if (useUnixSocket) {
            unixSocketName ?: Config.getWorkerSocketName(resolveWorkerType(type), appPackageName)
        } else {
            null
        }

        when (type) {
            "shell" -> ShellWorker(useUnixSocket, resolvedUnixSocketName).run() // 逻辑已移入 ShellWorker
            "root" -> RootWorker(useUnixSocket, resolvedUnixSocketName).run()
            else -> {
                System.err.println("Unknown worker type: $type")
                exitProcess(1)
            }
        }
    }

    // ================= Master 逻辑 =================

    private fun runAsMaster() {
        val isRoot = SystemUtils.isRoot()
        println(">>> vFlow Core MASTER Starting (PID: ${android.os.Process.myPid()}, UID: ${SystemUtils.getMyUid()}) <<<")
        println(">>> Core Version: ${CoreBuildInfo.VERSION_CODE} <<<")
        if (masterTransport == MasterTransport.UNIX) {
            println(">>> IPC Transport: UNIX (@${resolveUnixSocketName()}) <<<")
        } else {
            println(">>> IPC Transport: TCP (${Config.BIND_ADDRESS}:${Config.PORT_MASTER}) <<<")
        }

        Runtime.getRuntime().addShutdownHook(Thread {
            workerProcesses.forEach { SystemUtils.killProcess(it) }
            appWatcherJob?.interrupt()
        })

        if (masterTransport == MasterTransport.UNIX) {
            runUnixMasterServer(isRoot)
        } else {
            runTcpMasterServer(isRoot)
        }
    }

    private fun spawnWorkers(isRoot: Boolean, shellLauncherPath: String?) {
        println("--- Spawning Workers ---")

        // 1. 启动 Shell Worker
        try {
            val shellWorkerArgs = buildWorkerTransportArgs(Config.WorkerType.SHELL)
            val p = if (shellLauncherPath != null) {
                SystemUtils.startWorkerProcess("shell", shellLauncherPath, shellWorkerArgs)
            } else {
                SystemUtils.startWorkerProcess("shell", shellWorkerArgs)
            }
            workerProcesses.add(p)
            setupWorkerLogger(p, "ShellWorker")
        } catch (e: Exception) {
            System.err.println("❌ Failed to start ShellWorker: ${e.message}")
        }

        // 2. 启动 Root Worker (仅 Master 为 Root 时，保持原样，不需要 vflow_shell_exec)
        if (isRoot) {
            try {
                val p = SystemUtils.startWorkerProcess("root", buildWorkerTransportArgs(Config.WorkerType.ROOT))
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

    private fun runTcpMasterServer(isRoot: Boolean) {
        try {
            val serverSocket = ServerSocket(Config.PORT_MASTER, 50, InetAddress.getByName(Config.BIND_ADDRESS))
            serverSocket.reuseAddress = true
            println("✅ Master listening on ${Config.BIND_ADDRESS}:${Config.PORT_MASTER}")

            serverSocket.use { server ->
                spawnWorkers(isRoot, shellLauncherPath)
                startAppWatcher() // 启动 App 进程监控

                while (isRunning) {
                    val client = server.accept()
                    executor.submit { handleMasterTcpClient(client) }
                }
            }
        } catch (e: Exception) {
            System.err.println("❌ Master Fatal: ${e.message}")
            exitProcess(1)
        }
    }

    private fun runUnixMasterServer(isRoot: Boolean) {
        val socketName = resolveUnixSocketName()
        try {
            LocalServerSocket(socketName).use { server ->
                println("✅ Master listening on unix:@$socketName")

                spawnWorkers(isRoot, shellLauncherPath)
                startAppWatcher() // 启动 App 进程监控

                while (isRunning) {
                    val client = server.accept()
                    executor.submit { handleMasterLocalClient(client) }
                }
            }
        } catch (e: Exception) {
            System.err.println("❌ Master Fatal (UNIX): ${e.message}")
            e.printStackTrace()
            exitProcess(1)
        }
    }

    private fun handleMasterTcpClient(socket: Socket) {
        socket.use { s ->
            try {
                s.soTimeout = Config.SOCKET_TIMEOUT
                val reader = BufferedReader(InputStreamReader(s.inputStream))
                val writer = PrintWriter(OutputStreamWriter(s.getOutputStream()), true)
                handleMasterClientLoop(reader, writer)
            } catch (e: Exception) {}
        }
    }

    private fun handleMasterLocalClient(socket: LocalSocket) {
        socket.use { s ->
            try {
                s.soTimeout = Config.SOCKET_TIMEOUT
                val reader = BufferedReader(InputStreamReader(s.inputStream))
                val writer = PrintWriter(OutputStreamWriter(s.outputStream), true)
                handleMasterClientLoop(reader, writer)
            } catch (e: Exception) {
            }
        }
    }

    private fun handleMasterClientLoop(
        reader: BufferedReader,
        writer: PrintWriter
    ) {
        while (isRunning) {
            val reqStr = reader.readLine() ?: break
            val req = try { JSONObject(reqStr) } catch(e:Exception) { null }

            if (req != null) {
                if (req.optString("target") == "system" && req.optString("method") == "exit") {
                    writer.println(JSONObject().put("success", true).toString())
                    isRunning = false
                    executor.submit { Thread.sleep(500); exitProcess(0) }
                    return
                }
                if (tryRouteStreamRequest(req, reqStr, writer)) {
                    return
                }
                writer.println(routeRequest(req.optString("target"), reqStr))
            }
        }
    }

    private fun resolveUnixSocketName(): String {
        val providedPath = unixSocketName?.takeIf { it.isNotBlank() }
        if (providedPath != null) {
            return providedPath
        }
        val packageSuffix = (appPackageName ?: "com.chaomixian.vflow").replace('.', '_')
        return "${packageSuffix}_vflow_core"
    }

    private fun buildWorkerTransportArgs(type: Config.WorkerType): List<String> {
        if (masterTransport != MasterTransport.UNIX) {
            return emptyList()
        }
        return listOf(
            "--ipc-transport",
            "unix",
            "--unix-socket-name",
            Config.getWorkerSocketName(type, appPackageName),
            "--app-package",
            appPackageName.orEmpty()
        )
    }

    private fun resolveWorkerType(type: String): Config.WorkerType {
        return when (type) {
            "root" -> Config.WorkerType.ROOT
            else -> Config.WorkerType.SHELL
        }
    }

    private fun routeRequestToWorker(workerType: Config.WorkerType, requestStr: String): String {
        return try {
            if (masterTransport == MasterTransport.UNIX) {
                val socketName = Config.getWorkerSocketName(workerType, appPackageName)
                LocalSocket(LocalSocket.SOCKET_STREAM).use { ws ->
                    ws.connect(
                        LocalSocketAddress(socketName, LocalSocketAddress.Namespace.ABSTRACT)
                    )
                    ws.soTimeout = Config.SOCKET_TIMEOUT
                    val writer = PrintWriter(OutputStreamWriter(ws.outputStream), true)
                    val reader = BufferedReader(InputStreamReader(ws.inputStream))
                    writer.println(requestStr)
                    reader.readLine() ?: JSONObject().put("success", false).put("error", "Empty response").toString()
                }
            } else {
                val port = Config.getWorkerPort(workerType)
                Socket(Config.LOCALHOST, port).use { ws ->
                    ws.soTimeout = Config.SOCKET_TIMEOUT
                    val writer = PrintWriter(OutputStreamWriter(ws.getOutputStream()), true)
                    val reader = BufferedReader(InputStreamReader(ws.inputStream))
                    writer.println(requestStr)
                    reader.readLine() ?: JSONObject().put("success", false).put("error", "Empty response").toString()
                }
            }
        } catch (e: Exception) {
            JSONObject().put("success", false).put("error", "Worker error: ${e.message}").toString()
        }
    }

    private fun tryRouteStreamRequest(req: JSONObject, requestStr: String, clientWriter: PrintWriter): Boolean {
        val target = req.optString("target")
        val method = req.optString("method")
        if (target != "clipboard" || method != "subscribeClipboardStream") {
            return false
        }

        val workerType = Config.ROUTING_TABLE[target] ?: return false
        relayStreamRequestToWorker(workerType, requestStr, clientWriter)
        return true
    }

    private fun relayStreamRequestToWorker(
        workerType: Config.WorkerType,
        requestStr: String,
        clientWriter: PrintWriter
    ) {
        try {
            if (masterTransport == MasterTransport.UNIX) {
                val socketName = Config.getWorkerSocketName(workerType, appPackageName)
                LocalSocket(LocalSocket.SOCKET_STREAM).use { workerSocket ->
                    workerSocket.connect(
                        LocalSocketAddress(socketName, LocalSocketAddress.Namespace.ABSTRACT)
                    )
                    workerSocket.soTimeout = Config.SOCKET_TIMEOUT
                    val workerWriter = PrintWriter(OutputStreamWriter(workerSocket.outputStream), true)
                    val workerReader = BufferedReader(InputStreamReader(workerSocket.inputStream))
                    workerWriter.println(requestStr)
                    if (workerWriter.checkError()) {
                        clientWriter.println(JSONObject().put("success", false).put("error", "Failed to write stream request").toString())
                        return
                    }
                    while (isRunning) {
                        val line = workerReader.readLine() ?: break
                        clientWriter.println(line)
                        if (clientWriter.checkError()) {
                            break
                        }
                    }
                }
            } else {
                val port = Config.getWorkerPort(workerType)
                Socket(Config.LOCALHOST, port).use { workerSocket ->
                    workerSocket.soTimeout = Config.SOCKET_TIMEOUT
                    val workerWriter = PrintWriter(OutputStreamWriter(workerSocket.getOutputStream()), true)
                    val workerReader = BufferedReader(InputStreamReader(workerSocket.inputStream))
                    workerWriter.println(requestStr)
                    if (workerWriter.checkError()) {
                        clientWriter.println(JSONObject().put("success", false).put("error", "Failed to write stream request").toString())
                        return
                    }
                    while (isRunning) {
                        val line = workerReader.readLine() ?: break
                        clientWriter.println(line)
                        if (clientWriter.checkError()) {
                            break
                        }
                    }
                }
            }
        } catch (e: Exception) {
            clientWriter.println(
                JSONObject()
                    .put("success", false)
                    .put("error", "Worker stream error: ${e.message}")
                    .toString()
            )
        }
    }

    private fun routeRequest(target: String, requestStr: String): String {
        // 处理 system target 的特殊路由
        if (target == "system") {
            return try {
                val req = JSONObject(requestStr)
                val method = req.optString("method")

                // ping 请求直接返回
                if (method == "ping") {
                    return JSONObject()
                        .put("success", true)
                        .put("uid", SystemUtils.getMyUid())
                        .put("versionCode", CoreBuildInfo.VERSION_CODE)
                        .put("versionName", CoreBuildInfo.VERSION_NAME)
                        .toString()
                }

                // exec 请求根据 asRoot 参数路由
                if (method == "exec") {
                    val asRoot = req.optJSONObject("params")?.optBoolean("asRoot", false) ?: false
                    val workerType = if (asRoot) Config.WorkerType.ROOT else Config.WorkerType.SHELL

                    // 检查目标 Worker 是否存在
                    if (asRoot && !SystemUtils.isRoot()) {
                        return JSONObject().put("success", false).put("error", "RootWorker not available (Master not Root)").toString()
                    }

                    return routeRequestToWorker(workerType, requestStr)
                }

                // exit 请求（系统控制）
                if (method == "exit") {
                    return JSONObject().put("success", true).toString()
                }

                // 其他 system 请求
                JSONObject().put("success", false).put("error", "Unknown system method").toString()
            } catch (e: Exception) {
                JSONObject().put("success", false).put("error", "Invalid request").toString()
            }
        }

        // 其他 target 使用静态路由表
        val workerType = Config.ROUTING_TABLE[target]
        if (workerType == null) {
            return JSONObject().put("success", false).put("error", "No route").toString()
        }

        return routeRequestToWorker(workerType, requestStr)
    }

    // ================= App 进程守护逻辑 =================

    /**
     * 启动 App 进程监控器
     * 定期检查 App 进程是否存活，如果被杀则自动重启
     */
    private fun startAppWatcher() {
        val packageName = appPackageName
        if (packageName == null) {
            println("⚠️ App package name not provided, skipping App watcher")
            return
        }

        println(">>> App Watcher: Starting to monitor app package: $packageName <<<")

        appWatcherJob = Thread {
            var restartAttempts = 0
            val maxRestartAttempts = 10
            val checkInterval = 30_000L // 30秒检查一次

            while (isRunning && !Thread.currentThread().isInterrupted) {
                try {
                    Thread.sleep(checkInterval)
                } catch (e: InterruptedException) {
                    break
                }

                // 检查 App 进程是否存活
                val isAppAlive = isAppProcessAlive(packageName)

                if (!isAppAlive) {
                    println("⚠️ App Watcher: App process not found, attempting to restart...")

                    if (restartAttempts < maxRestartAttempts) {
                        restartAttempts++
                        val success = restartApp(packageName)
                        if (success) {
                            println("✅ App Watcher: Successfully restarted App service ($restartAttempts/$maxRestartAttempts)")
                        } else {
                            println("❌ App Watcher: Failed to restart App service ($restartAttempts/$maxRestartAttempts)")
                        }
                    } else {
                        println("⚠️ App Watcher: Max restart attempts reached, giving up")
                        break
                    }
                } else {
                    // App 存活，重置计数器
                    restartAttempts = 0
                }
            }
        }.apply { start() }
    }

    /**
     * 检查 App 进程是否存活
     */
    private fun isAppProcessAlive(packageName: String): Boolean {
        return try {
            // 使用 pidof 检查进程
            val process = ProcessBuilder("sh", "-c", "pidof -s $packageName").start()
            val output = process.inputStream.bufferedReader().use { it.readText().trim() }
            process.waitFor()

            // 如果有输出说明进程存在
            output.isNotEmpty()

        } catch (e: Exception) {
            // 如果 pidof 不可用，使用 ps 命令
            try {
                val process = ProcessBuilder("sh", "-c", "ps | $packageName").start()
                val output = process.inputStream.bufferedReader().use { it.readText() }
                process.waitFor()
                output.contains(packageName)
            } catch (e2: Exception) {
                false
            }
        }
    }

    /**
     * 重启 App 服务
     */
    private fun restartApp(packageName: String): Boolean {
        return try {
            // 使用 am start-service 命令重启 TriggerService
            val serviceComponent = "$packageName/${packageName}.services.TriggerService"
            val command = "am start-service -n $serviceComponent"

            println(">>> App Watcher: Executing: $command <<<")

            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            val exitCode = process.waitFor()

            // 读取错误输出
            val error = process.errorStream.bufferedReader().use { it.readText().trim() }
            if (error.isNotEmpty()) {
                System.err.println("App Watcher Error: $error")
            }

            exitCode == 0

        } catch (e: Exception) {
            System.err.println("❌ App Watcher: Failed to restart app: ${e.message}")
            e.printStackTrace()
            false
        }
    }
}

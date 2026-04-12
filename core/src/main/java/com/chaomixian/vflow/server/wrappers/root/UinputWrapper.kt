package com.chaomixian.vflow.server.wrappers.root

import com.chaomixian.vflow.server.common.Logger
import com.chaomixian.vflow.server.common.utils.DisplayCaptureUtils
import com.chaomixian.vflow.server.wrappers.IWrapper
import org.json.JSONObject
import java.io.BufferedWriter
import java.io.File
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

class UinputWrapper : IWrapper {

    companion object {
        private const val TAG = "UinputWrapper"
        private const val DEVICE_ID = 1
        private const val REGISTER_DELAY_MS = 120L
        private const val MIN_DURATION_MS = 100L
        private const val MOVE_INTERVAL_MS = 8L
        private const val SYNC_TIMEOUT_MS = 5_000L
        private const val TRACKING_ID = 1
        private const val SLOT_ID = 0
        private const val TOUCH_MAJOR = 5
        private const val PRESSURE = 50
    }

    private val sessionLock = Any()
    private val syncCounter = AtomicLong(0L)
    @Volatile
    private var session: UinputSession? = null

    override fun handle(method: String, params: JSONObject): JSONObject {
        val result = when (method) {
            "tap" -> tap(params.getInt("x"), params.getInt("y"))
            "longPress" -> longPress(
                params.getInt("x"),
                params.getInt("y"),
                params.optLong("duration", 600L)
            )
            "swipe" -> swipe(
                params.getInt("x1"),
                params.getInt("y1"),
                params.getInt("x2"),
                params.getInt("y2"),
                params.optLong("duration", 300L)
            )
            else -> OperationResult(false, "Unknown method: $method")
        }

        return JSONObject().apply {
            put("success", result.success)
            result.error?.let { put("error", it) }
        }
    }

    private fun tap(x: Int, y: Int): OperationResult {
        return executeGesture(
            listOf(
                GestureCommand.Inject(downEvents(x, y)),
                GestureCommand.Delay(40L),
                GestureCommand.Inject(upEvents())
            )
        )
    }

    private fun longPress(x: Int, y: Int, duration: Long): OperationResult {
        val pressDuration = duration.coerceAtLeast(MIN_DURATION_MS)
        return executeGesture(
            listOf(
                GestureCommand.Inject(downEvents(x, y)),
                GestureCommand.Delay(pressDuration),
                GestureCommand.Inject(upEvents())
            )
        )
    }

    private fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, duration: Long): OperationResult {
        val swipeDuration = duration.coerceAtLeast(MIN_DURATION_MS)
        val delaySlices = createDelaySlices(swipeDuration)
        val steps = delaySlices.size
        val commands = mutableListOf<GestureCommand>()

        commands += GestureCommand.Inject(downEvents(x1, y1))
        for ((index, delayMs) in delaySlices.withIndex()) {
            val progress = (index + 1).toFloat() / steps.toFloat()
            val currentX = x1 + ((x2 - x1) * progress).toInt()
            val currentY = y1 + ((y2 - y1) * progress).toInt()
            commands += GestureCommand.Delay(delayMs)
            commands += GestureCommand.Inject(moveEvents(currentX, currentY))
        }
        commands += GestureCommand.Inject(upEvents())

        return executeGesture(commands)
    }

    private fun executeGesture(commands: List<GestureCommand>): OperationResult {
        val uinputBinary = resolveUinputBinary()
            ?: return OperationResult(false, "uinput binary not found on device")

        val displayInfo = DisplayCaptureUtils.getDisplayInfo()
            ?: return OperationResult(false, "Unable to resolve display size")
        if (displayInfo.width <= 0 || displayInfo.height <= 0) {
            return OperationResult(false, "Invalid display size: ${displayInfo.width}x${displayInfo.height}")
        }

        synchronized(sessionLock) {
            val interactiveResult = executeGestureInteractively(
                uinputBinary = uinputBinary,
                screenWidth = displayInfo.width,
                screenHeight = displayInfo.height,
                commands = commands
            )
            if (interactiveResult.success) {
                return interactiveResult
            }

            Logger.warn(
                TAG,
                "Interactive uinput failed, falling back to file mode: ${interactiveResult.error.orEmpty()}"
            )
            closeSessionLocked()
            return executeGestureWithFile(
                uinputBinary = uinputBinary,
                screenWidth = displayInfo.width,
                screenHeight = displayInfo.height,
                commands = commands
            )
        }
    }

    private fun executeGestureInteractively(
        uinputBinary: String,
        screenWidth: Int,
        screenHeight: Int,
        commands: List<GestureCommand>
    ): OperationResult {
        val activeSession = ensureSessionLocked(uinputBinary, screenWidth, screenHeight)
            ?: return OperationResult(false, "failed to start interactive uinput session")

        return try {
            val syncToken = "gesture_${syncCounter.incrementAndGet()}"
            activeSession.writer.write(buildInteractivePayload(commands, syncToken))
            activeSession.writer.flush()

            if (waitForSync(activeSession, syncToken, SYNC_TIMEOUT_MS)) {
                Logger.info(TAG, "uinput gesture executed successfully via interactive session")
                OperationResult(true)
            } else {
                OperationResult(false, "timed out waiting for uinput sync")
            }
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to execute interactive uinput gesture", e)
            OperationResult(false, e.message ?: "interactive uinput failed")
        }
    }

    private fun executeGestureWithFile(
        uinputBinary: String,
        screenWidth: Int,
        screenHeight: Int,
        commands: List<GestureCommand>
    ): OperationResult {
        val payload = buildFilePayload(
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            commands = listOf(GestureCommand.Delay(REGISTER_DELAY_MS)) + commands
        )
        val tempFile = createTempFile()

        return try {
            tempFile.writeText(payload)
            val process = ProcessBuilder(uinputBinary, tempFile.absolutePath)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText().trim() }
            val exitCode = process.waitFor()

            if (exitCode == 0) {
                Logger.info(TAG, "uinput gesture executed successfully via file mode")
                OperationResult(true)
            } else {
                Logger.warn(TAG, "uinput file-mode gesture failed: $output")
                OperationResult(false, output.ifBlank { "uinput exited with code $exitCode" })
            }
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to execute file-mode uinput gesture", e)
            OperationResult(false, e.message ?: "file-mode uinput failed")
        } finally {
            tempFile.delete()
        }
    }

    private fun buildFilePayload(
        screenWidth: Int,
        screenHeight: Int,
        commands: List<GestureCommand>
    ): String {
        val builder = StringBuilder()
        builder.appendLine(registerCommand(screenWidth, screenHeight))

        for (command in commands) {
            when (command) {
                is GestureCommand.Delay -> builder.appendLine(
                    """
                    {
                      "id": $DEVICE_ID,
                      "command": "delay",
                      "duration": ${command.durationMs}
                    }
                    """.trimIndent()
                )
                is GestureCommand.Inject -> builder.appendLine(
                    """
                    {
                      "id": $DEVICE_ID,
                      "command": "inject",
                      "events": [${command.events.joinToString(", ")}]
                    }
                    """.trimIndent()
                )
            }
        }

        builder.appendLine(
            """
            {
              "id": $DEVICE_ID,
              "command": "sync",
              "syncToken": "gesture_complete"
            }
            """.trimIndent()
        )
        return builder.toString()
    }

    private fun buildInteractivePayload(
        commands: List<GestureCommand>,
        syncToken: String
    ): String {
        val builder = StringBuilder()
        var scheduledDelayMs = 0L

        builder.appendLine(
            """
            {
              "id": $DEVICE_ID,
              "command": "updateTimeBase"
            }
            """.trimIndent()
        )

        for (command in commands) {
            when (command) {
                is GestureCommand.Delay -> {
                    scheduledDelayMs += command.durationMs
                    builder.appendLine(
                        """
                        {
                          "id": $DEVICE_ID,
                          "command": "delay",
                          "duration": $scheduledDelayMs
                        }
                        """.trimIndent()
                    )
                }
                is GestureCommand.Inject -> builder.appendLine(
                    """
                    {
                      "id": $DEVICE_ID,
                      "command": "inject",
                      "events": [${command.events.joinToString(", ")}]
                    }
                    """.trimIndent()
                )
            }
        }

        builder.appendLine(
            """
            {
              "id": $DEVICE_ID,
              "command": "sync",
              "syncToken": "$syncToken"
            }
            """.trimIndent()
        )

        return builder.toString()
    }

    private fun ensureSessionLocked(
        uinputBinary: String,
        screenWidth: Int,
        screenHeight: Int
    ): UinputSession? {
        val currentSession = session
        if (currentSession != null &&
            currentSession.process.isAlive &&
            currentSession.screenWidth == screenWidth &&
            currentSession.screenHeight == screenHeight
        ) {
            return currentSession
        }

        closeSessionLocked()
        return startSessionLocked(uinputBinary, screenWidth, screenHeight)
    }

    private fun startSessionLocked(
        uinputBinary: String,
        screenWidth: Int,
        screenHeight: Int
    ): UinputSession? {
        return try {
            val process = ProcessBuilder(uinputBinary, "-")
                .redirectErrorStream(true)
                .start()
            val writer = process.outputStream.bufferedWriter()
            val outputQueue = LinkedBlockingQueue<String>()
            val readerThread = Thread({
                process.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        Logger.info(TAG, "uinput stdout: $line")
                        outputQueue.offer(line)
                    }
                }
            }, "uinput-reader").apply {
                isDaemon = true
                start()
            }

            val newSession = UinputSession(
                process = process,
                writer = writer,
                outputQueue = outputQueue,
                readerThread = readerThread,
                screenWidth = screenWidth,
                screenHeight = screenHeight
            )
            session = newSession

            val registerToken = "register_${syncCounter.incrementAndGet()}"
            writer.write(registerCommand(screenWidth, screenHeight))
            writer.newLine()
            writer.write(
                """
                {
                  "id": $DEVICE_ID,
                  "command": "sync",
                  "syncToken": "$registerToken"
                }
                """.trimIndent()
            )
            writer.newLine()
            writer.flush()

            if (!waitForSync(newSession, registerToken, SYNC_TIMEOUT_MS)) {
                Logger.warn(TAG, "Timed out waiting for interactive uinput registration sync")
                closeSessionLocked()
                return null
            }

            Thread.sleep(REGISTER_DELAY_MS)
            Logger.info(TAG, "Interactive uinput session ready")
            newSession
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to start interactive uinput session", e)
            closeSessionLocked()
            null
        }
    }

    private fun waitForSync(
        session: UinputSession,
        syncToken: String,
        timeoutMs: Long
    ): Boolean {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        while (true) {
            if (!session.process.isAlive) {
                return false
            }

            val remainingNs = deadline - System.nanoTime()
            if (remainingNs <= 0L) {
                return false
            }

            val line = session.outputQueue.poll(remainingNs, TimeUnit.NANOSECONDS) ?: return false
            if (line.contains(syncToken)) {
                return true
            }
        }
    }

    private fun closeSessionLocked() {
        val currentSession = session ?: return
        session = null

        try {
            currentSession.writer.close()
        } catch (_: Exception) {
        }

        if (currentSession.process.isAlive) {
            currentSession.process.destroy()
        }
    }

    private fun createDelaySlices(durationMs: Long): List<Long> {
        val steps = (durationMs / MOVE_INTERVAL_MS).coerceAtLeast(1L).toInt()
        val base = durationMs / steps
        val extra = durationMs % steps
        return List(steps) { index ->
            base + if (index < extra) 1L else 0L
        }
    }

    private fun registerCommand(screenWidth: Int, screenHeight: Int): String {
        val maxX = (screenWidth - 1).coerceAtLeast(1)
        val maxY = (screenHeight - 1).coerceAtLeast(1)
        return """
            {
              "id": $DEVICE_ID,
              "command": "register",
              "name": "vFlow UInput Touchscreen",
              "vid": 0x18d1,
              "pid": 0x4ee7,
              "bus": "usb",
              "port": "vflow/uinput-touch",
              "configuration": [
                {"type": "UI_SET_EVBIT", "data": ["EV_KEY", "EV_ABS"]},
                {"type": "UI_SET_KEYBIT", "data": ["BTN_TOUCH", "BTN_TOOL_FINGER"]},
                {"type": "UI_SET_PROPBIT", "data": ["INPUT_PROP_DIRECT"]},
                {"type": "UI_SET_ABSBIT", "data": ["ABS_MT_SLOT", "ABS_MT_TOUCH_MAJOR", "ABS_MT_PRESSURE", "ABS_MT_POSITION_X", "ABS_MT_POSITION_Y", "ABS_MT_TRACKING_ID"]}
              ],
              "abs_info": [
                {"code": "ABS_MT_SLOT", "info": {"value": 0, "minimum": 0, "maximum": $SLOT_ID, "fuzz": 0, "flat": 0, "resolution": 0}},
                {"code": "ABS_MT_TOUCH_MAJOR", "info": {"value": 0, "minimum": 0, "maximum": 15, "fuzz": 0, "flat": 0, "resolution": 0}},
                {"code": "ABS_MT_PRESSURE", "info": {"value": 0, "minimum": 0, "maximum": 255, "fuzz": 0, "flat": 0, "resolution": 0}},
                {"code": "ABS_MT_POSITION_X", "info": {"value": 0, "minimum": 0, "maximum": $maxX, "fuzz": 0, "flat": 0, "resolution": 1}},
                {"code": "ABS_MT_POSITION_Y", "info": {"value": 0, "minimum": 0, "maximum": $maxY, "fuzz": 0, "flat": 0, "resolution": 1}},
                {"code": "ABS_MT_TRACKING_ID", "info": {"value": 0, "minimum": 0, "maximum": 65535, "fuzz": 0, "flat": 0, "resolution": 0}}
              ]
            }
        """.trimIndent()
    }

    private fun downEvents(x: Int, y: Int): List<String> {
        return listOf(
            "\"EV_ABS\"", "\"ABS_MT_SLOT\"", SLOT_ID.toString(),
            "\"EV_ABS\"", "\"ABS_MT_TRACKING_ID\"", TRACKING_ID.toString(),
            "\"EV_ABS\"", "\"ABS_MT_POSITION_X\"", x.toString(),
            "\"EV_ABS\"", "\"ABS_MT_POSITION_Y\"", y.toString(),
            "\"EV_ABS\"", "\"ABS_MT_TOUCH_MAJOR\"", TOUCH_MAJOR.toString(),
            "\"EV_ABS\"", "\"ABS_MT_PRESSURE\"", PRESSURE.toString(),
            "\"EV_KEY\"", "\"BTN_TOUCH\"", "1",
            "\"EV_KEY\"", "\"BTN_TOOL_FINGER\"", "1",
            "\"EV_SYN\"", "\"SYN_REPORT\"", "0"
        )
    }

    private fun moveEvents(x: Int, y: Int): List<String> {
        return listOf(
            "\"EV_ABS\"", "\"ABS_MT_SLOT\"", SLOT_ID.toString(),
            "\"EV_ABS\"", "\"ABS_MT_POSITION_X\"", x.toString(),
            "\"EV_ABS\"", "\"ABS_MT_POSITION_Y\"", y.toString(),
            "\"EV_ABS\"", "\"ABS_MT_TOUCH_MAJOR\"", TOUCH_MAJOR.toString(),
            "\"EV_ABS\"", "\"ABS_MT_PRESSURE\"", PRESSURE.toString(),
            "\"EV_SYN\"", "\"SYN_REPORT\"", "0"
        )
    }

    private fun upEvents(): List<String> {
        return listOf(
            "\"EV_ABS\"", "\"ABS_MT_SLOT\"", SLOT_ID.toString(),
            "\"EV_ABS\"", "\"ABS_MT_TOUCH_MAJOR\"", "0",
            "\"EV_ABS\"", "\"ABS_MT_PRESSURE\"", "0",
            "\"EV_ABS\"", "\"ABS_MT_TRACKING_ID\"", "-1",
            "\"EV_KEY\"", "\"BTN_TOUCH\"", "0",
            "\"EV_KEY\"", "\"BTN_TOOL_FINGER\"", "0",
            "\"EV_SYN\"", "\"SYN_REPORT\"", "0"
        )
    }

    private fun resolveUinputBinary(): String? {
        val knownPaths = listOf("/system/bin/uinput", "/system/xbin/uinput")
        knownPaths.firstOrNull { File(it).canExecute() }?.let { return it }

        return try {
            val process = ProcessBuilder("sh", "-c", "command -v uinput").start()
            val output = process.inputStream.bufferedReader().use { it.readText().trim() }
            if (process.waitFor() == 0 && output.isNotBlank()) output else null
        } catch (_: Exception) {
            null
        }
    }

    private fun createTempFile(): File {
        val tempDir = File("/data/local/tmp").takeIf { it.exists() && it.canWrite() }
        return if (tempDir != null) {
            File.createTempFile("vflow_uinput_", ".json", tempDir)
        } else {
            File.createTempFile("vflow_uinput_", ".json")
        }
    }

    private data class OperationResult(
        val success: Boolean,
        val error: String? = null
    )

    private data class UinputSession(
        val process: Process,
        val writer: BufferedWriter,
        val outputQueue: LinkedBlockingQueue<String>,
        val readerThread: Thread,
        val screenWidth: Int,
        val screenHeight: Int
    )

    private sealed class GestureCommand {
        data class Delay(val durationMs: Long) : GestureCommand()
        data class Inject(val events: List<String>) : GestureCommand()
    }
}

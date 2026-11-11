package com.chaomixian.vflow.core.recording

import com.chaomixian.vflow.core.utils.StorageManager
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * UI 录制文件存储。
 *
 * 目录结构：
 * ```
 * /sdcard/vFlow/recordings/
 *   rec_20260101_120000_000_ab12.json
 *   rec_20260101_120000_000_ab12/
 *     seg_0.jpg
 *     seg_1.jpg
 * ```
 */
object UiRecordingStore {

    private const val DIR_NAME = "recordings"
    private const val EXTENSION = "json"

    private val gson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .disableHtmlEscaping()
        .create()

    /** 录制根目录：/sdcard/vFlow/recordings */
    val recordingsDir: File
        get() {
            val dir = File(StorageManager.rootDir, DIR_NAME)
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

    /** 生成一个唯一且可安全用作文件名的录制 ID。 */
    fun createId(): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        val random = UUID.randomUUID().toString().replace("-", "").take(6)
        return "rec_${timestamp}_$random"
    }

    /** 生成默认录制名称，例如 `UI录制_20260101_120000`。 */
    fun defaultName(): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return "UI录制_$timestamp"
    }

    /** 获取某个录制的截图目录。 */
    fun recordingDir(id: String): File {
        val dir = File(recordingsDir, id)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /** 读取全部录制，按创建时间倒序排列；坏文件会被跳过。 */
    fun listRecordings(): List<UiRecording> {
        val files = recordingsDir.listFiles { file ->
            file.isFile && file.extension.equals(EXTENSION, ignoreCase = true)
        } ?: return emptyList()
        return files
            .mapNotNull { loadFile(it) }
            .sortedByDescending { it.createdAt }
    }

    /**
     * 按 ID、名称或文件路径加载录制。
     * 传入路径时可以是绝对路径，也可以是相对于录制根目录的路径。
     */
    fun load(idOrNameOrPath: String): UiRecording? {
        val value = idOrNameOrPath.trim()
        if (value.isEmpty()) return null

        // 1. 绝对路径
        val direct = File(value)
        if (direct.isFile) return loadFile(direct)

        // 2. 相对于录制根目录的路径
        val relative = File(recordingsDir, value)
        if (relative.isFile) return loadFile(relative)

        // 3. ID
        val byId = File(recordingsDir, "$value.$EXTENSION")
        if (byId.isFile) return loadFile(byId)

        // 4. 名称
        return listRecordings().firstOrNull { it.name == value }
    }

    /** 保存录制并返回 JSON 文件。ID 为空时自动生成。 */
    fun save(recording: UiRecording): File {
        val id = recording.id.ifBlank { createId() }
        val target = File(recordingsDir, "$id.$EXTENSION")
        val temp = File(recordingsDir, "$id.$EXTENSION.tmp")
        val json = gson.toJson(recording.copy(id = id))
        temp.writeText(json, Charsets.UTF_8)
        if (target.exists() && !target.delete()) {
            temp.delete()
            throw IllegalStateException("无法覆盖已有录制文件: ${target.absolutePath}")
        }
        if (!temp.renameTo(target)) {
            // 某些 ROM 不支持 renameTo 覆盖，退化为直接写入
            target.writeText(json, Charsets.UTF_8)
            temp.delete()
        }
        return target
    }

    /** 重命名已有录制，仅修改 JSON 中的 name，截图目录不变。 */
    fun rename(recording: UiRecording, newName: String): Boolean {
        val id = (recording.id as String?)?.takeIf { it.isNotBlank() } ?: return false
        val fileName = "$id.$EXTENSION"
        val file = File(recordingsDir, fileName)
        if (!file.isFile) return false
        return runCatching {
            val element = JsonParser.parseString(file.readText(Charsets.UTF_8))
            if (!element.isJsonObject) return@runCatching false
            val obj = element.asJsonObject
            normalizeRecordingObject(obj, file)
            obj.addProperty("name", newName.trim().ifBlank { defaultName() })
            val json = gson.toJson(obj)
            val temp = File(recordingsDir, "$fileName.tmp")
            temp.writeText(json, Charsets.UTF_8)
            if (file.exists() && !file.delete()) {
                temp.delete()
                return@runCatching false
            }
            if (!temp.renameTo(file)) {
                file.writeText(json, Charsets.UTF_8)
                temp.delete()
            }
            true
        }.getOrDefault(false)
    }

    /** 删除录制 JSON 与截图目录。 */
    fun delete(recording: UiRecording): Boolean {
        val id = (recording.id as String?)?.takeIf { it.isNotBlank() } ?: return false
        var success = true
        val json = File(recordingsDir, "$id.$EXTENSION")
        if (json.exists() && !json.delete()) success = false
        val dir = File(recordingsDir, id)
        if (dir.exists() && !dir.deleteRecursively()) success = false
        return success
    }

    /** 计算录制占用空间（JSON + 截图）。 */
    fun sizeBytes(recording: UiRecording): Long {
        val id = (recording.id as String?)?.takeIf { it.isNotBlank() } ?: return 0L
        val json = File(recordingsDir, "$id.$EXTENSION")
        val jsonSize = if (json.isFile) json.length() else 0L
        val dir = File(recordingsDir, id)
        val screenshotSize = if (dir.isDirectory) {
            dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        } else {
            0L
        }
        return jsonSize + screenshotSize
    }

    private fun loadFile(file: File): UiRecording? {
        return runCatching {
            val element = JsonParser.parseString(file.readText(Charsets.UTF_8))
            if (!element.isJsonObject) return@runCatching null
            val obj = element.asJsonObject
            normalizeRecordingObject(obj, file)
            val parsed = gson.fromJson(obj, UiRecording::class.java) ?: return@runCatching null
            val safeId = (parsed.id as String?)?.takeIf { it.isNotBlank() }
                ?: file.nameWithoutExtension
            val safeName = (parsed.name as String?)?.takeIf { it.isNotBlank() } ?: safeId
            parsed.copy(id = safeId, name = safeName)
        }.getOrNull()
    }

    private fun normalizeString(obj: JsonObject, key: String, fallback: String) {
        val value = obj.get(key)
        if (value == null || value.isJsonNull || !value.isJsonPrimitive ||
            !value.asJsonPrimitive.isString || value.asString.isBlank()
        ) {
            obj.addProperty(key, fallback)
        }
    }

    /**
     * 补齐损坏或旧版本 JSON 中缺失的字段，避免 Gson 把非空字段反序列化成 null。
     * 结构完全无法识别时由外层 runCatching 跳过该文件。
     */
    private fun normalizeRecordingObject(obj: JsonObject, file: File) {
        val fallbackId = file.nameWithoutExtension
        normalizeString(obj, "id", fallbackId)
        normalizeString(obj, "name", fallbackId)
        if (!obj.has("createdAt") || obj.get("createdAt").isJsonNull) {
            obj.addProperty("createdAt", file.lastModified())
        }
        listOf("durationMs", "screenWidth", "screenHeight").forEach { field ->
            if (!obj.has(field) || obj.get(field).isJsonNull) obj.addProperty(field, 0L)
        }
        if (!obj.has("segments") || obj.get("segments").isJsonNull) {
            obj.add("segments", JsonArray())
        }
        if (!obj.has("events") || obj.get("events").isJsonNull) {
            obj.add("events", JsonArray())
        }
        obj.getAsJsonArray("segments")?.forEach { segmentElement ->
            if (!segmentElement.isJsonObject) return@forEach
            val segment = segmentElement.asJsonObject
            if (!segment.has("nodes") || segment.get("nodes").isJsonNull) {
                segment.add("nodes", JsonArray())
            }
            listOf("packageName", "activityName").forEach { field ->
                if (!segment.has(field) || segment.get(field).isJsonNull) {
                    segment.addProperty(field, "")
                }
            }
            listOf("startMs", "endMs").forEach { field ->
                if (!segment.has(field) || segment.get(field).isJsonNull) {
                    segment.addProperty(field, 0L)
                }
            }
        }
    }
}

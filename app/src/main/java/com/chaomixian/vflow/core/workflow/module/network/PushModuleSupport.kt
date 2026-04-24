package com.chaomixian.vflow.core.workflow.module.network

import android.content.Context
import com.chaomixian.vflow.ui.settings.ModuleConfigActivity
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.OkHttpClient
import java.net.InetSocketAddress
import java.net.Proxy

internal fun applyProxyIfConfigured(builder: OkHttpClient.Builder, context: Context): OkHttpClient.Builder {
    val proxy = readConfiguredProxy(context) ?: return builder
    return builder.proxy(proxy)
}

internal fun readConfiguredProxy(context: Context): Proxy? {
    val proxyAddress = context.getSharedPreferences(ModuleConfigActivity.PREFS_NAME, Context.MODE_PRIVATE)
        .getString(ModuleConfigActivity.KEY_NETWORK_PROXY, null)
        ?.trim()
        ?: return null

    return parseProxy(proxyAddress) ?: return null
}

internal fun parseProxy(address: String): Proxy? {
    return try {
        val (host, port, type) = when {
            address.startsWith("socks5://", ignoreCase = true) -> {
                val stripped = address.removePrefix("socks5://").removePrefix("SOCKS5://")
                val idx = stripped.lastIndexOf(':')
                if (idx < 0) return null
                Triple(stripped.substring(0, idx), stripped.substring(idx + 1).toInt(), Proxy.Type.SOCKS)
            }
            address.startsWith("socks://", ignoreCase = true) -> {
                val stripped = address.removePrefix("socks://").removePrefix("SOCKS://")
                val idx = stripped.lastIndexOf(':')
                if (idx < 0) return null
                Triple(stripped.substring(0, idx), stripped.substring(idx + 1).toInt(), Proxy.Type.SOCKS)
            }
            address.startsWith("http://", ignoreCase = true) || address.startsWith("https://", ignoreCase = true) -> {
                val stripped = address.substringAfter("://")
                val idx = stripped.lastIndexOf(':')
                if (idx < 0) return null
                Triple(stripped.substring(0, idx), stripped.substring(idx + 1).toInt(), Proxy.Type.HTTP)
            }
            else -> {
                val idx = address.lastIndexOf(':')
                if (idx < 0) return null
                Triple(address.substring(0, idx), address.substring(idx + 1).toInt(), Proxy.Type.HTTP)
            }
        }
        Proxy(type, InetSocketAddress.createUnresolved(host, port))
    } catch (_: Exception) {
        null
    }
}

internal data class BarkPushResponse(
    val code: Int,
    val message: String
)

internal data class TelegramPushResponse(
    val ok: Boolean,
    val description: String,
    val messageId: Long?,
    val chatId: String?
)

internal data class ParsedWebhookHeaders(
    val headers: Map<String, String>,
    val error: String? = null
)

internal fun buildDiscordContent(
    content: String,
    mentionUserIds: String
): String {
    val normalizedMentions = mentionUserIds
        .split("|", ",", "\n")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .joinToString(" ") { "<@$it>" }

    return when {
        normalizedMentions.isBlank() -> content
        content.isBlank() -> normalizedMentions
        else -> "$normalizedMentions $content"
    }
}

internal fun buildDiscordPushPayload(
    content: String,
    username: String,
    avatarUrl: String,
    tts: Boolean
): String {
    val payload = linkedMapOf<String, Any>(
        "content" to content
    )
    if (username.isNotBlank()) {
        payload["username"] = username
    }
    if (avatarUrl.isNotBlank()) {
        payload["avatar_url"] = avatarUrl
    }
    if (tts) {
        payload["tts"] = true
    }
    return Gson().toJson(payload)
}

internal fun parseBarkPushResponse(responseBody: String): BarkPushResponse? {
    return try {
        val json = JsonParser.parseString(responseBody).asJsonObject
        BarkPushResponse(
            code = json.get("code")?.asInt ?: -1,
            message = json.get("message")?.asString ?: ""
        )
    } catch (_: Exception) {
        null
    }
}

internal fun parseTelegramPushResponse(responseBody: String): TelegramPushResponse? {
    return try {
        val json = JsonParser.parseString(responseBody).asJsonObject
        val result = json.getAsJsonObject("result")
        val chat = result?.getAsJsonObject("chat")
        TelegramPushResponse(
            ok = json.get("ok")?.asBoolean ?: false,
            description = json.get("description")?.asString ?: "",
            messageId = result?.get("message_id")?.asLong,
            chatId = chat?.get("id")?.asString
        )
    } catch (_: Exception) {
        null
    }
}

internal fun buildBarkPushPayload(
    title: String,
    body: String,
    subtitle: String,
    level: String,
    volume: String,
    badge: String,
    icon: String,
    image: String,
    autoCopy: Boolean,
    copy: String,
    jumpUrl: String
): String {
    val payload = linkedMapOf<String, Any>(
        "title" to title,
        "body" to body
    )
    if (subtitle.isNotBlank()) {
        payload["subtitle"] = subtitle
    }
    if (level.isNotBlank()) {
        payload["level"] = level
    }
    badge.toIntOrNull()?.let { payload["badge"] = it }
    volume.toDoubleOrNull()?.let { payload["volume"] = it }
    if (icon.isNotBlank()) {
        payload["icon"] = icon
    }
    if (image.isNotBlank()) {
        payload["image"] = image
    }
    if (autoCopy) {
        payload["autoCopy"] = "1"
    }
    if (copy.isNotBlank()) {
        payload["copy"] = copy
    }
    if (jumpUrl.isNotBlank()) {
        payload["url"] = jumpUrl
    }
    return Gson().toJson(payload)
}

internal fun buildTelegramPushPayload(
    chatId: String,
    text: String,
    parseModeApiValue: String?,
    disableWebPreview: Boolean,
    messageThreadId: String
): String {
    val payload = linkedMapOf<String, Any>(
        "chat_id" to chatId,
        "text" to text
    )
    if (!parseModeApiValue.isNullOrBlank()) {
        payload["parse_mode"] = parseModeApiValue
    }
    if (disableWebPreview) {
        payload["disable_web_page_preview"] = true
    }
    messageThreadId.toIntOrNull()?.let { payload["message_thread_id"] = it }
    return Gson().toJson(payload)
}

internal fun parseHeadersJson(headersJson: String): ParsedWebhookHeaders {
    if (headersJson.isBlank()) {
        return ParsedWebhookHeaders(emptyMap())
    }
    return try {
        val jsonObject = JsonParser.parseString(headersJson).asJsonObject
        ParsedWebhookHeaders(jsonObject.entrySet().associate { (key, value) -> key to value.asString })
    } catch (e: Exception) {
        ParsedWebhookHeaders(emptyMap(), e.message ?: "请求头 JSON 格式错误")
    }
}

internal fun buildDefaultWebhookRawBody(title: String, message: String): String {
    return if (title.isBlank()) {
        message
    } else {
        "$title\n$message"
    }
}

internal fun buildDefaultWebhookFormFields(title: String, message: String): Map<String, String> {
    val fields = linkedMapOf<String, String>()
    if (title.isNotBlank()) {
        fields["title"] = title
    }
    fields["message"] = message
    return fields
}

internal fun parseJsonObjectStringMap(rawJson: String): Map<String, String>? {
    return try {
        val jsonObject = JsonParser.parseString(rawJson).asJsonObject
        jsonObject.entrySet().associate { (key, value) ->
            key to when {
                value.isJsonNull -> ""
                value.isJsonPrimitive -> value.asString
                else -> Gson().toJson(value)
            }
        }
    } catch (_: Exception) {
        null
    }
}

internal fun buildDefaultWebhookJsonPayload(title: String, message: String): JsonObject {
    val jsonObject = JsonObject()
    if (title.isNotBlank()) {
        jsonObject.addProperty("title", title)
    }
    jsonObject.addProperty("message", message)
    jsonObject.addProperty("timestamp", System.currentTimeMillis())
    return jsonObject
}

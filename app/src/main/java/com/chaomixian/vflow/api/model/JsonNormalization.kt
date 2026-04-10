package com.chaomixian.vflow.api.model

internal fun normalizeImportedJsonObject(value: Map<*, *>): Map<String, Any?> {
    return LinkedHashMap<String, Any?>().apply {
        value.forEach { (key, nestedValue) ->
            key?.toString()?.let { normalizedKey ->
                put(normalizedKey, normalizeImportedJsonValue(nestedValue))
            }
        }
    }
}

internal fun normalizeImportedJsonValue(value: Any?): Any? {
    return when (value) {
        is Map<*, *> -> normalizeImportedJsonObject(value)
        is List<*> -> value.map { normalizeImportedJsonValue(it) }
        else -> value
    }
}

package com.chaomixian.vflow.ui.workflow_editor

import java.nio.charset.StandardCharsets

internal data class ParamPath(
    val rootId: String,
    val segments: List<Segment>
) {
    val isTopLevel: Boolean
        get() = segments.isEmpty()

    sealed interface Segment {
        data class Key(val value: String) : Segment
        data class Index(val value: Int) : Segment
    }

    companion object {
        private const val ENCODED_SEGMENT_PREFIX = "%"

        fun parse(rawPath: String): ParamPath {
            val parts = splitPath(rawPath)
            require(parts.isNotEmpty() && parts.first().isNotBlank()) {
                "Parameter path must start with a root id"
            }
            return ParamPath(
                rootId = parts.first(),
                segments = parts.drop(1).map { rawSegment ->
                    decodeSegmentOrNull(rawSegment)?.let(Segment::Key)
                        ?: rawSegment.toIntOrNull()?.let(Segment::Index)
                        ?: Segment.Key(rawSegment)
                }
            )
        }

        fun encodeSegment(rawSegment: String): String {
            val bytes = rawSegment.toByteArray(StandardCharsets.UTF_8)
            val hex = bytes.joinToString(separator = "") { byte ->
                "%02x".format(byte.toInt() and 0xff)
            }
            return ENCODED_SEGMENT_PREFIX + hex
        }

        private fun splitPath(rawPath: String): List<String> {
            return rawPath.split('.')
        }

        private fun decodeSegmentOrNull(rawSegment: String): String? {
            if (!rawSegment.startsWith(ENCODED_SEGMENT_PREFIX)) return null
            val hex = rawSegment.removePrefix(ENCODED_SEGMENT_PREFIX)
            if (hex.length % 2 != 0) return rawSegment
            val bytes = ByteArray(hex.length / 2)
            for (index in bytes.indices) {
                val start = index * 2
                val value = hex.substring(start, start + 2).toIntOrNull(16) ?: return rawSegment
                bytes[index] = value.toByte()
            }
            return String(bytes, StandardCharsets.UTF_8)
        }
    }
}

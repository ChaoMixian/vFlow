package com.chaomixian.vflow.ui.workflow_editor

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
        fun parse(rawPath: String): ParamPath {
            val parts = rawPath.split('.')
            require(parts.isNotEmpty() && parts.first().isNotBlank()) {
                "Parameter path must start with a root id"
            }
            return ParamPath(
                rootId = parts.first(),
                segments = parts.drop(1).map { part ->
                    part.toIntOrNull()?.let(Segment::Index) ?: Segment.Key(part)
                }
            )
        }
    }
}

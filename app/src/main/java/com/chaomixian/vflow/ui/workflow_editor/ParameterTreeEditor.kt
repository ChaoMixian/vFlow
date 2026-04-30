package com.chaomixian.vflow.ui.workflow_editor

internal class ParameterTreeEditor(
    private val parameters: MutableMap<String, Any?>
) {
    fun set(path: ParamPath, value: Any?) {
        if (path.isTopLevel) {
            parameters[path.rootId] = value
            return
        }

        val updatedRoot = setNested(parameters[path.rootId], path.segments, value) ?: return
        parameters[path.rootId] = updatedRoot
    }

    fun clear(path: ParamPath, topLevelDefaultValue: Any?) {
        if (path.isTopLevel) {
            parameters[path.rootId] = topLevelDefaultValue
            return
        }

        val currentRoot = parameters[path.rootId] ?: return
        val updatedRoot = clearNested(currentRoot, path.segments) ?: return
        parameters[path.rootId] = updatedRoot
    }

    private fun setNested(
        currentValue: Any?,
        segments: List<ParamPath.Segment>,
        newValue: Any?
    ): Any? {
        val segment = segments.first()
        val remaining = segments.drop(1)
        return when (segment) {
            is ParamPath.Segment.Key -> {
                val map = currentValue.toEditableMap() ?: mutableMapOf()
                if (remaining.isEmpty()) {
                    map[segment.value] = newValue
                } else {
                    val updatedChild = setNested(map[segment.value], remaining, newValue) ?: return null
                    map[segment.value] = updatedChild
                }
                map
            }

            is ParamPath.Segment.Index -> {
                val list = (currentValue as? List<*>)?.toMutableList() ?: return null
                if (segment.value !in list.indices) return null
                if (remaining.isEmpty()) {
                    list[segment.value] = newValue
                } else {
                    val updatedChild = setNested(list[segment.value], remaining, newValue) ?: return null
                    list[segment.value] = updatedChild
                }
                list
            }
        }
    }

    private fun clearNested(
        currentValue: Any?,
        segments: List<ParamPath.Segment>
    ): Any? {
        val segment = segments.first()
        val remaining = segments.drop(1)
        return when (segment) {
            is ParamPath.Segment.Key -> {
                val map = currentValue.toEditableMap() ?: return null
                if (remaining.isEmpty()) {
                    map[segment.value] = ""
                } else {
                    val updatedChild = clearNested(map[segment.value], remaining) ?: return null
                    map[segment.value] = updatedChild
                }
                map
            }

            is ParamPath.Segment.Index -> {
                val list = (currentValue as? List<*>)?.toMutableList() ?: return null
                if (segment.value !in list.indices) return null
                if (remaining.isEmpty()) {
                    list[segment.value] = ""
                } else {
                    val updatedChild = clearNested(list[segment.value], remaining) ?: return null
                    list[segment.value] = updatedChild
                }
                list
            }
        }
    }

    private fun Any?.toEditableMap(): MutableMap<Any?, Any?>? {
        val map = this as? Map<*, *> ?: return null
        return map.entries.associate { it.key to it.value }.toMutableMap()
    }
}

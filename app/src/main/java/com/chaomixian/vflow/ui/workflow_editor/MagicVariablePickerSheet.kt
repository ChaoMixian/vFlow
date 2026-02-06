// 文件: main/java/com/chaomixian/vflow/ui/workflow_editor/MagicVariablePickerSheet.kt
package com.chaomixian.vflow.ui.workflow_editor

import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.parcelize.Parcelize

/**
 * 代表一个可供选择的变量的数据模型。
 * @param variableReference 变量的引用字符串。
 * 对于魔法变量, 格式为 "{{stepId.outputId}}";
 * 对于命名变量, 格式为 "[[variableName]]"。
 * @param variableName 变量的可读名称。
 * @param originDescription 描述变量来源的文本, 如 "来自: 查找文本" 或 "命名变量 (数字)"。
 */
@Parcelize
data class MagicVariableItem(
    val variableReference: String,
    val variableName: String,
    val originDescription: String,
    val typeId: String = "vflow.type.any"
) : Parcelable

/**
 * RecyclerView 列表项的密封类，支持两种类型：
 * 1. ClearAction: 一个特殊操作项，用于清除当前输入框的魔法变量连接。
 * 2. VariableGroup: 代表一个完整的变量分组，包含标题和变量列表，将渲染在一个卡片中。
 */
sealed class PickerListItem {
    object ClearAction : PickerListItem()
    data class VariableGroup(val title: String, val variables: List<MagicVariableItem>) : PickerListItem()
}

/**
 * 魔法变量选择器底部表单 (BottomSheetDialogFragment)。
 * 显示可用魔法变量的分组列表以及一个“清除”选项。
 */
class MagicVariablePickerSheet : BottomSheetDialogFragment() {

    /** 选择回调：当用户选择一个变量或清除操作时触发。null 表示清除了选择。 */
    var onSelection: ((MagicVariableItem?) -> Unit)? = null

    companion object {
        /**
         * 创建 MagicVariablePickerSheet 实例，并接收分组后的变量数据和过滤条件。
         */
        fun newInstance(
            stepVariables: Map<String, List<MagicVariableItem>>,
            namedVariables: Map<String, List<MagicVariableItem>>,
            acceptsMagicVariable: Boolean,
            acceptsNamedVariable: Boolean,
            acceptedMagicVariableTypes: Set<String> = emptySet(),
            enableTypeFilter: Boolean = false
        ): MagicVariablePickerSheet {
            return MagicVariablePickerSheet().apply {
                arguments = Bundle().apply {
                    putSerializable("stepVariables", HashMap(stepVariables))
                    putSerializable("namedVariables", HashMap(namedVariables))
                    putBoolean("acceptsMagic", acceptsMagicVariable)
                    putBoolean("acceptsNamed", acceptsNamedVariable)
                    putSerializable("acceptedTypes", HashSet(acceptedMagicVariableTypes))
                    putBoolean("enableTypeFilter", enableTypeFilter)
                }
            }
        }
    }

    /** 创建并返回底部表单的视图。 */
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.sheet_magic_variable_picker, container, false)
        val recyclerView = view.findViewById<RecyclerView>(R.id.recycler_view_magic_variables)

        val acceptsMagic = arguments?.getBoolean("acceptsMagic", true) ?: true
        val acceptsNamed = arguments?.getBoolean("acceptsNamed", true) ?: true

        // Bundle 序列化会丢失 Map 顺序，需要手动排序
        @Suppress("UNCHECKED_CAST")
        val namedVariables = arguments?.getSerializable("namedVariables") as? Map<String, List<MagicVariableItem>> ?: emptyMap()
        @Suppress("UNCHECKED_CAST")
        val stepVariables = arguments?.getSerializable("stepVariables") as? Map<String, List<MagicVariableItem>> ?: emptyMap()

        // 将分组数据转换为 RecyclerView 的列表项
        val items = mutableListOf<PickerListItem>().apply {
            add(PickerListItem.ClearAction) // 总是添加“清除”选项

            // 添加命名变量分组（按原顺序）
            if (acceptsNamed) {
                namedVariables.forEach { (groupName, variableList) ->
                    add(PickerListItem.VariableGroup(groupName, variableList))
                }
            }

            // 添加步骤输出变量分组（按序号倒序排列）
            if (acceptsMagic) {
                stepVariables.entries
                    .sortedByDescending { entry -> entry.key.substringAfter("#").substringBefore(" ").toIntOrNull() ?: 0 }
                    .forEach { (groupName, variableList) ->
                        add(PickerListItem.VariableGroup(groupName, variableList))
                    }
            }
        }

        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = MagicVariableAdapter(items) { selectedItem ->
            if (selectedItem == null) {
                onSelection?.invoke(null)
                dismiss()
            } else {
                handleVariableSelection(selectedItem)
            }
        }
        return view
    }

    private fun handleVariableSelection(item: MagicVariableItem) {
        @Suppress("UNCHECKED_CAST")
        val acceptedTypes = arguments?.getSerializable("acceptedTypes") as? Set<String> ?: emptySet()

        // 检查是否启用了类型限制（默认关闭，快捷指令风格）
        val enableTypeFilter = arguments?.getBoolean("enableTypeFilter", false) ?: false

        val type = VTypeRegistry.getType(item.typeId)
        val allProperties = type.properties

        // 如果未启用类型限制，或者没有指定接受的类型，则显示所有属性
        val properties = if (!enableTypeFilter || acceptedTypes.isEmpty()) {
            allProperties
        } else {
            // 只显示匹配的属性
            VTypeRegistry.getAcceptedProperties(item.typeId, acceptedTypes)
        }

        if (properties.isEmpty()) {
            // 如果没有匹配的属性，则直接使用变量本身
            onSelection?.invoke(item)
            dismiss()
        } else {
            // 特殊处理：字典、列表和字符串类型
            when (item.typeId) {
                "vflow.type.dictionary" -> showDictionaryOptionsDialog(item, properties, acceptedTypes)
                "vflow.type.list" -> showListOptionsDialog(item, properties, acceptedTypes)
                "vflow.type.string" -> showStringOptionsDialog(item, properties, acceptedTypes)
                else -> showPropertySelectionDialog(item, properties, acceptedTypes)
            }
        }
    }

    /**
     * 显示字典选项对话框（内置属性 + 指定键）
     * @param acceptedTypes 接受的类型集合，用于判断是否显示"使用变量本身"选项
     */
    private fun showDictionaryOptionsDialog(
        item: MagicVariableItem,
        properties: List<com.chaomixian.vflow.core.types.VPropertyDef>,
        acceptedTypes: Set<String>
    ) {
        val options = mutableListOf<String>()

        // 检查是否启用了类型限制（默认关闭，快捷指令风格）
        val enableTypeFilter = arguments?.getBoolean("enableTypeFilter", false) ?: false

        // 只有当类型本身被接受时，才显示"使用变量本身"选项
        if (!enableTypeFilter || acceptedTypes.isEmpty() || item.typeId in acceptedTypes) {
            options.add("使用 ${item.variableName} 本身")
        }

        properties.forEach { prop -> options.add("${prop.getLocalizedName(requireContext())} (${prop.name})") }
        options.add("选择指定键的值...")  // 添加选项

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("字典: ${item.variableName}")
            .setItems(options.toTypedArray()) { _, which ->
                val hasUseSelfOption = !enableTypeFilter || acceptedTypes.isEmpty() || item.typeId in acceptedTypes
                val offset = if (hasUseSelfOption) 1 else 0

                when {
                    hasUseSelfOption && which == 0 -> {
                        onSelection?.invoke(item)
                        dismiss()
                    }
                    which <= properties.size -> {
                        // 选择内置属性
                        val prop = properties[which - offset]
                        val oldRef = item.variableReference
                        val newRef = when {
                            oldRef.startsWith("{{") && oldRef.endsWith("}}") -> {
                                oldRef.removeSuffix("}}") + ".${prop.name}}}"
                            }
                            oldRef.startsWith("[[") && oldRef.endsWith("]]") -> {
                                oldRef.removeSuffix("]]") + ".${prop.name}]]"
                            }
                            else -> oldRef
                        }
                        val newItem = item.copy(
                            variableReference = newRef,
                            variableName = "${item.variableName} 的 ${prop.getLocalizedName(requireContext())}"
                        )
                        onSelection?.invoke(newItem)
                        dismiss()
                    }
                    else -> {
                        // 选择指定键
                        showDictionaryKeyInput(item)
                    }
                }
            }
            .show()
    }

    /**
     * 显示字典键输入对话框
     */
    private fun showDictionaryKeyInput(item: MagicVariableItem) {
        val context = requireContext()
        val editText = android.widget.EditText(context)
        editText.hint = "输入键名（区分大小写）"

        MaterialAlertDialogBuilder(context)
            .setTitle("输入字典键名")
            .setView(editText)
            .setPositiveButton("确定") { _, _ ->
                val key = editText.text.toString().trim()
                if (key.isNotEmpty()) {
                    val oldRef = item.variableReference
                    val newRef = when {
                        oldRef.startsWith("{{") && oldRef.endsWith("}}") -> {
                            oldRef.removeSuffix("}}") + ".$key}}"
                        }
                        oldRef.startsWith("[[") && oldRef.endsWith("]]") -> {
                            oldRef.removeSuffix("]]") + ".$key]]"
                        }
                        else -> oldRef
                    }

                    val newItem = item.copy(
                        variableReference = newRef,
                        variableName = "${item.variableName}.$key"
                    )
                    onSelection?.invoke(newItem)
                    dismiss()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /**
     * 显示列表选项对话框（内置属性 + 指定索引）
     * @param acceptedTypes 接受的类型集合，用于判断是否显示"使用变量本身"选项
     */
    private fun showListOptionsDialog(
        item: MagicVariableItem,
        properties: List<com.chaomixian.vflow.core.types.VPropertyDef>,
        acceptedTypes: Set<String>
    ) {
        val options = mutableListOf<String>()

        // 检查是否启用了类型限制（默认关闭，快捷指令风格）
        val enableTypeFilter = arguments?.getBoolean("enableTypeFilter", false) ?: false

        // 只有当类型本身被接受时，才显示"使用变量本身"选项
        if (!enableTypeFilter || acceptedTypes.isEmpty() || item.typeId in acceptedTypes) {
            options.add("使用 ${item.variableName} 本身")
        }

        properties.forEach { prop -> options.add("${prop.getLocalizedName(requireContext())} (${prop.name})") }
        options.add("选择指定索引的值...")  // 添加选项

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("列表: ${item.variableName}")
            .setItems(options.toTypedArray()) { _, which ->
                val hasUseSelfOption = !enableTypeFilter || acceptedTypes.isEmpty() || item.typeId in acceptedTypes
                val offset = if (hasUseSelfOption) 1 else 0

                when {
                    hasUseSelfOption && which == 0 -> {
                        onSelection?.invoke(item)
                        dismiss()
                    }
                    which <= properties.size -> {
                        // 选择内置属性
                        val prop = properties[which - offset]
                        val oldRef = item.variableReference
                        val newRef = when {
                            oldRef.startsWith("{{") && oldRef.endsWith("}}") -> {
                                oldRef.removeSuffix("}}") + ".${prop.name}}}"
                            }
                            oldRef.startsWith("[[") && oldRef.endsWith("]]") -> {
                                oldRef.removeSuffix("]]") + ".${prop.name}]]"
                            }
                            else -> oldRef
                        }
                        val newItem = item.copy(
                            variableReference = newRef,
                            variableName = "${item.variableName} 的 ${prop.getLocalizedName(requireContext())}"
                        )
                        onSelection?.invoke(newItem)
                        dismiss()
                    }
                    else -> {
                        // 选择指定索引
                        showListIndexInput(item)
                    }
                }
            }
            .show()
    }

    /**
     * 显示列表索引输入对话框
     */
    private fun showListIndexInput(item: MagicVariableItem) {
        val context = requireContext()
        val editText = android.widget.EditText(context)
        // 不设置 inputType，允许输入负号
        editText.hint = "例如: 0, 1, 2, -1, -2"

        MaterialAlertDialogBuilder(context)
            .setTitle("输入列表索引")
            .setView(editText)
            .setPositiveButton("确定") { _, _ ->
                val indexText = editText.text.toString().trim()
                if (indexText.isNotEmpty()) {
                    val oldRef = item.variableReference
                    val newRef = when {
                        oldRef.startsWith("{{") && oldRef.endsWith("}}") -> {
                            oldRef.removeSuffix("}}") + ".$indexText}}"
                        }
                        oldRef.startsWith("[[") && oldRef.endsWith("]]") -> {
                            oldRef.removeSuffix("]]") + ".$indexText]]"
                        }
                        else -> oldRef
                    }

                    val newItem = item.copy(
                        variableReference = newRef,
                        variableName = "${item.variableName}.$indexText"
                    )
                    onSelection?.invoke(newItem)
                    dismiss()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /**
     * 显示字符串选项对话框（内置属性 + 索引/切片）
     * @param acceptedTypes 接受的类型集合，用于判断是否显示"使用变量本身"选项
     */
    private fun showStringOptionsDialog(
        item: MagicVariableItem,
        properties: List<com.chaomixian.vflow.core.types.VPropertyDef>,
        acceptedTypes: Set<String>
    ) {
        val options = mutableListOf<String>()

        // 检查是否启用了类型限制（默认关闭，快捷指令风格）
        val enableTypeFilter = arguments?.getBoolean("enableTypeFilter", false) ?: false

        // 只有当类型本身被接受时，才显示"使用变量本身"选项
        if (!enableTypeFilter || acceptedTypes.isEmpty() || item.typeId in acceptedTypes) {
            options.add("使用 ${item.variableName} 本身")
        }

        properties.forEach { prop -> options.add("${prop.getLocalizedName(requireContext())} (${prop.name})") }
        options.add("选择指定索引的值...")  // 添加索引选项

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("文本: ${item.variableName}")
            .setItems(options.toTypedArray()) { _, which ->
                val hasUseSelfOption = !enableTypeFilter || acceptedTypes.isEmpty() || item.typeId in acceptedTypes
                val offset = if (hasUseSelfOption) 1 else 0

                when {
                    hasUseSelfOption && which == 0 -> {
                        onSelection?.invoke(item)
                        dismiss()
                    }
                    which <= properties.size -> {
                        // 选择内置属性
                        val prop = properties[which - offset]
                        val oldRef = item.variableReference
                        val newRef = when {
                            oldRef.startsWith("{{") && oldRef.endsWith("}}") -> {
                                oldRef.removeSuffix("}}") + ".${prop.name}}}"
                            }
                            oldRef.startsWith("[[") && oldRef.endsWith("]]") -> {
                                oldRef.removeSuffix("]]") + ".${prop.name}]]"
                            }
                            else -> oldRef
                        }
                        val newItem = item.copy(
                            variableReference = newRef,
                            variableName = "${item.variableName} 的 ${prop.getLocalizedName(requireContext())}"
                        )
                        onSelection?.invoke(newItem)
                        dismiss()
                    }
                    else -> {
                        // 选择指定索引/切片
                        showStringIndexInput(item)
                    }
                }
            }
            .show()
    }

    /**
     * 显示字符串索引/切片输入对话框
     */
    private fun showStringIndexInput(item: MagicVariableItem) {
        val context = requireContext()
        val editText = android.widget.EditText(context)
        // 不设置 inputType，允许输入负号和冒号
        editText.hint = "例如: 0, -1, 0:5, 1:-1, ::-1"

        MaterialAlertDialogBuilder(context)
            .setTitle("输入索引或切片")
            .setView(editText)
            .setPositiveButton("确定") { _, _ ->
                val indexText = editText.text.toString().trim()
                if (indexText.isNotEmpty()) {
                    val oldRef = item.variableReference
                    val newRef = when {
                        oldRef.startsWith("{{") && oldRef.endsWith("}}") -> {
                            oldRef.removeSuffix("}}") + ".$indexText}}"
                        }
                        oldRef.startsWith("[[") && oldRef.endsWith("]]") -> {
                            oldRef.removeSuffix("]]") + ".$indexText]]"
                        }
                        else -> oldRef
                    }

                    val newItem = item.copy(
                        variableReference = newRef,
                        variableName = "${item.variableName}[$indexText]"
                    )
                    onSelection?.invoke(newItem)
                    dismiss()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /**
     * 显示标准属性选择对话框（用于非字典/列表类型）
     * @param acceptedTypes 接受的类型集合，用于判断是否显示"使用变量本身"选项
     */
    private fun showPropertySelectionDialog(
        item: MagicVariableItem,
        properties: List<com.chaomixian.vflow.core.types.VPropertyDef>,
        acceptedTypes: Set<String>
    ) {
        val options = mutableListOf<String>()

        // 检查是否启用了类型限制（默认关闭，快捷指令风格）
        val enableTypeFilter = arguments?.getBoolean("enableTypeFilter", false) ?: false

        // 只有当类型本身被接受时，才显示"使用变量本身"选项
        if (!enableTypeFilter || acceptedTypes.isEmpty() || item.typeId in acceptedTypes) {
            options.add("使用 ${item.variableName} 本身")
        }

        properties.forEach { prop -> options.add("${prop.getLocalizedName(requireContext())} (${prop.name})") }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("选择 ${item.variableName} 的属性")
            .setItems(options.toTypedArray()) { _, which ->
                val hasUseSelfOption = !enableTypeFilter || acceptedTypes.isEmpty() || item.typeId in acceptedTypes
                val offset = if (hasUseSelfOption) 1 else 0

                if (hasUseSelfOption && which == 0) {
                    onSelection?.invoke(item)
                } else {
                    val prop = properties[which - offset]
                    val oldRef = item.variableReference
                    // 智能拼接属性
                    val newRef = when {
                        oldRef.startsWith("{{") && oldRef.endsWith("}}") -> {
                            oldRef.removeSuffix("}}") + ".${prop.name}}}"
                        }
                        oldRef.startsWith("[[") && oldRef.endsWith("]]") -> {
                            oldRef.removeSuffix("]]") + ".${prop.name}]]"
                        }
                        else -> oldRef
                    }

                    val newItem = item.copy(
                        variableReference = newRef,
                        variableName = "${item.variableName} 的 ${prop.getLocalizedName(requireContext())}"
                    )
                    onSelection?.invoke(newItem)
                }
                dismiss()
            }
            .show()
    }
}

/**
 * MagicVariablePickerSheet 中 RecyclerView 的适配器。
 * 支持“清除操作”项和“变量分组卡片”项两种视图类型。
 */
class MagicVariableAdapter(private val items: List<PickerListItem>, private val onVariableClick: (MagicVariableItem?) -> Unit) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    companion object { private const val TYPE_ACTION = 0; private const val TYPE_GROUP = 1 }
    override fun getItemViewType(position: Int) = if (items[position] is PickerListItem.ClearAction) TYPE_ACTION else TYPE_GROUP
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = if (viewType == TYPE_ACTION) ActionViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_magic_variable_action, parent, false)) else GroupViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_magic_variable_group_card, parent, false), onVariableClick)
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is ActionViewHolder) { holder.bind("清除 / 使用静态值"); holder.itemView.setOnClickListener { onVariableClick(null) } }
        else if (holder is GroupViewHolder) { holder.bind(items[position] as PickerListItem.VariableGroup) }
    }
    override fun getItemCount() = items.size
    class GroupViewHolder(view: View, private val onVariableClick: (MagicVariableItem?) -> Unit) : RecyclerView.ViewHolder(view) {
        private val titleTextView: TextView = view.findViewById(R.id.group_title)
        private val variablesContainer: LinearLayout = view.findViewById(R.id.variables_container)
        fun bind(group: PickerListItem.VariableGroup) {
            titleTextView.text = group.title
            variablesContainer.removeAllViews()
            val inflater = LayoutInflater.from(itemView.context)
            group.variables.forEach { variableItem ->
                val itemView = inflater.inflate(R.layout.item_magic_variable, variablesContainer, false)
                val nameTextView: TextView = itemView.findViewById(R.id.variable_name)
                val originTextView: TextView = itemView.findViewById(R.id.variable_origin)
                nameTextView.text = variableItem.variableName
                originTextView.text = variableItem.originDescription
                itemView.setOnClickListener { onVariableClick(variableItem) }
                variablesContainer.addView(itemView)
            }
        }
    }
    class ActionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val actionTextView: TextView = view.findViewById(R.id.action_text)
        fun bind(text: String) { actionTextView.text = text }
    }
}
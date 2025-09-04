// 文件: WorkflowEditorActivity.kt
// 描述: 工作流编辑器 Activity，用于创建和修改工作流的步骤和参数。
//      核心功能包括：显示步骤列表、添加/编辑/删除步骤、拖拽排序、
//      处理模块参数（通过 ActionEditorSheet 和 MagicVariablePickerSheet）、
//      保存工作流、执行工作流（包括权限检查）。

package com.chaomixian.vflow.ui.workflow_editor

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.WorkflowExecutor
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.WorkflowManager
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.core.workflow.model.Workflow
import com.chaomixian.vflow.permissions.PermissionActivity
import com.chaomixian.vflow.permissions.PermissionManager
import com.chaomixian.vflow.ui.common.BaseActivity
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import java.util.*

/**
 * 工作流编辑器 Activity。
 * 允许用户创建新工作流或编辑现有工作流。
 * 管理工作流的名称、步骤列表，并与 WorkflowManager 交互以进行持久化。
 * 使用 RecyclerView 显示步骤，并支持拖拽排序。
 * 通过 ActionEditorSheet 和 MagicVariablePickerSheet 处理步骤参数的编辑和魔法变量选择。
 */
class WorkflowEditorActivity : BaseActivity() {

    private lateinit var workflowManager: WorkflowManager // 工作流管理器实例
    private var currentWorkflow: Workflow? = null // 当前正在编辑的工作流对象，新建时为null
    private val actionSteps = mutableListOf<ActionStep>() // 编辑器中步骤的可变列表
    private lateinit var actionStepAdapter: ActionStepAdapter // RecyclerView 的适配器
    private lateinit var nameEditText: EditText // 工作流名称输入框
    private lateinit var itemTouchHelper: ItemTouchHelper // RecyclerView 拖拽辅助类
    private var currentEditorSheet: ActionEditorSheet? = null // 当前打开的参数编辑底部表单

    private var pendingExecutionWorkflow: Workflow? = null // 执行前等待权限的待处理工作流

    // --- 拖放逻辑所需的状态 ---
    private var listBeforeDrag: List<ActionStep>? = null // 拖动开始前的列表快照
    private var dragStartPosition: Int = -1 // 拖动开始时的原始位置

    // 权限请求的 ActivityResultLauncher
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            pendingExecutionWorkflow?.let { // 权限获取成功，执行待处理工作流
                Toast.makeText(this, "开始执行: ${it.name}", Toast.LENGTH_SHORT).show()
                WorkflowExecutor.execute(it, this)
            }
        }
        pendingExecutionWorkflow = null // 重置
    }

    companion object {
        const val EXTRA_WORKFLOW_ID = "WORKFLOW_ID" // Intent extra key，用于传递工作流ID
    }

    /** Activity 创建时的初始化。 */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_workflow_editor)
        applyWindowInsets() // 应用窗口边衬区以适配系统栏

        workflowManager = WorkflowManager(this)
        nameEditText = findViewById(R.id.edit_text_workflow_name)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar_editor)
        toolbar.setNavigationOnClickListener { finish() } // Toolbar 返回按钮

        setupRecyclerView() // 初始化 RecyclerView
        loadWorkflowData()  // 加载工作流数据 (新建或编辑)
        setupDragAndDrop()  // 设置拖拽排序功能

        // 添加、保存、执行按钮的点击监听
        findViewById<Button>(R.id.button_add_action).setOnClickListener { showActionPicker() }
        findViewById<Button>(R.id.button_save_workflow).setOnClickListener { saveWorkflow() }
        findViewById<Button>(R.id.button_execute_workflow).setOnClickListener {
            val name = nameEditText.text.toString().trim()
            if (name.isBlank()) {
                Toast.makeText(this, "工作流名称不能为空", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // 基于当前编辑器状态构建一个临时的 Workflow 对象用于执行
            val workflowToExecute = currentWorkflow?.copy(
                name = name,
                steps = actionSteps.toList() // 使用当前步骤列表的副本
            ) ?: Workflow(
                id = currentWorkflow?.id ?: UUID.randomUUID().toString(),
                name = name,
                steps = actionSteps.toList()
            )
            executeWorkflow(workflowToExecute)
        }
    }

    /**
     * 执行指定的工作流。
     * 首先检查并请求缺失的权限，获取权限后通过 WorkflowExecutor 执行。
     * @param workflow 要执行的工作流。
     */
    private fun executeWorkflow(workflow: Workflow) {
        val missingPermissions = PermissionManager.getMissingPermissions(this, workflow)
        if (missingPermissions.isEmpty()) { // 无缺失权限，直接执行
            Toast.makeText(this, "开始执行: ${workflow.name}", Toast.LENGTH_SHORT).show()
            WorkflowExecutor.execute(workflow, this)
        } else { // 有缺失权限，启动权限请求 Activity
            pendingExecutionWorkflow = workflow
            val intent = Intent(this, PermissionActivity::class.java).apply {
                putParcelableArrayListExtra(PermissionActivity.EXTRA_PERMISSIONS, ArrayList(missingPermissions))
                putExtra(PermissionActivity.EXTRA_WORKFLOW_NAME, workflow.name)
            }
            permissionLauncher.launch(intent)
        }
    }

    /**
     * 显示模块参数编辑器 (ActionEditorSheet)。
     * @param module 要编辑的模块。
     * @param existingStep 如果是编辑现有步骤，则为该步骤实例；添加新步骤则为 null。
     * @param position 步骤在列表中的位置；添加新步骤则为 -1。
     * @param focusedInputId 如果只编辑单个参数，则为该参数ID；否则为 null。
     */
    private fun showActionEditor(module: ActionModule, existingStep: ActionStep?, position: Int, focusedInputId: String?) {
        // 关键：将 actionSteps (整个工作流列表) 传递给 ActionEditorSheet，用于动态输入上下文和魔法变量选择
        val editor = ActionEditorSheet.newInstance(module, existingStep, focusedInputId, actionSteps.toList())
        currentEditorSheet = editor // 保存当前打开的编辑器实例

        // 设置保存回调
        editor.onSave = { newStepData -> // newStepData 只包含被修改的参数
            if (position != -1) { // 编辑现有步骤
                if (focusedInputId != null) { // 单参数编辑模式
                    // 合并旧参数和新修改的参数
                    val updatedParams = actionSteps[position].parameters.toMutableMap()
                    updatedParams.putAll(newStepData.parameters)
                    actionSteps[position] = actionSteps[position].copy(parameters = updatedParams)
                } else { // 整个模块编辑模式
                    actionSteps[position] = actionSteps[position].copy(parameters = newStepData.parameters)
                }
            } else { // 添加新步骤
                val stepsToAdd = module.createSteps() // 模块可能创建多个关联步骤 (如 If/Else/EndIf)
                // 将编辑器返回的参数应用到第一个创建的步骤上
                val configuredFirstStep = stepsToAdd.first().copy(parameters = newStepData.parameters)
                actionSteps.add(configuredFirstStep)
                if (stepsToAdd.size > 1) { // 如果模块创建了多个步骤，添加剩余的
                    actionSteps.addAll(stepsToAdd.subList(1, stepsToAdd.size))
                }
            }
            recalculateAndNotify() // 重新计算缩进并刷新列表
        }

        // 设置魔法变量请求回调
        editor.onMagicVariableRequested = { inputId ->
            // 如果是添加新步骤(position=-1)，则魔法变量的上下文是当前列表的末尾
            val stepPositionForContext = if (position != -1) position else actionSteps.size
            showMagicVariablePicker(stepPositionForContext, inputId, module)
        }

        editor.show(supportFragmentManager, "ActionEditor")
    }

    /**
     * 显示魔法变量选择器 (MagicVariablePickerSheet)。
     * @param editingStepPosition 正在编辑的步骤在列表中的位置 (用于确定可选变量范围)。
     * @param targetInputId 目标输入参数的ID。
     * @param editingModule 正在编辑的模块。
     */
    private fun showMagicVariablePicker(editingStepPosition: Int, targetInputId: String, editingModule: ActionModule) {
        val targetInputDef = editingModule.getInputs().find { it.id == targetInputId } ?: return
        val availableVariables = mutableListOf<MagicVariableItem>()

        // 遍历 editingStepPosition 之前的所有步骤，收集其输出作为可用魔法变量
        for (i in 0 until editingStepPosition) {
            val step = actionSteps[i]
            val module = ModuleRegistry.getModule(step.moduleId) ?: continue

            module.getOutputs(step).forEach { outputDef ->
                // 检查变量类型是否与目标输入参数兼容
                val isCompatible = targetInputDef.acceptedMagicVariableTypes.isEmpty() ||
                                   targetInputDef.acceptedMagicVariableTypes.contains(outputDef.typeName)
                if (isCompatible) {
                    availableVariables.add(
                        MagicVariableItem(
                            variableReference = "{{${step.id}.${outputDef.id}}}", // 魔法变量引用格式
                            variableName = outputDef.name,
                            originModuleName = module.metadata.name
                        )
                    )
                }
            }
        }

        val picker = MagicVariablePickerSheet.newInstance(availableVariables)
        // 设置选择回调：更新 ActionEditorSheet 中的参数值
        picker.onSelection = { selectedItem ->
            if (selectedItem != null) { //选择了魔法变量
                currentEditorSheet?.updateInputWithVariable(targetInputId, selectedItem.variableReference)
            } else { //选择了“清除连接”
                currentEditorSheet?.clearInputVariable(targetInputId)
            }
        }
        picker.show(supportFragmentManager, "MagicVariablePicker")
    }

    /** 处理步骤摘要中参数药丸的点击事件，打开 ActionEditorSheet 编辑对应参数。 */
    private fun handleParameterPillClick(position: Int, parameterId: String) {
        val step = actionSteps[position]
        val module = ModuleRegistry.getModule(step.moduleId) ?: return
        // 以单参数编辑模式打开编辑器
        showActionEditor(module, step, position, parameterId)
    }

    /** 初始化 RecyclerView 及其 Adapter 和 ItemDecoration。 */
    private fun setupRecyclerView() {
        val prefs = getSharedPreferences("vFlowPrefs", Context.MODE_PRIVATE)
        val hideConnections = prefs.getBoolean("hideConnections", false) // 是否隐藏连接线的用户偏好

        actionStepAdapter = ActionStepAdapter(
            actionSteps,
            hideConnections,
            onEditClick = { position, inputId -> // 列表项或参数药丸的编辑回调
                val step = actionSteps[position]
                ModuleRegistry.getModule(step.moduleId)?.let { module ->
                    showActionEditor(module, step, position, inputId)
                }
            },
            onDeleteClick = { position -> // 删除按钮回调
                val step = actionSteps[position]
                ModuleRegistry.getModule(step.moduleId)?.let { module ->
                    // 调用模块自身的删除逻辑 (可能会删除关联步骤，如If块)
                    if (module.onStepDeleted(actionSteps, position)) {
                        recalculateAndNotify() // 如果实际删除了步骤，则刷新
                    }
                }
            },
            onParameterPillClick = { position, parameterId -> // 参数药丸点击回调
                handleParameterPillClick(position, parameterId)
            }
        )
        findViewById<RecyclerView>(R.id.recycler_view_action_steps).apply {
            layoutManager = LinearLayoutManager(this@WorkflowEditorActivity)
            adapter = actionStepAdapter
            // if (!hideConnections) { // 连接线绘制功能暂未完全实现
            //     addItemDecoration(WorkflowConnectionDecorator(actionSteps))
            // }
        }
    }

    /** 设置 RecyclerView 的拖拽排序功能 (ItemTouchHelper)。 */
    private fun setupDragAndDrop() {
        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0 // 允许上下拖动
        ) {
            /** 拖动过程中，仅做视觉上的交换，不修改实际数据。 */
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPosition = viewHolder.adapterPosition
                val toPosition = target.adapterPosition
                if (fromPosition > 0 && toPosition > 0) { // 禁止移动触发器 (第0项)
                    Collections.swap(actionSteps, fromPosition, toPosition) // 视觉交换
                    actionStepAdapter.notifyItemMoved(fromPosition, toPosition)
                }
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) { /* 无滑动删除 */ }

            /** 当一个项目被选中开始拖动时，备份当前列表状态。 */
            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) { // 开始拖动
                    viewHolder?.let {
                        dragStartPosition = it.adapterPosition
                        listBeforeDrag = actionSteps.toList() // 备份列表
                    }
                }
            }

            /** 当拖动结束（松手）时，进行最终的逻辑校验和数据提交。 */
            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)

                val originalList = listBeforeDrag
                val fromPos = dragStartPosition
                val toPos = viewHolder.adapterPosition // 拖动结束后的位置

                // 重置状态变量
                listBeforeDrag = null
                dragStartPosition = -1

                if (originalList == null || fromPos == -1 || toPos == -1 || fromPos == toPos) {
                    recalculateAndNotify() // 无有效拖动，仅刷新缩进和UI
                    return
                }

                // 查找被拖动的完整积木块 (可能包含多个步骤)
                val (blockStart, blockEnd) = findBlockRangeInList(originalList, fromPos)
                val blockToMove = originalList.subList(blockStart, blockEnd + 1)

                // 在临时列表中模拟移动
                val tempList = originalList.toMutableList()
                tempList.removeAll(blockToMove.toSet()) // 先移除被拖动的块
                // 计算块在移除后的目标插入点
                val targetIndex = if (toPos > fromPos) toPos - blockToMove.size + 1 else toPos

                var isValidMove = false
                if (targetIndex >= 0 && targetIndex <= tempList.size) {
                    tempList.addAll(targetIndex.coerceAtMost(tempList.size), blockToMove) // 插入块到目标位置
                    if (isBlockStructureValid(tempList)) { // 验证移动后的结构是否有效
                        actionSteps.clear()
                        actionSteps.addAll(tempList) // 结构有效，更新主列表
                        isValidMove = true
                    }
                }

                if (!isValidMove) { // 移动无效，从备份中恢复
                    Toast.makeText(this@WorkflowEditorActivity, "无效的移动", Toast.LENGTH_SHORT).show()
                    actionSteps.clear()
                    actionSteps.addAll(originalList)
                }
                recalculateAndNotify() // 无论如何都刷新UI，确保最终状态正确
            }

            /** 禁止拖动触发器步骤 (position 0)。 */
            override fun getDragDirs(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
                if (viewHolder.adapterPosition == 0) return 0 // 第0项不允许拖动
                return super.getDragDirs(recyclerView, viewHolder)
            }
        }
        itemTouchHelper = ItemTouchHelper(callback)
        itemTouchHelper.attachToRecyclerView(findViewById(R.id.recycler_view_action_steps))
    }

    /**
     * 在给定的步骤列表 (通常是拖动前的备份) 中，根据指定位置查找其所属的完整积木块的范围 (开始和结束索引)。
     * @param list 要搜索的步骤列表。
     * @param position 列表中的任意位置，指示要查找范围的步骤。
     * @return Pair(块开始索引, 块结束索引)。如果步骤非积木块成员，则开始和结束索引相同。
     */
    private fun findBlockRangeInList(list: List<ActionStep>, position: Int): Pair<Int, Int> {
        val initialModule = ModuleRegistry.getModule(list.getOrNull(position)?.moduleId ?: return position to position)
        val behavior = initialModule?.blockBehavior

        // 非积木块成员，或模块不存在，则自身构成一个范围
        if (behavior == null || behavior.type == BlockType.NONE || behavior.pairingId == null) {
            return position to position
        }

        var start = position
        var end = position

        // 1. 向上回溯，精确查找块的起始位置 (BLOCK_START)
        var openCount = 0
        for (i in position downTo 0) {
            val currentModule = ModuleRegistry.getModule(list[i].moduleId)
            if (currentModule?.blockBehavior?.pairingId == behavior.pairingId) { // 只关心同类型的积木块
                when (currentModule.blockBehavior.type) {
                    BlockType.BLOCK_END -> openCount++
                    BlockType.BLOCK_START -> {
                        openCount--
                        if (openCount <= 0) { // 找到了包含当前position的、最外层的起始点
                            start = i
                            break
                        }
                    }
                    else -> {}
                }
            }
        }

        // 2. 从已确定的起始点出发，向下精确查找配对的结束位置 (BLOCK_END)
        openCount = 0
        for (i in start until list.size) {
            val currentModule = ModuleRegistry.getModule(list[i].moduleId)
            if (currentModule?.blockBehavior?.pairingId == behavior.pairingId) {
                when (currentModule.blockBehavior.type) {
                    BlockType.BLOCK_START -> openCount++
                    BlockType.BLOCK_END -> {
                        openCount--
                        if (openCount == 0) { // 找到了与start配对的那个结束点
                            end = i
                            break
                        }
                    }
                    else -> {}
                }
            }
        }
        return start to end
    }

    /**
     * 检查给定的步骤列表是否具有有效的积木块结构 (例如，开始/结束块是否正确配对)。
     * @param list 要验证的步骤列表。
     * @return 如果结构有效返回 true，否则返回 false。
     */
    private fun isBlockStructureValid(list: List<ActionStep>): Boolean {
        val blockStack = Stack<String?>() // 使用栈来跟踪嵌套的积木块 pairingId
        for (step in list) {
            val behavior = ModuleRegistry.getModule(step.moduleId)?.blockBehavior ?: continue
            when (behavior.type) {
                BlockType.BLOCK_START -> blockStack.push(behavior.pairingId)
                BlockType.BLOCK_END -> {
                    if (blockStack.isEmpty() || blockStack.peek() != behavior.pairingId) return false // 栈空或ID不匹配
                    blockStack.pop()
                }
                BlockType.BLOCK_MIDDLE -> { // 中间块必须在对应类型的块内
                    if (blockStack.isEmpty() || blockStack.peek() != behavior.pairingId) return false
                }
                else -> {} // NONE 类型不影响结构
            }
        }
        return blockStack.isEmpty() // 所有块都正确闭合
    }

    /** 应用窗口边衬区到 AppBar 和底部按钮容器，以适配系统栏。 */
    private fun applyWindowInsets() {
        val appBar = findViewById<AppBarLayout>(R.id.app_bar_layout_editor)
        val bottomButtonContainer = findViewById<LinearLayout>(R.id.bottom_button_container)

        ViewCompat.setOnApplyWindowInsetsListener(appBar) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = systemBars.top) // AppBar 顶部加上状态栏高度
            insets
        }

        ViewCompat.setOnApplyWindowInsetsListener(bottomButtonContainer) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(bottom = systemBars.bottom) // 底部按钮容器底部加上导航栏高度
            insets
        }
    }

    /**
     * 加载工作流数据。
     * 如果通过 Intent 传递了 WORKFLOW_ID，则加载现有工作流。
     * 否则，如果列表为空 (新建工作流)，则默认添加一个手动触发器。
     * 最后刷新UI。
     */
    private fun loadWorkflowData() {
        val workflowId = intent.getStringExtra(EXTRA_WORKFLOW_ID)
        if (workflowId != null) { // 编辑现有工作流
            currentWorkflow = workflowManager.getWorkflow(workflowId)
            currentWorkflow?.let {
                nameEditText.setText(it.name)
                actionSteps.clear()
                actionSteps.addAll(it.steps)
            }
        }
        if (actionSteps.isEmpty()) { // 新建工作流或加载失败，默认添加手动触发器
            ModuleRegistry.getModule("vflow.trigger.manual")?.let {
                actionSteps.addAll(it.createSteps())
            }
        }
        recalculateAndNotify() // 计算缩进并刷新列表
    }

    /** 显示动作模块选择器 (ActionPickerSheet)。 */
    private fun showActionPicker() {
        val picker = ActionPickerSheet()
        picker.onActionSelected = { module ->
            if (module.metadata.category == "触发器") { // 触发器只能有一个且在开头
                Toast.makeText(this, "触发器只能位于工作流的开始。", Toast.LENGTH_SHORT).show()
            } else {
                // 添加新模块，position为-1表示添加到末尾，focusedInputId为null表示编辑整个模块
                showActionEditor(module, null, -1, null)
            }
        }
        picker.show(supportFragmentManager, "ActionPicker")
    }

    /** 重新计算所有步骤的缩进级别并通知 Adapter 数据已更改，刷新整个列表。 */
    private fun recalculateAndNotify() {
        recalculateAllIndentation()
        actionStepAdapter.notifyDataSetChanged() // 刷新整个列表以反映缩进和数据变化
    }

    /**
     * 重新计算 actionSteps 列表中所有步骤的缩进级别。
     * 基于模块的 BlockBehavior (START, MIDDLE, END, NONE) 和 pairingId 来确定缩进。
     * 使用栈来跟踪当前的嵌套层级。
     */
    private fun recalculateAllIndentation() {
        val indentStack = Stack<String?>() // 存储积木块的 pairingId
        for (step in actionSteps) {
            val module = ModuleRegistry.getModule(step.moduleId)
            val behavior = module?.blockBehavior

            if (behavior == null) { // 模块不存在或无行为定义
                step.indentationLevel = 0
                continue
            }

            when (behavior.type) {
                BlockType.BLOCK_END -> { // 块结束
                    step.indentationLevel = (indentStack.size - 1).coerceAtLeast(0) // 缩进减一级
                    if (indentStack.isNotEmpty() && indentStack.peek() == behavior.pairingId) {
                        indentStack.pop() // 弹出匹配的块
                    }
                }
                BlockType.BLOCK_MIDDLE -> { // 块中间 (如 Else)
                    step.indentationLevel = (indentStack.size - 1).coerceAtLeast(0) // 与上一个块开始同级
                }
                BlockType.BLOCK_START -> { // 块开始
                    step.indentationLevel = indentStack.size // 当前嵌套级别
                    indentStack.push(behavior.pairingId) // 推入新块
                }
                BlockType.NONE -> { // 普通步骤
                    step.indentationLevel = indentStack.size // 与当前嵌套级别相同
                }
            }
        }
    }

    /**
     * 保存当前工作流。
     * 校验工作流名称是否为空。
     * 创建或更新 Workflow 对象，然后通过 WorkflowManager 保存。
     * 保存成功后关闭编辑器。
     */
    private fun saveWorkflow() {
        val name = nameEditText.text.toString().trim()
        if (name.isBlank()) {
            Toast.makeText(this, "工作流名称不能为空", Toast.LENGTH_SHORT).show()
        } else {
            val workflowToSave = currentWorkflow?.copy( // 更新现有工作流
                name = name,
                steps = actionSteps.toList() // 保存当前步骤列表的副本
            ) ?: Workflow( // 创建新工作流
                id = UUID.randomUUID().toString(),
                name = name,
                steps = actionSteps.toList()
            )
            workflowManager.saveWorkflow(workflowToSave)
            Toast.makeText(this, "工作流已保存", Toast.LENGTH_SHORT).show()
            finish() // 关闭编辑器
        }
    }
}

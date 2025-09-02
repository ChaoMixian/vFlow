// 文件: main/java/com/chaomixian/vflow/ui/workflow_editor/WorkflowEditorActivity.kt

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

class WorkflowEditorActivity : BaseActivity() {

    private lateinit var workflowManager: WorkflowManager
    private var currentWorkflow: Workflow? = null
    private val actionSteps = mutableListOf<ActionStep>()
    private lateinit var actionStepAdapter: ActionStepAdapter
    private lateinit var nameEditText: EditText
    private lateinit var itemTouchHelper: ItemTouchHelper
    private var currentEditorSheet: ActionEditorSheet? = null

    private var pendingExecutionWorkflow: Workflow? = null

    // --- 拖放逻辑所需的状态 ---
    private var listBeforeDrag: List<ActionStep>? = null
    private var dragStartPosition: Int = -1

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            pendingExecutionWorkflow?.let {
                Toast.makeText(this, "开始执行: ${it.name}", Toast.LENGTH_SHORT).show()
                WorkflowExecutor.execute(it, this)
            }
        }
        pendingExecutionWorkflow = null
    }

    companion object {
        const val EXTRA_WORKFLOW_ID = "WORKFLOW_ID"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_workflow_editor)
        applyWindowInsets()

        workflowManager = WorkflowManager(this)
        nameEditText = findViewById(R.id.edit_text_workflow_name)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar_editor)
        toolbar.setNavigationOnClickListener { finish() }

        setupRecyclerView()
        loadWorkflowData()
        setupDragAndDrop()

        findViewById<Button>(R.id.button_add_action).setOnClickListener { showActionPicker() }
        findViewById<Button>(R.id.button_save_workflow).setOnClickListener { saveWorkflow() }
        findViewById<Button>(R.id.button_execute_workflow).setOnClickListener {
            val name = nameEditText.text.toString().trim()
            if (name.isBlank()) {
                Toast.makeText(this, "工作流名称不能为空", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val workflowToExecute = currentWorkflow?.copy(
                name = name,
                steps = actionSteps
            ) ?: Workflow(
                id = currentWorkflow?.id ?: UUID.randomUUID().toString(),
                name = name,
                steps = actionSteps
            )

            executeWorkflow(workflowToExecute)
        }
    }

    private fun executeWorkflow(workflow: Workflow) {
        val missingPermissions = PermissionManager.getMissingPermissions(this, workflow)
        if (missingPermissions.isEmpty()) {
            Toast.makeText(this, "开始执行: ${workflow.name}", Toast.LENGTH_SHORT).show()
            WorkflowExecutor.execute(workflow, this)
        } else {
            pendingExecutionWorkflow = workflow
            val intent = Intent(this, PermissionActivity::class.java).apply {
                putParcelableArrayListExtra(PermissionActivity.EXTRA_PERMISSIONS, ArrayList(missingPermissions))
                putExtra(PermissionActivity.EXTRA_WORKFLOW_NAME, workflow.name)
            }
            permissionLauncher.launch(intent)
        }
    }

    private fun showActionEditor(module: ActionModule, existingStep: ActionStep?, position: Int, focusedInputId: String?) {
        // --- 核心修复：在这里传入 actionSteps 列表 ---
        val editor = ActionEditorSheet.newInstance(module, existingStep, focusedInputId, actionSteps)
        currentEditorSheet = editor

        editor.onSave = { newStep ->
            if (position != -1) {
                if (focusedInputId != null) {
                    val updatedParams = actionSteps[position].parameters.toMutableMap()
                    updatedParams.putAll(newStep.parameters)
                    actionSteps[position] = actionSteps[position].copy(parameters = updatedParams)
                } else {
                    actionSteps[position] = actionSteps[position].copy(parameters = newStep.parameters)
                }
            } else {
                val stepsToAdd = module.createSteps()
                val configuredFirstStep = stepsToAdd.first().copy(parameters = newStep.parameters)
                actionSteps.add(configuredFirstStep)
                if (stepsToAdd.size > 1) {
                    actionSteps.addAll(stepsToAdd.subList(1, stepsToAdd.size))
                }
            }
            recalculateAndNotify()
        }

        editor.onMagicVariableRequested = { inputId ->
            val stepPosition = if (position != -1) position else actionSteps.size
            showMagicVariablePicker(stepPosition, inputId, module)
        }

        editor.show(supportFragmentManager, "ActionEditor")
    }

    private fun showMagicVariablePicker(editingStepPosition: Int, targetInputId: String, editingModule: ActionModule) {
        val targetInputDef = editingModule.getInputs().find { it.id == targetInputId } ?: return
        val availableVariables = mutableListOf<MagicVariableItem>()

        for (i in 0 until editingStepPosition) {
            val step = actionSteps[i]
            val module = ModuleRegistry.getModule(step.moduleId) ?: continue

            module.getOutputs(step).forEach { outputDef ->
                val isCompatible = targetInputDef.acceptedMagicVariableTypes.isEmpty() || targetInputDef.acceptedMagicVariableTypes.contains(outputDef.typeName)
                if (isCompatible) {
                    availableVariables.add(
                        MagicVariableItem(
                            variableReference = "{{${step.id}.${outputDef.id}}}",
                            variableName = outputDef.name,
                            originModuleName = module.metadata.name
                        )
                    )
                }
            }
        }

        val picker = MagicVariablePickerSheet.newInstance(availableVariables)
        picker.onSelection = { selectedItem ->
            if (selectedItem != null) {
                currentEditorSheet?.updateInputWithVariable(targetInputId, selectedItem.variableReference)
            } else {
                currentEditorSheet?.clearInputVariable(targetInputId)
            }
        }
        picker.show(supportFragmentManager, "MagicVariablePicker")
    }

    private fun handleParameterPillClick(position: Int, parameterId: String) {
        val step = actionSteps[position]
        val module = ModuleRegistry.getModule(step.moduleId) ?: return
        showActionEditor(module, step, position, parameterId)
    }

    private fun setupRecyclerView() {
        val prefs = getSharedPreferences("vFlowPrefs", Context.MODE_PRIVATE)
        val hideConnections = prefs.getBoolean("hideConnections", false)

        actionStepAdapter = ActionStepAdapter(
            actionSteps,
            hideConnections,
            onEditClick = { position, inputId ->
                val step = actionSteps[position]
                ModuleRegistry.getModule(step.moduleId)?.let { module ->
                    showActionEditor(module, step, position, inputId)
                }
            },
            onDeleteClick = { position ->
                val step = actionSteps[position]
                ModuleRegistry.getModule(step.moduleId)?.let { module ->
                    if (module.onStepDeleted(actionSteps, position)) {
                        recalculateAndNotify()
                    }
                }
            },
            onParameterPillClick = { position, parameterId ->
                handleParameterPillClick(position, parameterId)
            }
        )
        findViewById<RecyclerView>(R.id.recycler_view_action_steps).apply {
            layoutManager = LinearLayoutManager(this@WorkflowEditorActivity)
            adapter = actionStepAdapter
            if (!hideConnections) {
                addItemDecoration(WorkflowConnectionDecorator(actionSteps))
            }
        }
    }

    // --- 拖放逻辑 (体验优化版) ---
    private fun setupDragAndDrop() {
        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            /**
             * 拖动过程中，只负责视觉上的交换，提供流畅的体验。
             */
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPosition = viewHolder.adapterPosition
                val toPosition = target.adapterPosition
                if (fromPosition > 0 && toPosition > 0) { // 禁止移动触发器
                    Collections.swap(actionSteps, fromPosition, toPosition)
                    actionStepAdapter.notifyItemMoved(fromPosition, toPosition)
                }
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

            /**
             * 当一个项目被选中开始拖动时，备份当前列表状态。
             */
            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                    viewHolder?.let {
                        dragStartPosition = it.adapterPosition
                        listBeforeDrag = actionSteps.toList()
                    }
                }
            }

            /**
             * 当拖动结束（松手）时，进行最终的逻辑校验和数据提交。
             */
            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)

                val originalList = listBeforeDrag
                val fromPos = dragStartPosition
                val toPos = viewHolder.adapterPosition

                // 重置状态变量，为下一次拖动做准备
                listBeforeDrag = null
                dragStartPosition = -1

                if (originalList == null || fromPos == -1 || toPos == -1 || fromPos == toPos) {
                    recalculateAndNotify() // 如果没有有效拖动，仅刷新缩进
                    return
                }

                // 根据原始列表和拖动起始点，找到被拖动的完整积木块
                val (blockStart, blockEnd) = findBlockRangeInList(originalList, fromPos)
                val blockToMove = originalList.subList(blockStart, blockEnd + 1)

                // 在一个临时列表中模拟移动
                val tempList = originalList.toMutableList()
                tempList.removeAll(blockToMove.toSet())
                val targetIndex = if (toPos > fromPos) toPos - blockToMove.size + 1 else toPos

                var isValidMove = false
                if (targetIndex >= 0 && targetIndex <= tempList.size) {
                    tempList.addAll(targetIndex, blockToMove)
                    if (isBlockStructureValid(tempList)) {
                        // 移动有效，更新主列表
                        actionSteps.clear()
                        actionSteps.addAll(tempList)
                        isValidMove = true
                    }
                }

                if (!isValidMove) {
                    // 移动无效，从备份中恢复
                    Toast.makeText(this@WorkflowEditorActivity, "无效的移动", Toast.LENGTH_SHORT).show()
                    actionSteps.clear()
                    actionSteps.addAll(originalList)
                }

                // 无论移动是否有效，都进行一次完整的UI刷新，以确保最终状态正确
                recalculateAndNotify()
            }

            override fun getDragDirs(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
                if (viewHolder.adapterPosition == 0) return 0
                return super.getDragDirs(recyclerView, viewHolder)
            }
        }
        itemTouchHelper = ItemTouchHelper(callback)
        itemTouchHelper.attachToRecyclerView(findViewById(R.id.recycler_view_action_steps))
    }

    /**
     * 在一个给定的列表（通常是拖动前的备份）中查找积木块的范围。
     */
    /**
     * 查找给定位置的步骤所属的积木块的完整范围（开始和结束索引）。
     *
     * @param list 要在其中搜索的步骤列表。
     * @param position 列表中的任意位置。
     * @return 一个 Pair，包含积木块的开始和结束索引。如果该步骤是单个模块，则它自成一个范围。
     */
    private fun findBlockRangeInList(list: List<ActionStep>, position: Int): Pair<Int, Int> {
        // 首先，安全地获取当前位置的模块及其行为
        val initialModule = ModuleRegistry.getModule(list[position].moduleId)
        val behavior = initialModule?.blockBehavior

        // 如果模块不存在，或者它不是一个积木块的一部分，则它自成一个范围
        if (behavior == null || behavior.type == BlockType.NONE || behavior.pairingId == null) {
            return position to position
        }

        var start = position
        var end = position

        // --- 第一步：向上回溯，精确查找块的起始位置 (BLOCK_START) ---
        var openCount = 0
        for (i in position downTo 0) {
            val currentModule = ModuleRegistry.getModule(list[i].moduleId)
            // 只关心与初始模块配对ID相同的积木块
            if (currentModule?.blockBehavior?.pairingId == behavior.pairingId) {
                when (currentModule.blockBehavior.type) {
                    BlockType.BLOCK_END -> openCount++ // 遇到结束块，计数器加一
                    BlockType.BLOCK_START -> {
                        openCount-- // 遇到开始块，计数器减一
                        if (openCount <= 0) {
                            // 当计数器归零或变为负数时，意味着我们找到了当前块的起始点
                            start = i
                            break // 找到起点，退出向上搜索
                        }
                    }
                    else -> {} // 中间块或普通块不影响层级计数
                }
            }
        }

        // --- 第二步：从已确定的起始点出发，向下精确查找配对的结束位置 (BLOCK_END) ---
        openCount = 0
        for (i in start until list.size) {
            val currentModule = ModuleRegistry.getModule(list[i].moduleId)
            // 同样只关心配对ID相同的积木块
            if (currentModule?.blockBehavior?.pairingId == behavior.pairingId) {
                when (currentModule.blockBehavior.type) {
                    BlockType.BLOCK_START -> openCount++ // 遇到开始块，计数器加一
                    BlockType.BLOCK_END -> {
                        openCount-- // 遇到结束块，计数器减一
                        if (openCount == 0) {
                            // 当计数器首次归零时，说明我们找到了与起点配对的那个唯一的结束点
                            end = i
                            break // 找到终点，退出向下搜索
                        }
                    }
                    else -> {}
                }
            }
        }

        return start to end
    }

    /**
     * 检查给定的步骤列表是否有有效的积木块结构。
     */
    private fun isBlockStructureValid(list: List<ActionStep>): Boolean {
        val blockStack = Stack<String?>()
        for (step in list) {
            val behavior = ModuleRegistry.getModule(step.moduleId)?.blockBehavior ?: continue
            when (behavior.type) {
                BlockType.BLOCK_START -> blockStack.push(behavior.pairingId)
                BlockType.BLOCK_END -> {
                    if (blockStack.isEmpty() || blockStack.peek() != behavior.pairingId) return false
                    blockStack.pop()
                }
                BlockType.BLOCK_MIDDLE -> {
                    if (blockStack.isEmpty() || blockStack.peek() != behavior.pairingId) return false
                }
                else -> {}
            }
        }
        return blockStack.isEmpty()
    }

    private fun applyWindowInsets() {
        val appBar = findViewById<AppBarLayout>(R.id.app_bar_layout_editor)
        val bottomButtonContainer = findViewById<LinearLayout>(R.id.bottom_button_container)

        ViewCompat.setOnApplyWindowInsetsListener(appBar) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = systemBars.top)
            insets
        }

        ViewCompat.setOnApplyWindowInsetsListener(bottomButtonContainer) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(bottom = systemBars.bottom)
            insets
        }
    }

    private fun loadWorkflowData() {
        val workflowId = intent.getStringExtra(EXTRA_WORKFLOW_ID)
        if (workflowId != null) {
            currentWorkflow = workflowManager.getWorkflow(workflowId)
            currentWorkflow?.let {
                nameEditText.setText(it.name)
                actionSteps.clear()
                actionSteps.addAll(it.steps)
            }
        }
        if (actionSteps.isEmpty()) {
            ModuleRegistry.getModule("vflow.trigger.manual")?.let {
                actionSteps.addAll(it.createSteps())
            }
        }
        recalculateAndNotify()
    }

    private fun showActionPicker() {
        val picker = ActionPickerSheet()
        picker.onActionSelected = { module ->
            if (module.metadata.category == "触发器") {
                Toast.makeText(this, "触发器只能位于工作流的开始。", Toast.LENGTH_SHORT).show()
            } else {
                showActionEditor(module, null, -1, null)
            }
        }
        picker.show(supportFragmentManager, "ActionPicker")
    }

    private fun recalculateAndNotify() {
        recalculateAllIndentation()
        actionStepAdapter.notifyDataSetChanged()
    }

    private fun recalculateAllIndentation() {
        val indentStack = Stack<String?>()
        for (step in actionSteps) {
            val module = ModuleRegistry.getModule(step.moduleId)
            val behavior = module?.blockBehavior

            if(behavior == null){
                step.indentationLevel = 0
                continue
            }

            when (behavior.type) {
                BlockType.BLOCK_END -> {
                    step.indentationLevel = (indentStack.size - 1).coerceAtLeast(0)
                    if (indentStack.isNotEmpty() && indentStack.peek() == behavior.pairingId) {
                        indentStack.pop()
                    }
                }
                BlockType.BLOCK_MIDDLE -> {
                    step.indentationLevel = (indentStack.size - 1).coerceAtLeast(0)
                }
                BlockType.BLOCK_START -> {
                    step.indentationLevel = indentStack.size
                    indentStack.push(behavior.pairingId)
                }
                BlockType.NONE -> {
                    step.indentationLevel = indentStack.size
                }
            }
        }
    }

    private fun saveWorkflow() {
        val name = nameEditText.text.toString().trim()
        if (name.isBlank()) {
            Toast.makeText(this, "工作流名称不能为空", Toast.LENGTH_SHORT).show()
        } else {
            val workflowToSave = currentWorkflow?.copy(
                name = name,
                steps = actionSteps
            ) ?: Workflow(
                id = UUID.randomUUID().toString(),
                name = name,
                steps = actionSteps
            )
            workflowManager.saveWorkflow(workflowToSave)
            Toast.makeText(this, "工作流已保存", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
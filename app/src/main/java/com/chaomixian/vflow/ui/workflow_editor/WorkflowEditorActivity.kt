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
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionState
import com.chaomixian.vflow.core.execution.ExecutionStateBus
import com.chaomixian.vflow.core.execution.WorkflowExecutor
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.WorkflowManager
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.core.workflow.model.Workflow
import com.chaomixian.vflow.permissions.PermissionActivity
import com.chaomixian.vflow.permissions.PermissionManager
import com.chaomixian.vflow.ui.app_picker.AppPickerActivity
import com.chaomixian.vflow.ui.common.BaseActivity
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.*

/**
 * 工作流编辑器 Activity。
 */
class WorkflowEditorActivity : BaseActivity() {

    private lateinit var workflowManager: WorkflowManager
    private var currentWorkflow: Workflow? = null
    private val actionSteps = mutableListOf<ActionStep>()
    private lateinit var actionStepAdapter: ActionStepAdapter
    private lateinit var nameEditText: EditText
    private lateinit var itemTouchHelper: ItemTouchHelper
    private var currentEditorSheet: ActionEditorSheet? = null
    private lateinit var executeButton: MaterialButton

    private var pendingExecutionWorkflow: Workflow? = null

    private var listBeforeDrag: List<ActionStep>? = null
    private var dragStartPosition: Int = -1

    private var appPickerCallback: ((resultCode: Int, data: Intent?) -> Unit)? = null
    private var editingPositionForAppPicker: Int = -1

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

    private val appPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        handleAppPickerResult(result.resultCode, result.data, editingPositionForAppPicker)
        appPickerCallback?.invoke(result.resultCode, result.data)
        appPickerCallback = null
        editingPositionForAppPicker = -1
    }

    companion object {
        const val EXTRA_WORKFLOW_ID = "WORKFLOW_ID"
    }

    /** Activity 创建时的初始化。 */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_workflow_editor)
        applyWindowInsets()

        workflowManager = WorkflowManager(this)
        nameEditText = findViewById(R.id.edit_text_workflow_name)
        executeButton = findViewById(R.id.button_execute_workflow)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar_editor)
        toolbar.setNavigationOnClickListener { finish() }

        setupRecyclerView()
        loadWorkflowData()
        setupDragAndDrop()

        // [修改] “添加动作”按钮现在只负责添加普通动作，不再处理触发器
        findViewById<Button>(R.id.button_add_action).setOnClickListener {
            showActionPicker(isTriggerPicker = false)
        }
        findViewById<Button>(R.id.button_save_workflow).setOnClickListener { saveWorkflow() }

        executeButton.setOnClickListener {
            val name = nameEditText.text.toString().trim()
            if (name.isBlank()) {
                Toast.makeText(this, "工作流名称不能为空", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val workflowToExecute = currentWorkflow?.copy(
                name = name,
                steps = actionSteps.toList()
            ) ?: Workflow(
                id = currentWorkflow?.id ?: UUID.randomUUID().toString(),
                name = name,
                steps = actionSteps.toList()
            )

            if (WorkflowExecutor.isRunning(workflowToExecute.id)) {
                WorkflowExecutor.stopExecution(workflowToExecute.id)
            } else {
                executeWorkflow(workflowToExecute)
            }
        }

        lifecycleScope.launch {
            ExecutionStateBus.stateFlow.collectLatest { state ->
                val workflowId = currentWorkflow?.id ?: return@collectLatest
                val relevantState = when(state) {
                    is ExecutionState.Running -> if (state.workflowId == workflowId) "running" else null
                    is ExecutionState.Finished -> if (state.workflowId == workflowId) "finished" else null
                    is ExecutionState.Cancelled -> if (state.workflowId == workflowId) "finished" else null
                }
                if (relevantState != null) {
                    updateExecuteButton(relevantState == "running")
                }
            }
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

    /**
     * 显示模块参数编辑器 (ActionEditorSheet)。
     */
    private fun showActionEditor(module: ActionModule, existingStep: ActionStep?, position: Int, focusedInputId: String?) {
        val editor = ActionEditorSheet.newInstance(module, existingStep, focusedInputId, actionSteps.toList())
        currentEditorSheet = editor

        editor.onSave = { newStepData ->
            if (position != -1) {
                if (focusedInputId != null) {
                    val updatedParams = actionSteps[position].parameters.toMutableMap()
                    updatedParams.putAll(newStepData.parameters)
                    actionSteps[position] = actionSteps[position].copy(parameters = updatedParams)
                } else {
                    actionSteps[position] = actionSteps[position].copy(parameters = newStepData.parameters)
                }
            } else {
                val stepsToAdd = module.createSteps()
                val configuredFirstStep = stepsToAdd.first().copy(parameters = newStepData.parameters)
                actionSteps.add(configuredFirstStep)
                if (stepsToAdd.size > 1) {
                    actionSteps.addAll(stepsToAdd.subList(1, stepsToAdd.size))
                }
            }
            recalculateAndNotify()
        }

        editor.onMagicVariableRequested = { inputId ->
            val stepPositionForContext = if (position != -1) position else actionSteps.size
            showMagicVariablePicker(stepPositionForContext, inputId, module)
        }

        editor.onStartActivityForResult = { intent, callback ->
            this.appPickerCallback = callback
            this.editingPositionForAppPicker = position
            appPickerLauncher.launch(intent)
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
                val isCompatible = targetInputDef.acceptedMagicVariableTypes.isEmpty() ||
                        targetInputDef.acceptedMagicVariableTypes.contains(outputDef.typeName)
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

    /** 初始化 RecyclerView 及其 Adapter 和 ItemDecoration。 */
    private fun setupRecyclerView() {
        val prefs = getSharedPreferences("vFlowPrefs", Context.MODE_PRIVATE)
        val hideConnections = prefs.getBoolean("hideConnections", false)

        actionStepAdapter = ActionStepAdapter(
            actionSteps,
            hideConnections,
            onEditClick = { position, inputId ->
                val step = actionSteps[position]
                val module = ModuleRegistry.getModule(step.moduleId)
                if (module == null) return@ActionStepAdapter

                // [修复] 核心逻辑变更：点击第一项（触发器）时，弹出触发器选择器
                if (position == 0) {
                    showTriggerPicker()
                } else {
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
            },
            onStartActivityForResult = { position, intent, callback ->
                appPickerCallback = { resultCode, data ->
                    callback.invoke(resultCode, data)
                }
                editingPositionForAppPicker = position
                appPickerLauncher.launch(intent)
            }
        )
        findViewById<RecyclerView>(R.id.recycler_view_action_steps).apply {
            layoutManager = LinearLayoutManager(this@WorkflowEditorActivity)
            adapter = actionStepAdapter
        }
    }

    /**
     * 处理从AppPickerActivity或ActivityPickerActivity返回的结果。
     */
    private fun handleAppPickerResult(resultCode: Int, data: Intent?, position: Int) {
        if (resultCode == Activity.RESULT_OK && data != null && position != -1) {
            val packageName = data.getStringExtra(AppPickerActivity.EXTRA_SELECTED_PACKAGE_NAME)
            val activityName = data.getStringExtra(AppPickerActivity.EXTRA_SELECTED_ACTIVITY_NAME)

            if (packageName != null && activityName != null) {
                val step = actionSteps.getOrNull(position) ?: return
                // 更新步骤的参数
                val updatedParams = step.parameters.toMutableMap()
                updatedParams["packageName"] = packageName
                updatedParams["activityName"] = activityName
                actionSteps[position] = step.copy(parameters = updatedParams)

                // 刷新列表以显示新的摘要
                recalculateAndNotify()

                // 刷新打开的编辑器
                currentEditorSheet?.updateParametersAndRebuildUi(updatedParams)
            }
        }
    }

    /** 设置 RecyclerView 的拖拽排序功能 (ItemTouchHelper)。 */
    private fun setupDragAndDrop() {
        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPosition = viewHolder.adapterPosition
                val toPosition = target.adapterPosition
                if (fromPosition > 0 && toPosition > 0) {
                    Collections.swap(actionSteps, fromPosition, toPosition)
                    actionStepAdapter.notifyItemMoved(fromPosition, toPosition)
                }
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                    viewHolder?.let {
                        dragStartPosition = it.adapterPosition
                        listBeforeDrag = actionSteps.toList()
                    }
                }
            }

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                val originalList = listBeforeDrag
                val fromPos = dragStartPosition
                val toPos = viewHolder.adapterPosition
                listBeforeDrag = null
                dragStartPosition = -1

                if (originalList == null || fromPos == -1 || toPos == -1 || fromPos == toPos) {
                    recalculateAndNotify()
                    return
                }

                val (blockStart, blockEnd) = findBlockRangeInList(originalList, fromPos)
                val blockToMove = originalList.subList(blockStart, blockEnd + 1)
                val tempList = originalList.toMutableList()
                tempList.removeAll(blockToMove.toSet())
                val targetIndex = if (toPos > fromPos) toPos - blockToMove.size + 1 else toPos

                var isValidMove = false
                if (targetIndex >= 0 && targetIndex <= tempList.size) {
                    tempList.addAll(targetIndex.coerceAtMost(tempList.size), blockToMove)
                    if (isBlockStructureValid(tempList)) {
                        actionSteps.clear()
                        actionSteps.addAll(tempList)
                        isValidMove = true
                    }
                }

                if (!isValidMove) {
                    Toast.makeText(this@WorkflowEditorActivity, "无效的移动", Toast.LENGTH_SHORT).show()
                    actionSteps.clear()
                    actionSteps.addAll(originalList)
                }
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

    private fun findBlockRangeInList(list: List<ActionStep>, position: Int): Pair<Int, Int> {
        val initialModule = ModuleRegistry.getModule(list.getOrNull(position)?.moduleId ?: return position to position)
        val behavior = initialModule?.blockBehavior
        if (behavior == null || behavior.type == BlockType.NONE || behavior.pairingId == null) {
            return position to position
        }
        var start = position
        var end = position
        var openCount = 0
        for (i in position downTo 0) {
            val currentModule = ModuleRegistry.getModule(list[i].moduleId)
            if (currentModule?.blockBehavior?.pairingId == behavior.pairingId) {
                when (currentModule.blockBehavior.type) {
                    BlockType.BLOCK_END -> openCount++
                    BlockType.BLOCK_START -> {
                        openCount--
                        if (openCount <= 0) {
                            start = i
                            break
                        }
                    }
                    else -> {}
                }
            }
        }
        openCount = 0
        for (i in start until list.size) {
            val currentModule = ModuleRegistry.getModule(list[i].moduleId)
            if (currentModule?.blockBehavior?.pairingId == behavior.pairingId) {
                when (currentModule.blockBehavior.type) {
                    BlockType.BLOCK_START -> openCount++
                    BlockType.BLOCK_END -> {
                        openCount--
                        if (openCount == 0) {
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
                updateExecuteButton(WorkflowExecutor.isRunning(it.id))
            }
        }
        if (actionSteps.isEmpty()) {
            ModuleRegistry.getModule("vflow.trigger.manual")?.let {
                actionSteps.addAll(it.createSteps())
            }
        }
        recalculateAndNotify()
    }

    private fun updateExecuteButton(isRunning: Boolean) {
        if (isRunning) {
            executeButton.text = "停止"
            executeButton.setIconResource(R.drawable.rounded_pause_24)
        } else {
            executeButton.text = getString(R.string.workflow_editor_execute)
            executeButton.setIconResource(R.drawable.ic_play_arrow)
        }
    }

    /**
     * [修改] 显示动作或触发器选择器。
     * @param isTriggerPicker 如果为 true，则只显示“触发器”分类的模块。
     */
    private fun showActionPicker(isTriggerPicker: Boolean) {
        val picker = ActionPickerSheet()
        // 传递一个参数告诉选择器我们想要哪种类型的模块
        picker.arguments = Bundle().apply {
            putBoolean("is_trigger_picker", isTriggerPicker)
        }

        picker.onActionSelected = { module ->
            if (isTriggerPicker) {
                // 如果是选择触发器，则替换掉第一个步骤
                val newTriggerSteps = module.createSteps()
                if (actionSteps.isNotEmpty()) {
                    actionSteps[0] = newTriggerSteps.first() // 直接替换
                } else {
                    actionSteps.add(newTriggerSteps.first()) // 如果列表为空则添加
                }
                // 如果新触发器需要配置（即有输入参数），则立即打开编辑器
                if (module.getInputs().isNotEmpty()) {
                    showActionEditor(module, actionSteps.first(), 0, null)
                } else {
                    recalculateAndNotify() // 否则直接刷新列表
                }
            } else {
                // 否则，正常添加新动作到末尾并打开编辑器
                showActionEditor(module, null, -1, null)
            }
        }
        picker.show(supportFragmentManager, "ActionPicker")
    }

    /**
     * [新增] 一个专门用于显示触发器选择器的便捷方法。
     */
    private fun showTriggerPicker() {
        showActionPicker(isTriggerPicker = true)
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

            if (behavior == null) {
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
                steps = actionSteps.toList()
            ) ?: Workflow(
                id = UUID.randomUUID().toString(),
                name = name,
                steps = actionSteps.toList()
            )
            workflowManager.saveWorkflow(workflowToSave)
            Toast.makeText(this, "工作流已保存", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
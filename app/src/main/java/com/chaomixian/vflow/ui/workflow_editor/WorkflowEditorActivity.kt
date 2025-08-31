package com.chaomixian.vflow.ui.workflow_editor

import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.WorkflowManager
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.core.workflow.model.Workflow
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
    }

    private fun showActionEditor(module: ActionModule, existingStep: ActionStep?, position: Int, focusedInputId: String?) {
        val editor = ActionEditorSheet.newInstance(module, existingStep, focusedInputId)
        currentEditorSheet = editor

        editor.onSave = { newStep ->
            if (position != -1) {
                actionSteps[position] = actionSteps[position].copy(parameters = newStep.parameters)
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
            showMagicVariablePicker(position, inputId, module)
        }

        editor.show(supportFragmentManager, "ActionEditor")
    }

    private fun showMagicVariablePicker(editingStepPosition: Int, targetInputId: String, editingModule: ActionModule) {
        val targetInputDef = editingModule.getInputs().find { it.id == targetInputId } ?: return
        val availableVariables = mutableListOf<MagicVariableItem>()
        val effectivePosition = if (editingStepPosition == -1) actionSteps.size else editingStepPosition

        for (i in 0 until effectivePosition) {
            val step = actionSteps[i]
            val module = ModuleRegistry.getModule(step.moduleId)
            // 使用新的 getOutputs(step) 方法
            module?.getOutputs(step)?.forEach { outputDef ->
                val isCompatible = targetInputDef.acceptedMagicVariableTypes.any { acceptedType ->
                    acceptedType.isAssignableFrom(outputDef.type)
                }
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

        if (availableVariables.isEmpty()) {
            Toast.makeText(this, "没有可兼容的变量", Toast.LENGTH_SHORT).show()
            return
        }

        val picker = MagicVariablePickerSheet.newInstance(availableVariables)
        picker.onVariableSelected = { selectedVariable ->
            currentEditorSheet?.updateInputWithVariable(targetInputId, selectedVariable.variableReference)
        }
        picker.show(supportFragmentManager, "MagicVariablePicker")
    }


    private fun setupRecyclerView() {
        val prefs = getSharedPreferences("vFlowPrefs", Context.MODE_PRIVATE)
        val hideConnections = prefs.getBoolean("hideConnections", false)

        actionStepAdapter = ActionStepAdapter(
            actionSteps,
            hideConnections,
            onEditClick = { position, inputId ->
                val step = actionSteps[position]
                val startPos = findBlockStartPosition(position)
                val startStep = actionSteps[startPos]
                val module = ModuleRegistry.getModule(startStep.moduleId)

                if (module != null) {
                    showActionEditor(module, step, startPos, inputId)
                }
            },
            onDeleteClick = { position ->
                val step = actionSteps[position]
                val module = ModuleRegistry.getModule(step.moduleId)
                if (module != null) {
                    if (module.onStepDeleted(actionSteps, position)) {
                        recalculateAndNotify()
                    }
                }
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

    private fun setupDragAndDrop() {
        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            private var dragFromPosition: Int = -1
            private var dragToPosition: Int = -1
            private var initialListState: List<ActionStep>? = null

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPos = viewHolder.adapterPosition
                val toPos = target.adapterPosition
                if (fromPos > 0 && toPos > 0) {
                    dragToPosition = toPos
                    actionStepAdapter.moveItem(fromPos, toPos)
                }
                return true
            }

            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && viewHolder != null) {
                    dragFromPosition = viewHolder.adapterPosition
                    dragToPosition = dragFromPosition
                    initialListState = actionSteps.toList()
                }
            }

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                if (dragFromPosition <= 0 || dragToPosition <= 0 || dragFromPosition == dragToPosition) {
                    if (dragFromPosition != -1) recalculateAndNotify()
                    resetDragState()
                    return
                }

                initialListState?.let {
                    actionSteps.clear()
                    actionSteps.addAll(it)
                }

                val blockStartPos = findBlockStartPosition(dragFromPosition)
                val blockEndPos = findBlockEndPosition(blockStartPos)
                val isMovingBlock = (blockStartPos == dragFromPosition && blockEndPos != blockStartPos)

                val itemsToMove: List<ActionStep>
                if (isMovingBlock) {
                    itemsToMove = actionSteps.subList(blockStartPos, blockEndPos + 1).toList()
                } else {
                    itemsToMove = listOf(actionSteps[dragFromPosition])
                }

                if (dragToPosition > blockStartPos && dragToPosition <= blockEndPos) {
                    recalculateAndNotify()
                    resetDragState()
                    return
                }

                actionSteps.removeAll(itemsToMove.toSet())

                val adjustedInsertionIndex = if (dragToPosition > blockStartPos) {
                    dragToPosition - itemsToMove.size + 1
                } else {
                    dragToPosition
                }

                val finalInsertionIndex = adjustedInsertionIndex.coerceIn(1, actionSteps.size)
                actionSteps.addAll(finalInsertionIndex, itemsToMove)

                recalculateAndNotify()
                resetDragState()
            }

            private fun resetDragState() {
                dragFromPosition = -1
                dragToPosition = -1
                initialListState = null
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

            override fun getDragDirs(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
                val position = viewHolder.adapterPosition
                if (position <= 0) return 0
                val step = actionSteps.getOrNull(position) ?: return 0
                val module = ModuleRegistry.getModule(step.moduleId)
                return when (module?.blockBehavior?.type) {
                    BlockType.BLOCK_MIDDLE, BlockType.BLOCK_END -> 0
                    else -> super.getDragDirs(recyclerView, viewHolder)
                }
            }
        }
        itemTouchHelper = ItemTouchHelper(callback)
        itemTouchHelper.attachToRecyclerView(findViewById(R.id.recycler_view_action_steps))
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
            val manualTriggerModule = ModuleRegistry.getModule("vflow.trigger.manual")!!
            actionSteps.addAll(manualTriggerModule.createSteps())
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
        val indentStack = Stack<Pair<String, String?>>()
        var currentIndent = 0
        for (i in 0 until actionSteps.size) {
            val step = actionSteps[i]
            val behavior = getBlockBehavior(step)

            if (behavior.type == BlockType.BLOCK_END) {
                if (indentStack.isNotEmpty() && indentStack.peek().second == behavior.pairingId) {
                    indentStack.pop()
                    currentIndent = indentStack.size
                }
            }
            else if (behavior.type == BlockType.BLOCK_MIDDLE) {
                if (indentStack.isNotEmpty() && indentStack.peek().second == behavior.pairingId) {
                    currentIndent = indentStack.size -1
                }
            }

            step.indentationLevel = currentIndent

            if (behavior.type == BlockType.BLOCK_START) {
                indentStack.push(Pair(step.moduleId, behavior.pairingId))
                currentIndent = indentStack.size
            }
        }
    }


    private fun getBlockBehavior(step: ActionStep): BlockBehavior {
        return ModuleRegistry.getModule(step.moduleId)?.blockBehavior ?: BlockBehavior(BlockType.NONE)
    }

    private fun findBlockStartPosition(anyPositionInBlock: Int): Int {
        val behavior = getBlockBehavior(actionSteps[anyPositionInBlock])
        if (behavior.type != BlockType.BLOCK_MIDDLE && behavior.type != BlockType.BLOCK_END) {
            return anyPositionInBlock
        }

        var level = 0
        for (i in anyPositionInBlock - 1 downTo 1) {
            val currentBehavior = getBlockBehavior(actionSteps[i])
            if (currentBehavior.pairingId == behavior.pairingId) {
                if (currentBehavior.type == BlockType.BLOCK_END || currentBehavior.type == BlockType.BLOCK_MIDDLE) {
                    level++
                }
                if (currentBehavior.type == BlockType.BLOCK_START) {
                    if (level == 0) return i
                    level--
                }
            }
        }
        return anyPositionInBlock
    }

    private fun findBlockEndPosition(startPosition: Int): Int {
        val startBehavior = getBlockBehavior(actionSteps[startPosition])
        if (startBehavior.type != BlockType.BLOCK_START) return startPosition

        var openBlocks = 1
        for (i in (startPosition + 1) until actionSteps.size) {
            val currentBehavior = getBlockBehavior(actionSteps[i])
            if (currentBehavior.pairingId == startBehavior.pairingId) {
                if (currentBehavior.type == BlockType.BLOCK_START) {
                    openBlocks++
                } else if (currentBehavior.type == BlockType.BLOCK_END) {
                    openBlocks--
                    if (openBlocks == 0) return i
                }
            }
        }
        return startPosition
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
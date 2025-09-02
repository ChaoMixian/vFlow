// main/java/com/chaomixian/vflow/ui/workflow_editor/WorkflowEditorActivity.kt

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
import androidx.appcompat.app.AlertDialog
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

            module?.getOutputs(step)?.forEach { outputDef ->
                val isCompatible = targetInputDef.acceptedMagicVariableTypes.contains(outputDef.typeName)
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

            // --- 核心修改：当为 IF 模块的 condition 连接变量后，自动设置默认的 checkMode ---
            if (editingModule.id == "vflow.logic.if.start" && targetInputId == "condition") {
                val sourceStepId = selectedVariable.variableReference.removeSurrounding("{{", "}}").split('.').firstOrNull()
                val sourceStep = actionSteps.find { it.id == sourceStepId }
                val sourceModule = sourceStep?.let { ModuleRegistry.getModule(it.moduleId) }
                val sourceOutput = sourceModule?.getOutputs(sourceStep)?.firstOrNull()
                val defaultOption = sourceOutput?.conditionalOptions?.firstOrNull()?.value

                if (defaultOption != null) {
                    val currentParams = (currentEditorSheet?.getStepParameters() ?: emptyMap()).toMutableMap()
                    currentParams["checkMode"] = defaultOption
                    currentEditorSheet?.updateParameters(currentParams)
                }
            }
        }
        picker.show(supportFragmentManager, "MagicVariablePicker")
    }

    // --- 核心新增：处理摘要中条件药丸点击事件的逻辑 ---
    private fun showConditionalOptionPicker(position: Int, parameterId: String, options: List<ConditionalOption>) {
        val step = actionSteps.getOrNull(position) ?: return
        val currentChoice = step.parameters[parameterId] as? String
        val choices = options.map { it.displayName }.toTypedArray()
        val currentChoiceIndex = options.indexOfFirst { it.value == currentChoice }

        AlertDialog.Builder(this)
            .setTitle("选择条件")
            .setSingleChoiceItems(choices, currentChoiceIndex) { dialog, which ->
                val selectedOption = options[which]
                val newParameters = step.parameters.toMutableMap()
                newParameters[parameterId] = selectedOption.value
                // 更新列表中的步骤数据，并通知适配器刷新
                actionSteps[position] = step.copy(parameters = newParameters)
                actionStepAdapter.notifyItemChanged(position)
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
    }


    private fun setupRecyclerView() {
        val prefs = getSharedPreferences("vFlowPrefs", Context.MODE_PRIVATE)
        val hideConnections = prefs.getBoolean("hideConnections", false)

        actionStepAdapter = ActionStepAdapter(
            actionSteps,
            hideConnections,
            onEditClick = { position, inputId ->
                val step = actionSteps[position]
                val module = ModuleRegistry.getModule(step.moduleId)
                if (module != null) {
                    showActionEditor(module, step, position, inputId)
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
            },
            // --- 核心新增：将新回调传递给 Adapter ---
            onParameterPillClick = { position, parameterId, options ->
                if (parameterId == "checkMode") {
                    showConditionalOptionPicker(position, parameterId, options)
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
            private var fromPos: Int = -1
            private var toPos: Int = -1

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPosition = viewHolder.adapterPosition
                val toPosition = target.adapterPosition

                if (fromPosition == 0 || toPosition == 0) return false // 禁止移动触发器

                fromPos = fromPosition
                toPos = toPosition

                actionStepAdapter.moveItem(fromPosition, toPosition)
                return true
            }

            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && viewHolder != null) {
                    fromPos = viewHolder.adapterPosition
                }
            }

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                if (fromPos != -1 && toPos != -1 && fromPos != toPos) {
                    recalculateAndNotify()
                }
                fromPos = -1
                toPos = -1
            }


            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

            override fun getDragDirs(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
                if (viewHolder.adapterPosition == 0) return 0 // 触发器不可拖动
                return super.getDragDirs(recyclerView, viewHolder)
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
        val indentStack = Stack<String?>()
        var currentIndent = 0
        for (step in actionSteps) {
            val behavior = ModuleRegistry.getModule(step.moduleId)?.blockBehavior ?: BlockBehavior(BlockType.NONE)

            if (behavior.type == BlockType.BLOCK_END) {
                if (indentStack.isNotEmpty() && indentStack.peek() == behavior.pairingId) {
                    indentStack.pop()
                    currentIndent = indentStack.size
                }
            } else if (behavior.type == BlockType.BLOCK_MIDDLE) {
                if (indentStack.isNotEmpty() && indentStack.peek() == behavior.pairingId) {
                    currentIndent = indentStack.size -1
                }
            }

            step.indentationLevel = currentIndent

            if (behavior.type == BlockType.BLOCK_START) {
                indentStack.push(behavior.pairingId)
                currentIndent = indentStack.size
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

    // 扩展 ActionEditorSheet 以便在外部访问其内部状态
    fun ActionEditorSheet.getStepParameters(): Map<String, Any?>? {
        // This would require making 'currentParameters' in ActionEditorSheet public or providing a getter
        // For simulation, we assume this is possible. In real code, you'd adjust visibility.
        return null // Placeholder
    }
    fun ActionEditorSheet.updateParameters(params: Map<String, Any?>) {
        // This would require a public method in ActionEditorSheet to update its internal state
    }
}
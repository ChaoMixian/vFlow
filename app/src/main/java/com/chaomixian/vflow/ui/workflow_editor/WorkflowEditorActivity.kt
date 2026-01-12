// 文件: main/java/com/chaomixian/vflow/ui/workflow_editor/WorkflowEditorActivity.kt
package com.chaomixian.vflow.ui.workflow_editor

import android.animation.Animator
import android.animation.AnimatorInflater
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionState
import com.chaomixian.vflow.core.execution.ExecutionStateBus
import com.chaomixian.vflow.core.execution.WorkflowExecutor
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.WorkflowManager
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.core.workflow.model.Workflow
import com.chaomixian.vflow.core.workflow.module.data.CreateVariableModule
import com.chaomixian.vflow.core.workflow.module.logic.FOREACH_PAIRING_ID
import com.chaomixian.vflow.core.workflow.module.logic.FOREACH_START_ID
import com.chaomixian.vflow.core.workflow.module.logic.ForEachModule
import com.chaomixian.vflow.core.workflow.module.logic.LOOP_PAIRING_ID
import com.chaomixian.vflow.core.workflow.module.logic.LOOP_START_ID
import com.chaomixian.vflow.core.workflow.module.logic.LoopModule
import com.chaomixian.vflow.permissions.PermissionActivity
import com.chaomixian.vflow.permissions.PermissionManager
import com.chaomixian.vflow.ui.app_picker.AppPickerActivity
import com.chaomixian.vflow.ui.common.BaseActivity
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.gson.Gson
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
    private lateinit var aiMagicButton: ImageButton
    private lateinit var recyclerView: RecyclerView
    private val gson = Gson()

    private var initialWorkflowJson: String? = null

    private var pendingExecutionWorkflow: Workflow? = null

    private var listBeforeDrag: List<ActionStep>? = null
    private var dragStartPosition: Int = -1

    private var appPickerCallback: ((resultCode: Int, data: Intent?) -> Unit)? = null
    private var editingPositionForAppPicker: Int = -1

    // 存储动画 Animator 实例
    private var dragGlowAnimator: Animator? = null
    private var dragBreathAnimator: Animator? = null
    private var executionAnimator: Animator? = null
    private var currentlyExecutingViewHolder: RecyclerView.ViewHolder? = null


    // 用于保存和恢复状态的常量
    private val STATE_ACTION_STEPS = "state_action_steps"
    private val STATE_WORKFLOW_NAME = "state_workflow_name"

    // 用于变量重命名
    private var oldVariableName: String? = null


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

    /**
     * 保存 Activity 状态，防止数据丢失。
     */
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // 保存当前正在编辑的步骤列表和工作流名称
        outState.putParcelableArrayList(STATE_ACTION_STEPS, ArrayList(actionSteps))
        outState.putString(STATE_WORKFLOW_NAME, nameEditText.text.toString())
    }


    /** Activity 创建时的初始化。 */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_workflow_editor)
        applyWindowInsets()

        workflowManager = WorkflowManager(this)
        nameEditText = findViewById(R.id.edit_text_workflow_name)
        aiMagicButton = findViewById(R.id.btn_ai_magic)
        executeButton = findViewById(R.id.button_execute_workflow)
        recyclerView = findViewById(R.id.recycler_view_action_steps)


        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar_editor)
        toolbar.setNavigationOnClickListener { handleExitRequest() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleExitRequest()
            }
        })

        setupRecyclerView()

        // 检查是否有已保存的状态，如果有则恢复，否则才从数据库加载
        if (savedInstanceState != null) {
            val savedSteps = savedInstanceState.getParcelableArrayList<ActionStep>(STATE_ACTION_STEPS)
            if (savedSteps != null) {
                actionSteps.clear()
                actionSteps.addAll(savedSteps)
            }
            nameEditText.setText(savedInstanceState.getString(STATE_WORKFLOW_NAME))

            // 恢复 currentWorkflow 对象以正确显示执行按钮状态
            val workflowId = intent.getStringExtra(EXTRA_WORKFLOW_ID)
            if (workflowId != null) {
                currentWorkflow = workflowManager.getWorkflow(workflowId)
                currentWorkflow?.let {
                    updateExecuteButton(WorkflowExecutor.isRunning(it.id))
                    initialWorkflowJson = gson.toJson(getCurrentWorkflowState())
                }
            }
            recalculateAndNotify() // 通知适配器数据已恢复
        } else {
            // 首次创建时，从数据库加载数据
            loadWorkflowData()
        }

        setupDragAndDrop()

        // “添加动作”按钮现在只负责添加普通动作，不再处理触发器
        findViewById<Button>(R.id.button_add_action).setOnClickListener {
            showActionPicker(isTriggerPicker = false)
        }
        findViewById<Button>(R.id.button_save_workflow).setOnClickListener { saveWorkflow(false) }

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

        // AI 按钮点击事件
        aiMagicButton.setOnClickListener {
            showAiCreationSheet()
        }

        lifecycleScope.launch {
            ExecutionStateBus.stateFlow.collectLatest { state ->
                val workflowId = currentWorkflow?.id ?: return@collectLatest
                if (state.workflowId != workflowId) return@collectLatest

                when (state) {
                    is ExecutionState.Running -> {
                        updateExecuteButton(true)
                        highlightStep(state.stepIndex)
                    }
                    is ExecutionState.Finished, is ExecutionState.Cancelled -> {
                        updateExecuteButton(false)
                        clearHighlight()
                    }
                    is ExecutionState.Failure -> {
                        updateExecuteButton(false)
                        highlightStepAsFailed(state.stepIndex)
                    }
                }
            }
        }
    }

    private fun hasUnsavedChanges(): Boolean {
        val currentJson = gson.toJson(getCurrentWorkflowState())
        return currentJson != initialWorkflowJson
    }

    private fun getCurrentWorkflowState(): Workflow {
        return currentWorkflow?.copy(
            name = nameEditText.text.toString(),
            steps = actionSteps.toList()
        ) ?: Workflow(
            id = UUID.randomUUID().toString(),
            name = nameEditText.text.toString(),
            steps = actionSteps.toList()
        )
    }

    private fun handleExitRequest() {
        if (hasUnsavedChanges()) {
            showUnsavedChangesDialog()
        } else {
            finish()
        }
    }

    private fun showUnsavedChangesDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_unsaved_title)
            .setMessage(R.string.dialog_unsaved_message)
            .setPositiveButton(R.string.dialog_unsaved_save_exit) { _, _ ->
                saveWorkflow(true)
            }
            .setNegativeButton(R.string.dialog_unsaved_discard) { _, _ ->
                finish()
            }
            .setNeutralButton(R.string.common_cancel, null)
            .show()
    }

    /**
     * 使用 LinearSmoothScroller 实现更平滑的滚动，并在滚动结束后应用动画。
     * @param stepIndex 目标步骤的索引。
     * @param animatorRes 要应用的动画资源ID。
     * @param isError 是否为错误高亮。
     */
    private fun smoothScrollToPositionAndHighlight(stepIndex: Int, animatorRes: Int, isError: Boolean = false) {
        val smoothScroller = object : LinearSmoothScroller(this) {
            // 重写此方法以调整滚动速度
            override fun calculateSpeedPerPixel(displayMetrics: DisplayMetrics): Float {
                // 值越小滚动越快，返回一个较慢的速度
                return 150f / displayMetrics.densityDpi
            }

            // 当滚动完成且目标视图可见时调用
            override fun onTargetFound(targetView: View, state: RecyclerView.State, action: Action) {
                super.onTargetFound(targetView, state, action)
                val viewHolder = recyclerView.getChildViewHolder(targetView)
                if (viewHolder != null) {
                    currentlyExecutingViewHolder = viewHolder
                    // 动画的目标始终是卡片视图
                    val cardView = viewHolder.itemView.findViewById<MaterialCardView>(R.id.step_card_view)
                    executionAnimator = AnimatorInflater.loadAnimator(this@WorkflowEditorActivity, animatorRes).apply {
                        setTarget(cardView)
                        start()
                    }
                }
            }
        }

        smoothScroller.targetPosition = stepIndex
        recyclerView.layoutManager?.startSmoothScroll(smoothScroller)
    }


    /**
     * 高亮当前正在执行的步骤。
     * @param stepIndex 要高亮的步骤的索引。
     */
    private fun highlightStep(stepIndex: Int) {
        if (stepIndex < 0 || stepIndex >= actionSteps.size) return
        clearHighlight() // 先清除上一个高亮
        smoothScrollToPositionAndHighlight(stepIndex, R.animator.execution_highlight)
    }

    /**
     * 高亮执行失败的步骤。
     * @param stepIndex 失败的步骤的索引。
     */
    private fun highlightStepAsFailed(stepIndex: Int) {
        if (stepIndex < 0 || stepIndex >= actionSteps.size) return
        clearHighlight() // 清除任何可能存在的正常高亮
        smoothScrollToPositionAndHighlight(stepIndex, R.animator.execution_error, isError = true)
    }


    /**
     * 清除所有高亮和动画效果。
     */
    private fun clearHighlight() {
        executionAnimator?.cancel()
        executionAnimator = null
        currentlyExecutingViewHolder?.itemView?.let {
            // 恢复所有可能被动画修改的属性
            it.alpha = 1.0f
            it.scaleX = 1.0f
            it.scaleY = 1.0f
            val cardView = it.findViewById<MaterialCardView>(R.id.step_card_view)
            cardView?.setCardBackgroundColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface, 0))
        }
        currentlyExecutingViewHolder = null
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

    /** 提取一个辅助函数以分组形式获取所有可用的命名变量 */
    private fun getAvailableNamedVariables(upToPosition: Int): Map<String, List<MagicVariableItem>> {
        val availableNamedVariables = mutableMapOf<String, MagicVariableItem>()
        actionSteps.subList(0, upToPosition)
            .filter { it.moduleId == CreateVariableModule().id }
            .forEach { step ->
                val varName = step.parameters["variableName"] as? String
                val varType = step.parameters["type"] as? String ?: "未知"
                if (!varName.isNullOrBlank()) {
                    availableNamedVariables[varName] = MagicVariableItem(
                        variableReference = "[[$varName]]", // 使用新的引用格式
                        variableName = varName,
                        originDescription = "命名变量 ($varType)"
                    )
                }
            }
        // 将所有命名变量归入一个固定的组
        return if (availableNamedVariables.isNotEmpty()) {
            mapOf("命名变量" to availableNamedVariables.values.toList())
        } else {
            emptyMap()
        }
    }


    private fun showActionEditor(module: ActionModule, existingStep: ActionStep?, position: Int, focusedInputId: String?) {
        val contextPosition = if (position != -1) position else actionSteps.size
        // 传递一个扁平的列表供 ActionEditorSheet 内部使用
        val namedVariableNames = getAvailableNamedVariables(contextPosition)
            .values.flatten().map { it.variableName }


        // 在打开编辑器前，保存旧的变量名
        if (existingStep != null && module.id == CreateVariableModule().id) {
            oldVariableName = existingStep.parameters["variableName"] as? String
        } else {
            oldVariableName = null
        }


        val editor = ActionEditorSheet.newInstance(module, existingStep, focusedInputId, actionSteps.toList(), namedVariableNames)
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
                // 在保存后，检查并处理变量重命名
                handleVariableNameChange(position)
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

    /**
     * 检查并处理变量重命名逻辑
     */
    private fun handleVariableNameChange(editedPosition: Int) {
        val editedStep = actionSteps.getOrNull(editedPosition) ?: return
        if (editedStep.moduleId != CreateVariableModule().id) return

        val newVariableName = editedStep.parameters["variableName"] as? String

        if (!oldVariableName.isNullOrBlank() && oldVariableName != newVariableName) {
            // 变量名被修改了
            val oldRef = "[[$oldVariableName]]"
            val newRef = if (!newVariableName.isNullOrBlank()) "[[${newVariableName}]]" else ""

            // 遍历被修改步骤之后的所有步骤
            for (i in (editedPosition + 1) until actionSteps.size) {
                val currentStep = actionSteps[i]
                val updatedParameters = currentStep.parameters.toMutableMap()
                var hasChanged = false

                currentStep.parameters.forEach { (key, value) ->
                    if (value is String && value == oldRef) {
                        updatedParameters[key] = newRef
                        hasChanged = true
                    }
                    // 如果参数是Map，可以进一步检查Map内部的值
                    if (value is Map<*, *>) {
                        val updatedMap = value.mapValues { (_, v) ->
                            if (v is String && v == oldRef) {
                                hasChanged = true
                                newRef
                            } else {
                                v
                            }
                        }
                        if(hasChanged) {
                            updatedParameters[key] = updatedMap
                        }
                    }
                }

                if (hasChanged) {
                    actionSteps[i] = currentStep.copy(parameters = updatedParameters)
                }
            }
            Toast.makeText(this, "已自动更新对变量 '${oldVariableName}' 的引用", Toast.LENGTH_LONG).show()
        }

        oldVariableName = null // 重置
    }

    /**
     * 通用辅助函数，用于查找当前步骤所在的指定类型循环块的起始步骤。
     * @param position 当前步骤在 `actionSteps` 列表中的索引。
     * @param pairingId 要查找的循环块的配对ID (e.g., LOOP_PAIRING_ID, FOREACH_PAIRING_ID).
     * @return 如果在循环内，则返回循环的起始 `ActionStep`，否则返回 `null`。
     */
    private fun findEnclosingLoopStartStep(position: Int, pairingId: String): ActionStep? {
        var openCount = 0
        for (i in (position - 1) downTo 0) {
            val step = actionSteps[i]
            val module = ModuleRegistry.getModule(step.moduleId) ?: continue
            val behavior = module.blockBehavior

            if (behavior.pairingId != pairingId) continue

            when (behavior.type) {
                BlockType.BLOCK_END -> openCount++
                BlockType.BLOCK_START -> {
                    if (openCount == 0) {
                        return step
                    }
                    openCount--
                }
                else -> {}
            }
        }
        return null
    }


    private fun showMagicVariablePicker(editingStepPosition: Int, targetInputId: String, editingModule: ActionModule) {
        // 处理嵌套 ID (例如 "extras.myKey")
        // 如果 targetInputId 包含点号，则只取第一部分 (extras) 作为 InputDefinition 的 ID 来查找定义
        val realInputId = if (targetInputId.contains('.')) {
            targetInputId.split('.', limit = 2)[0]
        } else {
            targetInputId
        }

        val targetInputDef = editingModule.getDynamicInputs(actionSteps.getOrNull(editingStepPosition), actionSteps).find { it.id == realInputId }

        if (targetInputDef == null) {
            Toast.makeText(this, "无法找到输入定义: $targetInputId", Toast.LENGTH_SHORT).show()
            return
        }

        // --- 按步骤分组收集魔法变量 ---
        val groupedStepOutputs = mutableMapOf<String, MutableList<MagicVariableItem>>()

        for (i in 0 until editingStepPosition) {
            val step = actionSteps[i]
            val module = ModuleRegistry.getModule(step.moduleId) ?: continue
            // 跳过循环头本身，避免引用自己
            if (module.id == LOOP_START_ID || module.id == FOREACH_START_ID) continue

            val outputs = module.getOutputs(step).filter { outputDef ->
                targetInputDef.acceptedMagicVariableTypes.isEmpty() ||
                        targetInputDef.acceptedMagicVariableTypes.contains(outputDef.typeName)
            }

            if (outputs.isNotEmpty()) {
                val groupName = "#$i ${module.metadata.name}"
                val items = outputs.map { outputDef ->
                    MagicVariableItem(
                        variableReference = "{{${step.id}.${outputDef.id}}}",
                        variableName = outputDef.name,
                        originDescription = "(${outputDef.typeName.split('.').last()})",
                        typeId = outputDef.typeName
                    )
                }
                groupedStepOutputs.getOrPut(groupName) { mutableListOf() }.addAll(items)
            }
        }

        // --- 动态添加循环变量 ---
        val enclosingLoopStep = findEnclosingLoopStartStep(editingStepPosition, LOOP_PAIRING_ID)
        if (enclosingLoopStep != null) {
            val loopModule = ModuleRegistry.getModule(enclosingLoopStep.moduleId) as? LoopModule
            if (loopModule != null) {
                val groupName = "#${actionSteps.indexOf(enclosingLoopStep)} ${loopModule.metadata.name}"
                val items = loopModule.getOutputs(enclosingLoopStep).map { outputDef ->
                    MagicVariableItem(
                        variableReference = "{{${enclosingLoopStep.id}.${outputDef.id}}}",
                        variableName = outputDef.name,
                        originDescription = "(${outputDef.typeName.split('.').last()})",
                        typeId = outputDef.typeName
                    )
                }
                groupedStepOutputs.getOrPut(groupName) { mutableListOf() }.addAll(items)
            }
        }

        val enclosingForEachStep = findEnclosingLoopStartStep(editingStepPosition, FOREACH_PAIRING_ID)
        if (enclosingForEachStep != null) {
            val forEachModule = ModuleRegistry.getModule(enclosingForEachStep.moduleId) as? ForEachModule
            if (forEachModule != null) {
                val groupName = "#${actionSteps.indexOf(enclosingForEachStep)} ${forEachModule.metadata.name}"
                val items = forEachModule.getOutputs(enclosingForEachStep).map { outputDef ->
                    MagicVariableItem(
                        variableReference = "{{${enclosingForEachStep.id}.${outputDef.id}}}",
                        variableName = outputDef.name,
                        originDescription = if (outputDef.typeName == "vflow.type.any") "" else "(${outputDef.typeName.split('.').last()})",
                        typeId = outputDef.typeName
                    )
                }
                groupedStepOutputs.getOrPut(groupName) { mutableListOf() }.addAll(items)
            }
        }

        // --- 获取命名变量 ---
        val namedVariables = getAvailableNamedVariables(editingStepPosition)

        // --- 启动选择器 ---
        val picker = MagicVariablePickerSheet.newInstance(
            groupedStepOutputs,
            namedVariables,
            acceptsMagicVariable = targetInputDef.acceptsMagicVariable,
            acceptsNamedVariable = targetInputDef.acceptsNamedVariable
        )

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
        actionStepAdapter = ActionStepAdapter(
            actionSteps,
            onEditClick = { position, inputId ->
                val step = actionSteps[position]
                val module = ModuleRegistry.getModule(step.moduleId)
                if (module == null) return@ActionStepAdapter

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
            // 双击复制的回调实现
            onDuplicateClick = { position ->
                duplicateStepOrBlock(position)
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
     * 复制单个步骤或整个积木块
     */
    private fun duplicateStepOrBlock(position: Int) {
        if (position !in actionSteps.indices) return

        // 禁止复制触发器。
        if (position == 0) {
            Toast.makeText(this, "触发器无法复制", Toast.LENGTH_SHORT).show()
            return
        }

        // 使用现有的逻辑找到块的范围
        val (blockStart, blockEnd) = findBlockRangeInList(actionSteps, position)

        // 创建副本列表
        val stepsToDuplicate = actionSteps.subList(blockStart, blockEnd + 1).map { step ->
            // 必须为副本生成新的 UUID，否则会导致变量引用混乱和 DiffUtil 错误
            step.copy(id = UUID.randomUUID().toString())
        }

        // 插入到原块的后面
        val insertPosition = blockEnd + 1
        actionSteps.addAll(insertPosition, stepsToDuplicate)

        recalculateAndNotify()
        Toast.makeText(this, "已复制 ${stepsToDuplicate.size} 个步骤", Toast.LENGTH_SHORT).show()

        // 滚动到新复制的位置
        recyclerView.smoothScrollToPosition(insertPosition)
    }

    /**
     * 处理应用选择器返回结果的方法。
     * 现在可以正确处理 "添加" (position == -1) 和 "编辑" (position != -1) 两种情况。
     */
    private fun handleAppPickerResult(resultCode: Int, data: Intent?, position: Int) {
        if (resultCode == Activity.RESULT_OK && data != null) {
            val packageName = data.getStringExtra(AppPickerActivity.EXTRA_SELECTED_PACKAGE_NAME)
            val activityName = data.getStringExtra(AppPickerActivity.EXTRA_SELECTED_ACTIVITY_NAME)

            if (packageName != null && activityName != null) {
                val updatedParams = mapOf(
                    "packageName" to packageName,
                    "activityName" to activityName
                )

                if (position != -1) {
                    // 编辑模式：更新 actionSteps 列表中的现有步骤
                    val step = actionSteps.getOrNull(position) ?: return
                    val newParams = step.parameters.toMutableMap()
                    newParams.putAll(updatedParams)
                    actionSteps[position] = step.copy(parameters = newParams)
                    recalculateAndNotify()
                    // 通知打开的编辑器工作表更新其UI
                    currentEditorSheet?.updateParametersAndRebuildUi(newParams)
                } else {
                    // 添加模式：只通知打开的编辑器工作表更新其内部状态
                    currentEditorSheet?.updateParametersAndRebuildUi(updatedParams)
                }
            }
        }
    }


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
                        dragGlowAnimator = AnimatorInflater.loadAnimator(this@WorkflowEditorActivity, R.animator.drag_glow).apply {
                            setTarget(it.itemView)
                            start()
                        }
                        dragBreathAnimator = AnimatorInflater.loadAnimator(this@WorkflowEditorActivity, R.animator.drag_breath).apply {
                            setTarget(it.itemView)
                            start()
                        }
                    }
                }
            }


            /**
             * 重写 clearView 方法以正确处理积木块的拖拽。
             */
            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(this@WorkflowEditorActivity.recyclerView, viewHolder)

                // 清理动画效果
                dragGlowAnimator?.cancel()
                dragBreathAnimator?.cancel()
                viewHolder.itemView.apply {
                    alpha = 1.0f
                    scaleX = 1.0f
                    scaleY = 1.0f
                }

                val originalList = listBeforeDrag ?: return
                val fromPos = dragStartPosition
                val toPos = viewHolder.adapterPosition

                // 重置状态
                listBeforeDrag = null
                dragStartPosition = -1

                // 无效移动检查
                if (fromPos <= 0 || toPos < 0 || fromPos == toPos) {
                    recalculateAndNotify()
                    return
                }

                // 执行积木块移动
                val newList = moveBlockInList(originalList, fromPos, toPos)

                if (isBlockStructureValid(newList)) {
                    actionSteps.clear()
                    actionSteps.addAll(newList)
                } else {
                    Toast.makeText(this@WorkflowEditorActivity, "无效的移动", Toast.LENGTH_SHORT).show()
                    actionSteps.clear()
                    actionSteps.addAll(originalList)
                }
                recalculateAndNotify()
            }

            /**
             * 在列表中移动整个积木块
             * 更安全的积木块移动实现
             */
            private fun moveBlockInList(originalList: List<ActionStep>, fromPos: Int, toPos: Int): List<ActionStep> {
                // 输入验证
                if (fromPos !in originalList.indices || toPos !in originalList.indices) {
                    return originalList
                }

                try {
                    // 找到要移动的完整积木块范围
                    val (blockStart, blockEnd) = findBlockRangeInList(originalList, fromPos)

                    // 验证范围有效性
                    if (blockStart < 0 || blockEnd >= originalList.size || blockStart > blockEnd) {
                        return originalList
                    }

                    // 如果目标在块内部，返回原列表
                    if (toPos in blockStart..blockEnd) {
                        return originalList
                    }

                    // 使用索引范围而不是对象引用来移除块（避免重复对象问题）
                    val tempList = originalList.toMutableList()
                    val blockToMove = tempList.subList(blockStart, blockEnd + 1).toList() // 创建副本

                    // 从后往前删除，避免索引变化
                    for (i in blockEnd downTo blockStart) {
                        tempList.removeAt(i)
                    }

                    // 重新计算目标位置（因为删除操作可能改变了索引）
                    val adjustedTargetPos = when {
                        toPos > blockEnd -> toPos - (blockEnd - blockStart + 1) // 向下移动，减去删除的块大小
                        toPos < blockStart -> toPos // 向上移动，位置不变
                        else -> return originalList // 理论上不会到达这里
                    }

                    // 计算最终插入位置
                    val insertPos = if (toPos > blockEnd) {
                        // 向下移动：插入到调整后目标位置的后面
                        (adjustedTargetPos + 1).coerceIn(1, tempList.size)
                    } else {
                        // 向上移动：插入到目标位置
                        adjustedTargetPos.coerceIn(1, tempList.size)
                    }

                    // 插入积木块
                    tempList.addAll(insertPos, blockToMove)

                    return tempList

                } catch (e: Exception) {
                    // 任何异常都返回原列表，确保不会崩溃
                    return originalList
                }
            }


            /**
             * 计算正确的插入位置（已废除，暂时保留）
             */
            private fun calculateInsertionPosition(
                originalList: List<ActionStep>,
                tempList: List<ActionStep>,
                toPos: Int,
                blockStart: Int,
                blockEnd: Int
            ): Int {
                val targetItem = originalList[toPos]
                val targetIndexInTempList = tempList.indexOf(targetItem)

                return if (targetIndexInTempList == -1) {
                    tempList.size // 找不到目标项，插入到末尾
                } else {
                    // 向下移动：插入到目标项后面；向上移动：插入到目标项前面
                    if (toPos > blockEnd) targetIndexInTempList + 1 else targetIndexInTempList
                }
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
     * 更健壮的积木块范围查找
     */
    private fun findBlockRangeInList(list: List<ActionStep>, position: Int): Pair<Int, Int> {
        if (position !in list.indices) return position to position

        val initialModule = ModuleRegistry.getModule(list[position].moduleId)
            ?: return position to position

        val behavior = initialModule.blockBehavior
        if (behavior?.type == BlockType.NONE || behavior?.pairingId == null) {
            return position to position
        }

        try {
            // 向上查找块的开始
            var blockStart = position
            var openCount = 0

            for (i in position downTo 0) {
                val module = ModuleRegistry.getModule(list[i].moduleId) ?: continue
                val currentBehavior = module.blockBehavior

                if (currentBehavior?.pairingId != behavior.pairingId) continue

                when (currentBehavior.type) {
                    BlockType.BLOCK_END -> openCount++
                    BlockType.BLOCK_START -> {
                        if (openCount == 0) {
                            blockStart = i
                            break
                        }
                        openCount--
                    }
                    else -> {}
                }
            }

            // 向下查找块的结束
            var blockEnd = blockStart
            openCount = 0

            for (i in blockStart until list.size) {
                val module = ModuleRegistry.getModule(list[i].moduleId) ?: continue
                val currentBehavior = module.blockBehavior

                if (currentBehavior?.pairingId != behavior.pairingId) continue

                when (currentBehavior.type) {
                    BlockType.BLOCK_START -> openCount++
                    BlockType.BLOCK_END -> {
                        openCount--
                        if (openCount == 0) {
                            blockEnd = i
                            break
                        }
                    }
                    else -> {}
                }
            }

            return blockStart to blockEnd

        } catch (e: Exception) {
            // 异常情况返回单个位置
            return position to position
        }
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

        // 检查是否存在需要清理的未知模块
        if (actionSteps.any { ModuleRegistry.getModule(it.moduleId) == null }) {
            val originalSize = actionSteps.size
            // 过滤列表，只保留 moduleId 存在于 ModuleRegistry 中的步骤
            val cleanedSteps = actionSteps.filter { ModuleRegistry.getModule(it.moduleId) != null }
            val removedCount = originalSize - cleanedSteps.size

            if (removedCount > 0) {
                actionSteps.clear()
                actionSteps.addAll(cleanedSteps)
                // 通过 Toast 通知用户
                Toast.makeText(this, "已自动移除 $removedCount 个未知或已过时的模块。", Toast.LENGTH_LONG).show()
            }
        }

        if (actionSteps.isEmpty()) {
            ModuleRegistry.getModule("vflow.trigger.manual")?.let {
                actionSteps.addAll(it.createSteps())
            }
        }
        recalculateAndNotify()
        initialWorkflowJson = gson.toJson(getCurrentWorkflowState())
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

    private fun showActionPicker(isTriggerPicker: Boolean) {
        val picker = ActionPickerSheet()
        picker.arguments = Bundle().apply {
            putBoolean("is_trigger_picker", isTriggerPicker)
        }

        picker.onActionSelected = { module ->
            if (module.metadata.category == "模板") {
                val newSteps = module.createSteps()
                actionSteps.addAll(newSteps)
                recalculateAndNotify()
            } else if (isTriggerPicker) {
                val newTriggerSteps = module.createSteps()
                if (actionSteps.isNotEmpty()) {
                    actionSteps[0] = newTriggerSteps.first()
                } else {
                    actionSteps.add(newTriggerSteps.first())
                }
                if (module.getInputs().isNotEmpty()) {
                    showActionEditor(module, actionSteps.first(), 0, null)
                } else {
                    recalculateAndNotify()
                }
            } else {
                showActionEditor(module, null, -1, null)
            }
        }
        picker.show(supportFragmentManager, "ActionPicker")
    }

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

    private fun saveWorkflow(shouldFinish: Boolean) {
        // 变量名重复性检查
        for (step in actionSteps) {
            val module = ModuleRegistry.getModule(step.moduleId)
            if (module != null) {
                val validationResult = module.validate(step, actionSteps)
                if (!validationResult.isValid) {
                    Toast.makeText(this, validationResult.errorMessage, Toast.LENGTH_LONG).show()
                    return // 验证失败，停止保存
                }
            }
        }

        val name = nameEditText.text.toString().trim()
        if (name.isBlank()) {
            Toast.makeText(this, "工作流名称不能为空", Toast.LENGTH_SHORT).show()
        } else {
            val isNewWorkflow = currentWorkflow == null
            val workflowToSave = currentWorkflow?.copy(
                name = name,
                steps = actionSteps.toList(),
                // 继承旧的 isEnabled 状态，如果没有则默认为 true
                isEnabled = currentWorkflow?.isEnabled ?: true
            ) ?: Workflow(
                id = UUID.randomUUID().toString(),
                name = name,
                steps = actionSteps.toList()
            )
            workflowManager.saveWorkflow(workflowToSave)

            // 如果是第一次保存新工作流，则更新当前Activity的状态
            if (isNewWorkflow) {
                currentWorkflow = workflowToSave
                updateExecuteButton(WorkflowExecutor.isRunning(workflowToSave.id))
            }

            Toast.makeText(this, "工作流已保存", Toast.LENGTH_SHORT).show()

            if (shouldFinish) {
                finish()
            } else {
                initialWorkflowJson = gson.toJson(workflowToSave)
            }
        }
    }

    private fun showAiCreationSheet() {
        val sheet = AiGenerationSheet()
        sheet.onWorkflowGenerated = { generatedWorkflow ->
            applyGeneratedWorkflow(generatedWorkflow)
        }
        sheet.show(supportFragmentManager, "AiGenerationSheet")
    }

    private fun applyGeneratedWorkflow(workflow: Workflow) {
        // 如果生成了名字且当前名字为空，则使用生成的名字
        if (nameEditText.text.isBlank() && workflow.name.isNotBlank()) {
            nameEditText.setText(workflow.name)
        }

        // 覆盖现有步骤（策略可以是追加，但通常用户希望重写）
        if (actionSteps.isNotEmpty()) {
            MaterialAlertDialogBuilder(this)
                .setTitle("覆盖现有步骤？")
                .setMessage("AI 生成了新的工作流步骤。是否要清空当前编辑器的所有步骤并应用新生成的内容？")
                .setPositiveButton("覆盖") { _, _ ->
                    actionSteps.clear()
                    actionSteps.addAll(workflow.steps)
                    recalculateAndNotify()
                    Toast.makeText(this, "已应用 AI 生成的工作流", Toast.LENGTH_SHORT).show()
                }
                .setNeutralButton("追加到末尾") { _, _ ->
                    actionSteps.addAll(workflow.steps)
                    recalculateAndNotify()
                    Toast.makeText(this, "已追加步骤", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("取消", null)
                .show()
        } else {
            actionSteps.addAll(workflow.steps)
            recalculateAndNotify()
            Toast.makeText(this, "已应用 AI 生成的工作流", Toast.LENGTH_SHORT).show()
        }
    }
}
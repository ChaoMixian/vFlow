// 文件: main/java/com/chaomixian/vflow/ui/workflow_editor/WorkflowEditorActivity.kt
package com.chaomixian.vflow.ui.workflow_editor

import android.animation.Animator
import android.animation.AnimatorInflater
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.DisplayMetrics
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.Toast
import com.chaomixian.vflow.core.locale.toast
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
import com.chaomixian.vflow.core.execution.ExecutionStateBus
import com.chaomixian.vflow.core.execution.WorkflowExecutor
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.workflow.WorkflowEnumMigration
import com.chaomixian.vflow.core.workflow.WorkflowJumpReferenceUpdater
import com.chaomixian.vflow.core.workflow.WorkflowManager
import com.chaomixian.vflow.core.workflow.WorkflowVisuals
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
import com.chaomixian.vflow.ui.app_picker.AppPickerMode
import com.chaomixian.vflow.ui.app_picker.UnifiedAppPickerSheet
import com.chaomixian.vflow.ui.common.BaseActivity
import com.chaomixian.vflow.ui.workflow_editor.inspector.WorkflowInspectorInsertController
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.gson.Gson
import java.util.*

/**
 * 工作流编辑器 Activity。
 */
class WorkflowEditorActivity : BaseActivity() {
    private lateinit var workflowManager: WorkflowManager
    private var currentWorkflow: Workflow? = null
    private val triggerSteps = mutableListOf<ActionStep>()
    private val actionSteps = mutableListOf<ActionStep>()
    private lateinit var actionStepAdapter: ActionStepAdapter
    private lateinit var nameEditText: EditText
    private lateinit var itemTouchHelper: ItemTouchHelper
    private var currentEditorSheet: ActionEditorSheet? = null
    private lateinit var undoButton: Button
    private lateinit var executeButton: FloatingActionButton
    private lateinit var editorMoreButton: ImageButton
    private lateinit var recyclerView: RecyclerView
    private val gson = Gson()
    private val delayedExecuteHandler = Handler(Looper.getMainLooper())
    private val undoStack = java.util.ArrayDeque<EditorSnapshot>()
    private var suppressUndoCapture = false
    private var nameEditSnapshotCaptured = false
    private var dragUndoSnapshot: EditorSnapshot? = null

    private var initialWorkflowJson: String? = null

    private var pendingExecutionWorkflow: Workflow? = null
    private lateinit var executionTracker: WorkflowEditorExecutionTracker
    private lateinit var magicVariableCatalogBuilder: WorkflowEditorMagicVariableCatalogBuilder

    private var listBeforeDrag: List<ActionStep>? = null
    private var dragStartPosition: Int = -1

    // PickerHandler 用于处理各种选择器类型
    private var pickerHandler: PickerHandler? = null

    // 存储动画 Animator 实例
    private var dragGlowAnimator: Animator? = null
    private var dragBreathAnimator: Animator? = null
    private var executionAnimator: Animator? = null
    private var currentlyExecutingViewHolder: RecyclerView.ViewHolder? = null
    private lateinit var inspectorInsertController: WorkflowInspectorInsertController


    // 用于保存和恢复状态的常量
    private val STATE_TRIGGER_STEPS = "state_trigger_steps"
    private val STATE_ACTION_STEPS = "state_action_steps"
    private val STATE_WORKFLOW_NAME = "state_workflow_name"

    // 用于变量重命名
    private var oldVariableName: String? = null


    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            pendingExecutionWorkflow?.let {
                executionTracker.beginExecutionTracking(it.id)
                toast(getString(R.string.editor_toast_execution_start, it.name))
                val executionInstanceId = WorkflowExecutor.execute(
                    workflow = it,
                    context = this,
                    triggerStepId = it.manualTrigger()?.id
                )
                executionTracker.finishExecutionLaunch(executionInstanceId)
            }
        }
        pendingExecutionWorkflow = null
    }

    // 通用的 Intent Launcher，用于处理 ModuleUIProvider 启动的选择器
    private val generalIntentLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        pickerHandler?.handleIntentResult(result.resultCode, result.data)
    }

    // 文件选择器 launcher - 使用 GET_CONTENT 以允许第三方选择器入口（如 MT 文件管理器）
    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data
        pickerHandler?.handleFilePickerResult(uri)
    }

    // 目录选择器 launcher - 使用 OpenDocumentTree 选择本地目录
    private val directoryPickerLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        pickerHandler?.handleDirectoryPickerResult(uri)
    }

    // 媒体选择器 launcher - 使用 GET_CONTENT 以允许第三方选择器入口（如 MT 文件管理器）
    private val mediaPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data
        pickerHandler?.handleMediaPickerResult(uri)
    }

    companion object {
        const val EXTRA_WORKFLOW_ID = "WORKFLOW_ID"
        private const val MAX_UNDO_STEPS = 50
    }

    private data class EditorSnapshot(
        val workflow: Workflow?,
        val workflowName: String,
        val triggerSteps: List<ActionStep>,
        val actionSteps: List<ActionStep>
    )

    /**
     * 保存 Activity 状态，防止数据丢失。
     */
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelableArrayList(STATE_TRIGGER_STEPS, ArrayList(triggerSteps))
        outState.putParcelableArrayList(STATE_ACTION_STEPS, ArrayList(actionSteps))
        outState.putString(STATE_WORKFLOW_NAME, nameEditText.text.toString())
    }


    /** Activity 创建时的初始化。 */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_workflow_editor)
        applyWindowInsets()

        workflowManager = WorkflowManager(this)
        magicVariableCatalogBuilder = WorkflowEditorMagicVariableCatalogBuilder(this, workflowManager)
        bindViews()
        setupExecutionTracker()
        setupNavigation()
        setupRecyclerView()
        setupInspectorInsertController()
        setupRecyclerFocusDismiss()
        setupPickerHandler()
        restoreOrLoadEditorState(savedInstanceState)
        setupDragAndDrop()
        setupUndoControls()
        setupPrimaryActions()
        observeExecutionState()
    }

    private fun bindViews() {
        nameEditText = findViewById(R.id.edit_text_workflow_name)
        editorMoreButton = findViewById(R.id.btn_editor_more)
        undoButton = findViewById(R.id.button_undo_edit)
        executeButton = findViewById(R.id.button_execute_workflow)
        recyclerView = findViewById(R.id.recycler_view_action_steps)
        configureExecuteButtonShadow()
    }

    private fun setupExecutionTracker() {
        executionTracker = WorkflowEditorExecutionTracker(
            isWorkflowRunning = WorkflowExecutor::isRunning,
            stopWorkflowExecution = WorkflowExecutor::stopExecution,
            updateExecuteButton = ::updateExecuteButton,
            highlightStep = ::highlightStep,
            highlightStepAsFailed = ::highlightStepAsFailed,
            clearHighlight = ::clearHighlight
        )
    }

    private fun setupNavigation() {
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar_editor)
        toolbar.setNavigationOnClickListener { handleExitRequest() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleExitRequest()
            }
        })
    }

    private fun setupInspectorInsertController() {
        inspectorInsertController = WorkflowInspectorInsertController(
            activity = this,
            actionSteps = actionSteps,
            recyclerView = recyclerView,
            onBeforeStepsChanged = { pushUndoSnapshot() },
            onStepsChanged = { recalculateAndNotify() }
        )
        inspectorInsertController.register()
    }

    private fun setupRecyclerFocusDismiss() {
        recyclerView.setOnTouchListener { _, _ ->
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(nameEditText.windowToken, 0)
            nameEditText.clearFocus()
            false
        }
    }

    private fun setupPickerHandler() {
        pickerHandler = PickerHandler(
            activity = this,
            appPickerLauncher = generalIntentLauncher,
            filePickerLauncher = filePickerLauncher,
            mediaPickerLauncher = mediaPickerLauncher,
            directoryPickerLauncher = directoryPickerLauncher,
            generalIntentLauncher = generalIntentLauncher,
            onUpdateParameters = { params ->
                currentEditorSheet?.updateParametersAndRebuildUi(params)
            }
        )
    }

    private fun restoreOrLoadEditorState(savedInstanceState: Bundle?) {
        if (savedInstanceState != null) {
            restoreEditorState(savedInstanceState)
            return
        }
        loadWorkflowData()
    }

    private fun restoreEditorState(savedInstanceState: Bundle) {
        restoreSavedSteps(savedInstanceState)
        nameEditText.setText(savedInstanceState.getString(STATE_WORKFLOW_NAME))
        restoreCurrentWorkflow()
        currentWorkflow?.let { initialWorkflowJson = gson.toJson(getCurrentWorkflowState()) }
        recalculateAndNotify()
    }

    private fun restoreSavedSteps(savedInstanceState: Bundle) {
        savedInstanceState.getParcelableArrayListCompat<ActionStep>(STATE_TRIGGER_STEPS)?.let {
            triggerSteps.clear()
            triggerSteps.addAll(it)
        }
        savedInstanceState.getParcelableArrayListCompat<ActionStep>(STATE_ACTION_STEPS)?.let {
            actionSteps.clear()
            actionSteps.addAll(it)
        }
    }

    private fun restoreCurrentWorkflow() {
        currentWorkflow = editingWorkflowId()?.let(workflowManager::getWorkflow)
        executionTracker.syncExecutionUiForWorkflow(currentWorkflow)
    }

    private fun setupPrimaryActions() {
        findViewById<Button>(R.id.button_add_action).setOnClickListener {
            showActionPicker(isTriggerPicker = false)
        }
        findViewById<Button>(R.id.button_save_workflow).setOnClickListener { saveWorkflow(false) }
        executeButton.setOnClickListener { handleExecuteButtonClick() }
        executeButton.setOnLongClickListener { handleExecuteButtonLongClick() }
        editorMoreButton.setOnClickListener { showEditorMoreOptionsSheet() }
    }

    private fun handleExecuteButtonClick() {
        if (executionTracker.isCurrentWorkflowExecuting()) {
            executionTracker.stopTrackedWorkflowExecution()
            return
        }

        val workflowToExecute = buildWorkflowForExecution() ?: return
        executeWorkflow(workflowToExecute)
    }

    private fun handleExecuteButtonLongClick(): Boolean {
        if (executionTracker.isCurrentWorkflowExecuting()) {
            return true
        }
        showExecuteDelayedMenu()
        return true
    }

    private fun observeExecutionState() {
        executionTracker.observe(lifecycleScope, ExecutionStateBus.stateFlow)
    }

    private fun hasUnsavedChanges(): Boolean {
        val currentJson = gson.toJson(getCurrentWorkflowState())
        return currentJson != initialWorkflowJson
    }

    private fun getCurrentWorkflowState(): Workflow {
        return currentWorkflow?.copy(
            name = nameEditText.text.toString(),
            triggers = triggerSteps.toList(),
            steps = actionSteps.toList()
        ) ?: Workflow(
            id = UUID.randomUUID().toString(),
            name = nameEditText.text.toString(),
            triggers = triggerSteps.toList(),
            steps = actionSteps.toList()
        )
    }

    private fun createDraftWorkflow(name: String = nameEditText.text.toString().trim()): Workflow {
        return Workflow(
            id = UUID.randomUUID().toString(),
            name = name,
            triggers = triggerSteps.toList(),
            steps = actionSteps.toList(),
            cardIconRes = WorkflowVisuals.defaultIconResName(),
            cardThemeColor = WorkflowVisuals.randomThemeColorHex()
        )
    }

    private fun getAllEditableSteps(): List<ActionStep> = triggerSteps + actionSteps

    private fun setupUndoControls() {
        undoButton.setOnClickListener { undoLastEdit() }
        nameEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                if (!suppressUndoCapture && nameEditText.hasFocus() && !nameEditSnapshotCaptured) {
                    pushUndoSnapshot()
                    nameEditSnapshotCaptured = true
                }
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) = Unit
        })
        nameEditText.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                nameEditSnapshotCaptured = false
            }
        }
        updateUndoButtonState()
    }

    private fun pushUndoSnapshot() {
        if (suppressUndoCapture) return
        pushUndoSnapshot(createEditorSnapshot())
    }

    private fun pushUndoSnapshot(snapshot: EditorSnapshot) {
        undoStack.addLast(snapshot)
        while (undoStack.size > MAX_UNDO_STEPS) {
            undoStack.removeFirst()
        }
        updateUndoButtonState()
    }

    private fun undoLastEdit() {
        val snapshot = undoStack.pollLast() ?: return
        suppressUndoCapture = true
        currentWorkflow = snapshot.workflow?.let(::copyWorkflow)
        nameEditText.setText(snapshot.workflowName)
        triggerSteps.clear()
        triggerSteps.addAll(copySteps(snapshot.triggerSteps))
        actionSteps.clear()
        actionSteps.addAll(copySteps(snapshot.actionSteps))
        suppressUndoCapture = false
        nameEditSnapshotCaptured = false

        recalculateAndNotify()
        val workflowId = currentWorkflow?.id
        updateExecuteButton(workflowId != null && WorkflowExecutor.isRunning(workflowId))
        updateUndoButtonState()
    }

    private fun updateUndoButtonState() {
        val canUndo = undoStack.isNotEmpty()
        undoButton.isEnabled = canUndo
        undoButton.alpha = if (canUndo) 1f else 0.38f
    }

    private fun createEditorSnapshot(): EditorSnapshot {
        return EditorSnapshot(
            workflow = currentWorkflow?.let(::copyWorkflow),
            workflowName = nameEditText.text.toString(),
            triggerSteps = copySteps(triggerSteps),
            actionSteps = copySteps(actionSteps)
        )
    }

    private fun copyWorkflow(workflow: Workflow): Workflow {
        return workflow.copy(
            triggers = copySteps(workflow.triggers),
            steps = copySteps(workflow.steps),
            tags = workflow.tags.toList()
        )
    }

    private fun copySteps(steps: List<ActionStep>): List<ActionStep> {
        return steps.map { step ->
            step.copy(
                parameters = deepCopyParameters(step.parameters),
                indentationLevel = step.indentationLevel
            )
        }
    }

    private fun deepCopyParameters(parameters: Map<String, Any?>): Map<String, Any?> {
        return parameters.mapValues { (_, value) -> deepCopyValue(value) }
    }

    private fun deepCopyValue(value: Any?): Any? {
        return when (value) {
            is Map<*, *> -> value.entries.associate { (key, mapValue) ->
                key.toString() to deepCopyValue(mapValue)
            }
            is List<*> -> value.map { item -> deepCopyValue(item) }
            else -> value
        }
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
     * 创建执行高亮动画（代码方式，避免 Android 13 的 AnimatorInflater bug）
     */
    private fun createExecutionHighlightAnimator(target: MaterialCardView): Animator {
        val colorSurface = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface, 0)
        val colorSurfaceContainerHigh = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceContainerHigh, 0)

        val colorAnimator = ValueAnimator.ofArgb(colorSurface, colorSurfaceContainerHigh).apply {
            duration = 700
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            addUpdateListener { animation ->
                target.setCardBackgroundColor(animation.animatedValue as Int)
            }
        }

        val alphaAnimator = ObjectAnimator.ofFloat(target, View.ALPHA, 1.0f, 0.7f).apply {
            duration = 700
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
        }

        val scaleXAnimator = ObjectAnimator.ofFloat(target, View.SCALE_X, 1.0f, 1.02f).apply {
            duration = 700
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
        }

        val scaleYAnimator = ObjectAnimator.ofFloat(target, View.SCALE_Y, 1.0f, 1.02f).apply {
            duration = 700
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
        }

        return AnimatorSet().apply {
            playTogether(colorAnimator, alphaAnimator, scaleXAnimator, scaleYAnimator)
        }
    }

    /**
     * 创建执行错误动画（代码方式，避免 Android 13 的 AnimatorInflater bug）
     */
    private fun createExecutionErrorAnimator(target: MaterialCardView): Animator {
        val colorSurface = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface, 0)
        val colorErrorContainer = MaterialColors.getColor(this, com.google.android.material.R.attr.colorErrorContainer, 0)

        val colorAnimator = ValueAnimator.ofArgb(colorSurface, colorErrorContainer).apply {
            duration = 200
            repeatCount = 2
            repeatMode = ValueAnimator.REVERSE
            addUpdateListener { animation ->
                target.setCardBackgroundColor(animation.animatedValue as Int)
            }
        }

        val translationXAnimator = ObjectAnimator.ofFloat(target, View.TRANSLATION_X, -10f, 10f).apply {
            duration = 50
            repeatCount = 5
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
        }

        return AnimatorSet().apply {
            playTogether(colorAnimator, translationXAnimator)
        }
    }

    /**
     * 使用 LinearSmoothScroller 实现更平滑的滚动，并在滚动结束后应用动画。
     * @param stepIndex 目标步骤的索引。
     * @param animatorRes 要应用的动画资源ID（已弃用参数，保留兼容性）。
     * @param isError 是否为错误高亮。
     */
    private fun smoothScrollToPositionAndHighlight(stepIndex: Int, animatorRes: Int, isError: Boolean = false) {
        val adapterPosition = stepIndex + 1
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
                    // 使用代码创建动画，避免 Android 13 的 AnimatorInflater bug
                    executionAnimator = if (isError) {
                        createExecutionErrorAnimator(cardView)
                    } else {
                        createExecutionHighlightAnimator(cardView)
                    }.apply {
                        start()
                    }
                }
            }
        }

        smoothScroller.targetPosition = adapterPosition
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
            val cardView = it.findViewById<MaterialCardView>(R.id.step_card_view)
            cardView?.apply {
                alpha = 1.0f
                scaleX = 1.0f
                scaleY = 1.0f
                translationX = 0f
                setCardBackgroundColor(
                    MaterialColors.getColor(
                        this@WorkflowEditorActivity,
                        com.google.android.material.R.attr.colorSurface,
                        0
                    )
                )
            }
        }
        currentlyExecutingViewHolder = null
    }


    private fun executeWorkflow(workflow: Workflow) {
        val missingPermissions = PermissionManager.getMissingPermissions(this, workflow)
        if (missingPermissions.isEmpty()) {
            executionTracker.beginExecutionTracking(workflow.id)
            toast(getString(R.string.editor_toast_execution_start, workflow.name))
            val executionInstanceId = WorkflowExecutor.execute(
                workflow = workflow,
                context = this,
                triggerStepId = workflow.manualTrigger()?.id
            )
            executionTracker.finishExecutionLaunch(executionInstanceId)
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
    private fun showActionEditor(module: ActionModule, existingStep: ActionStep?, position: Int, focusedInputId: String?) {
        val contextPosition = if (position != -1) position else actionSteps.size
        val namedVariableNames = magicVariableCatalogBuilder.buildNamedVariables(actionSteps, contextPosition)
            .values.flatten().map { it.variableName }


        // 在打开编辑器前，保存旧的变量名
        if (existingStep != null && module.id == CreateVariableModule().id) {
            oldVariableName = existingStep.parameters["variableName"] as? String
        } else {
            oldVariableName = null
        }


        val editor = ActionEditorSheet.newInstance(module, existingStep, focusedInputId, getAllEditableSteps(), namedVariableNames)
        currentEditorSheet = editor

        editor.onSave = { newStepData ->
            pushUndoSnapshot()
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

        editor.onMagicVariableRequested = { inputId, currentParams ->
            val stepPositionForContext = triggerSteps.size + if (position != -1) position else actionSteps.size
            showMagicVariablePicker(stepPositionForContext, inputId, module, currentParams)
        }

        editor.onStartActivityForResult = { intent, callback ->
            pickerHandler?.launchIntentForResult(intent, callback)
        }

        // 设置 Picker 监听器
        editor.setOnPickerRequestedListener { inputDef ->
            pickerHandler?.handle(inputDef)
        }

        editor.show(supportFragmentManager, "ActionEditor")
    }

    private fun showTriggerEditor(module: ActionModule, existingStep: ActionStep?, position: Int, focusedInputId: String?) {
        val editor = ActionEditorSheet.newInstance(module, existingStep, focusedInputId, getAllEditableSteps(), emptyList())
        currentEditorSheet = editor

        editor.onSave = { newStepData ->
            pushUndoSnapshot()
            if (position != -1) {
                if (focusedInputId != null) {
                    val updatedParams = triggerSteps[position].parameters.toMutableMap()
                    updatedParams.putAll(newStepData.parameters)
                    triggerSteps[position] = triggerSteps[position].copy(parameters = updatedParams)
                } else {
                    triggerSteps[position] = triggerSteps[position].copy(parameters = newStepData.parameters)
                }
            } else {
                val stepsToAdd = module.createSteps()
                val configuredFirstStep = stepsToAdd.first().copy(parameters = newStepData.parameters)
                triggerSteps.add(configuredFirstStep)
                if (stepsToAdd.size > 1) {
                    triggerSteps.addAll(stepsToAdd.subList(1, stepsToAdd.size))
                }
            }
            recalculateAndNotify()
        }

        editor.onMagicVariableRequested = { _, _ -> }

        editor.onStartActivityForResult = { intent, callback ->
            pickerHandler?.launchIntentForResult(intent, callback)
        }

        editor.setOnPickerRequestedListener { inputDef ->
            pickerHandler?.handle(inputDef)
        }

        editor.show(supportFragmentManager, "TriggerEditor")
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
            toast(getString(R.string.editor_toast_variable_reference_updated, oldVariableName))
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


    private fun showMagicVariablePicker(editingStepPosition: Int, targetInputId: String, editingModule: ActionModule, currentParams: Map<String, Any?>?) {
        val allSteps = getAllEditableSteps()
        val prefs = getSharedPreferences("vFlowPrefs", MODE_PRIVATE)
        val enableTypeFilter = prefs.getBoolean("enableTypeFilter", false)
        val pickerModel = magicVariableCatalogBuilder.buildPickerModel(
            editingStepPosition = editingStepPosition,
            targetInputId = targetInputId,
            editingModule = editingModule,
            currentParams = currentParams,
            allSteps = allSteps,
            triggerStepCount = triggerSteps.size,
            actionSteps = actionSteps,
            enableTypeFilter = enableTypeFilter,
            findEnclosingLoopStartStep = ::findEnclosingLoopStartStep,
            loopPairingId = LOOP_PAIRING_ID,
            forEachPairingId = FOREACH_PAIRING_ID,
            loopStartId = LOOP_START_ID,
            forEachStartId = FOREACH_START_ID
        )

        if (pickerModel == null) {
            toast(getString(R.string.editor_toast_input_definition_not_found, targetInputId))
            return
        }

        // --- 启动选择器 ---
        val picker = MagicVariablePickerSheet.newInstance(
            pickerModel.stepVariables,
            pickerModel.namedVariables,
            acceptsMagicVariable = pickerModel.acceptsMagicVariable,
            acceptsNamedVariable = pickerModel.acceptsNamedVariable,
            acceptedMagicVariableTypes = pickerModel.acceptedMagicVariableTypes,
            enableTypeFilter = pickerModel.enableTypeFilter
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

    private fun handleTriggerParameterPillClick(position: Int, parameterId: String) {
        val step = triggerSteps[position]
        val module = ModuleRegistry.getModule(step.moduleId) ?: return
        showTriggerEditor(module, step, position, parameterId)
    }

    private fun setupRecyclerView() {
        actionStepAdapter = ActionStepAdapter(
            actionSteps,
            visiblePositionProvider = { steps -> steps.indices.toList() },
            displayIndexProvider = { adapterPosition, _ -> adapterPosition },
            getAllSteps = { getAllEditableSteps() },
            getTriggerSteps = { getTriggerSteps() },
            onAddTriggerClick = { showTriggerPickerAtPosition(getTriggerInsertPosition()) },
            onEditTriggerClick = { position, inputId ->
                val step = triggerSteps[position]
                val module = ModuleRegistry.getModule(step.moduleId) ?: return@ActionStepAdapter
                showTriggerEditor(module, step, position, inputId)
            },
            onDeleteTriggerClick = { position ->
                val step = triggerSteps[position]
                ModuleRegistry.getModule(step.moduleId)?.let { module ->
                    val undoSnapshot = createEditorSnapshot()
                    if (module.onStepDeleted(triggerSteps, position)) {
                        pushUndoSnapshot(undoSnapshot)
                        recalculateAndNotify()
                    }
                }
            },
            onEditClick = { position, inputId ->
                val step = actionSteps[position]
                val module = ModuleRegistry.getModule(step.moduleId)
                if (module == null) return@ActionStepAdapter

                showActionEditor(module, step, position, inputId)
            },
            onDeleteClick = { position ->
                val step = actionSteps[position]
                ModuleRegistry.getModule(step.moduleId)?.let { module ->
                    val undoSnapshot = createEditorSnapshot()
                    if (module.onStepDeleted(actionSteps, position)) {
                        pushUndoSnapshot(undoSnapshot)
                        recalculateAndNotify()
                    }
                }
            },
            // 双击复制的回调实现
            onDuplicateClick = { position ->
                duplicateStepOrBlock(position)
            },
            // 在下方插入的回调实现
            onInsertBelowClick = { position ->
                showActionPickerAtPosition(position + 1)
            },
            onTriggerParameterPillClick = { position, parameterId ->
                handleTriggerParameterPillClick(position, parameterId)
            },
            onParameterPillClick = { position, parameterId ->
                handleParameterPillClick(position, parameterId)
            },
            onStartActivityForResult = { position, intent, callback ->
                pickerHandler?.launchIntentForResult(intent, callback)
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

        // 使用现有的逻辑找到块的范围
        val (blockStart, blockEnd) = findBlockRangeInList(actionSteps, position)

        // 创建副本列表
        val stepsToDuplicate = actionSteps.subList(blockStart, blockEnd + 1).map { step ->
            // 必须为副本生成新的 UUID，否则会导致变量引用混乱和 DiffUtil 错误
            step.copy(id = UUID.randomUUID().toString())
        }

        // 插入到原块的后面
        val insertPosition = blockEnd + 1
        pushUndoSnapshot()
        actionSteps.addAll(insertPosition, stepsToDuplicate)

        recalculateAndNotify()
        toast(getString(R.string.editor_toast_steps_duplicated, stepsToDuplicate.size))

        // 滚动到新复制的位置
        recyclerView.smoothScrollToPosition(insertPosition)
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
                val actualFrom = actionStepAdapter.getActualPosition(fromPosition)
                val actualTo = actionStepAdapter.getActualPosition(toPosition)
                if (actualFrom != null && actualTo != null) {
                    Collections.swap(actionSteps, actualFrom, actualTo)
                    actionStepAdapter.notifyItemMoved(fromPosition, toPosition)
                }
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                    viewHolder?.let {
                        dragStartPosition = actionStepAdapter.getActualPosition(it.adapterPosition) ?: -1
                        listBeforeDrag = actionSteps.toList()
                        dragUndoSnapshot = createEditorSnapshot()
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

                val originalList = listBeforeDrag ?: run {
                    dragUndoSnapshot = null
                    return
                }
                val fromPos = dragStartPosition
                val toPos = actionStepAdapter.getActualPosition(viewHolder.adapterPosition) ?: -1

                // 重置状态
                listBeforeDrag = null
                dragStartPosition = -1
                val undoSnapshot = dragUndoSnapshot
                dragUndoSnapshot = null

                // 无效移动检查
                if (fromPos < 0 || toPos < 0 || fromPos == toPos) {
                    // 延迟执行，避免 RecyclerView 布局冲突
                    this@WorkflowEditorActivity.recyclerView.post { recalculateAndNotify() }
                    return
                }

                // 执行积木块移动
                val newList = moveBlockInList(originalList, fromPos, toPos)

                if (isBlockStructureValid(newList)) {
                    val updatedList = WorkflowJumpReferenceUpdater.remapAfterReorder(originalList, newList)
                    if (updatedList != originalList && undoSnapshot != null) {
                        pushUndoSnapshot(undoSnapshot)
                    }
                    actionSteps.clear()
                    actionSteps.addAll(updatedList)
                } else {
                    toast(R.string.editor_toast_invalid_move)
                    actionSteps.clear()
                    actionSteps.addAll(originalList)
                }
                // 延迟执行，避免 RecyclerView 布局冲突
                this@WorkflowEditorActivity.recyclerView.post { recalculateAndNotify() }
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

                val firstActionIndex = originalList.indexOfFirst { !isTriggerStep(it) }
                if (firstActionIndex == -1 || fromPos < firstActionIndex || toPos < firstActionIndex) {
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
                        (adjustedTargetPos + 1).coerceIn(firstActionIndex, tempList.size)
                    } else {
                        // 向上移动：插入到目标位置
                        adjustedTargetPos.coerceIn(firstActionIndex, tempList.size)
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
        val bottomButtonContainer = findViewById<View>(R.id.bottom_button_container)

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
        val workflowId = editingWorkflowId()
        if (workflowId != null) {
            currentWorkflow = workflowManager.getWorkflow(workflowId)
            currentWorkflow?.let {
                nameEditText.setText(it.name)
                triggerSteps.clear()
                actionSteps.clear()
                triggerSteps.addAll(it.triggers)
                actionSteps.addAll(it.steps)
            }
        }
        executionTracker.syncExecutionUiForWorkflow(currentWorkflow)

        val originalSize = triggerSteps.size + actionSteps.size
        val cleanedTriggerSteps = triggerSteps.filter { ModuleRegistry.getModule(it.moduleId) != null }
        val cleanedActionSteps = actionSteps.filter { ModuleRegistry.getModule(it.moduleId) != null }
        val removedCount = originalSize - cleanedTriggerSteps.size - cleanedActionSteps.size

        if (removedCount > 0) {
            triggerSteps.clear()
            triggerSteps.addAll(cleanedTriggerSteps)
            actionSteps.clear()
            actionSteps.addAll(cleanedActionSteps)
            toast(getString(R.string.editor_toast_unknown_modules_removed, removedCount))
        }
        recalculateAndNotify()
        initialWorkflowJson = gson.toJson(getCurrentWorkflowState())
        maybePromptEnumMigration()
    }

    override fun onDestroy() {
        inspectorInsertController.unregister()
        delayedExecuteHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun showExecuteDelayedMenu() {
        val workflow = buildWorkflowForExecution() ?: return
        val popupMenu = PopupMenu(this, executeButton)
        popupMenu.inflate(R.menu.workflow_execute_delayed_menu)
        popupMenu.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.menu_execute_in_5s -> {
                    scheduleDelayedExecution(workflow, 5_000L)
                    true
                }
                R.id.menu_execute_in_15s -> {
                    scheduleDelayedExecution(workflow, 15_000L)
                    true
                }
                R.id.menu_execute_in_1min -> {
                    scheduleDelayedExecution(workflow, 60_000L)
                    true
                }
                else -> false
            }
        }
        popupMenu.show()
    }

    private fun buildWorkflowForExecution(name: String = nameEditText.text.toString().trim()): Workflow? {
        if (name.isBlank()) {
            toast(R.string.editor_toast_workflow_name_empty)
            return null
        }
        if (currentWorkflow == null) {
            currentWorkflow = createDraftWorkflow(name)
        }
        return currentWorkflow!!.copy(
            name = name,
            triggers = triggerSteps.toList(),
            steps = actionSteps.toList()
        )
    }

    private fun scheduleDelayedExecution(workflow: Workflow, delayMs: Long) {
        val delayText = when (delayMs) {
            5_000L -> getString(R.string.workflow_execute_delay_5s)
            15_000L -> getString(R.string.workflow_execute_delay_15s)
            60_000L -> getString(R.string.workflow_execute_delay_1min)
            else -> getString(R.string.workflow_execute_delay_seconds, delayMs / 1000)
        }
        toast(getString(R.string.workflow_execute_delayed, delayText, workflow.name))
        delayedExecuteHandler.postDelayed({
            val missingPermissions = PermissionManager.getMissingPermissions(this, workflow)
            if (missingPermissions.isEmpty()) {
                WorkflowExecutor.execute(
                    workflow = workflow,
                    context = this,
                    triggerStepId = workflow.manualTrigger()?.id
                )
            }
        }, delayMs)
    }

    private fun updateExecuteButton(isRunning: Boolean) {
        if (isRunning) {
            executeButton.contentDescription = getString(R.string.editor_button_stop)
            executeButton.setImageResource(R.drawable.rounded_pause_24)
        } else {
            executeButton.contentDescription = getString(R.string.workflow_editor_execute)
            executeButton.setImageResource(R.drawable.rounded_play_arrow_fill_24)
        }
    }

    private fun configureExecuteButtonShadow() {
        // FAB has both base elevation and state-driven translationZ/elevation behavior.
        // Clear all of them so we keep the FAB interaction/shape without the shadow.
        executeButton.stateListAnimator = null
        executeButton.elevation = 0f
        executeButton.translationZ = 0f
        executeButton.compatElevation = 0f
        executeButton.compatHoveredFocusedTranslationZ = 0f
        executeButton.compatPressedTranslationZ = 0f
    }

    private fun editingWorkflowId(): String? = intent.getStringExtra(EXTRA_WORKFLOW_ID)

    private fun isTriggerStep(step: ActionStep): Boolean {
        return ModuleRegistry.getModule(step.moduleId)?.metadata?.getResolvedCategoryId() == ModuleCategories.TRIGGER
    }

    private fun getTriggerSteps(): List<ActionStep> = triggerSteps.toList()

    private fun getTriggerInsertPosition(): Int = triggerSteps.size

    private fun ensureAtLeastOneTrigger() {
        if (triggerSteps.isNotEmpty()) return
        ModuleRegistry.getModule("vflow.trigger.manual")?.let {
            triggerSteps.addAll(it.createSteps())
        }
    }

    private fun showActionPicker(isTriggerPicker: Boolean) {
        val picker = ActionPickerSheet()
        picker.arguments = Bundle().apply {
            putBoolean("is_trigger_picker", isTriggerPicker)
        }

        picker.onActionSelected = { module ->
            if (module.metadata.getResolvedCategoryId() == ModuleCategories.TEMPLATE) {
                val newSteps = module.createSteps()
                pushUndoSnapshot()
                actionSteps.addAll(newSteps)
                recalculateAndNotify()
            } else if (isTriggerPicker) {
                showTriggerPickerAtPosition(getTriggerInsertPosition(), module)
            } else {
                showActionEditor(module, null, -1, null)
            }
        }
        picker.show(supportFragmentManager, "ActionPicker")
    }

    private fun showTriggerPicker() {
        showActionPicker(isTriggerPicker = true)
    }

    private fun showTriggerPickerAtPosition(insertPosition: Int, presetModule: ActionModule? = null) {
        if (presetModule != null) {
            showTriggerEditorAtPosition(presetModule, insertPosition)
            return
        }

        val picker = ActionPickerSheet()
        picker.arguments = Bundle().apply {
            putBoolean("is_trigger_picker", true)
        }

        picker.onActionSelected = { module ->
            showTriggerEditorAtPosition(module, insertPosition)
        }
        picker.show(supportFragmentManager, "TriggerPicker")
    }

    /**
     * 在指定位置显示模块选择器，选择模块后插入到该位置
     */
    private fun showActionPickerAtPosition(insertPosition: Int) {
        val picker = ActionPickerSheet()
        picker.arguments = Bundle().apply {
            putBoolean("is_trigger_picker", false)
        }

        picker.onActionSelected = { module ->
            if (module.metadata.getResolvedCategoryId() == ModuleCategories.TEMPLATE) {
                val newSteps = module.createSteps()
                pushUndoSnapshot()
                actionSteps.addAll(insertPosition, newSteps)
                recalculateAndNotify()
            } else {
                // 显示参数编辑器，传入插入位置
                showActionEditorAtPosition(module, insertPosition)
            }
        }
        picker.show(supportFragmentManager, "ActionPicker")
    }

    /**
     * 在指定位置显示参数编辑器，插入新模块
     */
    private fun showActionEditorAtPosition(module: ActionModule, insertPosition: Int) {
        val namedVariableNames = magicVariableCatalogBuilder.buildNamedVariables(actionSteps, insertPosition)
            .values.flatten().map { it.variableName }

        val editor = ActionEditorSheet.newInstance(module, null, null, getAllEditableSteps(), namedVariableNames)
        currentEditorSheet = editor

        editor.onSave = { newStepData ->
            pushUndoSnapshot()
            val stepsToAdd = module.createSteps()
            val configuredFirstStep = stepsToAdd.first().copy(parameters = newStepData.parameters)
            actionSteps.add(insertPosition, configuredFirstStep)
            if (stepsToAdd.size > 1) {
                actionSteps.addAll(insertPosition + 1, stepsToAdd.subList(1, stepsToAdd.size))
            }
            recalculateAndNotify()
        }

        editor.onMagicVariableRequested = { inputId, currentParams ->
            showMagicVariablePicker(triggerSteps.size + insertPosition, inputId, module, currentParams)
        }

        editor.onStartActivityForResult = { intent, callback ->
            pickerHandler?.launchIntentForResult(intent, callback)
        }

        editor.setOnPickerRequestedListener { inputDef ->
            pickerHandler?.handle(inputDef)
        }

        editor.show(supportFragmentManager, "ActionEditor")
    }

    private fun showTriggerEditorAtPosition(module: ActionModule, insertPosition: Int) {
        val editor = ActionEditorSheet.newInstance(module, null, null, getAllEditableSteps(), emptyList())
        currentEditorSheet = editor

        editor.onSave = { newStepData ->
            pushUndoSnapshot()
            val stepsToAdd = module.createSteps()
            val configuredFirstStep = stepsToAdd.first().copy(parameters = newStepData.parameters)
            triggerSteps.add(insertPosition, configuredFirstStep)
            if (stepsToAdd.size > 1) {
                triggerSteps.addAll(insertPosition + 1, stepsToAdd.subList(1, stepsToAdd.size))
            }
            recalculateAndNotify()
        }

        editor.onMagicVariableRequested = { _, _ -> }

        editor.onStartActivityForResult = { intent, callback ->
            pickerHandler?.launchIntentForResult(intent, callback)
        }

        editor.setOnPickerRequestedListener { inputDef ->
            pickerHandler?.handle(inputDef)
        }

        editor.show(supportFragmentManager, "TriggerEditor")
    }


    private fun recalculateAndNotify() {
        ensureAtLeastOneTrigger()
        recalculateAllIndentation()
        actionStepAdapter.notifyDataSetChanged()
    }

    private fun maybePromptEnumMigration() {
        val preview = WorkflowEnumMigration.scan(getCurrentWorkflowState()) ?: return

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_workflow_enum_migration_title)
            .setMessage(
                getString(
                    R.string.dialog_workflow_enum_migration_message,
                    preview.affectedStepCount,
                    preview.affectedFieldCount
                )
            )
            .setPositiveButton(R.string.common_yes) { _, _ ->
                pushUndoSnapshot()
                triggerSteps.clear()
                triggerSteps.addAll(preview.migratedWorkflow.triggers)
                actionSteps.clear()
                actionSteps.addAll(preview.migratedWorkflow.steps)
                recalculateAndNotify()
            }
            .setNegativeButton(R.string.common_no, null)
            .show()
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
                    toast(validationResult.errorMessage ?: "Unknown validation error")
                    return // 验证失败，停止保存
                }
            }
        }

        val name = nameEditText.text.toString().trim()
        if (name.isBlank()) {
            toast(R.string.editor_toast_workflow_name_empty)
        } else {
            val isNewWorkflow = currentWorkflow == null
            val workflowToSave = currentWorkflow?.copy(
                name = name,
                triggers = triggerSteps.toList(),
                steps = actionSteps.toList(),
                isEnabled = currentWorkflow?.isEnabled ?: true
            ) ?: createDraftWorkflow(name)
            workflowManager.saveWorkflow(workflowToSave)

            // 如果是第一次保存新工作流，则更新当前Activity的状态
            if (isNewWorkflow) {
                currentWorkflow = workflowToSave
            }

            executionTracker.syncExecutionUiForWorkflow(workflowToSave)

            toast(R.string.editor_toast_workflow_saved)

            if (shouldFinish) {
                finish()
            } else {
                initialWorkflowJson = gson.toJson(workflowToSave)
            }
        }
    }

    private fun showEditorMoreOptionsSheet() {
        if (currentWorkflow == null) {
            currentWorkflow = createDraftWorkflow()
        }
        val sheet = EditorMoreOptionsSheet()
        sheet.workflow = currentWorkflow
        sheet.onAiGenerateClicked = {
            dismissAllSheets()
            showAiCreationSheet()
        }
        sheet.onUiInspectorClicked = {
            dismissAllSheets()
            inspectorInsertController.startInspector()
        }
        sheet.onMetadataSaved = { updatedWorkflow ->
            currentWorkflow = updatedWorkflow
            if (updatedWorkflow.name.isNotBlank()) {
                workflowManager.saveWorkflow(updatedWorkflow)
            }
        }
        sheet.show(supportFragmentManager, "EditorMoreOptionsSheet")
    }

    private fun dismissAllSheets() {
        supportFragmentManager.fragments.forEach { fragment ->
            if (fragment is BottomSheetDialogFragment) {
                fragment.dismiss()
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
        val undoSnapshot = createEditorSnapshot()
        var undoRecorded = false
        fun recordGeneratedUndo() {
            if (!undoRecorded) {
                pushUndoSnapshot(undoSnapshot)
                undoRecorded = true
            }
        }

        // 如果生成了名字且当前名字为空，则使用生成的名字
        if (nameEditText.text.isBlank() && workflow.name.isNotBlank()) {
            recordGeneratedUndo()
            suppressUndoCapture = true
            nameEditText.setText(workflow.name)
            suppressUndoCapture = false
        }

        // 覆盖现有步骤（策略可以是追加，但通常用户希望重写）
        if (triggerSteps.isNotEmpty() || actionSteps.isNotEmpty()) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.editor_dialog_overwrite_title)
                .setMessage(R.string.editor_dialog_overwrite_message)
                .setPositiveButton(R.string.editor_button_overwrite) { _, _ ->
                    recordGeneratedUndo()
                    triggerSteps.clear()
                    actionSteps.clear()
                    triggerSteps.addAll(workflow.triggers)
                    actionSteps.addAll(workflow.steps)
                    recalculateAndNotify()
                    toast(R.string.editor_toast_ai_workflow_applied)
                }
                .setNeutralButton(R.string.editor_button_append_to_end) { _, _ ->
                    recordGeneratedUndo()
                    triggerSteps.addAll(workflow.triggers)
                    actionSteps.addAll(workflow.steps)
                    recalculateAndNotify()
                    toast(R.string.editor_toast_steps_appended)
                }
                .setNegativeButton(R.string.common_cancel, null)
                .show()
        } else {
            recordGeneratedUndo()
            triggerSteps.addAll(workflow.triggers)
            actionSteps.addAll(workflow.steps)
            recalculateAndNotify()
            toast(R.string.editor_toast_ai_workflow_applied)
        }
    }
}

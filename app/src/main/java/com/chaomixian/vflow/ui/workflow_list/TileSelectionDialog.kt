package com.chaomixian.vflow.ui.workflow_list

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.workflow.TileManager
import com.chaomixian.vflow.core.workflow.WorkflowManager
import com.chaomixian.vflow.core.workflow.model.WorkflowTile

class TileSelectionDialog : BottomSheetDialogFragment() {

    companion object {
        const val TAG = "TileSelectionDialog"
        private const val ARG_WORKFLOW_ID = "workflow_id"
        private const val ARG_WORKFLOW_NAME = "workflow_name"

        fun newInstance(workflowId: String, workflowName: String): TileSelectionDialog {
            return TileSelectionDialog().apply {
                arguments = Bundle().apply {
                    putString(ARG_WORKFLOW_ID, workflowId)
                    putString(ARG_WORKFLOW_NAME, workflowName)
                }
            }
        }
    }

    private lateinit var tileManager: TileManager
    private lateinit var workflowManager: WorkflowManager
    private var workflowId: String = ""
    private var workflowName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        workflowId = arguments?.getString(ARG_WORKFLOW_ID) ?: ""
        workflowName = arguments?.getString(ARG_WORKFLOW_NAME) ?: ""
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_tile_selection, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tileManager = TileManager(requireContext())
        workflowManager = WorkflowManager(requireContext())

        val titleText = view.findViewById<TextView>(R.id.text_tile_selection_title)
        val listView = view.findViewById<android.widget.ListView>(R.id.list_tile_selection)

        titleText.text = getString(R.string.tile_selection_title)

        // 加载所有Tile数据
        val tiles = tileManager.getAllTilesWithEmpty()
        val displayItems = tiles.map { tile ->
            val tileName = "Tile ${tile.tileIndex + 1}"
            val workflow = tile.workflowId?.let { workflowManager.getWorkflow(it) }
            val workflowDisplayName = workflow?.name ?: getString(R.string.tile_empty)
            "$tileName: $workflowDisplayName"
        }

        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_list_item_1,
            displayItems
        )
        listView.adapter = adapter

        listView.setOnItemClickListener { _, _, position, _ ->
            val tile = tiles[position]
            if (tile.workflowId == workflowId) {
                // 如果当前工作流已分配到此Tile，取消分配
                tileManager.removeTile(tile.tileIndex)
                android.widget.Toast.makeText(
                    requireContext(),
                    getString(R.string.tile_removed, tile.tileIndex + 1),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            } else {
                // 分配到此Tile（先移除之前可能存在的分配）
                tileManager.removeTileByWorkflowId(workflowId)
                tileManager.saveTile(WorkflowTile(tile.tileIndex, workflowId))
                android.widget.Toast.makeText(
                    requireContext(),
                    getString(R.string.tile_added, tile.tileIndex + 1),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
            dismiss()
        }

        view.findViewById<View>(R.id.button_cancel).setOnClickListener {
            dismiss()
        }
    }
}

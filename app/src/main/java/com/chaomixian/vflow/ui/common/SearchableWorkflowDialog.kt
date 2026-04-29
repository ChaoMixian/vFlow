package com.chaomixian.vflow.ui.common

import android.content.Context
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import com.chaomixian.vflow.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder

data class WorkflowDialogItem(
    val id: String,
    val name: String
)

object SearchableWorkflowDialog {

    fun show(
        context: Context,
        @StringRes titleResId: Int,
        items: List<WorkflowDialogItem>,
        onSelected: (WorkflowDialogItem) -> Unit,
        onCancelled: (() -> Unit)? = null
    ): AlertDialog = show(
        context = context,
        title = context.getString(titleResId),
        items = items,
        onSelected = onSelected,
        onCancelled = onCancelled
    )

    fun show(
        context: Context,
        title: String,
        items: List<WorkflowDialogItem>,
        onSelected: (WorkflowDialogItem) -> Unit,
        onCancelled: (() -> Unit)? = null
    ): AlertDialog {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_searchable_workflow_list, null)
        val searchView = dialogView.findViewById<SearchView>(R.id.search_view)
        val listView = dialogView.findViewById<ListView>(R.id.list_view)
        val emptyView = dialogView.findViewById<TextView>(R.id.text_empty)

        val adapter = ArrayAdapter(
            context,
            android.R.layout.simple_list_item_1,
            items.map { it.name }.toMutableList()
        )
        val filteredItems = items.toMutableList()

        listView.emptyView = emptyView
        listView.adapter = adapter

        lateinit var dialog: AlertDialog

        fun updateFilter(query: String?) {
            val normalizedQuery = query.orEmpty().trim()
            val nextItems = if (normalizedQuery.isEmpty()) {
                items
            } else {
                items.filter { it.name.contains(normalizedQuery, ignoreCase = true) }
            }
            filteredItems.clear()
            filteredItems.addAll(nextItems)
            adapter.clear()
            adapter.addAll(nextItems.map { it.name })
            adapter.notifyDataSetChanged()
        }

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                updateFilter(query)
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                updateFilter(newText)
                return true
            }
        })

        listView.setOnItemClickListener { _, _, position, _ ->
            filteredItems.getOrNull(position)?.let {
                onSelected(it)
                dialog.dismiss()
            }
        }

        dialog = MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setView(dialogView)
            .setOnCancelListener { onCancelled?.invoke() }
            .show()

        return dialog
    }
}
